import type { MessageWriter, SimpleMessage } from "@app/api/types/MessageRouter"
import { derived, Readable } from "svelte/store"

export type MessageReader<RouteMap> = {
  read: <RouteKey extends Extract<keyof RouteMap, string>> (topic: RouteKey) => Readable<RouteMap[RouteKey] | undefined>
  readWithDefault: <RouteKey extends Extract<keyof RouteMap, string>> (topic: RouteKey, value: any) => Readable<RouteMap[RouteKey]>
}


export function reader<T extends {} = any>(message: Readable<string | undefined>): MessageReader<T> {
  return {
    read<RouteKey extends Extract<keyof T, string>>(topic: RouteKey) {
      const derivied = derived(message, ($message: string | undefined, set: (x: T[RouteKey]) => void) => {
        if ($message) {
          const data: SimpleMessage<T[RouteKey]> = JSON.parse($message)
          if (data.topic === topic) {
            set(data.data)
          }
        }
      })
      return derivied
    },
    readWithDefault<RouteKey extends Extract<keyof T, string>>(topic: RouteKey, value: T[RouteKey]) {
      const derivied = derived(message, ($message: string | undefined, set: (x: T[RouteKey]) => void) => {
        if ($message) {
          const data: SimpleMessage<T[RouteKey]> = JSON.parse($message)
          if (data.topic === topic) {
            set(data.data)
          }
        }
      }, value)
      return derivied
    }
  }
}
export function writer<WriteMap extends {}>(message: { set<T>(value: T): void }): MessageWriter<WriteMap> {
  return {
    write: <WriteKey extends Extract<keyof WriteMap, string>>(subject: WriteKey, data: WriteMap[WriteKey]) => {
      message.set({ subject, data })
    }
  }
}