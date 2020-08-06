package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class StructNode(attributes: Attributes, val name: String, val elements: List<Pair<String, TypeDescriptor>>) :
	ASTNode {
	val isPacked: Boolean

	init {
		var packed = false
		for (attr in attributes)
			if (attr == Attribute.PACKED) packed = true
			else attr.notApplicableTo("Struct")
		this.isPacked = packed
	}

	override fun codegen(context: Context): Value {
		context.declareType(Type.Struct(name, elements.map { Pair(it.first, it.second.translate(context)) }, isPacked))
		return Value.Nothing
	}
}