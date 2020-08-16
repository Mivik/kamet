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
	private val internalMap: PersistentMap<String, Any>,
	private val typeMap: PersistentMap<String, Type>,
	private val functionMap: PersistentListMap<String, Function>
) : Disposable {
	companion object {
		fun topLevel(moduleName: String): Context =
			Context(
				null,
				LLVM.LLVMModuleCreateWithName(moduleName),
				LLVM.LLVMCreateBuilder(),
				null,
				PersistentMap(),
				PersistentMap(),
				Type.defaultTypeMap.subMap(),
				PersistentListMap()
			)
	}

	val llvmFunction: LLVMValueRef
		get() = currentFunction!!.llvm

	fun lookupValueOrNull(name: String) = valueMap[name]
	fun lookupValue(name: String) = valueMap[name] ?: error("Unknown identifier ${name.escape()}")
	fun lookupInternalOrNull(name: String) = internalMap[name]
	fun lookupInternal(name: String) = internalMap[name] ?: error("Unknown internal identifier ${name.escape()}")
	fun lookupTypeOrNull(name: String) = typeMap[name]
	fun lookupType(name: String) = typeMap[name] ?: error("Unknown type ${name.escape()}")

	fun hasValue(name: String): Boolean = valueMap.containsKey(name)
	fun lookupFunctions(name: String, receiverType: Type? = null): Iterable<Function> = functionMap[name]

	fun declare(name: String, value: Value) {
		valueMap[name] = value
	}

	fun declareInternal(name: String, value: Any) {
		internalMap[name] = value
	}

	inline fun declareType(type: Type) = declareType(type.name, type)

	fun declareType(name: String, type: Type) {
		if (typeMap.containsKey(name)) error("Redeclaration of type ${name.escape()}")
		require(type.resolved) { "Declaring an unresolved type: $type" }
		typeMap[name] = type
	}

	fun subContext(
		function: Value? = currentFunction,
		topLevel: Boolean = false
	): Context =
		Context(
			this,
			module,
			if (topLevel) LLVM.LLVMCreateBuilder() else builder,
			function,
			valueMap.subMap(),
			internalMap.subMap(),
			typeMap.subMap(),
			functionMap.subMap()
		)

	inline fun insertAt(block: LLVMBasicBlockRef) {
		LLVM.LLVMPositionBuilderAtEnd(builder, block)
	}

	inline val currentBlock: LLVMBasicBlockRef? get() = LLVM.LLVMGetInsertBlock(builder)

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
	internal fun Type.resolve(): Type =
		if (resolved) this
		else resolveForThis().let {
			if (it == this) this
			else it.resolve()
		}

	internal inline fun Function.invoke(receiver: Value?, arguments: List<Value>) = invokeForThis(receiver, arguments)
	internal inline fun TypeParameter.check(type: Type) = checkForThis(type)
	internal inline fun Type.Generic.resolveGeneric(typeArguments: List<Type>) = resolveGenericForThis(typeArguments)

	internal fun Function.match(
		receiverType: Type?,
		argumentTypes: List<Type>
	): Boolean {
		val type = type
		val parameterTypes = type.parameterTypes
		if (parameterTypes.size != argumentTypes.size) return false
		if (receiverType == null) {
			if (type.receiverType != null) return false
		} else {
			if (type.receiverType == null || !receiverType.canImplicitlyCastTo(type.receiverType)) return false
		}
		return parameterTypes.indices.all { argumentTypes[it].canImplicitlyCastTo(parameterTypes[it]) }
	}

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
		require(value.resolved) { "Declaring an unresolved function" }
		val parameterTypes = value.type.parameterTypes
		findDuplicate@ for (function in lookupFunctions(prototype.name)) {
			val type = function.type
			if (parameterTypes.size != type.parameterTypes.size) continue
			for (i in parameterTypes.indices)
				if (parameterTypes[i] != type.parameterTypes[i]) continue@findDuplicate
			error("Function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToString()})")
		}
		functionMap.add(prototype.name, value)
	}

	internal fun genericContext(typeParameters: List<TypeParameter>, typeArguments: List<Type>): Context {
		require(typeParameters.size == typeArguments.size) { "Expected ${typeParameters.size} type arguments, got ${typeArguments.size}: [${typeArguments.joinToString()}]" }
		val sub = subContext(topLevel = true)
		typeParameters.forEachIndexed { index, para ->
			if (!para.check(typeArguments[index])) error("${typeArguments[index]} does not satisfy $para")
			sub.declareType(para.name, typeArguments[index].resolve())
		}
		return sub
	}

	internal inline fun <reified R : Any> buildGeneric(
		name: String,
		typeParameters: List<TypeParameter>,
		typeArguments: List<Type>,
		block: Context.() -> R
	): R {
		val genericName = actualGenericName(name, typeArguments)
		internalMap[name]?.let { return it as R }
		return with(genericContext(typeParameters, typeArguments), block)
			.also { declareInternal(genericName, it) }
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
	val origin = currentBlock!!
	insertAt(block)
	this.action()
	return LLVM.LLVMGetInsertBlock(builder).also { insertAt(origin) }
}
