package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.global.LLVM

private fun Context.invokeFunction(function: Value, receiver: Value?, arguments: List<Value>): Value {
	val type = function.type as Type.Function
	val size = arguments.size + (receiver != null).toInt()
	val parameterTypes = type.parameterTypes
	val llvmArguments =
		if (receiver == null)
			buildPointerPointer(size) { arguments[it].implicitCast(parameterTypes[it]).llvm }
		else {
			val receiverType = type.receiverType!!
			buildPointerPointer(size) {
				if (it == 0) receiver.implicitCast(receiverType).llvm
				else arguments[it - 1].implicitCast(parameterTypes[it - 1]).llvm
			}
		}
	return type.returnType.new(
		LLVM.LLVMBuildCall(
			builder,
			function.llvm,
			llvmArguments,
			size,
			(!type.returnType.canImplicitlyCastTo(Type.Unit)).ifThat { "call_result" }
		)
	)
}

sealed class Function : Resolvable {
	abstract val type: Type.Function
	open val typeParameters: List<TypeParameter>
		get() = emptyList()

	abstract fun Context.invokeForThis(
		receiver: Value?,
		arguments: List<Value>,
		typeArguments: List<Type>
	): Value

	override fun Context.resolveForThis(): Function = this@Function

	class Named(val name: String) : Function() {
		override val type: Type.Function
			get() = error("Undetermined")

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>, typeArguments: List<Type>): Value {
			val autoReceiver = receiver ?: lookupValueOrNull("this")
			val function = findMatchingFunction(
				name,
				lookupFunctions(name, autoReceiver?.type, true),
				autoReceiver?.type,
				arguments.map { it.type },
				typeArguments
			)
			return function.invoke(autoReceiver, arguments, typeArguments)
		}

		override fun toString(): String = name
	}

	class Static(val ptr: Value) : Function() {
		override val type: Type.Function
			get() = ptr.type as Type.Function

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>, typeArguments: List<Type>): Value =
			invokeFunction(ptr, receiver, arguments)
	}

	class Dynamic(val index: Int, override val type: Type.Function) : Function() {
		override fun Context.resolveForThis(): Function =
			Dynamic(index, type.resolve(resolveTypeParameter = true) as Type.Function)

		@Suppress("NAME_SHADOWING")
		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>, typeArguments: List<Type>): Value {
			val receiver = receiver?.implicitCastOrNull(type.receiverType!!)
				?: error("Dynamic function require a non-null dynamic receiver.")
			val table = LLVM.LLVMBuildExtractValue(builder, receiver.llvm, 0, "extract_table")
			val functionPointer = LLVM.LLVMBuildLoad(
				builder,
				LLVM.LLVMBuildGEP2(
					builder,
					LLVM.LLVMInt8Type().pointer(),
					table,
					index.toLLVM(),
					1,
					BytePointer("")
				),
				"load_function_ptr"
			).bitCast(type.llvm.pointer(), "function_ptr_cast")
			return invokeFunction(
				Value(functionPointer, type),
				receiver,
				arguments
			)
		}
	}

	// TODO transform the function body
	class Generic private constructor(
		internal val node: FunctionGenerator,
		override val typeParameters: List<TypeParameter>,
		override val type: Type.Function
	) : Function() {
		companion object {
			internal fun obtain(context: Context, node: FunctionGenerator, typeParameters: List<TypeParameter>) =
				Generic(node, typeParameters, with(context.subContext()) {
					for (parameter in typeParameters) declareType(parameter.name, Type.TypeParameter(parameter))
					node.prototype.type.resolve() as Type.Function
				})
		}

		override val resolved: Boolean
			get() = false

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>, typeArguments: List<Type>): Value =
			error("Invoking an unresolved generic function")

		override fun toString(): String = genericName(node.prototype.name, typeParameters)
	}
}