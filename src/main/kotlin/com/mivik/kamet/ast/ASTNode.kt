package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

@Suppress("NOTHING_TO_INLINE")
internal abstract class ASTNode {
	abstract fun Context.codegenForThis(): Value

	open val returned: Boolean get() = false
}