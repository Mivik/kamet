package com.mivik.kamet

import java.util.LinkedList

internal class KametParser(chars: CharSequence) {
	private val lexer = KametLexer(chars)

	private val readBuffer = LinkedList<KametToken>()

	fun take(): KametToken =
		if (readBuffer.isEmpty()) lexer.lex()!!
		else readBuffer.pop()

	fun view(): KametToken =
		if (readBuffer.isEmpty()) lexer.lex()!!.also { readBuffer.push(it) }
		else readBuffer.peek()

	fun uselessSpace() {
		if (view() is KametToken.Whitespace) take()
	}

	fun space() {
		take() as KametToken.Whitespace
	}
}