package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

sealed class ValueRef(llvm: LLVMValueRef, type: Type) : Value(llvm, type) {
	abstract val isConst: Boolean
	abstract val originalType: Type
	internal abstract fun set(context: Context, value: Value)

	class Val(val value: Value) : ValueRef(value.llvm, value.type) {
		override val isConst: Boolean
			get() = true

		override val originalType: Type
			get() = value.type

		override fun set(context: Context, value: Value) = error("Val cannot be reassigned")

		override fun dereference(context: Context): Value = value
	}

	class Var(address: LLVMValueRef, type: Type) : ValueRef(address, type.reference()) {
		override val isConst: Boolean
			get() = false

		override val originalType: Type
			get() = (type as Type.Reference).originalType

		override fun set(context: Context, value: Value) {
			LLVM.LLVMBuildStore(context.builder, value.llvm, llvm)
		}

		override fun dereference(context: Context): Value =
			Value(LLVM.LLVMBuildLoad(context.builder, llvm, "load"), originalType)
	}
}

fun Value.asVal(): ValueRef.Val = ValueRef.Val(this)
