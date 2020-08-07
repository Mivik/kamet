package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class DirectNode(val value: Value) : ASTNode {
	override fun Context.codegenForThis(): Value = value

	override fun toString(): String = value.toString()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Value.direct() = DirectNode(this)
