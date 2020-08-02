package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

sealed class ValueRef {
	abstract val type: Type
	abstract val canSet: Boolean
	internal abstract fun get(context: Context): Value
	internal abstract fun set(context: Context, value: Value)

	class Val(private val value: Value) : ValueRef() {
		override val type: Type
			get() = value.type

		override val canSet: Boolean
			get() = false

		override fun get(context: Context): Value = value

		override fun set(context: Context, value: Value) = error("Val cannot be reassigned")
	}

	class Var(private val address: LLVMValueRef, override val type: Type) : ValueRef() {
		override val canSet: Boolean
			get() = true

		override fun get(context: Context): Value = Value(LLVM.LLVMBuildLoad(context.builder, address, "load"), type)

		override fun set(context: Context, value: Value) {
			LLVM.LLVMBuildStore(context.builder, value.llvm, address)
		}
	}
}