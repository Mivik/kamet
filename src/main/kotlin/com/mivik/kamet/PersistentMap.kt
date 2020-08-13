package com.mivik.kamet

class PersistentMap<K, V>(val parent: PersistentMap<K, V>? = null, val delegate: MutableMap<K, V> = mutableMapOf()) :
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