package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.AssignNode
import com.mivik.kamet.ast.ConstantNode
import com.mivik.kamet.ast.BinOpNode
import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.DoWhileNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.IfNode
import com.mivik.kamet.ast.InvocationNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import com.mivik.kamet.ast.TopLevelNode
import com.mivik.kamet.ast.ValDeclareNode
import com.mivik.kamet.ast.ValueNode
import com.mivik.kamet.ast.VarDeclareNode
import com.mivik.kamet.ast.WhileNode
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

	@Suppress("NOTHING_TO_INLINE")
	private inline fun off(token: Token) {
		readBuffer.offerFirst(token)
	}

	private inline fun <reified T> Token?.expect(): Token {
		require(this is T) { "Expected ${T::class.simpleName}, got $this" }
		return this
	}

	private fun unexpected(token: Token): Nothing = error("Unexpected $token")

	private fun precedenceOf(token: Token) =
		if (token is BinOp) token.precedence
		else -1

	// TODO Use stack to implement this.
	private fun takeBinOp(precedence: Int, lhs: ASTNode): ASTNode {
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

	private fun takePrimary(): ASTNode =
		when (val token = take()) {
			is Token.Identifier ->
				if (peek() == Token.LeftParenthesis) {
					take()
					if (peek() != Token.RightParenthesis) {
						val list = mutableListOf<ASTNode>()
						while (true) {
							list += takeExpr()
							val splitter = take()
							if (splitter == Token.RightParenthesis) break
							else splitter.expect<Token.Comma>()
						}
						InvocationNode(token.name, list)
					} else {
						take()
						InvocationNode(token.name, emptyList())
					}
				} else ValueNode(token.name)
			Token.LeftParenthesis -> takeExpr().also { take().expect<Token.RightParenthesis>() }
			is Token.Constant -> ConstantNode(token.type, token.literal)
			is Token.If -> {
				take().expect<Token.LeftParenthesis>()
				val condition = takeExpr()
				take().expect<Token.RightParenthesis>()
				val thenBlock = takeBlockOrStmt()
				if (peek() == Token.Else) {
					take()
					IfNode(condition, thenBlock, takeBlockOrStmt())
				} else IfNode(condition, thenBlock)
			}
			else -> unexpected(token)
		}

	fun takeExpr(): ASTNode {
		val lhs = takePrimary()
		return takeBinOp(-1, lhs)
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun takeBlockOrStmt(): ASTNode =
		when (peek()) {
			Token.LeftBrace -> takeBlock()
			else -> takeStmt()
		}

	fun takeStmt(): ASTNode =
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
			Token.While -> {
				take()
				take().expect<Token.LeftParenthesis>()
				val condition = takeExpr()
				take().expect<Token.RightParenthesis>()
				WhileNode(condition, takeBlockOrStmt())
			}
			Token.Do -> {
				take()
				val block = takeBlockOrStmt()
				take().expect<Token.While>()
				take().expect<Token.LeftParenthesis>()
				val condition = takeExpr()
				take().expect<Token.RightParenthesis>()
				DoWhileNode(block, condition)
			}
			is Token.Identifier -> {
				val token = take() as Token.Identifier
				if (peek() == Token.Assign) {
					take()
					AssignNode(token.name, takeExpr())
				} else {
					off(token)
					takeExpr()
				}
			}
			else -> takeExpr()
		}

	fun takeBlock(): BlockNode {
		take().expect<Token.LeftBrace>()
		val block = BlockNode()
		while (peek() != Token.RightBrace) {
			val stmt = takeStmt()
			block.elements += stmt
			if (stmt.returned) break
		}
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

	fun takeFunctionOrPrototype(): ASTNode {
		take().expect<Token.Function>()
		val prototype = takePrototype()
		return if (peek() is Token.LeftBrace) FunctionNode(prototype, takeBlock())
		else prototype
	}

	fun parse(): TopLevelNode {
		val list = mutableListOf<ASTNode>()
		while (true) {
			when (peek()) {
				is Token.Function -> list += takeFunctionOrPrototype()
				is Token.EOF -> return TopLevelNode(list)
			}
		}
	}
}