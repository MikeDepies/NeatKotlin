import type { Readable } from "svelte/store"
import type { SubscribeFeature, SubscribeFeatureExperiment } from "../types/SubscribeFeature"

export function subscribeFor<T>(store: Readable<T>, valueChange: (value: T) => void, predicate: (storeValue: T) => boolean) {
  return store.subscribe(storeValue => {
    if (predicate(storeValue)) {
      valueChange(storeValue)
    }
  })
}

export function getValue<T>(store: Readable<T>): T {
  let value: any
  store.subscribe(v => (value = v))()
  return value
}

export function subscribeWithFeature<T>(store: Readable<T>, feature: SubscribeFeature, valueChange: (value: T) => void) {
  if (feature.intialize) feature.intialize(store)
  const unsubscribe = store.subscribe(storeValue => {
    if (feature.predicate && feature.predicate(storeValue) || true) {
      valueChange(storeValue)
      if (feature.set) feature.set(storeValue, unsubscribe)
    }
  })
  return unsubscribe
}
