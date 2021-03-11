import type { Writable } from "svelte/store"
import type { MixedWritable } from "./MixedWritable"
import type { WritableArray } from "./WritableArray"
import type { WritableMap } from "./WritableMap"



export type WritableRecord<T extends {}> = {
  [KEY in keyof T]: T[KEY] extends Map<infer K, infer V> ? WritableMap<K, V> : T[KEY] extends (infer K)[] ? WritableArray<T[KEY]> : Writable<T[KEY]>
}

