package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Generic
import com.mivik.kamet.Type
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.Value
import com.mivik.kamet.genericName
import com.mivik.kamet.ifThat

internal class StructNode(
	val attributes: Attributes,
	name: String,
	elements: List<Pair<String, Type>>
) : ASTNode {
	val packed: Boolean
	val type: Type.Struct

	init {
		var packed = false
		for (attr in attributes)
			when (attr) {
				Attribute.PACKED -> packed = true
				else -> attr.notApplicableTo("Struct")
			}
		this.packed = packed
		type = Type.Struct(name, elements, this.packed)
	}

	override fun Context.codegenForThis(): Value {
		declareType(type.resolve())
		return Value.Unit
	}

	override fun toString(): String = with(type) {
		"${attributes}struct $name {${
			elements.isNotEmpty().ifThat {
				"\n\t" + elements.joinToString(",\n\t") { "${it.first}: ${it.second}" } + "\n"
			}
		}}"
	}
}

internal class GenericStructNode(
	val attributes: Attributes,
	val name: String,
	val elements: List<Pair<String, Type>>,
	val typeParameters: List<TypeParameter>
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		declareGeneric(name, object : Generic(name, typeParameters) {
			override fun Context.instantiate(arguments: List<Type>): Any =
				StructNode(attributes, genericName(name, arguments), elements).type.resolve()
		})
		return Value.Unit
	}
}
