import { derived, Readable, readable, writable, Writable } from "svelte/store"
import type { MixedWritable } from "../types/MixedWritable"
import type { WritableRecord } from "../types/WritableRecord"

export function nestedReadable<T, V>(_activeMatchData: Writable<WritableRecord<V> | undefined>,
  storeExtractor: ($matchData: WritableRecord<V> | undefined, set: (value: T) => void) => (() => void) | undefined
) {
  let lastSubscription: (() => void) | undefined
  return derived(_activeMatchData, ($matchData, set: (value: T) => void) => {
    if (lastSubscription !== undefined) {
      lastSubscription()
    }
    lastSubscription = storeExtractor($matchData, set)
  }, undefined)
}

export function nestedReadable2<V, K extends Extract<keyof V, string>>(_store: Writable<WritableRecord<V> | undefined>,
  key: K,
) {
  return readable<V[K] | undefined>(undefined, start => {
    let unsubscribeNestedStore: (() => void) | undefined = undefined
    const storeUnsubscribe = _store.subscribe($store => {
      if (unsubscribeNestedStore) {
        unsubscribeNestedStore()
        unsubscribeNestedStore = undefined
      }
      if ($store) {
        const nestedStore = $store[key]
        unsubscribeNestedStore = nestedStore.subscribe((value: any) => {
          start(value)
        })
      } else {
        start(undefined)
      }
    })
    return storeUnsubscribe
  })
}

export function lift<V extends MixedWritableContainer, K extends Extract<keyof V, string>>(_store: Readable<V | undefined>,
  key: K,
): V[K] {
  let store: undefined | MixedWritableContainer = undefined
  let liftedUnsubscribe: undefined | (() => void) = undefined
  return {
    set: value => {
      if (store) {
        store[key].set(value)
      }
    },
    subscribe: (set) => {
      const unsubscribe = _store.subscribe($store => {
        if (store !== $store && liftedUnsubscribe) {
          liftedUnsubscribe()
        }
        store = $store
        if ($store) {
          liftedUnsubscribe = $store[key].subscribe(set)
        }
      })
      return () => {
        unsubscribe()
      }
    }
  } as V[K]
}

type MixedWritableContainer = {
  [T: string]: MixedWritable<any, any>
}