package com.mivik.kamet

private fun unresolved(): Nothing = error("Getting the functions of an un-resolved trait.")

sealed class Trait(val name: String) : Resolvable {
	abstract val implementedFunctions: Iterable<Function.Generic>
	abstract val abstractFunctions: List<Function.Dynamic>
	internal abstract val prototypes: List<Prototype>
	abstract override fun Context.resolveForThis(): Trait
	val functions by lazy { concat(implementedFunctions, abstractFunctions) }
	override fun hashCode(): Int = name.hashCode()
	override fun equals(other: Any?): Boolean =
		other is Trait && name == other.name

	override fun toString(): String = name

	class Named(name: String) : Trait(name) {
		override val resolved: Boolean
			get() = false

		override fun Context.resolveForThis() = lookupTrait(name)

		override val implementedFunctions: Iterable<Function.Generic>
			get() = unresolved()
		override val abstractFunctions: List<Function.Dynamic>
			get() = unresolved()
		override val prototypes: List<Prototype>
			get() = unresolved()
	}

	class Base internal constructor(
		name: String,
		override val implementedFunctions: Iterable<Function.Generic>,
		override val abstractFunctions: List<Function.Dynamic>,
		override val prototypes: List<Prototype>
	) : Trait(name) {
		override val resolved: Boolean
			get() = true

		override fun Context.resolveForThis() = this@Base
	}
}