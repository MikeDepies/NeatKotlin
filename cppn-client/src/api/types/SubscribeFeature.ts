import type { Readable } from "svelte/store"

export interface SubscribeFeature {
  predicate?<T>(storeValue: T): boolean,
  intialize?<T>(store: Readable<T>): void
  set?<T>(storeValue: T, unsubscribe: () => void): void
}

export interface SubscribeFeatureExperiment<TRANSFORM> {
  predicate?<T>(storeValue: T): boolean,
  intialize?<T>(store: Readable<T>): void
  set?<T>(storeValue: T, unsubscribe: () => void): void
}