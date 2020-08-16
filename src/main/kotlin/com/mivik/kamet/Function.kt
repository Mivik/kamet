package com.mivik.kamet

import com.mivik.kamet.ast.AbstractFunctionNode
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

	abstract fun Context.invokeForThis(
		receiver: Value?,
		arguments: List<Value>
	): Value

	override fun Context.resolveForThis(): Function = this@Function

	class Named(val name: String) : Function() {
		override val type: Type.Function
			get() = error("Undetermined")

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value =
			findMatchingFunction(
				name,
				lookupFunctions(name, receiver?.type),
				receiver?.type,
				arguments.map { it.type }
			).invoke(receiver, arguments)

		override fun toString(): String = name
	}

	class Static(val function: Value) : Function() {
		override val type: Type.Function
			get() = function.type as Type.Function

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value =
			invokeFunction(function, receiver, arguments)
	}

	// TODO transform the function body
	class Generic internal constructor(
		internal val node: AbstractFunctionNode,
		val typeParameters: List<TypeParameter>
	) : Function() {
		override val type: Type.Function
			get() = node.prototype.type

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value =
			error("Invoking an unresolved generic function")

		override fun toString(): String = genericName(node.prototype.name, typeParameters)
	}

	class ActualGeneric(
		val name: String,
		val typeArguments: List<Type>
	) : Function() {
		override val type: Type.Function
			get() = error("Unresolved")
		override val resolved: Boolean
			get() = false

		override fun Context.resolveForThis(): Function {
			val generic = lookupGenericFunction(name)
			val node = generic.node
			return buildGeneric(node.prototype.name, generic.typeParameters, typeArguments) {
				// TODO not always static
				Static(node.directCodegen(genericName(node.prototype.name, generic.typeParameters)))
			}
		}

		override fun Context.invokeForThis(receiver: Value?, arguments: List<Value>): Value =
			error("Invoking an unresolved function")

		override fun toString(): String = actualGenericName(name, typeArguments)
	}
}