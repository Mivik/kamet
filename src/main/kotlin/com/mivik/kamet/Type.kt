package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

sealed class Type : Resolvable {
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

	abstract val name: String
	override fun toString(): String = name
	open fun dereference(): Type = this
	abstract val llvm: LLVMTypeRef
	override fun Context.resolveForThis(): Type = this@Type

	fun undefined(): Value = new(LLVM.LLVMGetUndef(llvm))
	open fun new(llvm: LLVMValueRef): Value = Value(llvm, this)

	open fun asPointerOrNull(): Pointer? = null
	inline val isPointer get() = asPointerOrNull() != null

	abstract class Composed : Type()
	abstract class Abstract : Type() {
		override val llvm: LLVMTypeRef
			get() = error("Getting the real type of an unresolved type: ${name.escape()}")
	}

	object Nothing : Type() {
		override val name: String
			get() = "Nothing"
		override val llvm: LLVMTypeRef = LLVM.LLVMVoidType()
	}

	object Unit : Type() {
		override val name: String
			get() = "Unit"
		override val llvm: LLVMTypeRef = LLVM.LLVMVoidType()
	}

	data class Named(override val name: String) : Abstract() {
		override val resolved: Boolean
			get() = false

		override fun Context.resolveForThis() = lookupTypeOrNull(name) ?: this@Named
	}

	data class Array(
		val elementType: Type,
		val size: Int,
		val isConst: Boolean
	) : Type() {
		override val name = "[${isConst.ifThat { "const " }}$elementType, $size]"
		override val resolved = elementType.resolved

		override fun Context.resolveForThis() =
			Array(elementType.resolve(), size, isConst)

		override val llvm: LLVMTypeRef by lazy { LLVM.LLVMArrayType(elementType.llvm, size) }
	}

	data class Function(val receiverType: Type?, val returnType: Type, val parameterTypes: List<Type>) : Composed() {
		override val name =
			"${receiverType.ifNotNull { "$receiverType." }}(${parameterTypes.joinToString()}):$returnType"
		override val resolved =
			(receiverType == null || receiverType.resolved) && returnType.resolved && parameterTypes.all { it.resolved }

		inline val hasReceiver get() = receiverType != null

		override fun Context.resolveForThis() =
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
	}

	data class Struct(override val name: String, val elements: List<Pair<String, Type>>, private val packed: Boolean) :
		Composed() {
		override val resolved = elements.all { it.second.resolved }

		override fun Context.resolveForThis() =
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

	sealed class Primitive(override val name: String, val sizeInBits: Int, override val llvm: LLVMTypeRef) : Type() {
		override val resolved: kotlin.Boolean
			get() = true

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

	data class Reference(val originalType: Type, val isConst: Boolean) : Type() {
		override val name = "&${isConst.ifThat { "const " }}($originalType)"
		override val resolved = originalType.resolved

		override fun new(llvm: LLVMValueRef): Value = ValueRef(llvm, originalType, isConst)

		override fun Context.resolveForThis() =
			Reference(originalType.resolve(), isConst)

		override val llvm: LLVMTypeRef by lazy { originalType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? =
			if (originalType is Array) originalType.elementType.pointer(originalType.isConst)
			else null

		override fun dereference(): Type = originalType
	}

	data class Pointer(val elementType: Type, val isConst: Boolean) : Type() {
		override val name = "*${isConst.ifThat { "const " }}($elementType)"

		init {
			require(elementType !is Reference) { "Creating a pointer to a reference" }
		}

		override val resolved = elementType.resolved

		override fun Context.resolveForThis() =
			Pointer(elementType.resolve(), isConst)

		override val llvm: LLVMTypeRef by lazy { elementType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? = this
	}

	data class Generic(val baseType: Type, val typeParameters: List<TypeParameter>) : Abstract() {
		override val name = baseType.name
		override val resolved = baseType.resolved

		override fun Context.resolveForThis() = Generic(baseType.resolve(), typeParameters)

		fun Context.resolveGenericForThis(typeArguments: List<Type>): Type =
			buildGeneric(baseType.name, typeParameters, typeArguments) {
				baseType.resolve()
			}
	}

	data class ActualGeneric(val genericType: Type, val typeArguments: List<Type>) : Abstract() {
		override val name = actualGenericName(genericType.name, typeArguments)
		override val resolved: Boolean
			get() = false

		override fun Context.resolveForThis(): Type =
			genericType.resolve().expect<Generic>().resolveGeneric(typeArguments)
	}
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> Type.Primitive.Integral.foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned

@Suppress("NOTHING_TO_INLINE")
inline fun Type.reference(isConst: Boolean = false) = Type.Reference(this, isConst)

@Suppress("NOTHING_TO_INLINE")
inline fun Type.pointer(isConst: Boolean = false) = Type.Pointer(this, isConst)
