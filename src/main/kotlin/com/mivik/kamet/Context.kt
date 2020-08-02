package com.mivik.kamet

import com.mivik.kot.escape
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMModuleRef
import org.bytedeco.llvm.global.LLVM

internal class Context(
	val parent: Context?,
	val module: LLVMModuleRef,
	val builder: LLVMBuilderRef,
	var result: Value?,
	private val implementedFunctions: MutableSet<String>,
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
				mutableSetOf(),
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

	fun functionImplemented(name: String) = name in implementedFunctions
	fun implementFunction(name: String) {
		implementedFunctions += name
	}

	fun subContext(): Context = Context(this, module, builder, null, implementedFunctions, mutableMapOf(), mutableMapOf())
}