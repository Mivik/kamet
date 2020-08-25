package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.Value
import com.mivik.kamet.genericName
import com.mivik.kamet.ifThat

private fun buildStructType(attributes: Attributes, name: String, elements: List<Pair<String, Type>>): Type.Struct {
	var packed = false
	for (attr in attributes)
		when (attr) {
			Attribute.PACKED -> packed = true
			else -> attr.notApplicableTo("Struct")
		}
	return Type.Struct(name, elements, packed)
}

private fun buildString(attributes: Attributes, name: String, elements: List<Pair<String, Type>>) =
	"${attributes}struct $name {${
		elements.isNotEmpty().ifThat {
			"\n\t" + elements.joinToString(",\n\t") { "${it.first}: ${it.second}" } + "\n"
		}
	}}"

internal class StructNode(
	val attributes: Attributes,
	name: String,
	elements: List<Pair<String, Type>>
) : ASTNode() {
	val type: Type.Struct = buildStructType(attributes, name, elements)

	override fun Context.codegenForThis(): Value {
		declareType(type.resolve())
		return Value.Unit
	}

	override fun toString(): String =
		buildString(attributes, type.name, type.elements)
}

internal class GenericStructNode(
	val attributes: Attributes,
	name: String,
	elements: List<Pair<String, Type>>,
	val typeParameters: List<TypeParameter>
) : ASTNode() {
	private val type = buildStructType(attributes, name, elements)

	override fun Context.codegenForThis(): Value {
		declareType(type.name, Type.Generic(type, typeParameters))
		return Value.Unit
	}

	override fun toString(): String =
		buildString(attributes, genericName(type.name, typeParameters), type.elements)
}
