package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

open class ValueRef(address: LLVMValueRef, val originalType: Type, val isConst: Boolean) :
	Value(address, originalType.reference(isConst)) {
	open fun setIn(context: Context, value: Value) {
		if (isConst) error("Attempt to alter a const reference")
		LLVM.LLVMBuildStore(context.builder, value.llvm, llvm)
	}

	override fun dereference(context: Context): Value =
		Value(LLVM.LLVMBuildLoad(context.builder, llvm, "load"), originalType)
}

class UnitValueRef(isConst: Boolean) : ValueRef(Type.Unit.nullPointer().llvm, Type.Unit, isConst) {
	override fun setIn(context: Context, value: Value) {}
	override fun dereference(context: Context): Value = Unit
}