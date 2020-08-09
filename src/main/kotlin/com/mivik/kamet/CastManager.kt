package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

typealias TPairPredicate = (Type, Type) -> Boolean

/**
 * Implicit cast (A as B) requires:
 *    (A = B)
 * or ((A = [Type.Nothing]*) and (B is [Type.Pointer]))
 * or ((A is [Type.Reference]) and canImplicitlyCast(referenced type of A, B))
 * or (A = [Type.Nothing])
 * or (A.isPointer) && (B.isPointer) && (A.pointedType = B.pointedType)
 *
 * Explicit cast is an extension to implicit cast
 */
internal object CastManager {
	private val case1: TPairPredicate = { a, b -> a == b }
	private val case2: TPairPredicate =
		{ a, b -> a is Type.Pointer && (a.elementType == Type.Nothing) && b is Type.Pointer }
	private val case3: TPairPredicate = { a, b -> a is Type.Reference && canImplicitlyCast(a.originalType, b) }
	private val case4: TPairPredicate = { a, _ -> a == Type.Nothing }
	private val case5: TPairPredicate = { a, b ->
		a.asPointerOrNull()?.let { aPointer ->
			b.asPointerOrNull()?.let { bPointer ->
				aPointer.elementType == bPointer.elementType
			}
		} ?: false
	}
	private val allCases = arrayOf(case1, case2, case3, case4, case5)

	fun canImplicitlyCast(from: Type, to: Type) = allCases.any { it(from, to) }

	private fun Context.implicitCastOrNull(from: Value, to: Type): Value? =
		when {
			case1(from.type, to) -> from
			case2(from.type, to) || case5(from.type, to) -> to.new(
				LLVM.LLVMBuildBitCast(
					builder,
					from.llvm,
					to.llvm,
					"pointer_cast"
				)
			)
			from.type is Type.Reference -> implicitCastOrNull(from.dereference(), to)
			case4(from.type, to) -> from.also { LLVM.LLVMBuildUnreachable(builder) }
			else -> null
		}

	fun Context.implicitCast(from: Value, to: Type): Value =
		implicitCastOrNull(from, to) ?: fail(from, to)

	@Suppress("NOTHING_TO_INLINE")
	inline fun fail(from: Value, to: Type): Nothing = error("Attempt to cast a ${from.type} into $to")

	fun Context.explicitCastOrNull(from: Value, dest: Type): Value? {
		implicitCastOrNull(from, dest)?.let { return it }
		val fromIsPointer = from.type.isPointer
		val destIsPointer = dest.isPointer
		return when {
			fromIsPointer && destIsPointer ->
				dest.new(
					LLVM.LLVMBuildBitCast(
						builder,
						from.llvm,
						dest.llvm,
						"pointer_cast"
					)
				)
			fromIsPointer && dest is Type.Primitive.Integral ->
				dest.new(
					LLVM.LLVMBuildPtrToInt(
						builder,
						from.llvm,
						dest.llvm,
						"pointer_to_int"
					)
				)
			from.type is Type.Primitive.Integral && destIsPointer -> dest.new(
				LLVM.LLVMBuildIntToPtr(
					builder,
					from.llvm,
					dest.llvm,
					"int_to_pointer"
				)
			)
			from.type is Type.Reference -> explicitCastOrNull(from.dereference(), dest)
			from.type is Type.Primitive && dest is Type.Primitive -> {
				val type = from.type
				dest.new(
					LLVM.LLVMBuildCast(
						builder, when (type) {
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
						}, from.llvm, dest.llvm, "primitive_cast"
					)
				)
			}
			else -> null
		}
	}

	fun Context.explicitCast(from: Value, to: Type) = explicitCastOrNull(from, to) ?: fail(from, to)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Type.canImplicitlyCastTo(to: Type) = CastManager.canImplicitlyCast(this, to)
