package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class ReturnNode(val value: ExprNode) : StmtNode {
	override fun codegen(context: Context): Value {
		context.result = value.codegen(context)
		return Value.Null
	}
}