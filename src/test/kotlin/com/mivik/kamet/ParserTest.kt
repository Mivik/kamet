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
				putchar(if (1==1) 65 else 66)
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