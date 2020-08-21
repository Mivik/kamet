package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.FunctionGenerator
import com.mivik.kamet.Trait
import com.mivik.kamet.Type
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.TypeParameterTable
import com.mivik.kamet.Value
import com.mivik.kamet.addIndent
import com.mivik.kamet.expect

internal class TraitNode(val name: String, val elements: List<FunctionGenerator>) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val implementedFunctions = mutableListOf<Function.Generic>()
		val abstractFunctions = mutableListOf<Function.Dynamic>()
		val prototypes = mutableListOf<PrototypeNode>()
		val trait = Trait.Base(name, implementedFunctions, abstractFunctions, prototypes)
		with(subContext(trait = trait)) {
			for (element in elements) {
				if (element is PrototypeNode) {
					prototypes += element.prototype.resolve()
				} else implementedFunctions += Function.Generic.obtain(
					this,
					element.expect<FunctionNode>().resolve(),
					listOf(TypeParameter.This(trait))
				)
			}
		}
		prototypes.sortBy { it.functionName }
		TypeParameterTable.scope {
			set(TypeParameter.This(trait), Type.Dynamic(trait, null))
			for ((index, prototype) in prototypes.withIndex())
				abstractFunctions += Function.Dynamic(
					index,
					prototype.type.resolve(true) as Type.Function
				)
		}
		declareTrait(trait)
		return Value.Unit
	}

	override fun toString(): String =
		"trait $name {\n${elements.joinToString("\n\n").addIndent()}\n}"
}