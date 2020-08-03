package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.UnaryOp
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.pointer
import com.mivik.kamet.unreachable
import org.bytedeco.llvm.global.LLVM

internal class UnaryOpNode(val op: UnaryOp, val value: ASTNode, val after: Boolean = false) : ASTNode {
	override fun codegen(context: Context): Value {
		val builder = context.builder
		var value = value.codegen(context)
		when (op) {
			UnaryOp.Indirection -> {
				value = value.dereference(context)
				val type = value.type
				require(type is Type.Pointer) { "Indirection of a non-pointer type: ${value.type}" }
				return ValueRef(value.llvm, type.originalType, type.isConst)
			}
			UnaryOp.AddressOf -> {
				require(value is ValueRef) { "Taking the address of a non-reference type: ${value.type}" }
				val type = value.type as Type.Reference
				return Value(value.llvm, type.originalType.pointer(type.isConst))
			}
			UnaryOp.Increment -> {
				require(value is ValueRef && !value.isConst) { "Increment on a non-variable type: ${value.type}" }
				val originalType = value.originalType
				val ret = if (after) value.dereference(context) else value
				value.set(
					context, Value(
						when (originalType) {
							is Type.Primitive.Integer ->
								LLVM.LLVMBuildAdd(
									builder,
									value.dereference(context).llvm,
									LLVM.LLVMConstInt(originalType.llvm, 1L, 0),
									"increment"
								)
							is Type.Primitive.Real ->
								LLVM.LLVMBuildFAdd(
									builder,
									value.dereference(context).llvm,
									LLVM.LLVMConstReal(originalType.llvm, 1.0),
									"increment"
								)
							else -> unreachable()
						}, originalType
					)
				)
				return ret
			}
			UnaryOp.Decrement -> {
				require(value is ValueRef && !value.isConst) { "Decrement on a non-variable type: ${value.type}" }
				val originalType = value.originalType
				val ret = if (after) value.dereference(context) else value
				value.set(
					context, Value(
						when (originalType) {
							is Type.Primitive.Integer ->
								LLVM.LLVMBuildSub(
									builder,
									value.dereference(context).llvm,
									LLVM.LLVMConstInt(originalType.llvm, 1L, 0),
									"decrement"
								)
							is Type.Primitive.Real ->
								LLVM.LLVMBuildFSub(
									builder,
									value.dereference(context).llvm,
									LLVM.LLVMConstReal(originalType.llvm, 1.0),
									"decrement"
								)
							else -> unreachable()
						}, originalType
					)
				)
				return ret
			}
			else -> {
			}
		}
		return Value(
			when (value.type) {
				Type.Primitive.Boolean ->
					when (op) {
						UnaryOp.Not -> LLVM.LLVMBuildNot(builder, value.llvm, "not")
						else -> unreachable()
					}
				is Type.Primitive.Integer ->
					when (op) {
						UnaryOp.Negative -> LLVM.LLVMBuildNeg(builder, value.llvm, "negative")
						UnaryOp.Inverse -> LLVM.LLVMBuildNot(builder, value.llvm, "inverse")
						else -> unreachable()
					}
				is Type.Primitive.Real ->
					when (op) {
						UnaryOp.Negative -> LLVM.LLVMBuildFNeg(builder, value.llvm, "negative")
						else -> unreachable()
					}
				else -> unreachable()
			}, value.type
		)
	}

	override fun toString(): String = "(${op.symbol}$value)"
}