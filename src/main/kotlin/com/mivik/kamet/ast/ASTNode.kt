package com.mivik.kamet.ast

import com.mivik.kamet.ValueRef
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM

internal open class ASTNode {
	open fun codegen(builder: LLVMBuilderRef): Value = Value.Unit
}

internal abstract class ExprNode : ASTNode() {
	abstract val type: Type
}

internal class ConstantNode(override val type: Type.Primitive, val value: String) : ExprNode() {
	override fun codegen(builder: LLVMBuilderRef): Value =
		Value(
			when (type) {
				is Type.Primitive.Boolean -> LLVM.LLVMConstInt(type.llvm, if (value == "true") 1 else 0, 0)
				is Type.Primitive.Integer -> LLVM.LLVMConstInt(type.llvm, value.toLong(), 0)
				is Type.Primitive.Real -> LLVM.LLVMConstReal(type.llvm, value.toDouble())
			}, type
		)

	override fun toString(): String = value
}

internal class ValueNode(val value: ValueRef) : ExprNode() {
	override val type: Type
		get() = value.type

	override fun codegen(builder: LLVMBuilderRef): Value = value.get(builder)

	override fun toString(): String = "ValueNode"
}

internal class StmtNode : ASTNode()
