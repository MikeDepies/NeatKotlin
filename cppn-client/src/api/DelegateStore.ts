import { Writable, writable } from "svelte/store"

export function delegate<T>(defaultValue : T, config : Partial<DelegateConfiguration<T>>) {
  let w = writable(defaultValue)
  return {
    subscribe: w.subscribe,
    set: (value: T) => {
      if (config.set) config.set(value)
      w.set(value)
    }
  }
}
type DelegateConfiguration<T> = Pick<Writable<T>, "set">

