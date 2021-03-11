import type { Readable } from "svelte/store"

export function getValue<T, R>(readable: Readable<T>): T | undefined {
  let v: T | undefined = undefined
  readable.subscribe(r => (v = r))()
  return v
}