package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

internal object CastManager {
	private fun basicCast(context: Context, from: Value, to: Type): Value? {
		if (from.type == to) return from
		if (from.type is Type.Pointer && from.type.originalType == Type.Nothing && to is Type.Pointer)
			return Value(LLVM.LLVMBuildBitCast(context.builder, from.llvm, to.llvm, "pointer_cast"), to)
		return null
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun fail(from: Value, to: Type): Nothing = error("Attempt to cast a ${from.type} to $to")

	fun implicitCast(context: Context, from: Value, to: Type): Value {
		basicCast(context, from, to)?.let { return it }
		fail(from, to)
	}

	fun explicitCast(context: Context, from: Value, to: Type): Value {
		basicCast(context, from, to)?.let { return it }
		if (from.type is Type.Pointer && to is Type.Pointer)
			return Value(LLVM.LLVMBuildBitCast(context.builder, from.llvm, to.llvm, "pointer_cast"), to)
		if (from.type is Type.Primitive && to is Type.Primitive)
			return Value(
				LLVM.LLVMBuildCast(
					context.builder, when (from.type) {
						is Type.Primitive.Integer ->
							when (to) {
								is Type.Primitive.Integer -> {
									if (from.type.sizeInBits > to.sizeInBits) LLVM.LLVMTrunc
									else if (from.type.signed) LLVM.LLVMSExt
									else LLVM.LLVMZExt
								}
								is Type.Primitive.Real -> {
									if (from.type.signed) LLVM.LLVMSIToFP
									else LLVM.LLVMUIToFP
								}
								is Type.Primitive.Boolean -> LLVM.LLVMTrunc
							}
						is Type.Primitive.Real ->
							when (to) {
								is Type.Primitive.Integer -> {
									if (to.signed) LLVM.LLVMFPToSI
									else LLVM.LLVMFPToUI
								}
								is Type.Primitive.Real -> {
									if (from.type.sizeInBits > to.sizeInBits) LLVM.LLVMFPTrunc
									else LLVM.LLVMFPExt
								}
								else -> fail(from, to)
							}
						is Type.Primitive.Boolean ->
							when (to) {
								is Type.Primitive.Integer -> {
									if (to.signed) LLVM.LLVMSExt
									else LLVM.LLVMZExt
								}
								is Type.Primitive.Real -> LLVM.LLVMUIToFP
								else -> unreachable()
							}
					}, from.llvm, to.llvm, "primitive_cast"
				), to
			)
		fail(from, to)
	}
}