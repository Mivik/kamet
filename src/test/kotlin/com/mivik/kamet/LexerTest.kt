package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LexerTest {
	private fun String.evaluate(): Value =
		Parser(this).takeExpr().codegen(LLVM.LLVMCreateBuilder())

	@Test
	fun testUnsigned() {
		assertEquals(
			Type.Primitive.Integer.UInt,
			"1U+2*500".evaluate().type
		)
		assertEquals(
			Type.Primitive.Integer.Int,
			"1+2U".evaluate().type
		)
	}

	@Test
	fun test() {
		val str = """
			1U+2*500
		""".trimIndent()
		val parser = Parser(str)
		val node = parser.takeExpr()
		val builder = LLVM.LLVMCreateBuilder()
		val value = node.codegen(builder)
		assertEquals(Type.Primitive.Integer.UInt, value.type)
		LLVM.LLVMDumpValue(value.llvm)
	}
}