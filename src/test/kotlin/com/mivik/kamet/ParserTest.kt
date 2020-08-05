package com.mivik.kamet

import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import com.mivik.kamet.ast.direct
import org.bytedeco.llvm.global.LLVM
import org.kiot.automaton.regexp
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ParserTest {
	private fun String.parse(context: Context = Context.topLevel("evaluate")): Value =
		Parser(this).takeExpr().codegen(context)

	private fun String.evaluate(): GenericValue =
		Context.topLevel("evaluate").let {
			val functionName = "eval"
			val parser = Parser(this)
			val expr = parser.takeExpr().codegen(it)
			FunctionNode(
				PrototypeNode(setOf(Attribute.NATIVE), functionName, expr.type.asDescriptor(), emptyList()),
				BlockNode().apply {
					elements += ReturnNode(expr.direct())
				}).codegen(it)
			it.verify()?.let { msg -> error("Verification failed: $msg") }
			val engine = JITEngine(it)
			val result = engine.run(functionName)
			engine.dispose()
			result
		}

	private fun String.compile(): JITEngine =
		Context.topLevel("evaluate").let {
			Parser(this).parse().codegen(it)
			it.verify()?.let { msg -> error("Verification failed: $msg") }
			JITEngine(it)
		}

	private fun String.runFunction(functionName: String, argument: GenericValue? = null): GenericValue =
		Context.topLevel("evaluate").let {
			val engine = compile()
			val result =
				if (argument == null) engine.run(functionName)
				else engine.run(functionName, argument)
			engine.dispose()
			result
		}

	@Test
	fun testUnsigned() {
		assertEquals(
			1001,
			"1U+2*500".evaluate().int
		)
		assertEquals(
			3L,
			"1+2UL".evaluate().long
		)
		assertEquals(
			Type.Primitive.Integer.ULong,
			"18446744073709551615UL".parse().type // (2^64)-1
		)
	}

	@Test
	fun testFIB() {
		val engine = """
			#[native] fun fib(x: Int): Int {
				if (x<=2) return 1
				var a = 1
				var b = 1
				var c = 2
				var x = x-2
				while (x!=0) {
					c = a+b
					a = b
					b = c
					x = x-1
				}
				return c
			}
			
			#[native] fun main(): Int {
				return fib(3)
			}
		""".trimIndent().compile()
		val func = engine.findFunction("fib")
		assertEquals(
			1,
			engine.run(func, GenericValue(1)).int
		)
		assertEquals(
			1,
			engine.run(func, GenericValue(2)).int
		)
		assertEquals(
			55,
			engine.run(func, GenericValue(10)).int
		)
		engine.dispose()
	}
}