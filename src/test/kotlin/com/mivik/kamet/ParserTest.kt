package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ParserTest {
	private fun String.evaluate(): Value =
		Parser(this).takeExpr().codegen(Context.topLevel("evaluate"))

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
			fun plus(a: Int, b: Int): Int {
				val c = a*a
				val d = b*b
				return c+d
			}
		""".trimIndent()
		val parser = Parser(str)
		val context = Context.topLevel("test")
		val function = parser.takeFunction()
		function.codegen(context)
		LLVM.LLVMDumpModule(context.module)
	}
}