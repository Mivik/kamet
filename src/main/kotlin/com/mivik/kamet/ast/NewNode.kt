package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.pointer
import org.bytedeco.llvm.global.LLVM

internal class NewNode(val type: Type) : ASTNode() {
	override fun Context.codegenForThis(): Value =
		type.resolve().let {
			Value(
				LLVM.LLVMBuildMalloc(builder, it.llvm, "malloc"),
				it.pointer()
			)
		}

	override fun toString(): String = "new $type"
}