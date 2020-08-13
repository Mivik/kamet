package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.GenericType
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ifThat

internal class StructNode(
	attributes: Attributes,
	name: String,
	elements: List<Pair<String, Type>>,
	val parameterNames: List<String>
) : ASTNode {
	val packed: Boolean
	val type: Type.Struct

	init {
		var packed = false
		for (attr in attributes)
			if (attr == Attribute.PACKED) packed = true
			else attr.notApplicableTo("Struct")
		this.packed = packed
		type = Type.Struct(name, elements, this.packed)
	}

	override fun Context.codegenForThis(): Value {
		if (parameterNames.isEmpty())
			declareType(type.resolve())
		else
			declareGeneric(type.name, GenericType(type, parameterNames))
		return Value.Unit
	}

	override fun toString(): String = with(type) {
		"${packed.ifThat { "#[packed] " }}struct $name {${
			elements.isNotEmpty().ifThat {
				"\n\t" + elements.joinToString(",\n\t") { "${it.first}: ${it.second}" } + "\n"
			}
		}}"
	}
}