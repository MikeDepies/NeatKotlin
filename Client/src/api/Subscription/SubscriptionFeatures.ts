import { getValue } from "./SubscriptionHelper"
import type { SubscribeFeature } from "../types/SubscribeFeature"

export function subscribeForCountFeature(numberOfChanges: number): SubscribeFeature {
  let setCount = 0
  return {
    intialize: store => {
      setCount = 0
    },
    set: (value, unsubscribe) => {
      setCount++
      if (setCount >= numberOfChanges) unsubscribe()
    }
  }
}
export function subscribeUniqueChangesNotUndefined(): SubscribeFeature {
  const { intialize, predicate: uniquedChanges, set } = subscribeUniqueChanges()
  const { predicate: notUndefined } = SubscribeNotUndefinedFeature
  return {
    intialize, set, predicate: value => {
      return (uniquedChanges && notUndefined && uniquedChanges(value) && notUndefined(value)) || false
    }
  }
}
export const SubscribeNotUndefinedFeature : SubscribeFeature  = {
  predicate: storeValue => storeValue !== undefined
}
export function subscribeUniqueChanges(): SubscribeFeature {
  let previousValue: any = undefined
  let firstValue: any
  let firstValueChecked = false
  return {
    intialize: store => {
      previousValue = getValue(store)
      firstValue = previousValue
    },
    predicate: storeValue => storeValue !== previousValue || (!firstValueChecked),
    set: () => (firstValueChecked = true)
  }
}