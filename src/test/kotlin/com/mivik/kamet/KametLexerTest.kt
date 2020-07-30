package com.mivik.kamet

import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal class KametLexerTest {
	@ExperimentalTime
	@Test
	fun test() {
		val str = """
			val a = "hello, world!"
			val b = 233
			val c = 19260817L
			val d = 1+2*3
			val e = 1E10+2.5
		""".trimIndent()
		val time = 5000
		measureTime {
			repeat(time) {
				val lexer = KametLexer(str)
				while (lexer.lex() != null);
			}
		}.also {
			println(it / time)
		}
	}
}