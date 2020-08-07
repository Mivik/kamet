package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

open class Value(val llvm: LLVMValueRef, val type: Type) {
	companion object {
		val Unit = Value(LLVM.LLVMGetUndef(Type.Unit.llvm), Type.Unit)
		val Nothing = Value(LLVM.LLVMGetUndef(Type.Nothing.llvm), Type.Nothing)
	}

	open fun Context.dereferenceForThis(): Value = this@Value
}