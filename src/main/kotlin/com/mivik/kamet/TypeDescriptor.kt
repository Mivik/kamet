package com.mivik.kamet

internal sealed class TypeDescriptor {
	abstract fun translate(context: Context): Type

	class Direct(val type: Type): TypeDescriptor() {
		override fun translate(context: Context): Type = type

		override fun toString(): String = type.toString()
	}

	class Named(val name: String) : TypeDescriptor() {
		override fun translate(context: Context): Type = context.lookupType(name)

		override fun toString(): String = name
	}

	class Reference(val originalType: TypeDescriptor, val isConst: Boolean) : TypeDescriptor() {
		override fun translate(context: Context): Type = originalType.translate(context).reference(isConst)

		override fun toString(): String = "&${if (isConst) "const " else ""}$originalType"
	}

	class Pointer(val originalType: TypeDescriptor, val isConst: Boolean) : TypeDescriptor() {
		override fun translate(context: Context): Type = originalType.translate(context).pointer(isConst)

		override fun toString(): String = "*${if (isConst) "const " else ""}$originalType"
	}
}

internal fun Type.asDescriptor() = TypeDescriptor.Direct(this)
