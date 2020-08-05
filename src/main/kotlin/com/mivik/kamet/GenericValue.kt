package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.LLVMGenericValueRef
import org.bytedeco.llvm.global.LLVM.*

@Suppress("NOTHING_TO_INLINE")
private inline fun fromLong(type: Type, value: Long, signed: Boolean = true) =
	LLVMCreateGenericValueOfInt(type.llvm, value, if (signed) 1 else 0)

class GenericValue(val llvm: LLVMGenericValueRef) {
	constructor(value: Boolean) : this(fromLong(Type.Primitive.Boolean, if (value) 1L else 0L, false))
	constructor(value: Char) : this(fromLong(Type.Primitive.Integer.Char, value.toLong()))
	constructor(value: Short) : this(fromLong(Type.Primitive.Integer.Short, value.toLong()))
	constructor(value: Int) : this(fromLong(Type.Primitive.Integer.Int, value.toLong()))
	constructor(value: Long) : this(fromLong(Type.Primitive.Integer.Long, value))
	constructor(value: Float) : this(LLVMCreateGenericValueOfFloat(Type.Primitive.Real.Float.llvm, value.toDouble()))
	constructor(value: Double) : this(LLVMCreateGenericValueOfFloat(Type.Primitive.Real.Double.llvm, value))
	constructor(value: Pointer) : this(LLVMCreateGenericValueOfPointer(value))
	constructor(value: String) : this(BytePointer(value))

	inline val boolean: Boolean
		get() = LLVMGenericValueToInt(llvm, 0) == 1L

	inline val byte: Byte
		get() = long.toByte()

	inline val char: Char
		get() = long.toChar()

	inline val short: Short
		get() = long.toShort()

	inline val int: Int
		get() = long.toInt()

	inline val long: Long
		get() = LLVMGenericValueToInt(llvm, 1)

	inline val float: Float
		get() = LLVMGenericValueToFloat(Type.Primitive.Real.Float.llvm, llvm).toFloat()

	inline val double: Double
		get() = LLVMGenericValueToFloat(Type.Primitive.Real.Double.llvm, llvm)

	inline val pointer: Pointer
		get() = LLVMGenericValueToPointer(llvm)

	inline val string: String
		get() = BytePointer(pointer).string
}