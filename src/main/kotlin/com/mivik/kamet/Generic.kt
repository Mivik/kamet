package com.mivik.kamet

abstract class Generic(val name: String, val typeParameters: List<TypeParameter>) {
	protected abstract fun Context.instantiate(arguments: List<Type>): Any

	fun Context.resolveForThis(arguments: List<Type>): Any {
		val genericName = genericName(name, arguments)
		lookupInternalOrNull(genericName)?.let { return it }
		require(typeParameters.size == arguments.size) { "Expected ${typeParameters.size} type arguments, got ${arguments.size}: [${arguments.joinToString()}]" }
		val sub = subContext(topLevel = true)
		typeParameters.forEachIndexed { index, para ->
			if (!para.check(arguments[index])) error("${arguments[index]} does not satisfy $para")
			sub.declareType(para.name, arguments[index])
		}
		return (with(sub) { instantiate(arguments) }).also { declareInternal(genericName, it) }
	}
}