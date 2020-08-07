package com.mivik.kamet

import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.global.LLVM

sealed class Type(val name: String, val llvm: LLVMTypeRef) {
	companion object {
		val defaultTypes = arrayOf(
			Nothing,
			Unit,
			Primitive.Boolean,
			Primitive.Integral.Char,
			Primitive.Integral.Byte,
			Primitive.Integral.UByte,
			Primitive.Integral.Short,
			Primitive.Integral.UShort,
			Primitive.Integral.Int,
			Primitive.Integral.UInt,
			Primitive.Integral.Long,
			Primitive.Integral.ULong,
			Primitive.Real.Float,
			Primitive.Real.Double
		)

		fun defaultTypeMap(): MutableMap<String, Type> =
			mutableMapOf<String, Type>().apply {
				for (type in defaultTypes)
					this[type.name] = type
			}
	}

	override fun toString(): String = name

	open fun dereference(): Type = this

	fun undefined(): Value = Value(LLVM.LLVMGetUndef(llvm), this)

	object Nothing : Type("Nothing", LLVM.LLVMVoidType())
	object Unit : Type("Unit", LLVM.LLVMVoidType())

	class Function(val returnType: Type, val parameterTypes: List<Type>) : Type(
		"$returnType(${parameterTypes.joinToString()})",
		LLVM.LLVMFunctionType(
			returnType.llvm,
			PointerPointer(*Array(parameterTypes.size) { parameterTypes[it].llvm }),
			parameterTypes.size,
			0
		)
	) {
		override fun equals(other: Any?): Boolean =
			if (other is Function) returnType == other.returnType && parameterTypes == other.parameterTypes
			else false

		override fun hashCode(): Int {
			var result = returnType.hashCode()
			result = 31 * result + parameterTypes.hashCode()
			return result
		}
	}

	class Struct(name: String, val elements: List<Pair<String, Type>>, packed: Boolean) :
		Type(
			name,
			LLVM.LLVMStructType(
				PointerPointer(*Array(elements.size) { elements[it].second.llvm }),
				elements.size,
				if (packed) 1 else 0
			)
		) {
		fun memberIndex(name: String) = elements.indexOfFirst { it.first == name }.also {
			if (it == -1) error("Struct type ${this.name} has no member named $name")
		}

		@Suppress("NOTHING_TO_INLINE")
		inline fun memberName(index: Int) = elements[index].first

		@Suppress("NOTHING_TO_INLINE")
		inline fun memberType(index: Int) = elements[index].second
	}

	sealed class Primitive(name: String, val sizeInBits: Int, llvm: LLVMTypeRef) : Type(name, llvm) {
		object Boolean : Primitive("Boolean", 1, LLVM.LLVMIntType(1))

		sealed class Integral(name: String, sizeInBits: kotlin.Int, val signed: kotlin.Boolean) :
			Primitive(name, sizeInBits, LLVM.LLVMIntType(sizeInBits)) {
			object Char : Integral("Char", 16, true)

			object Byte : Integral("Byte", 8, true)
			object UByte : Integral("UByte", 8, false)
			object Short : Integral("Short", 16, true)
			object UShort : Integral("UShort", 16, false)
			object Int : Integral("Int", 32, true)
			object UInt : Integral("UInt", 32, false)
			object Long : Integral("Long", 64, true)
			object ULong : Integral("ULong", 64, false)
		}

		sealed class Real(name: String, sizeInBits: Int, llvm: LLVMTypeRef) : Primitive(name, sizeInBits, llvm) {
			object Float : Real("Float", 32, LLVM.LLVMFloatType())
			object Double : Real("Double", 64, LLVM.LLVMDoubleType())
		}
	}

	class Reference(val originalType: Type, val isConst: Boolean) :
		Type("&${if (isConst) "const " else ""}($originalType)", originalType.llvm.pointer()) {
		init {
			require(originalType !is Reference) { "Creating a reference of a reference" }
		}

		override fun dereference(): Type = originalType

		override fun equals(other: Any?): Boolean =
			if (other is Reference)
				isConst == other.isConst && originalType == other.originalType
			else false

		override fun hashCode(): Int = originalType.hashCode()
	}

	class Pointer(val originalType: Type, val isConst: Boolean) :
		Type("*${if (isConst) "const " else ""}($originalType)", originalType.llvm.pointer()) {
		init {
			require(originalType !is Reference) { "Creating a pointer to a reference" }
		}

		override fun equals(other: Any?): Boolean =
			if (other is Pointer)
				isConst == other.isConst && originalType == other.originalType
			else false

		override fun hashCode(): Int = originalType.hashCode()
	}
}

internal inline val Type.isReference get() = this is Type.Reference
internal inline val Type.isPointer get() = this is Type.Pointer

internal inline fun <T> Type.Primitive.Integral.lazyFoldSign(signed: () -> T, unsigned: () -> T) =
	if (this.signed) signed() else unsigned()

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> Type.Primitive.Integral.foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned

@Suppress("NOTHING_TO_INLINE")
inline fun Type.reference(isConst: Boolean = false): Type = Type.Reference(this, isConst)

@Suppress("NOTHING_TO_INLINE")
inline fun Type.pointer(isConst: Boolean = false): Type = Type.Pointer(this, isConst)

@Suppress("NOTHING_TO_INLINE")
inline fun Type.nullPointer(): Value = pointer().let { Value(LLVM.LLVMConstNull(it.llvm), it) }
