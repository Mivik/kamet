package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ParserTest {
	private fun String.evaluate(context: Context = Context.topLevel("evaluate")): Value =
		Parser(this).takeExpr().codegen(context)

	@Test
	fun testUnsigned() {
		assertEquals(
			Type.Primitive.Integer.UInt,
			"1U+2*500".evaluate().type
		)
		assertEquals(
			Type.Primitive.Integer.ULong,
			"1+2UL".evaluate().type
		)
	}

	@Test
	fun test() {
		val str = """
			fun putchar(char: Int): Int
			
			fun main(): Int {
				var i = 0
				while (i<26) {
					putchar(65+i)
					i = i+1
				}
				return 0
			}
		""".trimIndent()
		val parser = Parser(str)
		val context = Context.topLevel("test")
		val list = parser.parse()
		list.codegen(context)
		LLVM.LLVMDumpModule(context.module)
	}
}