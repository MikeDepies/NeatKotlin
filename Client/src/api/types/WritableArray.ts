
export type WritableArray<T> = {
  set(map: T[]): void
  push(value: T): void
  pushFront(element: T): void
  remove(element: T): void
  removeBy(predicate: (element: T) => boolean): void
  subscribe(run: (value: T[]) => void): () => void
}
