package com.mivik.kamet.ast

import com.mivik.kamet.BinOp
import com.mivik.kamet.Context
import com.mivik.kamet.IllegalCastException
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.implicitCast
import com.mivik.kamet.reference
import com.mivik.kamet.impossible
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import com.mivik.kamet.Type.Primitive
import com.mivik.kamet.foldSign

internal class BinOpNode(val lhs: ASTNode, val rhs: ASTNode, val op: BinOp) : ASTNode {
	private fun unifyOperandTypes(lhsType: Type, rhsType: Type): Type =
		if (lhsType !is Primitive || rhsType !is Primitive) TODO()
		else if (lhsType == rhsType && lhsType in arrayOf(Primitive.Real.Double, Primitive.Real.Float, Primitive.Boolean)) lhsType
		else { // bit size first
			val lhsTypedI = lhsType as Primitive.Integer
			val lb = lhsTypedI.sizeInBits
			val rb = (rhsType as Primitive.Integer).sizeInBits
			when {
				lb > rb -> lhsType
				lb == rb -> lhsTypedI.foldSign(unsigned = lhsType, signed = rhsType) //unsigned first
				lb < rb -> rhsType
				else -> impossible()
			}
		}

	private fun liftIn(builder: LLVMBuilderRef, value: Value, type: Type): Value { // numeric lifting casts
		if (value.type == type) return value
		if (type !is Primitive) TODO()

		fun fail(): Nothing = throw IllegalCastException(value.type, type)
		val coercion = when (type) {
			is Primitive.Integer -> {
				when (value.type) {
					is Primitive.Integer ->
						type.foldSign(LLVMBuildSExt(builder, value.llvm, type.llvm, "signed_ext"), LLVMBuildZExt(builder, value.llvm, type.llvm, "unsigned_ext"))
					is Primitive.Real ->
						type.foldSign(LLVMBuildFPToSI(builder, value.llvm, type.llvm, "real_to_signed"), LLVMBuildFPToUI(builder, value.llvm, type.llvm, "real_to_unsigned"))
					else -> fail()
				}
			}
			is Primitive.Real -> {
				when (value.type) {
					is Primitive.Integer ->
						if (value.type.signed)
							LLVMBuildSIToFP(builder, value.llvm, type.llvm, "signed_to_real")
						else
							LLVMBuildUIToFP(builder, value.llvm, type.llvm, "unsigned_to_real")
					is Primitive.Real ->
						LLVMBuildFPExt(builder, value.llvm, type.llvm, "real_ext")
					else -> fail()
				}
			}
			else -> fail()
		}
		return Value(coercion, type)
	}

	override fun codegen(context: Context): Value {
		val lv = lhs.codegen(context)
		val rv = rhs.codegen(context)
		fun lhsMustValueRef(): ValueRef {
			require(lv is ValueRef && !lv.isConst) { "Assigning to a non-reference type: ${lv.type}" }
			return lv
		}

		return when (op) {
			is BinOp.AssignOperators -> {
				//v a = op(*a, b)
				lhsMustValueRef().setIn(context, mathCodegen(context, lv.dereference(context), rv.dereference(context), op.originalOp))
				lv
			}
			BinOp.Assign -> {
				lhsMustValueRef().let { it.setIn(context, rv.dereference(context).implicitCast(context, it.originalType)) }
				lv
			}
			BinOp.AccessMember -> {
				require(rhs is ValueNode) { "Expected a member name, got $rhs" }
				when (val type = lv.type) {
					is Type.Struct -> {
						val address = context.declareVariable("struct_store", lv)
						val index = type.memberIndex(rhs.name)
						Value(
							LLVMBuildStructGEP(context.builder, address.llvm, index, "access_member"),
							type.memberType(index).reference(true)
						)
					}
					is Type.Reference -> {
						val originStruct = type.originalType as Type.Struct
						val index = originStruct.memberIndex(rhs.name)
						ValueRef(
							LLVMBuildStructGEP(context.builder, lv.llvm, index, "access_member"),
							originStruct.memberType(index),
							type.isConst
						)
					}
					else -> TODO("branches for more types, and extension")
				}
			}
			else -> mathCodegen(context, lv.dereference(context), rv.dereference(context), op)
		}
	}

	private fun mathCodegen(context: Context, lhs: Value, rhs: Value, op: BinOp): Value {
		val operandType = unifyOperandTypes(lhs.type, rhs.type)
		val type =
			if (op.returnBoolean) Primitive.Boolean
			else operandType
		val builder = context.builder
		val lhsValue = liftIn(builder, lhs, operandType).llvm
		val rhsValue = liftIn(builder, rhs, operandType).llvm
		if (operandType == Primitive.Boolean) {
			return Value(
				when (op) {
					BinOp.And -> LLVMBuildAnd(builder, lhsValue, rhsValue, "and")
					BinOp.Or -> LLVMBuildOr(builder, lhsValue, rhsValue, "or")
					else ->
						LLVMBuildICmp(
							builder, when (op) {
								BinOp.Equal -> LLVMIntEQ
								BinOp.NotEqual -> LLVMIntNE
								BinOp.Less -> LLVMIntULT
								BinOp.LessOrEqual -> LLVMIntULE
								BinOp.Greater -> LLVMIntUGT
								BinOp.GreaterOrEqual -> LLVMIntUGE
								else -> impossible()
							}, lhsValue, rhsValue, "boolean_cmp"
						)
				}, Primitive.Boolean
			)
		}
		return if (op.returnBoolean) // comparision
			Value(
				when (operandType) {
					is Primitive.Integer -> {
						LLVMBuildICmp(
							builder, when (op) {
								BinOp.Equal -> LLVMIntEQ
								BinOp.NotEqual -> LLVMIntNE
								BinOp.Less -> operandType.foldSign(LLVMIntSLT, LLVMIntULT)
								BinOp.LessOrEqual -> operandType.foldSign(LLVMIntSLE, LLVMIntULE)
								BinOp.Greater -> operandType.foldSign(LLVMIntSGT, LLVMIntUGT)
								BinOp.GreaterOrEqual -> operandType.foldSign(LLVMIntSGE, LLVMIntUGE)
								else -> impossible()
							}, lhsValue, rhsValue, "integer_cmp"
						)
					}
					is Primitive.Real -> LLVMBuildFCmp(
						builder, when (op) {
							BinOp.Equal -> LLVMRealOEQ
							BinOp.NotEqual -> LLVMRealONE
							BinOp.Less -> LLVMRealOLT
							BinOp.LessOrEqual -> LLVMRealOLE
							BinOp.Greater -> LLVMRealOGT
							BinOp.GreaterOrEqual -> LLVMRealOGE
							else -> impossible()
						}, lhsValue, rhsValue, "real_cmp"
					)
					else -> impossible()
				}, Primitive.Boolean
			)
		else Value(
			when (type) {
				is Primitive.Integer ->
					LLVMBuildBinOp(
						builder, when (op) {
							BinOp.Plus -> LLVMAdd
							BinOp.Minus -> LLVMSub
							BinOp.Multiply -> LLVMMul
							BinOp.Divide -> type.foldSign(LLVMSDiv, LLVMUDiv)
							BinOp.Reminder -> type.foldSign(LLVMSRem, LLVMURem)
							BinOp.ShiftLeft -> LLVMShl
							BinOp.ShiftRight -> type.foldSign(LLVMAShr, LLVMLShr)
							BinOp.BitwiseAnd -> LLVMAnd
							BinOp.BitwiseOr -> LLVMOr
							BinOp.Xor -> LLVMXor
							else -> impossible()
						}, lhsValue, rhsValue, "integer_binop"
					)
				is Primitive.Real ->
					LLVMBuildBinOp(
						builder, when (op) {
							BinOp.Plus -> LLVMFAdd
							BinOp.Minus -> LLVMFSub
							BinOp.Multiply -> LLVMFMul
							BinOp.Divide -> LLVMFDiv
							BinOp.Reminder -> LLVMFRem
							else -> impossible()
						}, lhsValue, rhsValue, "real_binop"
					)
				else -> impossible()
			}, type
		)
	}

	override fun toString(): String = "($lhs ${op.symbol} $rhs)"
}