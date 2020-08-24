package com.mivik.kamet

private fun unresolved(): Nothing = error("Getting the functions of an un-resolved trait.")

sealed class Trait(val name: String) {
	abstract val implementedFunctions: Iterable<Function.Generic>
	abstract val abstractFunctions: List<Function.Dynamic>
	internal abstract val prototypes: List<Prototype>
	abstract fun Context.resolveForThis(): Trait
	val functions by lazy { concat(implementedFunctions, abstractFunctions) }
	override fun hashCode(): Int = name.hashCode()
	override fun equals(other: Any?): Boolean =
		other is Trait && name == other.name

	override fun toString(): String = name

	abstract class Abstract(name: String) : Trait(name) {
		override val implementedFunctions: Iterable<Function.Generic>
			get() = unresolved()
		override val abstractFunctions: List<Function.Dynamic>
			get() = unresolved()
		override val prototypes: List<Prototype>
			get() = unresolved()
	}

	class Named(name: String) : Abstract(name) {
		override fun Context.resolveForThis() = lookupTrait(name)
	}

	class Base internal constructor(
		name: String,
		override val implementedFunctions: Iterable<Function.Generic>,
		override val abstractFunctions: List<Function.Dynamic>,
		override val prototypes: List<Prototype>
	) : Trait(name) {
		override fun Context.resolveForThis() = this@Base

		fun rename(newName: String) =
			Base(newName, implementedFunctions, abstractFunctions, prototypes)
	}

	class Generic(val base: Trait, val typeParameters: List<TypeParameter>) :
		Abstract(genericName(base.name, typeParameters)) {
		override fun Context.resolveForThis(): Trait = TypeParameterTable.get().let { table ->
			val arguments = table.mapOrNull(typeParameters)
			if (arguments == null) this@Generic
			else resolveGeneric(arguments)
		}

		fun Context.resolveGeneric(typeArguments: List<Type>): Trait {
			var implementedFunctions: MutableList<Function.Generic>? = null
			var abstractFunctions: MutableList<Function.Dynamic>? = null
			var prototypes: MutableList<Prototype>? = null
			val ret = buildGeneric(base.name, typeParameters, typeArguments) {
				base.expect<Base>()
				implementedFunctions = base.implementedFunctions.toMutableList()
				abstractFunctions = base.abstractFunctions.toMutableList()
				prototypes = base.prototypes.toMutableList()
				Base(
					actualGenericName(base.name, typeArguments),
					implementedFunctions!!,
					abstractFunctions!!,
					prototypes!!
				)
			}
			if (implementedFunctions != null) {
				implementedFunctions!!.transform { it.resolve() as Function.Generic }
				abstractFunctions!!.transform { it.resolve() as Function.Dynamic }
				prototypes!!.transform { it.resolve() }
			}
			return ret
		}
	}

	class Actual(val generic: Trait, val typeArguments: List<Type>) :
		Abstract(actualGenericName(generic.name, typeArguments)) {
		override fun Context.resolveForThis(): Trait = generic.resolve().let {
			if (it is Generic) with(it) { resolveGeneric(typeArguments) }
			else Actual(it, typeArguments)
		}
	}
}