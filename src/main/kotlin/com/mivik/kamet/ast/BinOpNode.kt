package com.mivik.kamet.ast

import com.mivik.kamet.BinOp
import com.mivik.kamet.Context
import com.mivik.kamet.IllegalCastException
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.unreachable
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM

internal class BinOpNode(val lhs: ExprNode, val rhs: ExprNode, val op: BinOp) : ExprNode {
	private fun getOperandType(lhsValue: Value, rhsValue: Value): Type = run {
		val lhsType = lhsValue.type
		val rhsType = rhsValue.type
		if (lhsType !is Type.Primitive || rhsType !is Type.Primitive) TODO()
		else if (lhsType == Type.Primitive.Real.Double || rhsType == Type.Primitive.Real.Double) Type.Primitive.Real.Double
		else if (lhsType == Type.Primitive.Real.Float || rhsType == Type.Primitive.Real.Float) Type.Primitive.Real.Float
		else if (lhsType == Type.Primitive.Boolean && rhsType == Type.Primitive.Boolean) Type.Primitive.Boolean
		else {
			lhsType as Type.Primitive.Integer
			rhsType as Type.Primitive.Integer
			if (lhsType.sizeInBits > rhsType.sizeInBits) lhsType
			else if (lhsType.sizeInBits == rhsType.sizeInBits) {
				if (!lhsType.signed) lhsType
				else rhsType
			} else rhsType
		}
	}

	private fun lift(builder: LLVMBuilderRef, value: Value, type: Type): Value {
		if (value.type == type) return value
		if (type !is Type.Primitive) TODO()
		return Value(
			when (type) {
				is Type.Primitive.Integer -> {
					when (value.type) {
						is Type.Primitive.Integer ->
							if (type.signed) LLVM.LLVMBuildSExt(builder, value.llvm, type.llvm, "signed_ext")
							else LLVM.LLVMBuildZExt(builder, value.llvm, type.llvm, "unsigned_ext")
						is Type.Primitive.Real ->
							if (type.signed) LLVM.LLVMBuildFPToSI(builder, value.llvm, type.llvm, "real_to_signed")
							else LLVM.LLVMBuildFPToUI(builder, value.llvm, type.llvm, "real_to_unsigned")
						else -> throw IllegalCastException(value.type, type)
					}
				}
				is Type.Primitive.Real -> {
					when (value.type) {
						is Type.Primitive.Integer ->
							if (value.type.signed)
								LLVM.LLVMBuildSIToFP(builder, value.llvm, type.llvm, "signed_to_real")
							else
								LLVM.LLVMBuildUIToFP(builder, value.llvm, type.llvm, "unsigned_to_real")
						is Type.Primitive.Real ->
							LLVM.LLVMBuildFPExt(builder, value.llvm, type.llvm, "real_ext")
						else -> throw IllegalCastException(value.type, type)
					}
				}
				else -> throw IllegalCastException(value.type, type)
			}, type
		)
	}

	override fun codegen(context: Context): Value {
		val evaluatedLHS = lhs.codegen(context)
		val evaluatedRHS = rhs.codegen(context)
		val operandType = getOperandType(evaluatedLHS, evaluatedRHS)
		val type =
			if (op.returnBoolean) Type.Primitive.Boolean
			else operandType
		val builder = context.builder
		val lhsValue = lift(builder, lhs.codegen(context), operandType).llvm
		val rhsValue = lift(builder, rhs.codegen(context), operandType).llvm
		if (operandType == Type.Primitive.Boolean) {
			return Value(
				when (op) {
					BinOp.And -> LLVM.LLVMBuildAnd(builder, lhsValue, rhsValue, "and")
					BinOp.Or -> LLVM.LLVMBuildOr(builder, lhsValue, rhsValue, "or")
					else ->
						LLVM.LLVMBuildICmp(
							builder, when (op) {
								BinOp.Equal -> LLVM.LLVMIntEQ
								BinOp.NotEqual -> LLVM.LLVMIntNE
								BinOp.Less -> LLVM.LLVMIntULT
								BinOp.LessOrEqual -> LLVM.LLVMIntULE
								BinOp.Greater -> LLVM.LLVMIntUGT
								BinOp.GreaterOrEqual -> LLVM.LLVMIntUGE
								else -> unreachable()
							}, lhsValue, rhsValue, "boolean_comparison"
						)
				}, Type.Primitive.Boolean
			)
		}
		return if (op.returnBoolean)
			Value(
				when (operandType) {
					is Type.Primitive.Integer -> {
						LLVM.LLVMBuildICmp(
							builder, when (op) {
								BinOp.Equal -> LLVM.LLVMIntEQ
								BinOp.NotEqual -> LLVM.LLVMIntNE
								BinOp.Less ->
									if (operandType.signed) LLVM.LLVMIntSLT
									else LLVM.LLVMIntULT
								BinOp.LessOrEqual ->
									if (operandType.signed) LLVM.LLVMIntSLE
									else LLVM.LLVMIntULE
								BinOp.Greater ->
									if (operandType.signed) LLVM.LLVMIntSGT
									else LLVM.LLVMIntUGT
								BinOp.GreaterOrEqual ->
									if (operandType.signed) LLVM.LLVMIntSGE
									else LLVM.LLVMIntUGE
								else -> unreachable()
							}, lhsValue, rhsValue, "integer_comparison"
						)
					}
					is Type.Primitive.Real -> LLVM.LLVMBuildFCmp(
						builder, when (op) {
							BinOp.Equal -> LLVM.LLVMRealOEQ
							BinOp.NotEqual -> LLVM.LLVMRealONE
							BinOp.Less -> LLVM.LLVMRealOLT
							BinOp.LessOrEqual -> LLVM.LLVMRealOLE
							BinOp.Greater -> LLVM.LLVMRealOGT
							BinOp.GreaterOrEqual -> LLVM.LLVMRealOGE
							else -> unreachable()
						}, lhsValue, rhsValue, "real_comparison"
					)
					else -> unreachable()
				}, Type.Primitive.Boolean
			)
		else Value(
			when (type) {
				is Type.Primitive.Integer ->
					when (op) {
						BinOp.Plus -> LLVM.LLVMBuildAdd(builder, lhsValue, rhsValue, "plus")
						BinOp.Minus -> LLVM.LLVMBuildSub(builder, lhsValue, rhsValue, "minus")
						BinOp.Multiply -> LLVM.LLVMBuildMul(builder, lhsValue, rhsValue, "multiply")
						BinOp.Divide ->
							if (type.signed) LLVM.LLVMBuildSDiv(builder, lhsValue, rhsValue, "signed_divide")
							else LLVM.LLVMBuildUDiv(builder, lhsValue, rhsValue, "unsigned_divide")
						BinOp.Reminder ->
							if (type.signed) LLVM.LLVMBuildSRem(builder, lhsValue, rhsValue, "signed_reminder")
							else LLVM.LLVMBuildURem(builder, lhsValue, rhsValue, "unsigned_reminder")
						BinOp.ShiftLeft -> LLVM.LLVMBuildShl(builder, lhsValue, rhsValue, "shift_left")
						BinOp.ShiftRight ->
							if (type.signed) LLVM.LLVMBuildAShr(builder, lhsValue, rhsValue, "signed_shift_right")
							else LLVM.LLVMBuildLShr(builder, lhsValue, rhsValue, "unsigned_shift_right")
						BinOp.BitwiseAnd -> LLVM.LLVMBuildAnd(builder, lhsValue, rhsValue, "bitwise_and")
						BinOp.BitwiseOr -> LLVM.LLVMBuildOr(builder, lhsValue, rhsValue, "bitwise_or")
						BinOp.Xor -> LLVM.LLVMBuildXor(builder, lhsValue, rhsValue, "xor")
						else -> unreachable()
					}
				is Type.Primitive.Real ->
					when (op) {
						BinOp.Plus -> LLVM.LLVMBuildFAdd(builder, lhsValue, rhsValue, "plus")
						BinOp.Minus -> LLVM.LLVMBuildFSub(builder, lhsValue, rhsValue, "minus")
						BinOp.Multiply -> LLVM.LLVMBuildFMul(builder, lhsValue, rhsValue, "multiply")
						BinOp.Divide ->
							LLVM.LLVMBuildFDiv(builder, lhsValue, rhsValue, "signed_divide")
						BinOp.Reminder -> LLVM.LLVMBuildFRem(builder, lhsValue, rhsValue, "signed_reminder")
						else -> unreachable()
					}
				else -> unreachable()
			}, type
		)
	}

	override fun toString(): String = "($lhs ${op.symbol} $rhs)"
}