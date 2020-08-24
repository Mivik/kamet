package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.BlockNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kamet.ast.ReturnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

internal class KametTest {
	private fun String.parse(context: Context = Context.topLevel("evaluate")): Value =
		with(Parser(this).takeExpr()) { context.codegenForThis() }

	private fun String.toType(): Type =
		with(Context.topLevel("evaluate")) {
			Parser(this@toType).takeType().resolve()
		}

	private fun String.evaluate(returnType: Type): GenericValue {
		val functionName = "eval"
		val context = FunctionNode(
			PrototypeNode(
				Attributes(setOf(Attribute.NO_MANGLE)),
				Prototype(
					functionName,
					Type.Function(null, returnType, emptyList()),
					emptyList()
				)
			),
			BlockNode().also {
				it.elements += ReturnNode(Parser("{$this}").takeBlock())
			}
		).codegen()
		return JITEngine(context).use { run(functionName) }
	}

	private fun ASTNode.codegen(): Context =
		Context.topLevel("evaluate").also {
			with(it) { codegen() }
			it.verify()?.let { msg ->
				it.dump()
				error("Verification failed: $msg")
			}
		}

	private fun String.codegen(): Context = Parser(this).parse().codegen()
	private fun String.compile(): JITEngine = JITEngine(codegen())
	private fun String.runFunction(functionName: String, argument: GenericValue? = null): GenericValue =
		compile().use {
			if (argument == null) run(functionName)
			else run(functionName, argument)
		}

	@Suppress("NOTHING_TO_INLINE")
	private inline fun String.tryCompile() = compile().dispose()

	@Test
	fun `unsigned values`() {
		assertEquals(
			1001,
			"1U + 2 * 500".evaluate(Type.Primitive.Integral.UInt).int
		)
		assertEquals(
			3L,
			"1 + 2UL".evaluate(Type.Primitive.Integral.ULong).long
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
				let x: Int
				return x
			}
		""".trimIndent().tryCompile()
	}

	@Test
	fun fibonacci() {
		"""
			#[no_mangle] fun fib(x: Int): Int {
				if (x <= 2) return 1
				var a = 1
				var b = 1
				var c = 2
				var x = x - 2
				while (x!=0) {
					c = a+b
					a = b
					b = c
					x = x - 1
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
				val x: [Int, 5]
				let first: &Int = x[0]
				first = 1926
				let second: *Int = &x[1]
				*second = 817
				let third: *Int = second+1
				*third = 2333
				let forth: *Int = x+3
				*forth = 1234
				x[0] + x[1] + x[2] + x[3]
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
					let intPointer: *Int = test.a
					intPointer[0] = 123
					let doublePointer: *Double = test.b
					doublePointer[1] = 233.5
					return test.a[0] + test.b[1]
				}
			""".trimIndent().runFunction("test").double.toInt()
		)
	}

	@Test
	fun `struct in function`() {
		"""
			fun test() {
				#[packed] struct A {
					a: Int,
					b: Double
				}
				struct B {
					a: Int,
					b: Boolean
				}
				let a: A
				let b: B
			}
		""".trimIndent().tryCompile()
	}

	@Test
	fun new() {
		assertEquals(
			2,
			"""
				let a: *Int = new Int
				*a = 2
				let ret = *a
				ret
			""".trimIndent().evaluate(Type.Primitive.Integral.Int).int
		)

		assertEquals(
			2,
			"""
				fun get(): *Int {
					let a: *Int = new Int
					*a = 2
					return a
				}
				
				#[no_mangle] fun test(): Int {
					let ptr = get()
					let ret = *ptr
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
			""".trimIndent().runFunction("test").int
		)
	}

	@Test
	fun extern() {
		assertFails {
			"fun cos(x: Double): Double".tryCompile()
		}
		"#[extern] fun cos(x: Double): Double".tryCompile()
	}

	@Test
	fun concat() {
		assertEquals(
			concat(listOf(1, 2), listOf(3)).toList(),
			listOf(1, 2, 3)
		)
		assertEquals(
			concat(listOf(1), listOf(2), emptyList()).toList(),
			listOf(1, 2)
		)
	}

	@Test
	fun `unused attributes`() {
		assertFails {
			"""
				fun test() {
					#[extern]
					let a: Int
				}
			""".trimIndent().tryCompile()
		}
	}

	@Test
	fun `function type`() {
		assertEquals(
			Type.Function(
				null,
				Type.Unit,
				emptyList()
			),
			"() -> Unit".toType()
		)
		assertEquals(
			Type.Function(null, Type.Primitive.Integral.Int, listOf(Type.Primitive.Integral.Int)),
			"(Int) -> Int".toType()
		)
		assertEquals(
			Type.Function(
				Type.Primitive.Integral.Int,
				Type.Unit,
				listOf(Type.Primitive.Real.Double, Type.Primitive.Real.Double)
			),
			"Int.(Double, Double) -> Unit".toType()
		)
	}

	@Test
	fun generic() {
		"""
			struct Pair<A, B> {
				first: A,
				second: B
			}
			
			fun test() {
				var intPair: Pair<Int, Int>
				intPair.first =  1
				intPair.second = 2
				var doublePair: Pair<Double, Double>
				doublePair.first = 1.2
				doublePair.second = 2.3
			}
		""".trimIndent().tryCompile()
		assertFails {
			"""
				struct Wrapper<T> {
					element: T
				}
				
				fun test() {
					var wrapper: Wrapper<Int>
					wrapper.element = 1.2
				}
			""".trimIndent().tryCompile()
		}
	}

	@Test
	fun `function with receiver`() {
		assertEquals(
			25,
			"""
				fun Int.sqr(): Int = this * this
				
				#[no_mangle] fun test(): Int = 5.sqr()
			""".trimIndent().runFunction("test").int
		)
		assertEquals(
			3,
			"""
				struct Test {
					a: Int,
					b: Int
				}
				
				fun &Test.sum(): Int = a + b
				
				#[no_mangle] fun test(): Int {
					var test: Test
					test.a = 1
					test.b = 2
					return test.sum()
				}
			""".trimIndent().runFunction("test").int
		)
	}

	@Test
	fun `generic function`() {
		assertEquals(
			4,
			"""
				fun <T> max(a: T, b: T): T = if (a > b) a else b
				
				#[no_mangle] fun test(): Int {
					return max<Int>(max<Int>(1, 3), max<Int>(2, 4))
				}
			""".trimIndent().runFunction("test").int
		)
		assertTrue(
			"""
				fun <T> T.lessThan(other: T): Boolean = this < other
				
				#[no_mangle] fun test(): Boolean {
					return 1.lessThan<Int>(2) && (2.3).lessThan<Double>(4.5)
				}
			""".trimIndent().runFunction("test").boolean
		)
	}

	@Test
	fun `generic inference`() {
		"""
			fun <T> max(a: T, b: T): T = if (a > b) a else b
			
			fun test() {
				let int = max(max(1, 3), max(2, 4))
				let double = max(max(1.2, 3.5), max(2.4, 5.7))
			}
		""".trimIndent().tryCompile()
		assertFails {
			"""
				fun <T> max(a: T, b: T): T = if (a > b) a else b
				
				fun test() {
					max(max(1, 2), max(2.3, 3.5))
				}
			""".trimIndent().tryCompile()
		}
	}

	@Test
	fun trait() {
		assertEquals(
			9,
			"""
				trait Polygon {
					fun &const This.side_count(): Int
				}
				
				struct Square {}
				struct Arbitrary {
					count: Int
				}
				
				impl Polygon for Square {
					fun &const This.side_count(): Int = 4 
				}
				
				impl Polygon for Arbitrary {
					fun &const This.side_count(): Int = count
				}
				
				#[no_mangle] fun test(): Int {
					val square: Square
					var arbitrary: Arbitrary
					arbitrary.count = 5
					return square.side_count() + arbitrary.side_count()
				}
			""".trimIndent().runFunction("test").int
		)
		assertEquals(
			3,
			"""
				trait Number {
					fun &const This.value(): Int
				
					fun &const This.inc(): Int {
						return value() + 1
					}
				}
				
				struct One {}
				
				impl Number for One {
					fun &const This.value(): Int = 1
				}
				
				#[no_mangle] fun test(): Int {
					val one: One
					return one.value() + one.inc()
				}
			""".trimIndent().runFunction("test").int
		)
	}

	@Test
	fun `generic trait`() {
		assertEquals(
			42,
			"""
				trait Wrapper<T> {
					fun &This.unwrap(): T
				}
				
				struct IntWrapper {
					value: Int
				}
				
				impl Wrapper<Int> for IntWrapper {
					fun &This.unwrap(): Int = value
				}
				
				#[no_mangle] fun test(): Int {
					var wrapper: IntWrapper
					wrapper.value = 42
					return wrapper.unwrap()
				}
			""".trimIndent().runFunction("test").int
		)
	}
}