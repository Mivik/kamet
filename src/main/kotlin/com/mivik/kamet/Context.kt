package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.PrototypeNode
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMModuleRef
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

	@Suppress("NOTHING_TO_INLINE")
	inline fun insertAt(block: LLVMBasicBlockRef) {
		LLVM.LLVMPositionBuilderAtEnd(builder, block)
	}

	inline val currentBlock get() = LLVM.LLVMGetInsertBlock(builder)

	internal fun basicBlock(name: String = "block") = LLVM.LLVMAppendBasicBlock(llvmFunction, name)

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun br(block: LLVMBasicBlockRef) {
		LLVM.LLVMBuildBr(builder, block)
	}

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun condBr(condition: Value, thenBlock: LLVMBasicBlockRef, elseBlock: LLVMBasicBlockRef) {
		LLVM.LLVMBuildCondBr(builder, condition.llvm, thenBlock, elseBlock)
	}

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun ASTNode.codegen() = with(this) { codegenForThis() }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Value.dereference() = with(this) { dereferenceForThis() }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun ValueRef.setValue(value: Value) = with(this) { setValueForThis(value) }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Value.implicitCast(to: Type) = with(CastManager) { implicitCast(this@implicitCast, to) }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Value.explicitCast(to: Type) = with(CastManager) { explicitCast(this@explicitCast, to) }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun TypeDescriptor.translate() = with(this) { translateForThis() }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Type.undefined() = with(this) { undefinedForThis() }

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Value.pointerToInt() = explicitCast(Type.pointerAddressType)

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Type.new(llvm: LLVMValueRef, isConst: Boolean = false) = newForThis(llvm, isConst)

	@Suppress("NOTHING_TO_INLINE")
	internal inline fun Type.sizeOf(): Value = Type.pointerAddressType.new(LLVM.LLVMSizeOf(dereference().llvm))

	fun allocate(type: LLVMTypeRef, name: String? = null): LLVMValueRef {
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder))
		val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
		val tmpBuilder = LLVM.LLVMCreateBuilder()
		LLVM.LLVMPositionBuilder(tmpBuilder, entryBlock, LLVM.LLVMGetFirstInstruction(entryBlock))
		val address = LLVM.LLVMBuildAlloca(tmpBuilder, type, name)
		if (name != null) LLVM.LLVMSetValueName2(address, name, name.length.toLong())
		LLVM.LLVMDisposeBuilder(tmpBuilder)
		return address
	}

	fun declareVariable(name: String, value: Value, isConst: Boolean = false): ValueRef {
		if (value.type.canImplicitlyCastTo(Type.Unit)) return UnitValueRef(isConst)
		val ret = ValueRef(allocate(value.type.llvm, name), value.type, isConst)
		declare(name, ret)
		if (LLVM.LLVMIsUndef(value.llvm) == 0) ret.setValue(value)
		return ret
	}

	internal fun declareFunction(prototype: PrototypeNode, value: Value) {
		val parameterTypes = (value.type as Type.Function).parameterTypes
		findDuplicate@ for (function in lookupFunctions(prototype.name)) {
			val type = function.type as Type.Function
			if (parameterTypes.size != type.parameterTypes.size) continue
			for (i in parameterTypes.indices)
				if (parameterTypes[i] != type.parameterTypes[i]) continue@findDuplicate
			error("Function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToString()})")
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

	fun dump() {
		LLVM.LLVMDumpModule(module)
	}

	/**
	 * @return null if the module passed the verification, and otherwise the error message.
	 */
	fun verify(): String? {
		val error = BytePointer(null as Pointer?)
		val ret = LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error)
		return if (ret == 1) error.toJava()
		else null
	}
}

@OptIn(ExperimentalContracts::class)
internal inline fun Context.doInsideAndThen(
	block: LLVMBasicBlockRef,
	then: LLVMBasicBlockRef,
	action: Context.() -> Unit
) {
	contract {
		callsInPlace(action, InvocationKind.EXACTLY_ONCE)
	}
	br(block)
	insertAt(block)
	this.action()
	insertAt(then)
}

@OptIn(ExperimentalContracts::class)
internal inline fun Context.doInside(block: LLVMBasicBlockRef, action: Context.() -> Unit): LLVMBasicBlockRef {
	contract {
		callsInPlace(action, InvocationKind.EXACTLY_ONCE)
	}
	val origin = currentBlock
	insertAt(block)
	this.action()
	return LLVM.LLVMGetInsertBlock(builder).also { insertAt(origin) }
}
