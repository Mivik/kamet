package com.mivik.kamet

import org.bytedeco.llvm.global.LLVM

data class TraitImpl(val trait: Trait, val type: Type, val functions: List<Function.Generic>) {
	lateinit var table: Value

	fun Context.initialize() {
		val voidPointer = Type.Primitive.Integral.UByte.pointer()
		val arrayType = Type.Array(voidPointer, functions.size, true)
		val rawTable = LLVM.LLVMAddGlobal(module, arrayType.llvm, "table of impl $trait for $type")
		val thisType = Type.Dynamic(trait, type).reference()
//		val thisType = type.reference()
		LLVM.LLVMSetInitializer(
			rawTable,
			LLVM.LLVMConstArray(
				voidPointer.llvm,
				buildPointerPointer(functions.size) {
					functions[it].instantiate(thisType).expect<Function.Static>().ptr.llvm.bitCast(voidPointer.llvm)
				},
				functions.size
			)
		)
		table = arrayType.pointer().new(rawTable)
	}
}