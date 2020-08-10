package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ifThat

internal class StructNode(
	attributes: Attributes,
	val name: String,
	val elements: List<Pair<String, Type>>
) : ASTNode {
	val isPacked: Boolean

	init {
		var packed = false
		for (attr in attributes)
			if (attr == Attribute.PACKED) packed = true
			else attr.notApplicableTo("Struct")
		this.isPacked = packed
	}

	override fun Context.codegenForThis(): Value {
		declareType(Type.Struct(name, elements.map { Pair(it.first, it.second.resolve()) }, isPacked))
		return Value.Unit
	}

	override fun toString(): String =
		"${isPacked.ifThat { "#[packed] " }}struct $name {${
			elements.isNotEmpty().ifThat {
				"\n\t" + elements.joinToString(",\n\t") { "${it.first}: ${it.second}" } + "\n"
			}
		}}"
}