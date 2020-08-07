package com.mivik.kamet

import com.mivik.kamet.Type.Primitive
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.LLVMGenericValueRef
import org.bytedeco.llvm.global.LLVM.LLVMCreateGenericValueOfFloat
import org.bytedeco.llvm.global.LLVM.LLVMCreateGenericValueOfInt
import org.bytedeco.llvm.global.LLVM.LLVMCreateGenericValueOfPointer
import org.bytedeco.llvm.global.LLVM.LLVMGenericValueToFloat
import org.bytedeco.llvm.global.LLVM.LLVMGenericValueToInt
import org.bytedeco.llvm.global.LLVM.LLVMGenericValueToPointer

@Suppress("NOTHING_TO_INLINE")
private inline fun fromLong(type: Type, value: Long, signed: Boolean = true) =
	LLVMCreateGenericValueOfInt(type.llvm, value, if (signed) 1 else 0)

class GenericValue(val llvm: LLVMGenericValueRef) {
	constructor(value: Boolean) : this(fromLong(Primitive.Boolean, if (value) 1L else 0L, false))
	constructor(value: Char) : this(fromLong(Primitive.Integral.Char, value.toLong()))
	constructor(value: Short) : this(fromLong(Primitive.Integral.Short, value.toLong()))
	constructor(value: Int) : this(fromLong(Primitive.Integral.Int, value.toLong()))
	constructor(value: Long) : this(fromLong(Primitive.Integral.Long, value))
	constructor(value: Float) : this(LLVMCreateGenericValueOfFloat(Primitive.Real.Float.llvm, value.toDouble()))
	constructor(value: Double) : this(LLVMCreateGenericValueOfFloat(Primitive.Real.Double.llvm, value))
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
		get() = LLVMGenericValueToFloat(Primitive.Real.Float.llvm, llvm).toFloat()

	inline val double: Double
		get() = LLVMGenericValueToFloat(Primitive.Real.Double.llvm, llvm)

	inline val pointer: Pointer
		get() = LLVMGenericValueToPointer(llvm)

	inline val string: String
		get() = BytePointer(pointer).string
}