package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.Trait
import com.mivik.kamet.TraitImpl
import com.mivik.kamet.Type
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.TypeParameterTable
import com.mivik.kamet.Value
import com.mivik.kamet.addIndent
import com.mivik.kamet.expect

internal class ImplNode(val trait: Trait, val type: Type, val elements: List<FunctionNode>) : ASTNode() {
	override fun Context.codegenForThis(): Value {
		val trait = trait.resolve()
		val type = type.resolve()
		val elements = elements.sortedBy { it.node.functionName }
		val prototypes = trait.prototypes
		val functions = mutableListOf<Function.Generic>()
		with(subContext(trait = trait)) {
			for ((index, oldElement) in elements.withIndex()) {
				val element = oldElement.resolve()
				if (element.prototype != prototypes[index]) error("Unknown implemented function: ${element.node}")
				functions += Function.Generic.obtain(
					this,
					element.rename("${element.node.prototype.name}<$type>"),
					listOf(TypeParameter.This(trait))
				)
			}
		}
		if (elements.size != prototypes.size) error("Function not implemented: ${prototypes[elements.size]}")
		declareTraitImpl(TraitImpl(trait, type, functions))
		return Value.Unit
	}

	override fun toString(): String =
		"impl $trait for $type {\n${elements.joinToString("\n\n").addIndent()}\n}"
}