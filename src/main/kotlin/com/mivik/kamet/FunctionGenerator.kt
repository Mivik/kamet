package com.mivik.kamet

internal interface FunctionGenerator {
	fun Context.generateForThis(newName: String? = null): Function

	val prototype: Prototype
}