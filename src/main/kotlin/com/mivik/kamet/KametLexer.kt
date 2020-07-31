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

internal sealed class KametToken {
	override fun toString(): String = javaClass.simpleName

	object Val : KametToken()
	object Var : KametToken()
	object Whitespace : KametToken()
	object Newline : KametToken()
	object Assign : KametToken()
	object LeftParenthesis : KametToken()
	object RightParenthesis : KametToken()
	open class BinaryOperation : KametToken()
	object Plus : BinaryOperation()
	object Minus : BinaryOperation()
	object Multiply : BinaryOperation()
	object Divide : BinaryOperation()
	object Equal : BinaryOperation()
	object NotEqual : BinaryOperation()
	object Less : BinaryOperation()
	object LessOrEqual : BinaryOperation()
	object Greater : BinaryOperation()
	object GreaterOrEqual : BinaryOperation()
	object ShiftLeft : BinaryOperation()
	object ShiftRight : BinaryOperation()
	object And : BinaryOperation()
	object Or : BinaryOperation()
	object BitwiseAnd : BinaryOperation()
	object BitwiseOr : BinaryOperation()
	object Xor : BinaryOperation()


	class Identifier(val name: String) : KametToken() {
		override fun toString(): String = "Identifier($name)"
	}

	class BooleanLiteral(val value: Boolean) : KametToken() {
		override fun toString(): String = "BooleanLiteral($value)"
	}

	class StringLiteral(val value: String) : KametToken() {
		override fun toString(): String = "StringLiteral(${value.escape()})"
	}

	class IntLiteral(val value: Int) : KametToken() {
		override fun toString(): String = "IntLiteral($value)"
	}

	class LongLiteral(val value: Long) : KametToken() {
		override fun toString(): String = "LongLiteral($value)"
	}

	class DoubleLiteral(val value: Double) : KametToken() {
		override fun toString(): String = "DoubleLiteral($value)"
	}
}

private enum class KametState : LexerState {
	IN_STRING
}

private enum class KametAction : LexerAction {
	WHITESPACE, NEWLINE, VAL, VAR, ENTER_STRING, ESCAPE_CHAR, UNICODE_CHAR, EXIT_STRING, PLAIN_TEXT,
	IDENTIFIER, ASSIGN, INT_LITERAL, LONG_LITERAL, PARENTHESIS, SINGLE_CHAR_OPERATOR, DOUBLE_CHAR_OPERATOR, DOUBLE_LITERAL, BOOLEAN_LITERAL
}

internal class KametLexer(chars: CharSequence) : Lexer<KametToken>(data, chars) {
	companion object {
		val data = LexerData.build {
			options.strict = false
			state(default) {
				"[ \t]+" action KametAction.WHITESPACE
				"\r|\n|\r\n" action KametAction.NEWLINE
				"[\\(\\)]" action KametAction.PARENTHESIS
				"&&|==|!=|<<|>>|<=|>=|\\|\\|" action KametAction.DOUBLE_CHAR_OPERATOR
				"[+\\-*/&\\|\\^<>]" action KametAction.SINGLE_CHAR_OPERATOR
				"val" action KametAction.VAL
				"var" action KametAction.VAR
				"true|false" action KametAction.BOOLEAN_LITERAL
				"[\\w\$_][\\w\\d\$_]*" action KametAction.IDENTIFIER
				"-?\\d+L" action KametAction.LONG_LITERAL
				"-?\\d+" action KametAction.INT_LITERAL
				"=" action KametAction.ASSIGN
				"\"" action KametAction.ENTER_STRING
				"(-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+\\-]?[0-9]*)?)|Infinity|-Infinity|NaN" action KametAction.DOUBLE_LITERAL
			}
			state(KametState.IN_STRING) {
				"\\\\u[0-9a-fA-F]{4}" action KametAction.UNICODE_CHAR
				"\\\\." action KametAction.ESCAPE_CHAR
				"\"" action KametAction.EXIT_STRING
				"." action KametAction.PLAIN_TEXT
			}
		}
	}

	private val stringContent = StringBuilder()

	override fun onAction(action: Int) {
		when (KametAction.values()[action - 1]) {
			KametAction.VAL -> returnValue(KametToken.Val)
			KametAction.VAR -> returnValue(KametToken.Var)
			KametAction.ASSIGN -> returnValue(KametToken.Assign)
			KametAction.WHITESPACE -> returnValue(KametToken.Whitespace)
			KametAction.NEWLINE -> returnValue(KametToken.Newline)
			KametAction.IDENTIFIER -> returnValue(KametToken.Identifier(string()))
			KametAction.PARENTHESIS -> returnValue(if (chars[lastMatch] == '(') KametToken.LeftParenthesis else KametToken.RightParenthesis)
			KametAction.DOUBLE_LITERAL -> returnValue(KametToken.DoubleLiteral(string().toDouble()))
			KametAction.LONG_LITERAL -> returnValue(
				KametToken.LongLiteral(
					chars.substring(lastMatch, index - 1).toLong()
				)
			)
			KametAction.BOOLEAN_LITERAL -> returnValue(KametToken.BooleanLiteral(string() == "true"))
			KametAction.INT_LITERAL -> returnValue(KametToken.IntLiteral(string().toInt()))
			KametAction.DOUBLE_CHAR_OPERATOR -> returnValue(
				when (string()) {
					"==" -> KametToken.Equal
					"!=" -> KametToken.NotEqual
					"<=" -> KametToken.LessOrEqual
					">=" -> KametToken.GreaterOrEqual
					"<<" -> KametToken.ShiftLeft
					">>" -> KametToken.ShiftRight
					"&&" -> KametToken.And
					"||" -> KametToken.Or
					else -> unreachable()
				}
			)
			KametAction.SINGLE_CHAR_OPERATOR -> returnValue(
				when (chars[lastMatch]) {
					'+' -> KametToken.Plus
					'-' -> KametToken.Minus
					'*' -> KametToken.Multiply
					'/' -> KametToken.Divide
					'&' -> KametToken.BitwiseAnd
					'|' -> KametToken.BitwiseOr
					'^' -> KametToken.Xor
					'<' -> KametToken.Less
					'>' -> KametToken.Greater
					else -> unreachable()
				}
			)

			KametAction.ENTER_STRING -> switchState(KametState.IN_STRING)
			KametAction.UNICODE_CHAR -> stringContent.append(chars.substring(lastMatch + 2, index).toShort(16).toChar())
			KametAction.ESCAPE_CHAR ->
				stringContent.append(
					when (val char = chars[lastMatch + 1]) {
						'\\' -> '\\'
						'"' -> '"'
						'n' -> '\n'
						'r' -> '\r'
						't' -> '\t'
						'b' -> '\b'
						'f' -> '\u000c'
						else -> throw IllegalEscapeException(char)
					}
				)
			KametAction.PLAIN_TEXT -> stringContent.append(string())
			KametAction.EXIT_STRING -> {
				returnValue(KametToken.StringLiteral(stringContent.toString())) // not immediately
				stringContent.clear()
				switchState(0)
			}
		}
	}
}