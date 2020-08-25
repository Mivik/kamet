package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

@Suppress("NOTHING_TO_INLINE")
internal abstract class ASTNode {
	var beginIndex: Int = -1
	var endIndex: Int = -1

	inline fun withStartIndex(location: Int) = apply {
		this.beginIndex = location
	}

	inline fun withEndIndex(location: Int) = apply {
		this.beginIndex = location
	}

	abstract fun Context.codegenForThis(): Value

	open val returned: Boolean get() = false
}