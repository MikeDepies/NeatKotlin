export function isPromise<T>(promise: Promise<T> | T): promise is Promise<T> {
  return !!promise && typeof (promise as Promise<T>).then === 'function'
}