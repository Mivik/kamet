package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
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
import com.mivik.kamet.ast.UnaryOpNode
import com.mivik.kamet.ast.ValDeclareNode
import com.mivik.kamet.ast.ValueNode
import com.mivik.kamet.ast.VarDeclareNode
import com.mivik.kamet.ast.WhileNode
import java.util.LinkedList

internal class Parser(private val lexer: Lexer) {
	constructor(chars: CharSequence) : this(Lexer(chars))

	private val readBuffer = LinkedList<Token>()
	private var lastAttribute: Attributes? = null

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

	@Suppress("NOTHING_TO_INLINE")
	private inline fun trim() {
		while (peek() == Token.Newline) take()
	}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun trimAndPeek(): Token {
		trim()
		return peek()
	}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun trimAndTake(): Token {
		trim()
		return take()
	}

	private fun consumeAttrs(): Attributes = (lastAttribute ?: emptySet()).also { lastAttribute = null }

	private fun unexpected(token: Token): Nothing = error("Unexpected $token")

	private fun precedenceOf(token: Token) =
		if (token is BinOp) token.precedence
		else -1

	// TODO Use stack to implement this.
	private fun takeBinOp(precedence: Int, lhs: ASTNode): ASTNode {
		var currentLHS = lhs
		while (true) {
			val current = peek()
			if (current == UnaryOp.Increment || current == UnaryOp.Decrement) {
				take()
				return UnaryOpNode(
					current as UnaryOp,
					currentLHS,
					true
				)
			}
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
		when (val token = trimAndTake()) {
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
			Token.If -> {
				take().expect<Token.LeftParenthesis>()
				val condition = takeExpr()
				take().expect<Token.RightParenthesis>()
				val thenBlock = takeBlockOrStmt()
				if (peek() == Token.Else) {
					take()
					IfNode(condition, thenBlock, takeBlockOrStmt())
				} else IfNode(condition, thenBlock)
			}
			BinOp.Plus -> takeExpr() // just ignore it
			is UnaryOp -> UnaryOpNode(token, takePrimary())
			BinOp.Minus -> UnaryOpNode(UnaryOp.Negative, takePrimary())
			BinOp.Multiply -> UnaryOpNode(UnaryOp.Indirection, takePrimary())
			BinOp.BitwiseAnd -> UnaryOpNode(UnaryOp.AddressOf, takePrimary())
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
		when (trimAndPeek()) {
			Token.Val -> {
				take()
				// TODO better error output for stuff like this (should output "Expected xxx, got xxx" instead of throwing an cast error)
				val name = (take() as Token.Identifier).name
				take().expect<BinOp.Assign>()
				ValDeclareNode(name, takeExpr())
			}
			Token.Var -> {
				take()
				val name = (take() as Token.Identifier).name
				take().expect<BinOp.Assign>()
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
			else -> takeExpr()
		}

	fun takeBlock(): BlockNode {
		trim()
		take().expect<Token.LeftBrace>()
		val block = BlockNode()
		var returned = false
		while (trimAndPeek() != Token.RightBrace) {
			val stmt = takeStmt()
			if (!returned) {
				block.elements += stmt
				returned = stmt.returned
			}
		}
		take()
		return block
	}

	fun takeType(): TypeDescriptor =
		when (val token = take()) {
			BinOp.BitwiseAnd -> {
				val isConst = peek() == Token.Const
				if (isConst) take()
				TypeDescriptor.Reference(takeType(), isConst)
			}
			BinOp.Multiply -> {
				val isConst = peek() == Token.Const
				if (isConst) take()
				TypeDescriptor.Pointer(takeType(), isConst)
			}
			Token.LeftParenthesis -> takeType().also { take().expect<Token.RightParenthesis>() }
			is Token.Identifier -> TypeDescriptor.Named(token.name)
			else -> unreachable()
		}

	fun takePrototype(): PrototypeNode {
		trim()
		take().expect<Token.Function>()
		val name = (take() as Token.Identifier).name
		val args = mutableListOf<Pair<String, TypeDescriptor>>()
		take().expect<Token.LeftParenthesis>()
		if (peek() != Token.RightParenthesis)
			while (true) {
				val argName = (take() as Token.Identifier).name
				take().expect<Token.Colon>()
				args.add(Pair(argName, takeType()))
				val splitter = take()
				if (splitter == Token.RightParenthesis) break
				else splitter.expect<Token.Comma>()
			}
		else take()
		return if (peek() == Token.Colon) {
			take()
			PrototypeNode(consumeAttrs(), name, takeType(), args)
		} else PrototypeNode(consumeAttrs(), name, Type.Unit.asDescriptor(), args)
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun takeFunctionOrPrototype(): ASTNode {
		val prototype = takePrototype()
		return if (peek() is Token.LeftBrace) FunctionNode(prototype, takeBlock())
		else prototype
	}

	private fun takeAttributes(): Attributes {
		take().expect<Token.NumberSign>()
		take().expect<Token.LeftBracket>()
		return if (peek() != Token.RightBracket) {
			val set = mutableSetOf<Attribute>()
			while (true) {
				val name = (take() as Token.Identifier).name
				set.add(Attribute.lookup(name) ?: error("Unknown attribute \"$name\""))
				if (peek() == Token.RightBracket) break
			}
			take()
			set
		} else emptySet()
	}

	fun parse(): TopLevelNode {
		val list = mutableListOf<ASTNode>()
		while (true) {
			when (trimAndPeek()) {
				is Token.Function -> list += takeFunctionOrPrototype()
				Token.EOF -> return TopLevelNode(list)
				Token.NumberSign -> lastAttribute = takeAttributes()
				else -> TODO()
			}
		}
	}
}