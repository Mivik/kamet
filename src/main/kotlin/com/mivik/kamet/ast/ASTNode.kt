package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal interface ASTNode {
	fun codegen(context: Context): Value = Value.Empty
}

internal interface StmtNode : ASTNode

internal interface ExprNode : ASTNode, StmtNode