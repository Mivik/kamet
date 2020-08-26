package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

open class ValueRef(address: LLVMValueRef, val originalType: Type, val isConst: Boolean) :
	Value(address, originalType.reference(isConst)) {
	internal open fun Context.setValueForThis(value: Value) {
		LLVM.LLVMBuildStore(builder, value.llvm, this@ValueRef.llvm)
	}

	override fun Context.dereferenceForThis(): Value =
		originalType.new(LLVM.LLVMBuildLoad(builder, this@ValueRef.llvm, "load"))
}

class UnitValueRef(isConst: Boolean) : ValueRef(NullPointer.llvm, Type.Unit, isConst) {
	override fun Context.setValueForThis(value: Value) {}
	override fun Context.dereferenceForThis(): Value = Unit
}