package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.UnaryOp
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.expect
import com.mivik.kamet.impossible
import com.mivik.kamet.pointer
import org.bytedeco.llvm.global.LLVM

internal class UnaryOpNode(val op: UnaryOp, val value: ASTNode, val after: Boolean = false) : ASTNode {
	override fun Context.codegenForThis(): Value {
		var value = value.codegen()
		when (op) {
			UnaryOp.Indirection -> {
				value = value.dereference()
				val type = value.type
				require(type is Type.Pointer) { "Indirection of a non-pointer type: ${value.type}" }
				return ValueRef(value.llvm, type.originalType, type.isConst)
			}
			UnaryOp.AddressOf -> {
				require(value is ValueRef) { "Taking the address of a val: ${value.type}" }
				val type = value.type.expect<Type.Reference>()
				return Value(value.llvm, type.originalType.pointer(type.isConst))
			}
			UnaryOp.Increment -> {
				require(value is ValueRef && !value.isConst) { "Increment on a non-variable type: ${value.type}" }
				val originalType = value.originalType
				val ret = if (after) value.dereference() else value
				value.setValue(
					Value(
						when (originalType) {
							is Type.Primitive.Integral ->
								LLVM.LLVMBuildAdd(
									builder,
									value.dereference().llvm,
									LLVM.LLVMConstInt(originalType.llvm, 1L, 0),
									"increment"
								)
							is Type.Primitive.Real ->
								LLVM.LLVMBuildFAdd(
									builder,
									value.dereference().llvm,
									LLVM.LLVMConstReal(originalType.llvm, 1.0),
									"increment"
								)
							else -> impossible()
						}, originalType
					)
				)
				return ret
			}
			UnaryOp.Decrement -> {
				require(value is ValueRef && !value.isConst) { "Decrement on a non-variable type: ${value.type}" }
				val originalType = value.originalType
				val ret = if (after) value.dereference() else value
				value.setValue(
					Value(
						when (originalType) {
							is Type.Primitive.Integral ->
								LLVM.LLVMBuildSub(
									builder,
									value.dereference().llvm,
									LLVM.LLVMConstInt(originalType.llvm, 1L, 0),
									"decrement"
								)
							is Type.Primitive.Real ->
								LLVM.LLVMBuildFSub(
									builder,
									value.dereference().llvm,
									LLVM.LLVMConstReal(originalType.llvm, 1.0),
									"decrement"
								)
							else -> impossible()
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
						else -> impossible()
					}
				is Type.Primitive.Integral ->
					when (op) {
						UnaryOp.Negative -> LLVM.LLVMBuildNeg(builder, value.llvm, "negative")
						UnaryOp.Inverse -> LLVM.LLVMBuildNot(builder, value.llvm, "inverse")
						else -> impossible()
					}
				is Type.Primitive.Real ->
					when (op) {
						UnaryOp.Negative -> LLVM.LLVMBuildFNeg(builder, value.llvm, "negative")
						else -> impossible()
					}
				else -> impossible()
			}, value.type
		)
	}

	override fun toString(): String = "(${op.symbol}$value)"
}