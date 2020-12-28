import type { Readable } from "svelte/store"
import { subscribeForCountFeature, subscribeUniqueChangesNotUndefined } from "./SubscriptionFeatures"
import { subscribeWithFeature } from "./SubscriptionHelper"

export function promisfySubscription<T>(store: Readable<T>) {
  const p = new Promise<T>((resolve, reject) => {
    const countFeature = subscribeForCountFeature(1)
    const uniqueNotDefined = subscribeUniqueChangesNotUndefined()
    subscribeWithFeature(store,
      {
        intialize: store => {
          if (countFeature.intialize) countFeature.intialize(store)
          if (uniqueNotDefined.intialize) uniqueNotDefined.intialize(store)
        },
        predicate: value => (uniqueNotDefined.predicate && countFeature.predicate && countFeature.predicate(value) && uniqueNotDefined.predicate(value)) || false,
        set: (value, unsubscribe) => {
          if (countFeature.set) countFeature.set(value, unsubscribe)
          if (uniqueNotDefined.set) uniqueNotDefined.set(value, unsubscribe)
        }
      },
      v => {
      resolve(v)
    })
  })
  return p
}