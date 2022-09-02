package server.util

inline fun <reified T> Any.safeCast(): T? = if (this is T) this else null