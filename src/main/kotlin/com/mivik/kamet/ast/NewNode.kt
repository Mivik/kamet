package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import com.mivik.kamet.pointer
import org.bytedeco.llvm.global.LLVM

internal class NewNode(val type: TypeDescriptor) : ASTNode {
	override fun Context.codegenForThis(): Value =
		type.translate().let {
			Value(
				LLVM.LLVMBuildMalloc(builder, it.llvm, "malloc"),
				it.pointer()
			)
		}
}