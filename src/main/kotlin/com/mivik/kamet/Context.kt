package com.mivik.kamet

import com.mivik.kamet.ast.ASTNode
import com.mivik.kamet.ast.FunctionNode
import com.mivik.kamet.ast.PrototypeNode
import org.bytedeco.llvm.LLVM.LLVMAttributeRef
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMContextRef
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
	val llvm: LLVMContextRef,
	val module: LLVMModuleRef,
	val builder: LLVMBuilderRef,
	val currentFunction: Value?,
	val currentTrait: Trait?,
	private val valueMap: PersistentMap<String, Value>,
	private val internalMap: PersistentMap<String, Any>,
	private val typeMap: PersistentMap<String, Type>,
	private val functionMap: PersistentGroupingMap<String, Function>,
	private val genericFunctionMap: PersistentMap<String, Function.Generic>,
	private val traitMap: PersistentMap<String, Trait>,
	private val traitImplMap: PersistentGroupingMap<Type, TraitImpl>,
) : Disposable {
	companion object {
		fun topLevel(moduleName: String): Context =
			Context(
				null,
				LLVM.LLVMContextCreate(),
				LLVM.LLVMModuleCreateWithName(moduleName),
				LLVM.LLVMCreateBuilder(),
				null, null,
				PersistentMap(),
				PersistentMap(),
				Type.defaultTypeMap.subMap(),
				PersistentGroupingMap(),
				PersistentMap(),
				PersistentMap(),
				PersistentGroupingMap()
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
	fun lookupGenericFunctionOrNull(name: String) = genericFunctionMap[name]
	fun lookupGenericFunction(name: String) =
		genericFunctionMap[name] ?: error("Unknown generic function ${name.escape()}")

	fun lookupTraitOrNull(name: String) = traitMap[name]
	fun lookupTrait(name: String) = traitMap[name] ?: error("Unknown trait ${name.escape()}")

	fun hasValue(name: String): Boolean = valueMap.containsKey(name)
	fun lookupFunctions(name: String, receiverType: Type? = null, generic: Boolean = false): Iterable<Function> {
		val list =
			mutableListOf<Iterable<Function>>(functionMap[name].filter { it.type.receiverType == receiverType })
		if (generic) genericFunctionMap[name]?.let { list += listOf(it) }
		if (receiverType != null)
			traitImplMap[receiverType.dereference()].forEach { impl ->
				val trait = impl.trait
				list += trait.implementedFunctions.filter { it.node.prototype.name == name }
				list += trait.abstractFunctions.filterIndexed { index, _ -> trait.prototypes[index].name == name }
			}
		return ChainIterable(list.readOnly())
	}

	fun lookupImplOrNull(trait: Trait, type: Type): TraitImpl? {
		for (impl in traitImplMap[type])
			if (impl.trait == trait) return impl
		return null
	}

	fun lookupImpl(trait: Trait, type: Type): TraitImpl =
		lookupImplOrNull(trait, type) ?: error("$trait is not implemented for $type")

	fun Type.implemented(trait: Trait) =
		when (this) {
			is Type.Impl -> this.trait == trait
			is Type.This -> this.trait == trait
			else -> traitImplMap.has(this) { it.trait == trait }
		}

	internal fun Value.getElementPtr(vararg indices: Int, name: String = "") =
		LLVM.LLVMBuildGEP2(
			builder,
			type.expect<Type.Pointer>().elementType.llvm,
			llvm,
			buildPointerPointer(indices.size) { indices[it].toLLVM() },
			indices.size,
			name
		)

	fun Value.asPointerOrNull(): Value? =
		if (type is Type.Reference && type.originalType is Type.Array) {
			val type = type.originalType.elementType.pointer(type.isConst)
			type.new(llvm.bitCast(type.llvm))
		} else type.asPointerOrNull()?.let { Value(llvm, it) }

	fun declare(name: String, value: Value) {
		valueMap[name] = value
	}

	fun declareInternal(name: String, value: Any) {
		internalMap[name] = value
	}

	inline fun declareType(type: Type) = declareType(type.name, type)

	fun declareType(name: String, type: Type) {
		typeMap[name] = type
	}

	fun declareTrait(trait: Trait) = declareTrait(trait.name, trait)

	fun declareTrait(name: String, trait: Trait) {
		traitMap[name] = trait
	}

	fun obtainAttribute(name: String, value: Long = 0): LLVMAttributeRef {
		val id = LLVM.LLVMGetEnumAttributeKindForName(name, name.length.toLong())
		return LLVM.LLVMCreateEnumAttribute(llvm, id, value)
	}

	fun subContext(
		function: Value? = currentFunction,
		trait: Trait? = currentTrait,
		topLevel: Boolean = false
	): Context =
		Context(
			this,
			llvm,
			module,
			if (topLevel) LLVM.LLVMCreateBuilder() else builder,
			function, trait,
			valueMap.subMap(),
			internalMap.subMap(),
			typeMap.subMap(),
			functionMap.subMap(),
			genericFunctionMap.subMap(),
			traitMap.subMap(),
			traitImplMap.subMap()
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
	internal inline fun Value.implicitCastOrNull(to: Type) =
		with(CastManager) { implicitCastOrNull(this@implicitCastOrNull, to) }

	internal inline fun Value.implicitCast(to: Type) = with(CastManager) { implicitCast(this@implicitCast, to) }
	internal inline fun Value.explicitCast(to: Type) = with(CastManager) { explicitCast(this@explicitCast, to) }
	internal inline fun Type.canImplicitlyCastTo(to: Type) =
		with(CastManager) { canImplicitlyCast(this@canImplicitlyCastTo, to) }

	internal inline fun Value.pointerToInt() = explicitCast(Type.pointerAddressType)
	internal inline fun Type.sizeOf() = Type.pointerAddressType.new(LLVM.LLVMSizeOf(dereference().llvm))
	internal inline fun FunctionGenerator.generate(newName: String? = null) = generateForThis(newName)
	internal inline fun Function.resolve() = resolveForThis()
	internal inline fun Trait.resolve() = resolveForThis()
	internal inline fun PrototypeNode.resolve() = resolveForThis()
	internal inline fun FunctionNode.resolve() = resolveForThis()
	internal inline fun Prototype.resolve() = resolveForThis()

	internal inline fun Type.transform(noinline action: Context.(Type) -> Type?): Type = transformForThis(action)

	internal inline fun Type.resolve(resolveTypeParameter: Boolean = false): Type {
		val parameterTable = TypeParameterTable.get()
		return transform {
			when {
				it is Type.Named -> lookupType(it.name)
				resolveTypeParameter && it is Type.TypeParameter -> parameterTable[it.typeParameter]
				currentTrait != null && it == Type.UnresolvedThis -> Type.This(currentTrait)
				else -> null
			}
		}
	}

	internal fun LLVMValueRef.bitCast(to: LLVMTypeRef, name: String = "") =
		LLVM.LLVMBuildBitCast(
			builder,
			this,
			to,
			name
		)

	internal inline fun Function.invoke(receiver: Value?, arguments: List<Value>, typeArguments: List<Type>) =
		invokeForThis(receiver, arguments, typeArguments)

	internal inline fun TypeParameter.check(type: Type) = checkForThis(type)

	internal fun Function.instantiate(
		receiverType: Type?,
		argumentTypes: List<Type>? = null,
		typeArguments: List<Type> = emptyList()
	): Function? {
		val type = type
		val parameterTypes = type.parameterTypes
		if (argumentTypes != null && parameterTypes.size != argumentTypes.size) return null
		if (typeArguments.isNotEmpty() && typeParameters.size != typeArguments.size) return null

		// type inference begins
		return TypeParameterTable.scope {
			for (i in typeArguments.indices) set(typeParameters[i], typeArguments[i])
			if (receiverType == null) {
				if (type.receiverType != null) return null
			} else {
				if (type.receiverType == null || !receiverType.canImplicitlyCastTo(type.receiverType)) return null
			}
			if (argumentTypes != null && parameterTypes.indices.any {
					!argumentTypes[it].canImplicitlyCastTo(
						parameterTypes[it]
					)
				}) return null
			if (this@instantiate is Function.Generic) {
				val name = node.prototype.name
				val newTypeArguments = if (typeArguments.isEmpty()) map(typeParameters) else typeArguments
				buildGeneric(name, typeParameters, newTypeArguments) {
					node.generate(actualGenericName(name, newTypeArguments))
				}
			} else this@instantiate
		}
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

	internal fun declareFunction(prototype: Prototype, value: Function) {
		if (value is Function.Generic) {
			if (genericFunctionMap.containsKey(prototype.name)) error("Generic function redeclared: ${prototype.name}")
			genericFunctionMap[prototype.name] = value
			return
		}
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

	internal fun declareTraitImpl(impl: TraitImpl) {
		if (traitImplMap.has(impl.type) { impl.trait == it.trait }) error("Re-implementation of ${impl.trait} for ${impl.type}")
		traitImplMap.add(impl.type, impl)
		with(impl) { initialize() }
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
		@Suppress("NAME_SHADOWING")
		val typeArguments = typeArguments.map { it.resolve() }
		val genericName = actualGenericName(name, typeArguments)
		internalMap[genericName]?.let { return it as R }
		TypeParameterTable.set(typeParameters, typeArguments)
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
		LLVM.LLVMAddFunctionInliningPass(pass)
		LLVM.LLVMRunPassManager(pass, module)
		LLVM.LLVMDisposePassManager(pass)
	}

	fun dump() {
		LLVM.LLVMDumpModule(module)
	}

	/**
	 * @return null if the module passed the verification, and otherwise the error message.
	 */
	fun verify(): String? = captureError {
		LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, it)
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
