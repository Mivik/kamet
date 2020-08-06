package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class SizeOfNode(val type: TypeDescriptor) : ASTNode {
	override fun codegen(context: Context): Value =
		Value(LLVM.LLVMSizeOf(type.translate(context).dereference().llvm), Type.Primitive.Integral.ULong)
}