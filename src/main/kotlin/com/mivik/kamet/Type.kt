package com.mivik.kamet

import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM
import java.util.Objects

private inline fun <reified T : Type> T.typeEquals(otherType: Any?, block: (T) -> Boolean): Boolean {
	if (this === otherType) return true
	return if (otherType is Type) {
		if (this is Type.TypeParameter) {
			if (otherType is Type.TypeParameter) {
				if (otherType is T) block(otherType) else false
			} else TypeParameterTable.get().equals(typeParameter, otherType)
		} else if (otherType is Type.TypeParameter) TypeParameterTable.get().equals(otherType.typeParameter, this)
		else if (otherType is T) block(otherType)
		else false
	} else false
}

@Suppress("EqualsOrHashCode")
sealed class Type {
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
	open fun dereference(): Type = this
	abstract val llvm: LLVMTypeRef
	open fun Context.transformForThis(action: Context.(Type) -> Type?): Type = action(this@Type) ?: this@Type
	override fun toString(): String = name

	override fun equals(other: Any?): Boolean =
		typeEquals(other) { this === other }

	fun undefined(): Value = new(LLVM.LLVMGetUndef(llvm))
	open fun new(llvm: LLVMValueRef): Value = Value(llvm, this)

	open fun asPointerOrNull(): Pointer? = null
	inline val isPointer get() = asPointerOrNull() != null

	abstract class Composed : Type()
	abstract class Abstract : Type() {
		override val llvm: LLVMTypeRef
			get() = error("Getting the real type of an abstract type: ${name.escape()}")
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

	class Named(override val name: String) : Abstract() {
		override fun equals(other: Any?): Boolean =
			typeEquals(other) { name == it.name }

		override fun hashCode(): Int = name.hashCode()
	}

	open class TypeParameter(val typeParameter: com.mivik.kamet.TypeParameter) : Abstract() {
		override val name: String
			get() = typeParameter.name

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { false }

		override fun hashCode(): Int = typeParameter.hashCode()
	}

	class Array(
		val elementType: Type,
		val size: Int,
		val isConst: Boolean
	) : Type() {
		override val name = "[${isConst.ifThat { "const " }}$elementType, $size]"

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Array) ?: Array(elementType.transform(action), size, isConst)

		override val llvm: LLVMTypeRef by lazy { LLVM.LLVMArrayType(elementType.llvm, size) }

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { elementType == it.elementType && size == it.size && isConst == it.isConst }

		override fun hashCode(): Int = Objects.hash(elementType, size, isConst)
	}

	class Function(val receiverType: Type?, val returnType: Type, val parameterTypes: List<Type>) : Composed() {
		override val name = makeName()

		fun makeName(functionName: String = "") =
			"${receiverType.ifNotNull { "$receiverType." }}$functionName(${parameterTypes.joinToString()}): $returnType"

		inline val hasReceiver get() = receiverType != null

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Function) ?: Function(
				receiverType?.transform(action),
				returnType.transform(action),
				parameterTypes.map { it.transform(action) })

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
			typeEquals(other) {
				receiverType == it.receiverType
						&& returnType == it.returnType
						&& parameterTypes == it.parameterTypes
			}

		override fun hashCode(): Int = Objects.hash(receiverType, returnType, parameterTypes)
	}

	class Struct(override val name: String, val elements: List<Pair<String, Type>>, private val packed: Boolean) :
		Composed() {
		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Struct) ?: Struct(name, elements.map { Pair(it.first, it.second.transform(action)) }, packed)

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

		override fun equals(other: Any?): Boolean =
			typeEquals(other) {
				name == it.name
						&& elements == it.elements
						&& packed == it.packed
			}

		override fun hashCode(): Int = Objects.hash(name, elements, packed)
	}

	sealed class Primitive(override val name: String, val sizeInBits: Int, override val llvm: LLVMTypeRef) : Type() {
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

	open class Reference(val originalType: Type, val isConst: Boolean) : Type() {
		override val name = "&${isConst.ifThat { "const " }}($originalType)"

		override fun new(llvm: LLVMValueRef): Value = ValueRef(llvm, originalType, isConst)

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Reference) ?: originalType.transform(action).reference(isConst)

		override val llvm: LLVMTypeRef by lazy { originalType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? =
			if (originalType is Array) originalType.elementType.pointer(originalType.isConst)
			else null

		override fun dereference(): Type = originalType

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { originalType == it.originalType && isConst == it.isConst }

		override fun hashCode(): Int = Objects.hash(originalType, isConst)
	}

	class Pointer(val elementType: Type, val isConst: Boolean) : Type() {
		override val name = "*${isConst.ifThat { "const " }}($elementType)"

		init {
			require(elementType !is Reference) { "Creating a pointer to a reference" }
		}

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Pointer) ?: Pointer(elementType.transform(action), isConst)

		override val llvm: LLVMTypeRef by lazy { elementType.llvm.pointer() }

		override fun asPointerOrNull(): Pointer? = this

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { elementType == it.elementType && isConst == it.isConst }

		override fun hashCode(): Int = Objects.hash(elementType, isConst)
	}

	class Generic(val base: Type, val typeParameters: List<com.mivik.kamet.TypeParameter>) : Abstract() {
		override val name = genericName(base.name, typeParameters)

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Generic) ?: Generic(base.transform(action), typeParameters)

		fun Context.resolveGeneric(typeArguments: List<Type>): Type =
			buildGeneric(base.name, typeParameters, typeArguments) {
				base.resolve(resolveTypeParameter = true)
			}

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { base == it.base && typeParameters == it.typeParameters }

		override fun hashCode(): Int = Objects.hash(base, typeParameters)
	}

	class Actual(val generic: Type, val typeArguments: List<Type>) : Abstract() {
		override val name = actualGenericName(generic.name, typeArguments)

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Actual) ?: generic.transform(action).let {
				if (it is Generic) with(it) { resolveGeneric(typeArguments) }
				else Actual(it, typeArguments)
			}

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { generic == it.generic && typeArguments == it.typeArguments }

		override fun hashCode(): Int = Objects.hash(generic, typeArguments)
	}

	class DynamicReference(val trait: Trait, val type: Type?, isConst: Boolean) :
		Reference(Dynamic(trait, type), isConst) {
		// "pointers to void are invalid - use i8* instead"
		override val llvm: LLVMTypeRef by lazy {
			LLVM.LLVMStructType(
				buildPointerPointer(LLVM.LLVMInt8Type().pointer().pointer(), LLVM.LLVMInt8Type().pointer()),
				2,
				0
			)
		}

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@DynamicReference) ?: DynamicReference(trait.resolve(), type?.resolve(), isConst)
	}

	class Dynamic(val trait: Trait, val type: Type?) : Abstract() {
		override val name = "dyn ${trait.name}${type.ifNotNull { "[$type]" }}"

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@Dynamic) ?: Dynamic(trait.resolve(), type?.resolve())

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { trait == it.trait }

		override fun hashCode(): Int = trait.hashCode()
	}

	object UnresolvedThis : Abstract() {
		override val name: String
			get() = "This"
	}

	class This(val trait: Trait) : TypeParameter(com.mivik.kamet.TypeParameter.This(trait)) {
		override val name: String
			get() = "This[$trait]"

		override fun Context.transformForThis(action: Context.(Type) -> Type?): Type =
			action(this@This) ?: This(trait.resolve())

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { trait == it.trait }

		override fun hashCode(): Int = trait.hashCode()
	}

	class Impl(val trait: Trait) : Abstract() {
		override val name: String
			get() = "impl $trait"

		override fun equals(other: Any?): Boolean =
			typeEquals(other) { trait == it.trait }

		override fun hashCode(): Int = trait.hashCode()
	}
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> Type.Primitive.Integral.foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned

@Suppress("NOTHING_TO_INLINE")
inline fun Type.reference(isConst: Boolean = false) =
	if (this is Type.Dynamic) Type.DynamicReference(trait, type, isConst)
	else Type.Reference(this, isConst)

@Suppress("NOTHING_TO_INLINE")
inline fun Type.pointer(isConst: Boolean = false) = Type.Pointer(this, isConst)
