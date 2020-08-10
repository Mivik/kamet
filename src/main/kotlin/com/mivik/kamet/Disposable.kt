package com.mivik.kamet

interface Disposable {
	fun dispose()
}

inline fun <T : Disposable, R> T.use(block: T.() -> R): R =
	block().also { dispose() }
