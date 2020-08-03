package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
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
				putchar(48+(i++))
				putchar(48+i)
				return 0
			}
		""".trimIndent()
		val parser = Parser(str)
		val context = Context.topLevel("test")
		val node = parser.parse()
		node.codegen(context)
		if (true) {
			val pass = LLVM.LLVMCreatePassManager()
			LLVM.LLVMAddConstantPropagationPass(pass)
			LLVM.LLVMAddInstructionCombiningPass(pass)
			LLVM.LLVMAddReassociatePass(pass)
			LLVM.LLVMRunPassManager(pass, context.module)
		}
		val error = BytePointer(null as Pointer?)
		LLVM.LLVMVerifyModule(context.module, LLVM.LLVMPrintMessageAction, error)
		LLVM.LLVMDumpModule(context.module)
	}
}