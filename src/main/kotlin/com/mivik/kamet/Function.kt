package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

private fun Context.invokeFunction(function: Value, receiver: Value?, arguments: List<Value>): Value {
	val type = function.type as Type.Function
	val size = arguments.size + (receiver != null).toInt()
	val parameterTypes = type.parameterTypes
	return type.returnType.new(
		LLVM.LLVMBuildCall(
			builder,
			function.llvm,
			if (receiver == null)
				buildPointerPointer(size) { arguments[it].implicitCast(parameterTypes[it]).llvm }
			else {
				val receiverType = type.receiverType!!
				buildPointerPointer(size) {
					if (it == 0) receiver.implicitCast(receiverType).llvm
					else arguments[it - 1].implicitCast(parameterTypes[it - 1]).llvm
				}
			},
			size,
			(!type.returnType.canImplicitlyCastTo(Type.Unit)).ifThat { "call_result" }
		)
	)
}

sealed class Function : Resolvable {
	abstract val type: Type.Function
	abstract val typeParameters: List<TypeParameter>
	abstract fun Context.invokeForThis(
		receiver: Value?,
		arguments: List<Value>
	): Value

	override fun Context.resolveForThis(): Function = this@Function

	class Static(val function: Value) : Function() {
		override val type: Type.Function
			get() = function.type as Type.Function

		override val typeParameters: List<TypeParameter>
			get() = emptyList()

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value =
			invokeFunction(function, receiver, arguments)
	}
}