package com.mivik.kamet

import com.mivik.kamet.ast.PrototypeNode

internal interface FunctionGenerator {
	fun Context.generateForThis(newName: String? = null): Function

	val prototype: PrototypeNode
}