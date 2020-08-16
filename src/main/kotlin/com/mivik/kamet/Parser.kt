package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.BinOpNode
import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.CallNode
import com.mivik.kamet.ast.CastNode
import com.mivik.kamet.ast.ConstantNode
import com.mivik.kamet.ast.DoWhileNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.GenericFunctionNode
import com.mivik.kamet.ast.GenericPrototypeNode
import com.mivik.kamet.ast.GenericStructNode
import com.mivik.kamet.ast.IfNode
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
	private var attributeNewlySet = false

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

	private fun consumeAttrs(): Attributes = (lastAttribute ?: Attributes()).also { lastAttribute = null }

	private fun unexpected(token: Token): Nothing = error("Unexpected $token")

	private fun precedenceOf(token: Token) =
		when (token) {
			is Operator -> token.precedence
			Token.LeftBracket -> 13
			else -> -1
		}

	private fun clearAttributes() {
		if (attributeNewlySet) attributeNewlySet = false
		else if (lastAttribute != null) error("Unused attributes: $lastAttribute")
	}

	private fun takeAttributes() {
		take().expect<Token.NumberSign>()
		take().expect<Token.LeftBracket>()
		lastAttribute = if (peek() != Token.RightBracket) {
			val attrs = mutableSetOf<Attribute>()
			while (true) {
				val name = take().expect<Token.Identifier>().name
				attrs += Attribute.lookup(name) ?: error("Unknown attribute ${name.escape()}")
				if (peek() == Token.RightBracket) break
			}
			take()
			Attributes(attrs.readOnly())
		} else Attributes()
		attributeNewlySet = true
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

	private fun takeArguments(): List<ASTNode> {
		take().expect<Token.LeftParenthesis>()
		val list = mutableListOf<ASTNode>()
		takeList(Token.RightParenthesis) {
			list += takeExpr()
		}
		return list.readOnly()
	}

	private fun takePrimary(): ASTNode =
		when (val token = trimAndTake()) {
			Token.Null -> Value.NullPointer.direct()
			Token.This -> ValueNode("this")
			is Token.Identifier ->
				when (peek()) {
					Token.LeftParenthesis, BinOp.Less -> {
						val function = takeTypeArguments().let {
							if (it.isEmpty()) Function.Named(token.name)
							else Function.ActualGeneric(token.name, it)
						}
						CallNode(function, null, takeArguments())
					}
					else -> ValueNode(token.name)
				}
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

	fun takeStmt(): ASTNode =
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
				takeAttributes()
				takeStmt().also { clearAttributes() }
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

	private fun takeFunctionType(receiverType: Type?): Type.Function {
		take().expect<Token.LeftParenthesis>()
		val types = mutableListOf<Type>()
		takeList(Token.RightParenthesis) {
			types += takeType()
		}
		take().expect<Token.Arrow>()
		return Type.Function(receiverType, takeType(), types)
	}

	fun takeTypeParameter(): TypeParameter =
		when (val token = trimAndTake()) {
			is Token.Identifier -> TypeParameter(token.name)
			else -> unexpected(token)
		}

	fun takeTypeArguments(): List<Type> {
		if (peek() != BinOp.Less) return emptyList()
		take()
		val list = mutableListOf<Type>()
		takeList(BinOp.Greater) {
			list += takeType()
		}
		return list.readOnly()
	}

	fun takeTypeParameterList(): List<TypeParameter> {
		if (peek() != BinOp.Less) return emptyList()
		take()
		val ret = mutableListOf<TypeParameter>()
		takeList(BinOp.Greater) {
			ret += takeTypeParameter()
		}
		return ret.readOnly()
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
			Token.LeftParenthesis ->
				if (peek() == Token.RightParenthesis) {
					off(token)
					takeFunctionType(null)
				} else {
					takeType().let {
						val after = trimAndTake()
						if (after == Token.Comma) {
							off(Token.DirectType(it))
							off(Token.LeftParenthesis)
							takeFunctionType(null)
						} else {
							after.expect<Token.RightParenthesis>()
							if (peek() == Token.Arrow) {
								take()
								val returnType = takeType()
								Type.Function(null, returnType, listOf(it))
							} else it
						}
					}
				}
			Token.LeftBracket -> { // [(const )type, size]
				val elementType = takeType()
				val isConst = peek() == Token.Const
				if (isConst) take()
				take().expect<Token.Comma>()
				// TODO complex constant expression
				val size = takeExpr().expect<ConstantNode>()
				size.type.expect<Type.Primitive.Integral>()
				take().expect<Token.RightBracket>()
				Type.Array(elementType, size.value.toInt(), isConst)
			}
			is Token.Identifier ->
				Type.Named(token.name).let {
					if (peek() == BinOp.Less) Type.ActualGeneric(it, takeTypeArguments())
					else it
				}
			else -> unexpected(token)
		}.let {
			if (peek() == BinOp.AccessMember) {
				take()
				if (peek() == Token.LeftParenthesis) takeFunctionType(it)
				else {
					off(BinOp.AccessMember)
					it
				}
			} else it
		}

	private inline fun takeList(until: Token, action: () -> Unit) {
		if (trimAndPeek() == until) {
			take()
			return
		}
		while (true) {
			action()
			val splitter = trimAndTake()
			if (splitter == until) break
			else splitter.expect<Token.Comma>()
		}
	}

	fun takePrototype(): ASTNode {
		trimAndTake().expect<Token.Function>()
		val typeParameters = takeTypeParameterList()
		val type = takeType()
		val hasReceiver = peek() == BinOp.AccessMember
		val name =
			if (hasReceiver) {
				take()
				take().expect<Token.Identifier>().name
			} else type.expect<Type.Named>().name
		val names = mutableListOf<String>()
		val types = mutableListOf<Type>()
		take().expect<Token.LeftParenthesis>()
		takeList(Token.RightParenthesis) {
			names += trimAndTake().expect<Token.Identifier>().name
			trimAndTake().expect<Token.Colon>()
			types += takeType()
		}
		val returnType =
			if (trimAndPeek() == Token.Colon) {
				take()
				takeType()
			} else Type.Unit
		val functionType = Type.Function(if (hasReceiver) type else null, returnType, types.readOnly())
		return PrototypeNode(consumeAttrs(), name, functionType, names.readOnly()).let {
			if (typeParameters.isEmpty()) it
			else GenericPrototypeNode(it, typeParameters)
		}
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun takeFunctionOrPrototype(): ASTNode {
		val prototype = takePrototype()
		return if (peek() is Token.LeftBrace) {
			val block = takeBlock()
			if (prototype is PrototypeNode)
				FunctionNode(prototype, block.also {
					if (!it.returned) it.elements += ReturnNode(UndefNode(prototype.type.returnType))
				})
			else {
				prototype as GenericPrototypeNode
				GenericFunctionNode(FunctionNode(prototype.node, block.also {
					if (!it.returned) it.elements += ReturnNode(UndefNode(prototype.node.type.returnType))
				}), prototype.typeParameters)
			}
		} else prototype
	}

	fun takeStruct(): ASTNode {
		take().expect<Token.Struct>()
		val name = trimAndTake().expect<Token.Identifier>().name
		val typeParameters = takeTypeParameterList()
		take().expect<Token.LeftBrace>()
		val list = mutableListOf<Pair<String, Type>>()
		takeList(Token.RightBrace) {
			val memberName = trimAndTake().expect<Token.Identifier>().name
			trimAndTake().expect<Token.Colon>()
			list += Pair(memberName, takeType())
		}
		return if (typeParameters.isEmpty()) StructNode(consumeAttrs(), name, list.readOnly())
		else GenericStructNode(consumeAttrs(), name, list.readOnly(), typeParameters.readOnly())
	}

	fun parse(): TopLevelNode {
		val list = mutableListOf<ASTNode>()
		while (true) {
			when (trimAndPeek()) {
				Token.Function -> list += takeFunctionOrPrototype().also {
					if (it is PrototypeNode)
						require(it.extern) { "Function without implementation is not allowed" }
				}
				Token.Struct -> list += takeStruct()
				Token.NumberSign -> takeAttributes()
				Token.EOF -> return TopLevelNode(list.readOnly())
				else -> TODO()
			}
			clearAttributes()
		}
	}
}