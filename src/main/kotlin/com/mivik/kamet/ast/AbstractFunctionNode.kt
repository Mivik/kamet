package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal abstract class AbstractFunctionNode : ASTNode() {
	abstract fun Context.directCodegenForThis(newName: String? = null): Value

	abstract val prototype: PrototypeNode
}