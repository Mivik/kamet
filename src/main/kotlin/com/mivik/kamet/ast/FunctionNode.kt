package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.FunctionGenerator
import com.mivik.kamet.Prototype
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.Value
import com.mivik.kamet.toInt
import org.bytedeco.llvm.global.LLVM

internal class FunctionNode(
	val node: PrototypeNode,
	val body: BlockNode
) : FunctionGenerator, ASTNode {
	override val prototype: Prototype
		get() = node.prototype

	override fun Context.generateForThis(newName: String?): Function {
		if (hasValue(node.functionName)) error("Redeclaration: ${node.functionName}")
		val node = node.resolve()
		val function = node.generate(newName) as Function.Static
		val ptr = function.ptr
		LLVM.LLVMGetNamedFunction(module, node.prototype.name)
		val sub = subContext(ptr)
		insertAt(sub.basicBlock("entry"))
		val type = function.type
		val parameterTypes = type.parameterTypes
		val offset = type.hasReceiver.toInt()
		with(sub) {
			if (type.hasReceiver)
				declare("this", type.receiverType!!.new(LLVM.LLVMGetParam(ptr.llvm, 0)))
		}
		for ((i, name) in node.prototype.parameterNames.withIndex())
			sub.declare(name, parameterTypes[i].new(LLVM.LLVMGetParam(ptr.llvm, i + offset)))
		with(sub) { body.codegen() }
		return function
	}

	fun rename(newName: String) =
		FunctionNode(node.rename(newName), body)

	fun Context.resolveForThis() =
		FunctionNode(node.resolve(), body)

	override fun Context.codegenForThis(): Value =
		(generate() as Function.Static).ptr

	override fun toString(): String = "$node $body"
}

internal class GenericFunctionNode(
	val node: FunctionNode,
	val typeParameters: List<TypeParameter>
) : FunctionGenerator, ASTNode {
	override val prototype: Prototype
		get() = node.prototype

	override fun Context.generateForThis(newName: String?): Function =
		Function.Generic.obtain(this, node, typeParameters)

	override fun Context.codegenForThis(): Value {
		declareFunction(prototype, generate())
		return Value.Unit
	}

	override fun toString(): String = "${GenericPrototypeNode(node.node, typeParameters)} ${node.body}"
}
