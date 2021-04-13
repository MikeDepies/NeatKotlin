
export type SimpleMessage<T> = {
  topic: string,
  data: T
}
export type MessageWriter<WriteMap> = {
  write: <WriteKey extends Extract<keyof WriteMap, string>>(topic: WriteKey, data: WriteMap[WriteKey]) => void
}