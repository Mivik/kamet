package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

typealias TPairPredicate = (Type, Type) -> Boolean

/**
 * If type A be can implicitly cast to type B, they match at least one of the following rules:
 * - (A == B)
 * - ((A == Nothing*) and (B is Pointer))
 * - ((A is Reference) and (A.originalType can be implicitly cast to B))
 * - (A == Nothing)
 *
 * Explicit cast is an extension to implicit cast
 */
internal object CastManager {
	private val case1: TPairPredicate = { a, b -> a == b }
	private val case2: TPairPredicate = { a, b -> a is Type.Pointer && (a.originalType == Type.Nothing) && b.isPointer }
	private val case3: TPairPredicate = { a, b -> a is Type.Reference && canImplicitlyCast(a.originalType, b) }
	private val case4: TPairPredicate = { a, _ -> a == Type.Nothing }
	private val allCases = arrayOf(case1, case2, case3, case4)

	fun canImplicitlyCast(from: Type, to: Type) = allCases.any { it(from, to) }

	private fun implicitCastOrNull(context: Context, from: Value, to: Type): Value? = when {
		from.type is Type.Reference -> implicitCastOrNull(context, from.dereference(context), to)
		from.type == Type.Nothing -> from.also { LLVM.LLVMBuildUnreachable(context.builder) }
		case1(from.type, to) -> from
		case2(from.type, to) -> Value(
			LLVM.LLVMBuildBitCast(context.builder, from.llvm, to.llvm, "pointer_cast"),
			type = to
		)
		else -> null
	}

	fun implicitCast(context: Context, from: Value, to: Type): Value =
		implicitCastOrNull(context, from, to) ?: fail(from, to)

	@Suppress("NOTHING_TO_INLINE")
	inline fun fail(from: Value, to: Type): Nothing = error("Attempt to cast a ${from.type} to $to")

	fun explicitCastOrNull(context: Context, from: Value, dest: Type): Value? {
		implicitCastOrNull(context, from, dest)?.let { return it }
		return when {
			from.type.isReference -> explicitCastOrNull(
				context,
				from.dereference(context),
				dest
			)
			from.type.isPointer && dest.isPointer -> Value(
				LLVM.LLVMBuildBitCast(
					context.builder,
					from.llvm,
					dest.llvm,
					"pointer_cast"
				), dest
			)
			from.type is Type.Primitive && dest is Type.Primitive -> Value(
				LLVM.LLVMBuildCast(
					context.builder, when (val type = from.type) {
						is Type.Primitive.Integral ->
							when (dest) {
								is Type.Primitive.Integral ->
									if (type.sizeInBits > dest.sizeInBits) LLVM.LLVMTrunc
									else type.foldSign(LLVM.LLVMSExt, LLVM.LLVMZExt)
								is Type.Primitive.Real -> type.foldSign(LLVM.LLVMSIToFP, LLVM.LLVMUIToFP)
								is Type.Primitive.Boolean -> LLVM.LLVMTrunc
							}
						is Type.Primitive.Real ->
							when (dest) {
								is Type.Primitive.Integral -> dest.foldSign(LLVM.LLVMFPToSI, LLVM.LLVMFPToUI)
								is Type.Primitive.Real -> when {
									type.sizeInBits > dest.sizeInBits -> LLVM.LLVMFPTrunc
									type.sizeInBits <= dest.sizeInBits -> LLVM.LLVMFPExt
									else -> impossible()
								}
								else -> fail(from, dest)
							}
						is Type.Primitive.Boolean ->
							when (dest) {
								is Type.Primitive.Integral -> dest.foldSign(LLVM.LLVMSExt, LLVM.LLVMZExt)
								is Type.Primitive.Real -> LLVM.LLVMUIToFP
								else -> impossible()
							}
						else -> TODO()
					}, from.llvm, dest.llvm, "primitive_cast"
				), type = dest
			)
			else -> null
		}
	}

	fun explicitCast(context: Context, from: Value, to: Type) = explicitCastOrNull(context, from, to) ?: fail(from, to)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Value.implicitCast(context: Context, to: Type) = CastManager.implicitCast(context, this, to)

@Suppress("NOTHING_TO_INLINE")
internal inline fun Type.canImplicitlyCastTo(to: Type) = CastManager.canImplicitlyCast(this, to)

@Suppress("NOTHING_TO_INLINE")
internal inline fun Type.equivalent(other: Type) = this.canImplicitlyCastTo(other) || other.canImplicitlyCastTo(this)
