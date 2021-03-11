import { writable } from "svelte/store"
import type { WritableArray } from "../types/WritableArray"


export function arrayWritable<T>(initalValue: T[])  : WritableArray<T>{
  const w = writable<T[]>(initalValue)
  return {
    ...w,
    length: () => {
      let data: T[] = []
      w.subscribe(v => data = v)()
      return data.length
    },
    setElement(index : number, element: T) {
      let data: T[] = []
      w.subscribe(v => data = v)()
      data[index] = element
      w.set([...data])
    },
    push(element: T) {
      let data: T[] = []
      w.subscribe(v => data = v)()
      w.set([...data, element])
    },
    pushFront(element: T) {
      let data: T[] = []
      w.subscribe(v => data = v)()
      w.set([...data, element])
    },
    remove(element: T) {
      let data: T[] = []
      w.subscribe(v => data = v)()
      w.set([...data.filter(d => d !== element)])
    },
    removeBy(predicate: (element: T) => boolean) {
      let data: T[] = []
      w.subscribe(v => data = v)()
      w.set([...data.filter(d => predicate(d))])
    }
  }
}
