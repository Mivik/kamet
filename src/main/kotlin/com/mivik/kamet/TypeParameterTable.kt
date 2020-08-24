package com.mivik.kamet

internal class TypeParameterTable private constructor(private val delegate: MutableMap<TypeParameter, Type> = mutableMapOf()) :
	MutableMap<TypeParameter, Type> by delegate {
	companion object {
		private val threadLocal =
			object : ThreadLocal<TypeParameterTable>() {
				override fun initialValue(): TypeParameterTable = TypeParameterTable()
			}

		fun set(typeParameters: List<TypeParameter>, typeArguments: List<Type>) {
			get().apply {
				clear()
				for (i in typeParameters.indices) put(typeParameters[i], typeArguments[i])
			}
		}

		fun get(): TypeParameterTable = threadLocal.get()

		inline fun <R> scope(block: TypeParameterTable.() -> R): R {
			val table = get()
			table.clear()
			table.enabled = true
			val ret = table.block()
			table.enabled = false
			table.clear()
			return ret
		}
	}

	var enabled = false

	fun equals(typeParameter: TypeParameter, other: Type): Boolean {
		if (!enabled) return false
		this[typeParameter]?.let { return it == other }
		this[typeParameter] = other
		return true
	}

	fun map(typeParameters: List<TypeParameter>): List<Type> =
		typeParameters.map { this[it] ?: error("Cannot infer type for $it") }

	fun mapOrNull(typeParameters: List<TypeParameter>): List<Type>? =
		typeParameters.map { this[it] ?: return null }
}