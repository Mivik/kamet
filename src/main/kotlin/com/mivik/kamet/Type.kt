package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.global.LLVM

sealed class Type(val superType: Type? = Any) {
	abstract val sizeInBits: Int // TODO this is not always int!

	override fun toString(): String = javaClass.simpleName

	open fun isSubtypeOf(other: Type): Boolean {
		if (other == Any) return true
		var current = this
		while (current != other) current = current.superType ?: return false
		return true
	}

	fun isSuperTypeOf(other: Type): Boolean = other.isSubtypeOf(this)

	object Any : Type(null) {
		override val sizeInBits: Int
			get() = 0
	}

	object Nothing : Type(null) {
		override val sizeInBits: Int
			get() = 0

		override fun isSubtypeOf(other: Type): Boolean = true
	}

	object Unit : Type(null) {
		override val sizeInBits: Int
			get() = 0

		override fun isSubtypeOf(other: Type): Boolean = false
	}

	sealed class Primitive(override val sizeInBits: Int, val llvm: LLVMTypeRef) : Type() {
		object Boolean : Primitive(1, LLVM.LLVMIntType(1))

		sealed class Integer(sizeInBits: kotlin.Int, val signed: kotlin.Boolean) :
			Primitive(sizeInBits, LLVM.LLVMIntType(sizeInBits)) {
			object Char : Integer(16, true)

			object Byte : Integer(8, true)
			object UByte : Integer(8, false)
			object Short : Integer(16, true)
			object UShort : Integer(16, false)
			object Int : Integer(32, true)
			object UInt : Integer(32, false)
			object Long : Integer(64, true)
			object ULong : Integer(64, false)
		}

		sealed class Real(sizeInBits: Int, llvm: LLVMTypeRef) : Primitive(sizeInBits, llvm) {
			object Float : Real(32, LLVM.LLVMFloatType())
			object Double : Real(64, LLVM.LLVMDoubleType())
		}
	}
}