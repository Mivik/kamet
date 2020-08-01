package com.mivik.kamet

import com.mivik.kamet.ast.ConstantNode
import com.mivik.kamet.ast.ExprNode
import com.mivik.kamet.ast.BinOpNode
import com.mivik.kamet.ast.ValueNode
import java.util.LinkedList

internal class Parser(private val lexer: Lexer) {
	constructor(chars: CharSequence) : this(Lexer(chars))

	private val readBuffer = LinkedList<Token>()
	private var context = Context()

	private fun take(): Token =
		if (readBuffer.isEmpty()) lexer.lex()
		else readBuffer.pop()

	private fun peek(): Token =
		if (readBuffer.isEmpty()) lexer.lex().also { readBuffer.push(it) }
		else readBuffer.peek()

	private inline fun <reified T> Token?.expect() {
		require(this is T) { "Expected ${T::class.simpleName}, got $this" }
	}

	private fun unexpected(token: Token): Nothing = error("Unexpected $token")

	private fun precedenceOf(token: Token) =
		if (token is BinOp) token.precedence
		else -1

	// TODO Use stack to implement this.
	private fun takeBinOp(precedence: Int, lhs: ExprNode): ExprNode {
		var currentLHS = lhs
		while (true) {
			val current = peek()
			val currentPrecedence = precedenceOf(current)
			if (currentPrecedence <= precedence) return currentLHS
			take()
			var rhs = takePrimary()
			val nextPrecedence = precedenceOf(peek())
			if (currentPrecedence < nextPrecedence)
				rhs = takeBinOp(currentPrecedence, rhs)
			currentLHS = BinOpNode(currentLHS, rhs, current as BinOp)
		}
	}

	private fun takePrimary(): ExprNode =
		when (val token = take()) {
			is Token.Identifier -> ValueNode(context.lookup(token.name))
			is Token.LeftParenthesis -> takeParenthesisExpr()
			is Token.Constant -> ConstantNode(token.type, token.literal)
			else -> unexpected(token)
		}

	private fun takeParenthesisExpr(): ExprNode {
		val ret = takeExpr()
		take().expect<Token.RightParenthesis>()
		return ret
	}

	fun takeExpr(): ExprNode {
		val lhs = takePrimary()
		return takeBinOp(-1, lhs)
	}
}