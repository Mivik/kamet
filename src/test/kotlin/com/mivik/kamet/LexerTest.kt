package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM
import kotlin.test.Test

internal class LexerTest {
	@Test
	fun test() {
		val str = """
			1+2*3
		""".trimIndent()
		val parser = Parser(str)
		val node = parser.takeExpr()
		val builder = LLVM.LLVMCreateBuilder()
		val value = node.codegen(builder)
		LLVM.LLVMDumpValue(value.llvm)
	}
}