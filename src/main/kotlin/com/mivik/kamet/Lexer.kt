package com.mivik.kamet

import com.mivik.kot.escape
import org.kiot.lexer.Lexer
import org.kiot.lexer.LexerAction
import org.kiot.lexer.LexerData
import org.kiot.lexer.LexerState

private class IllegalEscapeException(private val char: Char) : IllegalArgumentException() {
	override val message: String?
		get() = "Illegal escape char: ${char.description()}"
}

internal sealed class Token {
	override fun toString(): String = javaClass.simpleName

	object EOF : Token()

	object Val : Token()
	object Var : Token()
	object Assign : Token()
	object LeftParenthesis : Token()
	object RightParenthesis : Token()
	object LeftBrace : Token()
	object RightBrace : Token()
	object Colon : Token()
	object Comma : Token()
	object Function : Token()
	object Return : Token()
	object If : Token()
	object Else : Token()
	object While : Token()
	object Do : Token()

	class Identifier(val name: String) : Token() {
		override fun toString(): String = "Identifier($name)"
	}

	class StringLiteral(val value: String) : Token() {
		override fun toString(): String = "StringLiteral(${value.escape()})"
	}

	class Constant(val literal: String, val type: Type.Primitive) : Token() {
		override fun toString(): String = "Constant($literal, $type)"
	}
}

internal sealed class BinOp(val symbol: String, val precedence: Int, val returnBoolean: Boolean = false) : Token() {
	object Plus : BinOp("+", 8)
	object Minus : BinOp("-", 8)
	object Multiply : BinOp("*", 9)
	object Divide : BinOp("/", 9)
	object Reminder : BinOp("%", 9)
	object Equal : BinOp("==", 5, true)
	object NotEqual : BinOp("!=", 5, true)
	object Less : BinOp("<", 6, true)
	object LessOrEqual : BinOp("<=", 6, true)
	object Greater : BinOp(">", 6, true)
	object GreaterOrEqual : BinOp(">=", 6, true)
	object ShiftLeft : BinOp("<<", 7)
	object ShiftRight : BinOp(">>", 7)
	object And : BinOp("&&", 1, true)
	object Or : BinOp("||", 0, true)
	object BitwiseAnd : BinOp("&", 4)
	object BitwiseOr : BinOp("|", 2)
	object Xor : BinOp("^", 3)
}

private enum class State : LexerState {
	IN_STRING
}

private enum class Action : LexerAction {
	VAL, VAR, ENTER_STRING, ESCAPE_CHAR, UNICODE_CHAR, EXIT_STRING, PLAIN_TEXT,
	IDENTIFIER, ASSIGN, INT_LITERAL, LONG_LITERAL, SINGLE_CHAR_OPERATOR, DOUBLE_CHAR_OPERATOR, DOUBLE_LITERAL, BOOLEAN_LITERAL,
	UNSIGNED_INT_LITERAL, UNSIGNED_LONG_LITERAL, FUNCTION, RETURN, IF, ELSE, WHILE, DO
}

internal class Lexer(chars: CharSequence) : Lexer<Token>(data, chars) {
	companion object {
		val data = LexerData.build {
			options.strict = false
			state(default) {
				"[ \t]+".ignore()
				"\r|\n|\r\n".ignore()
				"&&|==|!=|<<|>>|<=|>=|\\|\\|" action Action.DOUBLE_CHAR_OPERATOR
				"[+\\-*/&\\|\\^<>%\\(\\)\\{\\}:,]" action Action.SINGLE_CHAR_OPERATOR
				"val" action Action.VAL
				"var" action Action.VAR
				"fun" action Action.FUNCTION
				"return" action Action.RETURN
				"while" action Action.WHILE
				"do" action Action.DO
				"if" action Action.IF
				"else" action Action.ELSE
				"true|false" action Action.BOOLEAN_LITERAL
				"[\\w\$_][\\w\\d\$_]*" action Action.IDENTIFIER
				"\\d+UL" action Action.UNSIGNED_LONG_LITERAL
				"\\d+U" action Action.UNSIGNED_INT_LITERAL
				"-?\\d+L" action Action.LONG_LITERAL
				"-?\\d+" action Action.INT_LITERAL
				"=" action Action.ASSIGN
				"\"" action Action.ENTER_STRING
				"(-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+\\-]?[0-9]*)?)|Infinity|-Infinity|NaN" action Action.DOUBLE_LITERAL
			}
			state(State.IN_STRING) {
				"\\\\u[0-9a-fA-F]{4}" action Action.UNICODE_CHAR
				"\\\\." action Action.ESCAPE_CHAR
				"\"" action Action.EXIT_STRING
				"." action Action.PLAIN_TEXT
			}
		}
	}

	private val stringContent = StringBuilder()

	override fun lex(): Token = super.lex() ?: Token.EOF

	override fun onAction(action: Int) {
		when (Action.values()[action - 1]) {
			Action.VAL -> returnValue(Token.Val)
			Action.VAR -> returnValue(Token.Var)
			Action.FUNCTION -> returnValue(Token.Function)
			Action.RETURN -> returnValue(Token.Return)
			Action.ASSIGN -> returnValue(Token.Assign)
			Action.IF -> returnValue(Token.If)
			Action.ELSE -> returnValue(Token.Else)
			Action.WHILE -> returnValue(Token.While)
			Action.DO -> returnValue(Token.Do)
			Action.IDENTIFIER -> returnValue(Token.Identifier(string()))
			Action.DOUBLE_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Real.Double))
			Action.UNSIGNED_INT_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 1), Type.Primitive.Integer.UInt))
			Action.UNSIGNED_LONG_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 2), Type.Primitive.Integer.ULong))
			Action.INT_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Integer.Int))
			Action.LONG_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 1), Type.Primitive.Integer.Long))
			Action.BOOLEAN_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Boolean))
			Action.DOUBLE_CHAR_OPERATOR -> returnValue(
				when (string()) {
					"==" -> BinOp.Equal
					"!=" -> BinOp.NotEqual
					"<=" -> BinOp.LessOrEqual
					">=" -> BinOp.GreaterOrEqual
					"<<" -> BinOp.ShiftLeft
					">>" -> BinOp.ShiftRight
					"&&" -> BinOp.And
					"||" -> BinOp.Or
					else -> unreachable()
				}
			)
			Action.SINGLE_CHAR_OPERATOR -> returnValue(
				when (chars[lastMatch]) {
					'+' -> BinOp.Plus
					'-' -> BinOp.Minus
					'*' -> BinOp.Multiply
					'/' -> BinOp.Divide
					'&' -> BinOp.BitwiseAnd
					'|' -> BinOp.BitwiseOr
					'^' -> BinOp.Xor
					'<' -> BinOp.Less
					'>' -> BinOp.Greater
					'%' -> BinOp.Reminder
					'{' -> Token.LeftBrace
					'}' -> Token.RightBrace
					'(' -> Token.LeftParenthesis
					')' -> Token.RightParenthesis
					':' -> Token.Colon
					',' -> Token.Comma
					else -> unreachable()
				}
			)

			Action.ENTER_STRING -> switchState(State.IN_STRING)
			Action.UNICODE_CHAR -> stringContent.append(chars.substring(lastMatch + 2, index).toShort(16).toChar())
			Action.ESCAPE_CHAR ->
				stringContent.append(
					when (val char = chars[lastMatch + 1]) {
						'\\' -> '\\'
						'"' -> '"'
						'n' -> '\n'
						'r' -> '\r'
						't' -> '\t'
						'b' -> '\b'
						'f' -> '\u000c'
						'v' -> '\u000b'
						'0' -> '\u0000'
						else -> throw IllegalEscapeException(char)
					}
				)
			Action.PLAIN_TEXT -> stringContent.append(string())
			Action.EXIT_STRING -> {
				returnValue(Token.StringLiteral(stringContent.toString())) // not returning immediately
				stringContent.clear()
				switchState(0)
			}
		}
	}
}