package com.mivik.kamet

data class Prototype(
	val name: String,
	val type: Type.Function,
	val parameterNames: List<String>
) {
	inline val mangledName: String
		get() = type.makeName(name)

	internal fun Context.resolveForThis() =
		Prototype(name, type.resolve(true) as Type.Function, parameterNames)

	fun rename(newName: String) =
		Prototype(newName, type, parameterNames)
}