package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class StructNode(attributes: Attributes, val name: String, val elements: List<Pair<String, TypeDescriptor>>) :
	ASTNode {
	val packed: Boolean

	init {
		var packed = false
		for (attr in attributes)
			when (attr) {
				Attribute.PACKED -> packed = true
				else -> attr.notApplicableTo("Struct")
			}
		this.packed = packed
	}

	override fun codegen(context: Context): Value {
		context.declareType(Type.Struct(name, elements.map { Pair(it.first, it.second.translate(context)) }, packed))
		return Value.Nothing
	}
}