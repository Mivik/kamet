package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

internal object CastManager {
	fun cast(context: Context, from: Value, to: Type): Value {
		if (from.type == to) return from
		if (from.type is Type.Pointer && from.type.originalType == Type.Nothing && to is Type.Pointer)
			return Value(LLVM.LLVMBuildBitCast(context.builder, from.llvm, to.llvm, "pointer_cast"), to)
		error("Attempt to cast a ${from.type} to $to")
	}
}