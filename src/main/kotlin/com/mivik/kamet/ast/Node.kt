package com.mivik.kamet.ast

import com.mivik.kamet.KametToken

internal abstract class Node

internal abstract class ExprNode : Node()

internal class IntNode(val value: Int) : ExprNode()
internal class LongNode(val value: Long) : ExprNode()

internal class BinaryOpNode(val lhs: ExprNode, val rhs: Node, val type: KametToken.BinaryOperation): ExprNode()
