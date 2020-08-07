package com.mivik.kamet

internal sealed class TypeDescriptor {
	abstract fun Context.translateForThis(): Type

	class Direct(val type: Type) : TypeDescriptor() {
		override fun Context.translateForThis(): Type = type
		override fun toString(): String = type.toString()
	}

	class Named(val name: String) : TypeDescriptor() {
		override fun Context.translateForThis(): Type = lookupType(name)
		override fun toString(): String = name
	}

	class Reference(val originalType: TypeDescriptor, val isConst: Boolean) : TypeDescriptor() {
		override fun Context.translateForThis(): Type = originalType.translate().reference(isConst)
		override fun toString(): String = "&${if (isConst) "const " else ""}$originalType"
	}

	class Pointer(val originalType: TypeDescriptor, val isConst: Boolean) : TypeDescriptor() {
		override fun Context.translateForThis(): Type = originalType.translate().pointer(isConst)
		override fun toString(): String = "*${if (isConst) "const " else ""}$originalType"
	}

	class Function(val returnType: TypeDescriptor, val parameterTypes: List<TypeDescriptor>) : TypeDescriptor() {
		override fun Context.translateForThis(): Type =
			Type.Function(returnType.translate(), parameterTypes.map { it.translate() })

		override fun toString(): String = "$returnType(${parameterTypes.joinToString()})"
	}
}

internal fun Type.asDescriptor() = TypeDescriptor.Direct(this)
