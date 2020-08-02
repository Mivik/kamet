package com.mivik.kamet

import com.mivik.kamet.ast.ConstantNode
import com.mivik.kamet.ast.ExprNode
import com.mivik.kamet.ast.BinOpNode
import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import com.mivik.kamet.ast.StmtNode
import com.mivik.kamet.ast.ValDeclareNode
import com.mivik.kamet.ast.ValueNode
import com.mivik.kamet.ast.VarDeclareNode
import java.util.LinkedList

internal class Parser(private val lexer: Lexer) {
	constructor(chars: CharSequence) : this(Lexer(chars))

	private val readBuffer = LinkedList<Token>()

	private fun take(): Token =
		if (readBuffer.isEmpty()) lexer.lex()
		else readBuffer.pop()

	private fun peek(): Token =
		if (readBuffer.isEmpty()) lexer.lex().also { readBuffer.push(it) }
		else readBuffer.peek()

	private inline fun <reified T> Token?.expect(): Token {
		require(this is T) { "Expected ${T::class.simpleName}, got $this" }
		return this
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
			is Token.Identifier -> ValueNode(token.name)
			Token.LeftParenthesis -> takeExpr().also { take().expect<Token.RightParenthesis>() }
			is Token.Constant -> ConstantNode(token.type, token.literal)
			else -> unexpected(token)
		}

	fun takeExpr(): ExprNode {
		val lhs = takePrimary()
		return takeBinOp(-1, lhs)
	}

	fun takeStmt(): StmtNode =
		when (peek()) {
			Token.Val -> {
				take()
				// TODO better error output for stuff like this (should output "Expected xxx, got xxx" instead of throwing an cast error)
				val name = (take() as Token.Identifier).name
				take().expect<Token.Assign>()
				ValDeclareNode(name, takeExpr())
			}
			Token.Var -> {
				take()
				val name = (take() as Token.Identifier).name
				take().expect<Token.Assign>()
				VarDeclareNode(name, takeExpr())
			}
			Token.Return -> {
				take()
				ReturnNode(takeExpr())
			}
			else -> takeExpr()
		}

	fun takeBlock(): BlockNode {
		take().expect<Token.LeftBrace>()
		val block = BlockNode()
		while (peek() != Token.RightBrace)
			block.elements += takeStmt()
		take()
		return block
	}

	fun takePrototype(): PrototypeNode {
		val name = (take() as Token.Identifier).name
		val args = mutableListOf<Pair<String, String>>()
		take().expect<Token.LeftParenthesis>()
		if (peek() != Token.RightParenthesis)
			while (true) {
				val argName = (take() as Token.Identifier).name
				take().expect<Token.Colon>()
				val typeName = (take() as Token.Identifier).name
				args.add(Pair(argName, typeName))
				val splitter = take()
				if (splitter == Token.RightParenthesis) break
				else splitter.expect<Token.Comma>()
			}
		else take()
		return if (peek() == Token.Colon) {
			take()
			PrototypeNode(name, (take() as Token.Identifier).name, args)
		} else PrototypeNode(name, Type.Unit.name, args)
	}

	fun takeFunction(): FunctionNode {
		take().expect<Token.Function>()
		val prototype = takePrototype()
		val body = takeBlock()
		return FunctionNode(prototype, body)
	}
}