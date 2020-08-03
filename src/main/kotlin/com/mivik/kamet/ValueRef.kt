package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

class ValueRef(address: LLVMValueRef, val originalType: Type, val isConst: Boolean) : Value(address, originalType.reference()) {
	fun set(context: Context, value: Value) {
		if (isConst) error("Attempt to alter a const reference")
		LLVM.LLVMBuildStore(context.builder, value.llvm, llvm)
	}

	override fun dereference(context: Context): Value =
		Value(LLVM.LLVMBuildLoad(context.builder, llvm, "load"), originalType)
}