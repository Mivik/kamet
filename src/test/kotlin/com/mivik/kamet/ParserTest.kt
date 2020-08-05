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
			#[native] fun putchar(char: Int): Int
			
			/**
			 * Some multiline comment
			 * Try Kamet!
			 **/
			
			fun _print(x: Long) {
				if (x==0) return
				else {
					_print(x/10)
					putchar(('0'+(x%10)) as Int)
				}
			}
			
			fun print(x: Long) {
				if (x==0) putchar('0' as Int)
				else if (x<0) {
					putchar('-' as Int)
					_print(-x)
				} else _print(x)
				putchar('\n' as Int)
			}
			
			fun quick_pow(x: Long, p: Long, mod: Long): Long {
				var x = x
				var p = p
				var ret = 1 as Long
				x %= mod
				p %= mod-1
				while (p!=0) {
					if ((p&1)==1) ret *= x
					x *= x
					p >>= 1
				}
				return ret
			}

			#[native] fun main(): Int {
				print(quick_pow(2L, 5L, 998244353L))
				return 0
			}
		""".trimIndent()
		val parser = Parser(str)
		val context = Context.topLevel("test")
		val node = parser.parse()
		node.codegen(context)
		val error = BytePointer(null as Pointer?)
		LLVM.LLVMVerifyModule(context.module, LLVM.LLVMPrintMessageAction, error)
		if (true) {
			val pass = LLVM.LLVMCreatePassManager()
			LLVM.LLVMAddConstantPropagationPass(pass)
			LLVM.LLVMAddInstructionCombiningPass(pass)
			LLVM.LLVMAddReassociatePass(pass)
			LLVM.LLVMRunPassManager(pass, context.module)
		}
		LLVM.LLVMDumpModule(context.module)
	}
}