package com.mivik.kamet

sealed class TypeParameter {
	abstract val name: String
	abstract fun Context.checkForThis(type: Type): Boolean
	override fun toString(): String = name

	data class Simple(override val name: String) : TypeParameter() {
		override fun Context.checkForThis(type: Type): Boolean = true
		override fun toString(): String = name
	}

	open class Trait(override val name: String, val trait: com.mivik.kamet.Trait) : TypeParameter() {
		override fun Context.checkForThis(type: Type): Boolean =
			type.implemented(trait) ||
					(type is Type.Dynamic && type.trait == trait)

		override fun hashCode(): Int {
			var result = name.hashCode()
			result = 31 * result + trait.hashCode()
			return result
		}

		override fun equals(other: Any?): Boolean =
			other is Trait && name == other.name && trait == other.trait

		override fun toString(): String = "$name : $trait"
	}

	class This(trait: com.mivik.kamet.Trait) : Trait("This", trait) {
		override fun hashCode(): Int = trait.hashCode()
		override fun equals(other: Any?): Boolean =
			other is This && trait == other.trait

		override fun toString(): String = "This[$trait]"
	}
}