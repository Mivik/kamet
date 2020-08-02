package com.mivik.kamet

import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.global.LLVM

sealed class Type(val name: String, val llvm: LLVMTypeRef, val superType: Type? = Any) {
	companion object {
		val defaultTypes = arrayOf(
			Any,
			Nothing,
			Unit,
			Primitive.Boolean,
			Primitive.Integer.Char,
			Primitive.Integer.Byte,
			Primitive.Integer.UByte,
			Primitive.Integer.Short,
			Primitive.Integer.UShort,
			Primitive.Integer.Int,
			Primitive.Integer.UInt,
			Primitive.Integer.Long,
			Primitive.Integer.ULong,
			Primitive.Real.Float,
			Primitive.Real.Double
		)

		fun defaultTypeMap(): MutableMap<String, Type> =
			mutableMapOf<String, Type>().apply {
				for (type in defaultTypes) this[type.name] = type
			}
	}

	abstract val sizeInBits: Int // TODO this is not always int!

	override fun toString(): String = javaClass.simpleName

	open fun isSubtypeOf(other: Type): Boolean {
		if (other == Any) return true
		var current = this
		while (current != other) current = current.superType ?: return false
		return true
	}

	fun isSuperTypeOf(other: Type): Boolean = other.isSubtypeOf(this)

	fun undefined(): Value = Value(LLVM.LLVMGetUndef(llvm), this)

	object Any : Type("Any", LLVM.LLVMVoidType(), null) {
		override val sizeInBits: Int
			get() = 0
	}

	object Nothing : Type("Nothing", LLVM.LLVMVoidType()) {
		override val sizeInBits: Int
			get() = 0

		override fun isSubtypeOf(other: Type): Boolean = true
	}

	object Unit : Type("Unit", LLVM.LLVMVoidType()) {
		override val sizeInBits: Int
			get() = 0

		override fun isSubtypeOf(other: Type): Boolean = other == Unit
	}

	class Function(val returnType: Type, val parameterTypes: List<Type>) : Type(
		buildString {
			append('(')
			append(parameterTypes.joinToString(", ") { it.name })
			append(") -> ")
			append(returnType.name)
		},
		LLVM.LLVMFunctionType(
			returnType.llvm,
			PointerPointer(*Array(parameterTypes.size) { parameterTypes[it].llvm }),
			parameterTypes.size,
			0
		)
	) {
		override val sizeInBits: Int
			get() = error("Attempt to get the size of a function")

		override fun equals(other: kotlin.Any?): Boolean =
			if (other is Function) returnType == other.returnType && parameterTypes == other.parameterTypes
			else false

		override fun hashCode(): Int {
			var result = returnType.hashCode()
			result = 31 * result + parameterTypes.hashCode()
			return result
		}
	}

	sealed class Primitive(name: String, override val sizeInBits: Int, llvm: LLVMTypeRef) : Type(name, llvm) {
		object Boolean : Primitive("Boolean", 1, LLVM.LLVMIntType(1))

		sealed class Integer(name: String, sizeInBits: kotlin.Int, val signed: kotlin.Boolean) :
			Primitive(name, sizeInBits, LLVM.LLVMIntType(sizeInBits)) {
			object Char : Integer("Char", 16, true)

			object Byte : Integer("Byte", 8, true)
			object UByte : Integer("UByte", 8, false)
			object Short : Integer("Short", 16, true)
			object UShort : Integer("UShort", 16, false)
			object Int : Integer("Int", 32, true)
			object UInt : Integer("UInt", 32, false)
			object Long : Integer("Long", 64, true)
			object ULong : Integer("ULong", 64, false)
		}

		sealed class Real(name: String, sizeInBits: Int, llvm: LLVMTypeRef) : Primitive(name, sizeInBits, llvm) {
			object Float : Real("Float", 32, LLVM.LLVMFloatType())
			object Double : Real("Double", 64, LLVM.LLVMDoubleType())
		}
	}
}