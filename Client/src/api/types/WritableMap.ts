
export type WritableMap<K, V> = {
  set(map: Map<K, V>): void
  put(key: K, value: V): void
  remove(key: K): void
  subscribe(run: (value: Map<K, V>) => void): () => void
}
