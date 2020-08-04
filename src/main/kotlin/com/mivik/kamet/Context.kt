package com.mivik.kamet

import com.mivik.kamet.ast.PrototypeNode
import com.mivik.kot.escape
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMModuleRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

class Context(
	val parent: Context?,
	val module: LLVMModuleRef,
	val builder: LLVMBuilderRef,
	val currentFunction: Value?,
	private val valueMap: MutableMap<String, Value>,
	private val typeMap: MutableMap<String, Type>,
	private val functionMap: MutableMap<String, MutableList<Value>>
) {
	companion object {
		fun topLevel(moduleName: String): Context =
			Context(
				null,
				LLVM.LLVMModuleCreateWithName(moduleName),
				LLVM.LLVMCreateBuilder(),
				null,
				mutableMapOf(),
				Type.defaultTypeMap(),
				mutableMapOf()
			)
	}

	val llvmFunction: LLVMValueRef
		get() = currentFunction!!.llvm

	fun lookupValueOrNull(name: String): Value? {
		var current = this
		while (true) {
			current.valueMap[name]?.let { return it }
			current = current.parent ?: return null
		}
	}

	fun lookupValue(name: String): Value = lookupValueOrNull(name) ?: error("Unknown identifier ${name.escape()}")

	fun hasValue(name: String): Boolean = valueMap.containsKey(name)

	fun lookupTypeOrNull(name: String): Type? {
		var current = this
		while (true) {
			current.typeMap[name]?.let { return it }
			current = current.parent ?: return null
		}
	}

	fun lookupType(name: String): Type = lookupTypeOrNull(name) ?: error("Unknown type ${name.escape()}")

	fun declare(name: String, value: Value) {
		valueMap[name] = value
	}

	fun subContext(currentFunction: Value = this.currentFunction!!): Context =
		Context(this, module, builder, currentFunction, mutableMapOf(), mutableMapOf(), mutableMapOf())

	@Suppress("NOTHING_TO_INLINE")
	inline fun setBlock(block: LLVMBasicBlockRef) {
		LLVM.LLVMPositionBuilderAtEnd(builder, block)
	}

	fun declareVariable(name: String, value: Value): ValueRef {
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder))
		val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
		val tmpBuilder = LLVM.LLVMCreateBuilder()
		LLVM.LLVMPositionBuilder(tmpBuilder, entryBlock, LLVM.LLVMGetFirstInstruction(entryBlock))
		val address = LLVM.LLVMBuildAlloca(tmpBuilder, value.type.llvm, name)
		val ret = ValueRef(address, value.type, false)
		LLVM.LLVMSetValueName2(address, name, name.length.toLong())
		LLVM.LLVMDisposeBuilder(tmpBuilder)
		declare(name, ret)
		if (LLVM.LLVMIsUndef(value.llvm) == 0) ret.set(this, value)
		return ret
	}

	internal fun declareFunction(prototype: PrototypeNode, value: Value) {
		declare(prototype.functionName, value)
		functionMap.getOrPut(prototype.name) { mutableListOf() } += value
	}

	internal fun lookupFunctions(name: String): List<Value> {
		var current = this
		while (true) {
			current.functionMap[name]?.let { return it }
			current = current.parent ?: return emptyList()
		}
	}
}