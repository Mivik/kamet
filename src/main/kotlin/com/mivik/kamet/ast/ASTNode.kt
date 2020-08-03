package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal interface ASTNode {
	fun codegen(context: Context): Value

	val returned: Boolean
		get() = false
}