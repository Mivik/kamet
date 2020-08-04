package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class UndefNode(val type: TypeDescriptor) : ASTNode {
	override fun codegen(context: Context): Value = type.translate(context).undefined()
}