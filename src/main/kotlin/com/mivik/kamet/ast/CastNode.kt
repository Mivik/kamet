package com.mivik.kamet.ast

import com.mivik.kamet.CastManager
import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class CastNode(val value: ASTNode, val type: TypeDescriptor) : ASTNode {
	override fun codegen(context: Context): Value =
		CastManager.explicitCast(context, value.codegen(context), type.translate(context))
}