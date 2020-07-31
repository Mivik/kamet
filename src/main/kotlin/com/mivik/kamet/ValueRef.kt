package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

sealed class ValueRef {
	abstract val type: Type
	abstract val canSet: Boolean
	abstract fun get(builder: LLVMBuilderRef): Value
	abstract fun set(builder: LLVMBuilderRef, value: Value)

	class Val(private val value: Value) : ValueRef() {
		override val type: Type
			get() = value.type


		override val canSet: Boolean
			get() = false

		override fun get(builder: LLVMBuilderRef): Value = value

		override fun set(builder: LLVMBuilderRef, value: Value) = error("Val cannot be reassigned")
	}

	class Var(private val address: LLVMValueRef, override val type: Type) : ValueRef() {
		override val canSet: Boolean
			get() = true

		override fun get(builder: LLVMBuilderRef): Value = Value(LLVM.LLVMBuildLoad(builder, address, "load"), type)

		override fun set(builder: LLVMBuilderRef, value: Value) {
			LLVM.LLVMBuildStore(builder, value.llvm, address)
		}
	}
}