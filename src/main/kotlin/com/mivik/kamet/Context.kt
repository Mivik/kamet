package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.PrototypeNode
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
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

	fun declareType(type: Type) {
		if (typeMap.containsKey(type.name)) error("Redeclare of type ${type.name}")
		typeMap[type.name] = type
	}

	fun declare(name: String, value: Value) {
		valueMap[name] = value
	}

	fun subContext(currentFunction: Value = this.currentFunction!!): Context =
		Context(this, module, builder, currentFunction, mutableMapOf(), mutableMapOf(), mutableMapOf())

	inline var block: LLVMBasicBlockRef
		get() = LLVM.LLVMGetInsertBlock(builder)
		set(block) {
			LLVM.LLVMPositionBuilderAtEnd(builder, block)
		}
	internal fun codegenUsing(block: LLVMBasicBlockRef, node: ASTNode): Value {
		this.block = block
		return node.codegen(this)
	}

	fun declareVariable(name: String, value: Value, isConst: Boolean = false): ValueRef {
		if (value.type.canImplicitlyCastTo(Type.Unit)) return UnitValueRef(isConst)
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder))
		val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
		val tmpBuilder = LLVM.LLVMCreateBuilder()
		LLVM.LLVMPositionBuilder(tmpBuilder, entryBlock, LLVM.LLVMGetFirstInstruction(entryBlock))
		val address = LLVM.LLVMBuildAlloca(tmpBuilder, value.type.llvm, name)
		val ret = ValueRef(address, value.type, isConst)
		LLVM.LLVMSetValueName2(address, name, name.length.toLong())
		LLVM.LLVMDisposeBuilder(tmpBuilder)
		declare(name, ret)
		if (LLVM.LLVMIsUndef(value.llvm) == 0) ret.setIn(this, value)
		return ret
	}

	internal fun declareFunction(prototype: PrototypeNode, value: Value) {
		val parameterTypes = (value.type as Type.Function).parameterTypes
		findDuplicate@ for (function in lookupFunctions(prototype.name)) {
			val type = function.type as Type.Function
			if (parameterTypes.size != type.parameterTypes.size) continue
			for (i in parameterTypes.indices)
				if (parameterTypes[i] != type.parameterTypes[i]) continue@findDuplicate
			error("Function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToString(", ")})")
		}
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

	fun runDefaultPass(): Context = apply {
		val pass = LLVM.LLVMCreatePassManager()
		LLVM.LLVMAddConstantPropagationPass(pass)
		LLVM.LLVMAddInstructionCombiningPass(pass)
		LLVM.LLVMAddReassociatePass(pass)
		LLVM.LLVMAddGVNPass(pass)
		LLVM.LLVMAddCFGSimplificationPass(pass)
		LLVM.LLVMRunPassManager(pass, module)
		LLVM.LLVMDisposePassManager(pass)
	}

	/**
	 * @return Null if the module passed the verification, and otherwise the error message.
	 */
	fun verify(): String? {
		val error = BytePointer(null as Pointer?)
		val ret = LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error)
		return if (ret == 1) error.asErrorMessage()
		else null
	}
}