package com.mivik.kamet

class GenericType(val originalType: Type.Composed, val parameterNames: List<String>) {
	fun Context.buildForThis(arguments: List<Type>): Type {
		require(parameterNames.size == arguments.size) { "Expected ${parameterNames.size} type arguments, got ${arguments.size}: [${arguments.joinToString()}]" }
		val sub = subContext()
		for (i in parameterNames.indices)
			sub.declareType(parameterNames[i], arguments[i])
		return with(sub) { originalType.resolve("$originalType<${arguments.joinToString(",")}>") }
	}
}