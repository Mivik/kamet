package com.mivik.kamet.ast

import com.mivik.kamet.KametToken

internal abstract class Node

internal abstract class ExprNode : Node()

internal abstract class ConstantNode : ExprNode()

internal class IntNode(val value: Int) : ConstantNode()
internal class LongNode(val value: Long) : ConstantNode()
internal class DoubleNode(val value: Double) : ConstantNode()
internal class StringNode(val value: String) : ConstantNode()

internal class BinaryOpNode(val lhs: ExprNode, val rhs: Node, val type: KametToken.BinaryOperation) : ExprNode()
