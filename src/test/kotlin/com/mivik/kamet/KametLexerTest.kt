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
			val c = -19260817L
			val d = 1+2*3
			val e = 1E10+2.5
			val f = true && (1!=2)
		""".trimIndent()
		println(KametLexer(str).lexAll())
	}
}