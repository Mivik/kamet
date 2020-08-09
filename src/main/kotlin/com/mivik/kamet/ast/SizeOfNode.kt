package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class SizeOfNode(val type: TypeDescriptor) : ASTNode {
	override fun Context.codegenForThis(): Value = type.translate().sizeOf()
}