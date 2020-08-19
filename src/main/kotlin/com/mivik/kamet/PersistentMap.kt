package com.mivik.kamet

class PersistentMap<K, V>(
	private val parent: PersistentMap<K, V>? = null,
	private val delegate: MutableMap<K, V> = mutableMapOf()
) :
	MutableMap<K, V> by delegate {
	companion object {
		private tailrec fun <K, V> get(map: PersistentMap<K, V>, key: K): V? {
			return map.delegate[key] ?: get(map.parent ?: return null, key)
		}
	}

	override fun containsKey(key: K): Boolean {
		var current = this
		while (true) {
			if (current.delegate.containsKey(key)) return true
			current = current.parent ?: return false
		}
	}

	override fun get(key: K): V? = get(this, key)

	fun subMap() = PersistentMap(this)
}

class PersistentGroupingMap<K, V>(
	val parent: PersistentGroupingMap<K, V>? = null,
	val delegate: MutableMap<K, MutableList<V>> = mutableMapOf()
) {
	fun containsKey(key: K): Boolean {
		var current = this
		while (true) {
			if (current.delegate.containsKey(key)) return true
			current = current.parent ?: return false
		}
	}

	fun add(key: K, value: V) {
		delegate.getOrPut(key) { mutableListOf() } += value
	}

	fun collect(key: K): MutableList<Iterable<V>> {
		val list = mutableListOf<Iterable<V>>()
		var current = this
		while (true) {
			current.delegate[key]?.let { list += it }
			current = current.parent ?: return list
		}
	}

	fun contains(key: K, value: V): Boolean = has(key) { it == value }

	inline fun has(key: K, predict: (V) -> Boolean): Boolean {
		var current = this
		while (true) {
			current.delegate[key]?.forEach { if (predict(it)) return true }
			current = current.parent ?: return false
		}
	}

	operator fun get(key: K): Iterable<V> = ChainIterable(collect(key).readOnly())

	fun subMap() = PersistentGroupingMap(this)
}
