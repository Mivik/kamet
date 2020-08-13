package com.mivik.kamet.ast

import com.mivik.kamet.BinOp
import com.mivik.kamet.Context
import com.mivik.kamet.IllegalCastException
import com.mivik.kamet.Type
import com.mivik.kamet.Type.Primitive
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.expect
import com.mivik.kamet.foldSign
import com.mivik.kamet.impossible
import org.bytedeco.llvm.global.LLVM.LLVMAShr
import org.bytedeco.llvm.global.LLVM.LLVMAdd
import org.bytedeco.llvm.global.LLVM.LLVMAnd
import org.bytedeco.llvm.global.LLVM.LLVMBuildAnd
import org.bytedeco.llvm.global.LLVM.LLVMBuildBinOp
import org.bytedeco.llvm.global.LLVM.LLVMBuildFCmp
import org.bytedeco.llvm.global.LLVM.LLVMBuildFPExt
import org.bytedeco.llvm.global.LLVM.LLVMBuildFPToSI
import org.bytedeco.llvm.global.LLVM.LLVMBuildFPToUI
import org.bytedeco.llvm.global.LLVM.LLVMBuildICmp
import org.bytedeco.llvm.global.LLVM.LLVMBuildOr
import org.bytedeco.llvm.global.LLVM.LLVMBuildSExt
import org.bytedeco.llvm.global.LLVM.LLVMBuildSIToFP
import org.bytedeco.llvm.global.LLVM.LLVMBuildStructGEP2
import org.bytedeco.llvm.global.LLVM.LLVMBuildUIToFP
import org.bytedeco.llvm.global.LLVM.LLVMBuildZExt
import org.bytedeco.llvm.global.LLVM.LLVMFAdd
import org.bytedeco.llvm.global.LLVM.LLVMFDiv
import org.bytedeco.llvm.global.LLVM.LLVMFMul
import org.bytedeco.llvm.global.LLVM.LLVMFRem
import org.bytedeco.llvm.global.LLVM.LLVMFSub
import org.bytedeco.llvm.global.LLVM.LLVMIntEQ
import org.bytedeco.llvm.global.LLVM.LLVMIntNE
import org.bytedeco.llvm.global.LLVM.LLVMIntSGE
import org.bytedeco.llvm.global.LLVM.LLVMIntSGT
import org.bytedeco.llvm.global.LLVM.LLVMIntSLE
import org.bytedeco.llvm.global.LLVM.LLVMIntSLT
import org.bytedeco.llvm.global.LLVM.LLVMIntUGE
import org.bytedeco.llvm.global.LLVM.LLVMIntUGT
import org.bytedeco.llvm.global.LLVM.LLVMIntULE
import org.bytedeco.llvm.global.LLVM.LLVMIntULT
import org.bytedeco.llvm.global.LLVM.LLVMLShr
import org.bytedeco.llvm.global.LLVM.LLVMMul
import org.bytedeco.llvm.global.LLVM.LLVMOr
import org.bytedeco.llvm.global.LLVM.LLVMRealOEQ
import org.bytedeco.llvm.global.LLVM.LLVMRealOGE
import org.bytedeco.llvm.global.LLVM.LLVMRealOGT
import org.bytedeco.llvm.global.LLVM.LLVMRealOLE
import org.bytedeco.llvm.global.LLVM.LLVMRealOLT
import org.bytedeco.llvm.global.LLVM.LLVMRealONE
import org.bytedeco.llvm.global.LLVM.LLVMSDiv
import org.bytedeco.llvm.global.LLVM.LLVMSRem
import org.bytedeco.llvm.global.LLVM.LLVMShl
import org.bytedeco.llvm.global.LLVM.LLVMSub
import org.bytedeco.llvm.global.LLVM.LLVMUDiv
import org.bytedeco.llvm.global.LLVM.LLVMURem
import org.bytedeco.llvm.global.LLVM.LLVMXor

internal class BinOpNode(val lhs: ASTNode, val rhs: ASTNode, val op: BinOp) : ASTNode {
	private fun unifyOperandTypes(lhsType: Type, rhsType: Type): Type =
		if (lhsType !is Primitive || rhsType !is Primitive) TODO()
		else if (lhsType == Primitive.Real.Double || rhsType == Primitive.Real.Double) Primitive.Real.Double
		else if (lhsType == Primitive.Real.Float || rhsType == Primitive.Real.Float) Primitive.Real.Float
		else if (lhsType == Primitive.Boolean && rhsType == Primitive.Boolean) Primitive.Boolean
		else { // bit size first
			val lhsTypedI = lhsType as Primitive.Integral
			val lb = lhsTypedI.sizeInBits
			val rb = (rhsType as Primitive.Integral).sizeInBits
			when {
				lb > rb -> lhsType
				lb == rb -> lhsTypedI.foldSign(unsigned = lhsType, signed = rhsType) // unsigned first
				lb < rb -> rhsType
				else -> impossible()
			}
		}

	private fun Context.lift(value: Value, type: Type): Value { // numeric lifting casts
		if (value.type == type) return value
		if (type !is Primitive) TODO()

		fun fail(): Nothing = throw IllegalCastException(value.type, type)
		val coercion = when (type) {
			is Primitive.Integral -> {
				when (value.type) {
					is Primitive.Integral ->
						if (type.signed) LLVMBuildSExt(builder, value.llvm, type.llvm, "signed_ext")
						else LLVMBuildZExt(builder, value.llvm, type.llvm, "unsigned_ext")
					is Primitive.Real ->
						if (type.signed) LLVMBuildFPToSI(builder, value.llvm, type.llvm, "real_to_signed")
						else LLVMBuildFPToUI(builder, value.llvm, type.llvm, "real_to_unsigned")
					else -> fail()
				}
			}
			is Primitive.Real -> {
				when (value.type) {
					is Primitive.Integral ->
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
		return type.new(coercion)
	}

	override fun Context.codegenForThis(): Value {
		val lv = lhs.codegen()
		val rv by lazy { rhs.codegen() }
		fun lhsMustValueRef(): ValueRef {
			require(lv is ValueRef && !lv.isConst) { "Assigning to a non-reference type: ${lv.type}" }
			return lv
		}

		return when (op) {
			is BinOp.AssignOperators -> {
				lhsMustValueRef().setValue(arithmeticCodegen(lv.dereference(), rv.dereference(), op.originalOp))
				lv
			}
			BinOp.Assign -> {
				lhsMustValueRef().let {
					it.setValue(rv.dereference().implicitCast(it.originalType))
				}
				lv
			}
			BinOp.AccessMember -> {
				when (rhs) {
					is ValueNode -> {
						val type = lv.type.expect<Type.Reference>()
						val originStruct = type.originalType.expect<Type.Struct>()
						val index = originStruct.memberIndex(rhs.name)
						ValueRef(
							LLVMBuildStructGEP2(builder, originStruct.llvm, lv.llvm, index, "access_member"),
							originStruct.memberType(index),
							type.isConst
						)
					}
					is CallNode -> {
						// lhs is actually the receiver
						CallNode(lv.direct(), rhs.name, rhs.elements).codegen()
					}
					else -> error("Unexpected $rhs")
				}
			}
			else -> arithmeticCodegen(lv, rv, op)
		}
	}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun checkPointerOperation(op: BinOp) =
		require(op == BinOp.Plus || op == BinOp.Minus) { "$op is not allowed between pointers and integral." }

	@Suppress("NOTHING_TO_INLINE")
	private inline fun Context.pointerArithmeticCodegen(lhs: Value, rhs: Value, op: BinOp): Value {
		val lhsPointer = lhs.type.asPointerOrNull()
		val rhsPointer = rhs.type.asPointerOrNull()
		return when {
			lhsPointer != null && rhsPointer != null -> {
				require(op == BinOp.Minus) { "$op is not allowed between two pointers." }
				require(lhsPointer.elementType == rhsPointer.elementType) { "Subtraction between two incompatible pointer types: ${lhs.type} and ${rhs.type}" }
				arithmeticCodegen(
					arithmeticCodegen(lhs.pointerToInt(), rhs.pointerToInt(), BinOp.Minus),
					lhsPointer.elementType.sizeOf(),
					BinOp.Divide
				)
			}
			lhsPointer != null -> {
				rhs.type.expect<Primitive.Integral>()
				checkPointerOperation(op)
				arithmeticCodegen(
					lhs.pointerToInt(),
					arithmeticCodegen(rhs, lhsPointer.elementType.sizeOf(), BinOp.Multiply),
					op
				).explicitCast(lhs.type)
			}
			rhsPointer != null -> {
				lhs.type.expect<Primitive.Integral>()
				checkPointerOperation(op)
				arithmeticCodegen(
					arithmeticCodegen(lhs, rhsPointer.elementType.sizeOf(), BinOp.Multiply),
					rhs.pointerToInt(),
					op
				).explicitCast(rhs.type)
			}
			else -> impossible()
		}
	}

	@Suppress("NAME_SHADOWING")
	private fun Context.arithmeticCodegen(lhs: Value, rhs: Value, op: BinOp): Value {
		if (lhs.type.isPointer || rhs.type.isPointer)
			return pointerArithmeticCodegen(lhs, rhs, op)
		val lhs = lhs.dereference()
		val rhs = rhs.dereference()
		val resultType = unifyOperandTypes(lhs.type, rhs.type)
		val type =
			if (op.returnBoolean) Primitive.Boolean
			else resultType
		val lhsValue = lift(lhs, resultType).llvm
		val rhsValue = lift(rhs, resultType).llvm
		if (resultType == Primitive.Boolean) {
			return Primitive.Boolean.new(
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
				}
			)
		}
		return if (op.returnBoolean) // comparision
			Primitive.Boolean.new(
				when (resultType) {
					is Primitive.Integral -> {
						LLVMBuildICmp(
							builder, when (op) {
								BinOp.Equal -> LLVMIntEQ
								BinOp.NotEqual -> LLVMIntNE
								BinOp.Less -> resultType.foldSign(LLVMIntSLT, LLVMIntULT)
								BinOp.LessOrEqual -> resultType.foldSign(LLVMIntSLE, LLVMIntULE)
								BinOp.Greater -> resultType.foldSign(LLVMIntSGT, LLVMIntUGT)
								BinOp.GreaterOrEqual -> resultType.foldSign(LLVMIntSGE, LLVMIntUGE)
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
				}
			)
		else type.new(
			when (type) {
				is Primitive.Integral ->
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
			}
		)
	}

	override fun toString(): String = "($lhs ${op.symbol} $rhs)"
}