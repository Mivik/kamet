package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

sealed class Function {
	abstract val type: Type.Function

	abstract fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value

	class Static(val function: Value) : Function() {
		override val type: Type.Function
			get() = function.type as Type.Function

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value {
			require(receiver == null) { "Non-null receiver invocation on a static function." }
			val parameterTypes = type.parameterTypes
			return type.returnType.new(
				LLVM.LLVMBuildCall(
					builder,
					function.llvm,
					buildPointerPointer(arguments.size) { arguments[it].implicitCast(parameterTypes[it]).llvm },
					arguments.size,
					(!type.returnType.canImplicitlyCastTo(Type.Unit)).ifThat { "call_result" }
				)
			)
		}
	}
}