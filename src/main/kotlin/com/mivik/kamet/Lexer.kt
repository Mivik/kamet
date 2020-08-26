package com.mivik.kamet

import org.kiot.lexer.Lexer
import org.kiot.lexer.LexerAction
import org.kiot.lexer.LexerData
import org.kiot.lexer.LexerState

internal sealed class Token {
	override fun toString(): String = javaClass.simpleName

	object EOF : Token()

	object Whitespace : Token()
	object Val : Token()
	object Var : Token()
	object Let : Token()
	object LeftParenthesis : Token()
	object RightParenthesis : Token()
	object LeftBracket : Token()
	object RightBracket : Token()
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
	object Const : Token()
	object Newline : Token()
	object NumberSign : Token()
	object Struct : Token()
	object Null : Token()
	object SizeOf : Token()
	object New : Token()
	object Trait : Token()
	object This : Token()
	object ThisType : Token()
	object Impl : Token()
	object Arrow : Token()
	object For : Token()
	object ScopeResolution : Token()

	class DirectType(val type: Type) : Token() {
		override fun toString(): String = "Type($type)"
	}

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

internal sealed class Operator(val symbol: String, val precedence: Int) : Token()

internal sealed class UnaryOp(symbol: String, precedence: Int) : Operator(symbol, precedence) {
	object Negative : UnaryOp("-", 12)
	object Inverse : UnaryOp("~", 12)
	object Not : UnaryOp("!", 12)
	object Increment : UnaryOp("++", 12)
	object Decrement : UnaryOp("--", 12)
	object Indirection : UnaryOp("*", 12)
	object AddressOf : UnaryOp("&", 12)
	object Delete : UnaryOp("delete ", 12)
}

internal open class BinOp(symbol: String, precedence: Int, val returnBoolean: Boolean = false) :
	Operator(symbol, precedence) {
	internal open class BooOp(symbol: String, precedence: Int) : BinOp(symbol, precedence, true)
	object Plus : BinOp("+", 9)
	object Minus : BinOp("-", 9)
	object Multiply : BinOp("*", 10)
	object Divide : BinOp("/", 10)
	object Reminder : BinOp("%", 10)
	object Equal : BooOp("==", 6)
	object NotEqual : BooOp("!=", 6)
	object Less : BooOp("<", 7)
	object LessOrEqual : BooOp("<=", 7)
	object Greater : BooOp(">", 7)
	object GreaterOrEqual : BooOp(">=", 7)
	object ShiftLeft : BinOp("<<", 8)
	object ShiftRight : BinOp(">>", 8)
	object And : BooOp("&&", 2)
	object Or : BooOp("||", 1)
	object BitwiseAnd : BinOp("&", 5)
	object BitwiseOr : BinOp("|", 3)
	object Xor : BinOp("^", 4)
	object Assign : BinOp("=", 0)
	object AccessMember : BinOp(".", 13)
	object As : BinOp("as", 11)

	open class AssignOperators(val originalOp: BinOp) : BinOp(originalOp.symbol + "=", 0)
	object PlusAssign : AssignOperators(Plus)
	object MinusAssign : AssignOperators(Minus)
	object MultiplyAssign : AssignOperators(Multiply)
	object DivideAssign : AssignOperators(Divide)
	object ReminderAssign : AssignOperators(Reminder)
	object BitwiseAndAssign : AssignOperators(BitwiseAnd)
	object BitwiseOrAssign : AssignOperators(BitwiseOr)
	object XorAssign : AssignOperators(Xor)
	object ShiftLeftAssign : AssignOperators(ShiftLeft)
	object ShiftRightAssign : AssignOperators(ShiftRight)
}

private enum class State : LexerState {
	IN_STRING
}

private enum class Action : LexerAction {
	WHITESPACE, VAL, VAR, ENTER_STRING, ESCAPE_CHAR, UNICODE_CHAR, EXIT_STRING, PLAIN_TEXT, CONST, NEWLINE, STRUCT, NULL, AS, SIZEOF, CHAR_LITERAL, FOR, LET,
	IDENTIFIER, INT_LITERAL, LONG_LITERAL, SINGLE_CHAR_OPERATOR, DOUBLE_CHAR_OPERATOR, DOUBLE_LITERAL, BOOLEAN_LITERAL, NEW, DELETE, THIS_TYPE,
	UNSIGNED_INT_LITERAL, UNSIGNED_LONG_LITERAL, FUNCTION, RETURN, IF, ELSE, WHILE, DO, SHIFT_LEFT_ASSIGN, SHIFT_RIGHT_ASSIGN, IMPL, TRAIT, THIS
}

internal class Lexer(chars: CharSequence) : Lexer<Token>(data, chars) {
	companion object {
		val data = LexerData.build {
			options.strict = false
			options.minimize = true
			state(default) {
				"[ \t]+" action Action.WHITESPACE
				"//[^\r\n]*".ignore()
				"/\\*([^*]|(\\*+[^*/]))*\\*+/".ignore()
				"\r|\n|\r\n" action Action.NEWLINE
				"<<=" action Action.SHIFT_LEFT_ASSIGN
				">>=" action Action.SHIFT_RIGHT_ASSIGN
				"[+\\-*/&\\|\\^%]=|&&|==|!=|<<|>>|<=|>=|\\|\\||\\+\\+|--|->|::" action Action.DOUBLE_CHAR_OPERATOR
				"[+\\-*/&\\|\\^<>%\\(\\)\\{\\}:,=~!#\\[\\]\\.]" action Action.SINGLE_CHAR_OPERATOR
				"'(\\\\u[0-9a-fA-F]{4}|\\\\.|.)'" action Action.CHAR_LITERAL
				"val" action Action.VAL
				"var" action Action.VAR
				"let" action Action.LET
				"fun" action Action.FUNCTION
				"return" action Action.RETURN
				"while" action Action.WHILE
				"const" action Action.CONST
				"for" action Action.FOR
				"struct" action Action.STRUCT
				"sizeof" action Action.SIZEOF
				"null" action Action.NULL
				"do" action Action.DO
				"as" action Action.AS
				"if" action Action.IF
				"else" action Action.ELSE
				"new" action Action.NEW
				"delete" action Action.DELETE
				"impl" action Action.IMPL
				"this" action Action.THIS
				"This" action Action.THIS_TYPE
				"true|false" action Action.BOOLEAN_LITERAL
				"trait" action Action.TRAIT
				"[\\w\$_][\\w\\d\$_]*" action Action.IDENTIFIER
				"\\d+UL" action Action.UNSIGNED_LONG_LITERAL
				"\\d+U" action Action.UNSIGNED_INT_LITERAL
				"\\d+L" action Action.LONG_LITERAL
				"\\d+" action Action.INT_LITERAL
				"\"" action Action.ENTER_STRING
				"((0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+\\-]?[0-9]*)?)|Infinity|-Infinity|NaN" action Action.DOUBLE_LITERAL
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
			Action.WHITESPACE -> returnValue(Token.Whitespace)
			Action.VAL -> returnValue(Token.Val)
			Action.VAR -> returnValue(Token.Var)
			Action.LET -> returnValue(Token.Let)
			Action.NEWLINE -> returnValue(Token.Newline)
			Action.FUNCTION -> returnValue(Token.Function)
			Action.RETURN -> returnValue(Token.Return)
			Action.IF -> returnValue(Token.If)
			Action.ELSE -> returnValue(Token.Else)
			Action.WHILE -> returnValue(Token.While)
			Action.DO -> returnValue(Token.Do)
			Action.CONST -> returnValue(Token.Const)
			Action.STRUCT -> returnValue(Token.Struct)
			Action.AS -> returnValue(BinOp.As)
			Action.NULL -> returnValue(Token.Null)
			Action.SIZEOF -> returnValue(Token.SizeOf)
			Action.NEW -> returnValue(Token.New)
			Action.FOR -> returnValue(Token.For)
			Action.DELETE -> returnValue(UnaryOp.Delete)
			Action.THIS -> returnValue(Token.This)
			Action.THIS_TYPE -> returnValue(Token.ThisType)
			Action.IMPL -> returnValue(Token.Impl)
			Action.IDENTIFIER -> returnValue(Token.Identifier(string()))
			Action.TRAIT -> returnValue(Token.Trait)
			Action.CHAR_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Integral.Char))
			Action.DOUBLE_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Real.Double))
			Action.UNSIGNED_INT_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 1), Type.Primitive.Integral.UInt))
			Action.UNSIGNED_LONG_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 2), Type.Primitive.Integral.ULong))
			Action.INT_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Integral.Int))
			Action.LONG_LITERAL ->
				returnValue(Token.Constant(chars.substring(lastMatch, index - 1), Type.Primitive.Integral.Long))
			Action.BOOLEAN_LITERAL -> returnValue(Token.Constant(string(), Type.Primitive.Boolean))
			Action.SHIFT_LEFT_ASSIGN -> returnValue(BinOp.ShiftLeftAssign)
			Action.SHIFT_RIGHT_ASSIGN -> returnValue(BinOp.ShiftRightAssign)
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
					"++" -> UnaryOp.Increment
					"--" -> UnaryOp.Decrement
					"+=" -> BinOp.PlusAssign
					"-=" -> BinOp.MinusAssign
					"*=" -> BinOp.MultiplyAssign
					"/=" -> BinOp.DivideAssign
					"%=" -> BinOp.ReminderAssign
					"&=" -> BinOp.BitwiseAndAssign
					"|=" -> BinOp.BitwiseOrAssign
					"^=" -> BinOp.XorAssign
					"->" -> Token.Arrow
					"::" -> Token.ScopeResolution
					else -> impossible()
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
					'>' -> BinOp.Greater
					'<' -> BinOp.Less
					'%' -> BinOp.Reminder
					'=' -> BinOp.Assign
					'(' -> Token.LeftParenthesis
					')' -> Token.RightParenthesis
					'[' -> Token.LeftBracket
					']' -> Token.RightBracket
					'{' -> Token.LeftBrace
					'}' -> Token.RightBrace
					':' -> Token.Colon
					',' -> Token.Comma
					'~' -> UnaryOp.Inverse
					'!' -> UnaryOp.Not
					'#' -> Token.NumberSign
					'.' -> BinOp.AccessMember
					else -> impossible()
				}
			)
			Action.ENTER_STRING -> switchState(State.IN_STRING)
			Action.UNICODE_CHAR -> stringContent.append(chars.substring(lastMatch + 2, index).toShort(16).toChar())
			Action.ESCAPE_CHAR -> stringContent.append(chars[lastMatch + 1].escape())
			Action.PLAIN_TEXT -> stringContent.append(string())
			Action.EXIT_STRING -> {
				returnValue(Token.StringLiteral(stringContent.toString())) // not returning immediately
				stringContent.clear()
				switchState(0)
			}
		}
	}
}