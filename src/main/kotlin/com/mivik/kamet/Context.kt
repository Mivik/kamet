package com.mivik.kamet

import com.mivik.kot.escape
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMModuleRef
import org.bytedeco.llvm.global.LLVM

internal class Context(
	val parent: Context?,
	val module: LLVMModuleRef,
	val builder: LLVMBuilderRef,
	val currentFunction: Value?,
	private val valueMap: MutableMap<String, ValueRef>,
	private val typeMap: MutableMap<String, Type>
) {
	companion object {
		fun topLevel(moduleName: String): Context =
			Context(
				null,
				LLVM.LLVMModuleCreateWithName(moduleName),
				LLVM.LLVMCreateBuilder(),
				null,
				mutableMapOf(),
				Type.defaultTypeMap()
			)
	}

	fun lookupValueOrNull(name: String): ValueRef? {
		var current = this
		while (true) {
			current.valueMap[name]?.let { return it }
			current = current.parent ?: return null
		}
	}

	fun lookupValue(name: String): ValueRef = lookupValueOrNull(name) ?: error("Unknown identifier ${name.escape()}")

	fun hasValue(name: String): Boolean = valueMap.containsKey(name)

	fun lookupTypeOrNull(name: String): Type? {
		var current = this
		while (true) {
			current.typeMap[name]?.let { return it }
			current = current.parent ?: return null
		}
	}

	fun lookupType(name: String): Type = lookupTypeOrNull(name) ?: error("Unknown type ${name.escape()}")

	fun declare(name: String, valueRef: ValueRef) {
		valueMap[name] = valueRef
	}

	fun subContext(currentFunction: Value = this.currentFunction!!): Context =
		Context(this, module, builder, currentFunction, mutableMapOf(), mutableMapOf())

	fun declareVariable(name: String, value: Value): ValueRef.Var {
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder))
		val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
		val tmpBuilder = LLVM.LLVMCreateBuilder()
		LLVM.LLVMPositionBuilderBefore(tmpBuilder, LLVM.LLVMGetFirstInstruction(entryBlock))
		val address = LLVM.LLVMBuildAlloca(tmpBuilder, value.type.llvm, name)
		val ret = ValueRef.Var(address, value.type)
		LLVM.LLVMSetValueName2(address, name, name.length.toLong())
		LLVM.LLVMDisposeBuilder(tmpBuilder)
		declare(name, ret)
		if (LLVM.LLVMIsUndef(value.llvm)==0) ret.set(this, value)
		return ret
	}
}