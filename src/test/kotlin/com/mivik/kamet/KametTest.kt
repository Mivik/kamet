package com.mivik.kamet

import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import org.bytedeco.llvm.global.LLVM
import kotlin.test.Test
import kotlin.test.assertEquals

internal class KametTest {
	private fun String.parse(context: Context = Context.topLevel("evaluate")): Value =
		with(Parser(this).takeExpr()) { context.codegenForThis() }

	private fun String.evaluate(returnType: Type): GenericValue =
		Context.topLevel("evaluate").let { context ->
			val functionName = "eval"
			val parser = Parser("{$this}")
			val block = parser.takeBlock()
			FunctionNode(
				PrototypeNode(setOf(Attribute.NO_MANGLE), functionName, returnType.asDescriptor(), emptyList()),
				BlockNode().also {
					it.elements += ReturnNode(block)
				}
			).let { with(context) { it.codegen() } }
			context.verify()?.let { msg ->
				context.dump()
				error("Verification failed: $msg")
			}
			val engine = JITEngine(context)
			val result = engine.run(functionName)
			engine.dispose()
			result
		}

	private fun String.compile(): JITEngine =
		Context.topLevel("evaluate").let { context ->
			Parser(this).parse().let { with(context) { it.codegen() } }
			context.verify()?.let { msg -> error("Verification failed: $msg") }
			JITEngine(context)
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

	@Suppress("NOTHING_TO_INLINE")
	private inline fun String.tryCompile() = compile().dispose()

	@Test
	fun `unsigned values`() {
		assertEquals(
			1001,
			"1U+2*500".evaluate(Type.Primitive.Integral.UInt).int
		)
		assertEquals(
			3L,
			"1+2UL".evaluate(Type.Primitive.Integral.ULong).long
		)
		assertEquals(
			Type.Primitive.Integral.ULong,
			"18446744073709551615UL".parse().type // (2^64)-1
		)
	}

	@Test
	fun undefined() {
		"""
			#[no_mangle] fun main(): Int {
				val x: Int
				return x
			}
		""".trimIndent().tryCompile()
	}

	@Test
	fun fibonacci() {
		"""
			#[no_mangle] fun fib(x: Int): Int {
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
		""".trimIndent().compile().use {
			val func = findFunction("fib")
			assertEquals(
				1,
				run(func, GenericValue(1)).int
			)
			assertEquals(
				1,
				run(func, GenericValue(2)).int
			)
			assertEquals(
				55,
				run(func, GenericValue(10)).int
			)
		}
	}

	@Test
	fun array() {
		assertEquals(
			6310,
			"""
				const var x: [Int, 5]
				val first: &Int = x[0]
				first = 1926
				val second: *Int = &x[1]
				*second = 817
				val third: *Int = second+1
				*third = 2333
				val forth: *Int = x+3
				*forth = 1234
				x[0]+x[1]+x[2]+x[3]
			""".trimIndent().evaluate(Type.Primitive.Integral.Int).int
		)
		assertEquals(
			356,
			"""
				struct Test {
					a: [Int, 5],
					b: [Double, 10]
				}
				
				#[no_mangle] fun test(): Double {
					var test: Test
					val intPointer: *Int = test.a
					intPointer[0] = 123
					val doublePointer: *Double = test.b
					doublePointer[1] = 233.5
					return test.a[0]+test.b[1]
				}
			""".trimIndent().runFunction("test").double.toInt()
		)
	}

	@Test
	fun `struct in function`() {
		"""
			fun test() {
				struct A {
					a: Int,
					b: Double
				}
				val a: A
			}
		""".trimIndent().tryCompile()
	}

	@Test
	fun new() {
		assertEquals(
			2,
			"""
				val a: *Int = new Int
				*a = 2
				val ret = *a
				ret
			""".trimIndent().evaluate(Type.Primitive.Integral.Int).int
		)

		assertEquals(
			2,
			"""
				fun get(): *Int {
					val a: *Int = new Int
					*a = 2
					return a
				}
				
				#[no_mangle] fun test(): Int {
					val ptr = get()
					val ret = *ptr
					delete ptr
					return ret
				}
			""".trimIndent().runFunction("test").int
		)
	}

	@Test
	fun `passing array`() {
		assertEquals(
			1,
			"""
				fun modify(a: &[Int, 5]) {
					a[0] = 1
				}
				
				#[no_mangle] fun test(): Int {
					var a: [Int, 5]
					modify(a)
					return a[0]
				}
			""".trimIndent().compile().use {
				run("test").int
			}
		)
	}
}