package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.BinOpNode
import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.CastNode
import com.mivik.kamet.ast.ConstantNode
import com.mivik.kamet.ast.DoWhileNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.IfNode
import com.mivik.kamet.ast.InvocationNode
import com.mivik.kamet.ast.NewNode
import com.mivik.kamet.ast.PointerSubscriptNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import com.mivik.kamet.ast.SizeOfNode
import com.mivik.kamet.ast.StructNode
import com.mivik.kamet.ast.TopLevelNode
import com.mivik.kamet.ast.UnaryOpNode
import com.mivik.kamet.ast.UndefNode
import com.mivik.kamet.ast.ValDeclareNode
import com.mivik.kamet.ast.ValueNode
import com.mivik.kamet.ast.VarDeclareNode
import com.mivik.kamet.ast.WhileNode
import com.mivik.kamet.ast.direct
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
		when (token) {
			is Operator -> token.precedence
			Token.LeftBracket -> 13
			else -> -1
		}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun makeOp(lhs: ASTNode?, rhs: ASTNode, op: Operator): ASTNode =
		if (lhs == null) UnaryOpNode(
			when (op) {
				BinOp.Minus -> UnaryOp.Negative
				BinOp.Multiply -> UnaryOp.Indirection
				BinOp.BitwiseAnd -> UnaryOp.AddressOf
				else -> op.expect()
			}, rhs
		) else BinOpNode(lhs, rhs, op.expect())

	// TODO Use stack to implement this.
	private fun takeBinOp(precedence: Int, lhs: ASTNode?): ASTNode {
		var currentLHS = lhs
		while (true) {
			val current = peek()
			if (current == UnaryOp.Increment || current == UnaryOp.Decrement) {
				currentLHS ?: error("Expected expression before postfix operator: $current")
				take()
				return UnaryOpNode(
					current as UnaryOp,
					currentLHS,
					true
				)
			}
			if (current == Token.LeftBracket) {
				take()
				currentLHS ?: error("Expected expression before array subscript: $current")
				currentLHS = PointerSubscriptNode(currentLHS, takeExpr())
				take().expect<Token.RightBracket>()
				continue
			}
			val currentPrecedence = precedenceOf(current)
			if (currentPrecedence <= precedence) return currentLHS!!
			take()
			if (current == BinOp.As) currentLHS =
				CastNode(currentLHS ?: error("Expected expression before \"as\" operator: $current"), takeType())
			else {
				var rhs = takePrimary()
				val nextPrecedence = precedenceOf(peek())
				if (currentPrecedence < nextPrecedence)
					rhs = takeBinOp(currentPrecedence, rhs)
				currentLHS = makeOp(currentLHS, rhs, current.expect())
			}
		}
	}

	private fun takePrimary(): ASTNode =
		when (val token = trimAndTake()) {
			is Token.Null -> Value.NullPointer.direct()
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
				if (trimAndPeek() == Token.Else) {
					take()
					IfNode(condition, thenBlock, takeBlockOrStmt())
				} else IfNode(condition, thenBlock)
			}
			is UnaryOp, BinOp.Minus, BinOp.Multiply, BinOp.BitwiseAnd -> {
				off(token)
				takeBinOp(-1, null)
			}
			Token.SizeOf -> SizeOfNode(takeType())
			Token.New -> NewNode(takeType())
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

	tailrec fun takeStmt(): ASTNode =
		when (trimAndPeek()) {
			Token.Struct -> takeStruct()
			Token.Val -> {
				take()
				val name = take().expect<Token.Identifier>().name
				var type: Type? = null
				if (peek() == Token.Colon) {
					take()
					type = takeType()
				}
				if (peek() == BinOp.Assign) {
					take()
					ValDeclareNode(name, type, takeExpr())
				} else ValDeclareNode(name, null, UndefNode(type!!))
			}
			Token.Const, Token.Var -> {
				val isConst =
					if (peek() == Token.Const) {
						take()
						trimAndPeek().expect<Token.Var>()
						true
					} else false
				take()
				val name = take().expect<Token.Identifier>().name
				var type: Type? = null
				if (peek() == Token.Colon) {
					take()
					type = takeType()
				}
				if (peek() == BinOp.Assign) {
					take()
					VarDeclareNode(name, type, takeExpr(), isConst)
				} else VarDeclareNode(name, null, UndefNode(type!!), isConst)
			}
			Token.Return -> {
				take()
				if (peek() == Token.Newline) ReturnNode(Value.Unit.direct())
				else try {
					ReturnNode(takeExpr())
				} catch (_: Throwable) {
					ReturnNode(Value.Unit.direct())
				}
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
			Token.NumberSign -> {
				lastAttribute = takeAttributes()
				takeStmt()
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

	fun takeType(): Type =
		when (val token = trimAndTake()) {
			BinOp.BitwiseAnd -> { // &(const )type
				val isConst = peek() == Token.Const
				if (isConst) take()
				Type.Reference(takeType(), isConst)
			}
			BinOp.Multiply -> { // *(const )type
				val isConst = peek() == Token.Const
				if (isConst) take()
				Type.Pointer(takeType(), isConst)
			}
			Token.LeftParenthesis -> takeType().also { trimAndTake().expect<Token.RightParenthesis>() }
			Token.LeftBracket -> { // [(const )type, size]
				val elementType = takeType()
				val isConst = peek() == Token.Const
				if (isConst) take()
				take().expect<Token.Comma>()
				// TODO complex constant expression
				val size = takeExpr().expect<ConstantNode>()
				size.type.expect<Type.Primitive.Integral>()
				take().expect<Token.RightBracket>()
				Type.Array(elementType, size.value.toLongIgnoringOverflow().toInt(), isConst)
			}
			is Token.Identifier -> {
				val type = Type.Named(token.name)
				if (peek() == Token.LeftParenthesis) {
					take()
					val parameterTypes = mutableListOf<Type>()
					if (peek() != Token.RightParenthesis)
						while (true) {
							parameterTypes += takeType()
							val splitter = trimAndTake()
							if (splitter == Token.RightParenthesis) break
							else splitter.expect<Token.Comma>()
						}
					Type.Function(type, parameterTypes)
				} else type
			}
			else -> impossible()
		}

	fun takePrototype(): PrototypeNode {
		trimAndTake().expect<Token.Function>()
		val name = trimAndTake().expect<Token.Identifier>().name
		val args = mutableListOf<Pair<String, Type>>()
		take().expect<Token.LeftParenthesis>()
		if (trimAndPeek() != Token.RightParenthesis)
			while (true) {
				val argName = trimAndTake().expect<Token.Identifier>().name
				trimAndTake().expect<Token.Colon>()
				args.add(Pair(argName, takeType()))
				val splitter = trimAndTake()
				if (splitter == Token.RightParenthesis) break
				else splitter.expect<Token.Comma>()
			}
		else take()
		return if (trimAndPeek() == Token.Colon) {
			take()
			PrototypeNode(consumeAttrs(), name, takeType(), args)
		} else PrototypeNode(consumeAttrs(), name, Type.Unit, args)
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun takeFunctionOrPrototype(): ASTNode {
		val prototype = takePrototype()
		return if (peek() is Token.LeftBrace) FunctionNode(prototype, takeBlock().also {
			if (!it.returned) it.elements += ReturnNode(UndefNode(prototype.returnType))
		})
		else prototype
	}

	private fun takeAttributes(): Attributes {
		take().expect<Token.NumberSign>()
		take().expect<Token.LeftBracket>()
		return if (peek() != Token.RightBracket) {
			val set = mutableSetOf<Attribute>()
			while (true) {
				val name = take().expect<Token.Identifier>().name
				set.add(Attribute.lookup(name) ?: error("Unknown attribute \"$name\""))
				if (peek() == Token.RightBracket) break
			}
			take()
			set
		} else emptySet()
	}

	fun takeStruct(): StructNode {
		take().expect<Token.Struct>()
		val name = trimAndTake().expect<Token.Identifier>().name
		val elements = mutableListOf<Pair<String, Type>>()
		trimAndTake().expect<Token.LeftBrace>()
		if (trimAndPeek() != Token.RightBrace)
			while (true) {
				val elementName = trimAndTake().expect<Token.Identifier>().name
				trimAndTake().expect<Token.Colon>()
				elements.add(Pair(elementName, takeType()))
				val splitter = trimAndTake()
				if (splitter == Token.RightBrace) break
				else splitter.expect<Token.Comma>()
			}
		return StructNode(consumeAttrs(), name, elements)
	}

	fun parse(): TopLevelNode {
		val list = mutableListOf<ASTNode>()
		while (true) {
			when (trimAndPeek()) {
				Token.Function -> list += takeFunctionOrPrototype()
				Token.Struct -> list += takeStruct()
				Token.EOF -> return TopLevelNode(list)
				Token.NumberSign -> lastAttribute = takeAttributes()
				else -> TODO()
			}
		}
	}
}