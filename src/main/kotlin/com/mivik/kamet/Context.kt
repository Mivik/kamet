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

@Suppress("NOTHING_TO_INLINE")
class Context(
	val parent: Context?,
	val module: LLVMModuleRef,
	val builder: LLVMBuilderRef,
	val currentFunction: Value?,
	private val valueMap: PersistentMap<String, Value>,
	private val typeMap: PersistentMap<String, Type>,
	private val functionMap: PersistentMap<String, MutableList<Function>>,
	private val genericMap: PersistentMap<String, GenericType>
) : Disposable {
	companion object {
		fun topLevel(moduleName: String): Context =
			Context(
				null,
				LLVM.LLVMModuleCreateWithName(moduleName),
				LLVM.LLVMCreateBuilder(),
				null,
				PersistentMap(),
				Type.defaultTypeMap.subMap(),
				PersistentMap(),
				PersistentMap()
			)
	}

	val llvmFunction: LLVMValueRef
		get() = currentFunction!!.llvm

	fun lookupValueOrNull(name: String) = valueMap[name]
	fun lookupValue(name: String) = valueMap[name] ?: error("Unknown identifier ${name.escape()}")
	fun lookupTypeOrNull(name: String) = typeMap[name]
	fun lookupType(name: String) = typeMap[name] ?: error("Unknown type ${name.escape()}")
	fun lookupGenericOrNull(name: String) = genericMap[name]
	fun hasValue(name: String): Boolean = valueMap.containsKey(name)
	fun lookupFunctions(name: String, receiverType: Type? = null) = functionMap[name] ?: emptyList()
	fun lookupGeneric(name: String) = genericMap[name] ?: error("Unknown generic type ${name.escape()}")

	inline fun declareType(type: Type) = declareType(type.name, type)

	fun declareType(name: String, type: Type) {
		if (typeMap.containsKey(name)) error("Redeclaration of type ${name.escape()}")
		typeMap[name] = type
	}

	fun declareGeneric(name: String, generic: GenericType) {
		if (genericMap.containsKey(name)) error("Redeclaration of generic type ${name.escape()}")
		genericMap[name] = generic
	}

	fun declare(name: String, value: Value) {
		valueMap[name] = value
	}

	fun subContext(currentFunction: Value? = this.currentFunction): Context =
		Context(
			this,
			module,
			builder,
			currentFunction,
			valueMap.subMap(),
			typeMap.subMap(),
			functionMap.subMap(),
			genericMap.subMap()
		)

	inline fun insertAt(block: LLVMBasicBlockRef) {
		LLVM.LLVMPositionBuilderAtEnd(builder, block)
	}

	inline val currentBlock get() = LLVM.LLVMGetInsertBlock(builder)

	internal fun basicBlock(name: String = "block") = LLVM.LLVMAppendBasicBlock(llvmFunction, name)

	internal inline fun br(block: LLVMBasicBlockRef) {
		LLVM.LLVMBuildBr(builder, block)
	}

	internal inline fun condBr(condition: Value, thenBlock: LLVMBasicBlockRef, elseBlock: LLVMBasicBlockRef) {
		LLVM.LLVMBuildCondBr(builder, condition.llvm, thenBlock, elseBlock)
	}

	internal inline fun ASTNode.codegen() = with(this) { codegenForThis() }
	internal inline fun Value.dereference() = with(this) { dereferenceForThis() }
	internal inline fun ValueRef.setValue(value: Value) = with(this) { setValueForThis(value) }
	internal inline fun Value.implicitCast(to: Type) = with(CastManager) { implicitCast(this@implicitCast, to) }
	internal inline fun Value.explicitCast(to: Type) = with(CastManager) { explicitCast(this@explicitCast, to) }
	internal inline fun Value.pointerToInt() = explicitCast(Type.pointerAddressType)
	internal inline fun Type.sizeOf() = Type.pointerAddressType.new(LLVM.LLVMSizeOf(dereference().llvm))
	internal inline fun Type.resolve(newName: String = name) = resolveForThis(newName)
	internal inline fun Function.invoke(receiver: Value?, arguments: List<Value>) = invokeForThis(receiver, arguments)
	internal inline fun GenericType.resolve(arguments: List<Type>) = buildForThis(arguments)

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

	internal fun declareFunction(prototype: PrototypeNode, value: Function) {
		val parameterTypes = value.type.parameterTypes
		findDuplicate@ for (function in lookupFunctions(prototype.name)) {
			val type = function.type
			if (parameterTypes.size != type.parameterTypes.size) continue
			for (i in parameterTypes.indices)
				if (parameterTypes[i] != type.parameterTypes[i]) continue@findDuplicate
			error("Function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToString()})")
		}
		functionMap.getOrPut(prototype.name) { mutableListOf() } += value
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

	override fun dispose() {
		LLVM.LLVMDisposeModule(module)
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
