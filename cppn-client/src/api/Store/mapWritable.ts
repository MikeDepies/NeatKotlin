import { writable } from "svelte/store"
import type { WritableMap } from "../types/WritableMap"


export function mapWritable<K, V>(initialValue: Map<K, V>) : WritableMap<K,V> {
  const w = writable<Map<K, V>>(initialValue)
  return {
    ...w,
    put(key: K, value: V) {
      let newMap: Map<K, V> = new Map<K, V>()
      w.subscribe(m => newMap = m)()
      newMap.set(key, value)
      w.set(newMap)
    },
    remove(key: K) {
      let newMap: Map<K, V> = new Map<K, V>()
      w.subscribe(m => newMap = m)
      newMap.delete(key)
      w.set(newMap)
    }
  }
}
