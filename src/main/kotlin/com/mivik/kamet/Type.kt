package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM
import org.kiot.util.contentEquals

sealed class Type(val name: String) {
	companion object {
		private val defaultTypes by lazy {
			arrayOf(
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
		}

		val defaultTypeMap: PersistentMap<String, Type> by lazy {
			PersistentMap<String, Type>().apply {
				for (type in defaultTypes)
					this[type.name] = type
			}
		}

		val pointerAddressType = Primitive.Integral.ULong
	}

	override fun toString(): String = name

	open fun dereference(): Type = this

	abstract fun Context.resolveForThis(): Type
	abstract val llvm: LLVMTypeRef

	fun undefined(): Value = new(LLVM.LLVMGetUndef(llvm))
	open fun new(llvm: LLVMValueRef): Value = Value(llvm, this)

	open fun asPointerOrNull(): Pointer? = null
	inline val isPointer get() = asPointerOrNull() != null

	abstract class Composed(name: String) : Type(name)
	abstract class Unresolved(name: String) : Type(name) {
		override val llvm: LLVMTypeRef
			get() = error("Getting the real type of an unresolved type: ${name.escape()}")
	}

	object Nothing : Type("Nothing") {
		override fun Context.resolveForThis(): Type = this@Nothing
		override val llvm: LLVMTypeRef = LLVM.LLVMVoidType()
	}

	object Unit : Type("Unit") {
		override fun Context.resolveForThis(): Type = this@Unit
		override val llvm: LLVMTypeRef = LLVM.LLVMVoidType()
	}

	class Named(name: String) : Unresolved(name) {
		override fun Context.resolveForThis(): Type = lookupType(name)
	}

	class Array(
		val elementType: Type,
		val size: Int,
		val isConst: Boolean
	) : Type("[${isConst.ifThat { "const " }}$elementType, $size]") {
		override fun Context.resolveForThis(): Type = Array(elementType.resolve(), size, isConst)

		override val llvm: LLVMTypeRef by lazy { LLVM.LLVMArrayType(elementType.llvm, size) }

		override fun equals(other: Any?): Boolean =
			if (other is Array) elementType == other.elementType && size == other.size
			else false

		override fun hashCode(): Int {
			var result = elementType.hashCode()
			result = 31 * result + size
			return result
		}
	}

	class Function(val receiverType: Type?, val returnType: Type, val parameterTypes: List<Type>) :
		Composed("${receiverType.ifNotNull { "$receiverType." }}(${parameterTypes.joinToString()}):$returnType") {
		inline val hasReceiver get() = receiverType != null

		override fun Context.resolveForThis(): Type =
			Function(receiverType?.resolve(), returnType.resolve(), parameterTypes.map { it.resolve() })

		override val llvm: LLVMTypeRef by lazy {
			val size = parameterTypes.size + hasReceiver.toInt()
			LLVM.LLVMFunctionType(
				returnType.llvm,
				if (receiverType == null)
					buildPointerPointer(size) { parameterTypes[it].llvm }
				else
					buildPointerPointer(size) {
						if (it == 0) receiverType.llvm
						else parameterTypes[it - 1].llvm
					},
				size,
				0
			)
		}

		override fun equals(other: Any?): Boolean =
			if (other is Function)
				receiverType == other.receiverType &&
						returnType == other.returnType &&
						parameterTypes.contentEquals(other.parameterTypes)
			else false

		override fun hashCode(): Int {
			var result = receiverType.hashCode()
			result = 31 * result + returnType.hashCode()
			result = 31 * result + parameterTypes.hashCode()
			return result
		}
	}

	class Struct(name: String, val elements: List<Pair<String, Type>>, private val packed: Boolean) : Composed(name) {
		override fun Context.resolveForThis(): Type =
			Struct(name, elements.map { Pair(it.first, it.second.resolve()) }, packed)

		override val llvm: LLVMTypeRef by lazy {
			LLVM.LLVMStructType(
				buildPointerPointer(elements.size) { elements[it].second.llvm },
				elements.size,
				packed.toInt()
			)
		}

		fun memberIndex(name: String) = elements.indexOfFirst { it.first == name }.also {
			if (it == -1) error("Struct type ${this.name.escape()} has no member named ${name.escape()}")
		}

		@Suppress("NOTHING_TO_INLINE")
		inline fun memberName(index: Int) = elements[index].first

		@Suppress("NOTHING_TO_INLINE")
		inline fun memberType(index: Int) = elements[index].second
	}

	sealed class Primitive(name: String, val sizeInBits: Int, override val llvm: LLVMTypeRef) : Type(name) {
		override fun Context.resolveForThis(): Type = this@Primitive

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
		Type("&${isConst.ifThat { "const " }}($originalType)") {
		override fun new(llvm: LLVMValueRef): Value = ValueRef(llvm, originalType, isConst)

		override fun Context.resolveForThis(): Type = Reference(originalType.resolve(), isConst)

		override val llvm: LLVMTypeRef by lazy { originalType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? =
			if (originalType is Array) originalType.elementType.pointer(originalType.isConst)
			else null

		override fun dereference(): Type = originalType

		override fun equals(other: Any?): Boolean =
			if (other is Reference)
				isConst == other.isConst && originalType == other.originalType
			else false

		override fun hashCode(): Int = originalType.hashCode()
	}

	class Pointer(val elementType: Type, val isConst: Boolean) :
		Type("*${isConst.ifThat { "const " }}($elementType)") {
		init {
			require(elementType !is Reference) { "Creating a pointer to a reference" }
		}

		override fun Context.resolveForThis(): Type = Pointer(elementType.resolve(), isConst)

		override val llvm: LLVMTypeRef by lazy { elementType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? = this

		override fun equals(other: Any?): Boolean =
			if (other is Pointer)
				isConst == other.isConst && elementType == other.elementType
			else false

		override fun hashCode(): Int = elementType.hashCode()
	}

	class Generic(val genericName: String, val arguments: List<Type>) :
		Unresolved(genericName(genericName, arguments)) {
		override fun Context.resolveForThis(): Type =
			lookupGeneric(genericName).resolve(arguments.map { it.resolve() }) as Type
	}
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> Type.Primitive.Integral.foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned

@Suppress("NOTHING_TO_INLINE")
inline fun Type.reference(isConst: Boolean = false) = Type.Reference(this, isConst)

@Suppress("NOTHING_TO_INLINE")
inline fun Type.pointer(isConst: Boolean = false) = Type.Pointer(this, isConst)
