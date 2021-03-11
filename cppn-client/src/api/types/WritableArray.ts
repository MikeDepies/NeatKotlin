
export type WritableArray<T> = {
  length() : number
  setElement(index: number, element : T) : void
  set(map: T[]): void
  push(value: T): void
  pushFront(element: T): void
  remove(element: T): void
  removeBy(predicate: (element: T) => boolean): void
  subscribe(run: (value: T[]) => void): () => void
}
