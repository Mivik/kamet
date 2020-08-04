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
		fail(from, to)
	}
}