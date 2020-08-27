package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.escape
import com.mivik.kamet.ifNotNull
import com.mivik.kamet.ifThat
import com.mivik.kamet.reference
import org.bytedeco.llvm.global.LLVM

private fun Context.convert(value: Value, expected: Type? = null): Value {
	expected?.resolve()?.let { return value.implicitCast(it) }
	return value.dereference()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun unclearVariable(name: String): Nothing =
	error("Declaration of variable ${name.escape()} without type or initializer")

internal class LetDeclareNode(
	val name: String,
	val type: Type? = null,
	val defaultValue: ASTNode? = null
) : ASTNode() {
	override fun Context.codegenForThis(): Value {
		if (defaultValue == null) {
			type ?: unclearVariable(name)
			val value = type.resolve().undefined()
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		} else {
			val value = convert(defaultValue.codegen(), type)
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		}
		return Value.Unit
	}

	override fun toString(): String = "val $name${type.ifNotNull { ": $type" }} = $defaultValue"
}

internal class VarDeclareNode(
	val name: String,
	val type: Type? = null,
	val defaultValue: ASTNode? = null,
	val isConst: Boolean,
	val isGlobal: Boolean = false
) : ASTNode() {
	override fun Context.codegenForThis(): Value {
		val value =
			if (defaultValue == null) {
				type ?: unclearVariable(name)
				type.resolve().undefined()
			} else convert(defaultValue.codegen(), type)
		if (isGlobal) {
			declare(name, value.type.reference(isConst).new(LLVM.LLVMAddGlobal(module, value.type.llvm, name).also {
				if (defaultValue == null)
					LLVM.LLVMSetInitializer(it, LLVM.LLVMConstNull(value.type.llvm))
				else if (LLVM.LLVMIsAConstant(value.llvm) == null) error("Initializer of global variable must be a constant")
				else LLVM.LLVMSetInitializer(it, value.llvm)
			}))
		} else declareVariable(name, value, isConst)
		return Value.Unit
	}

	override fun toString(): String =
		"${if (isConst) "val" else "var"} $name${type.ifNotNull { ": $type" }} = $defaultValue"
}
