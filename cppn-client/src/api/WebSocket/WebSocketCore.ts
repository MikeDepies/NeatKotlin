import type { WebSocketFeature } from "@app/api/WebSocket/WebSocketFeature"
import { writable } from "svelte/store"
import type { ManagedWebSocket } from "../types/ManagedWebSocket"
import type { WebSocketMessageStore } from "../types/WebSocketMessageStore"
import type { WebSocketStore } from "../types/WebSocketStore"
//Option to queue messages if websocket is not open yet?
//promisfy websocket store to write with
export function createManagedWebsocket(wsUrl: string, features: WebSocketFeature[] = []): ManagedWebSocket {
  const ws = new WebSocket(wsUrl)
  const _message = writable<string | undefined>(undefined)
  const _websocket = writable<WebSocket | undefined>(undefined)
  ws.onopen = (openEvent) => {
    features.forEach(f => {
      if (f.onConnect) f.onConnect(ws)
    })
    ws.onmessage = (msgEvent: MessageEvent<string>) => {
      _message.set(msgEvent.data)
    }
    ws.onclose = (closeEvent) => {
      _websocket.set(undefined)
      _message.set(undefined)
      features.forEach(f => {
        if (f.onClose) f.onClose(ws)
      })
    }
    ws.onerror = (errorEvent) => {
      _websocket.set(undefined)
      _message.set(undefined)
      features.forEach(f => {
        if (f.onError) f.onError(ws)
      })
    }
    _websocket.set(ws)
  }
  const websocket: WebSocketStore = {
    subscribe: _websocket.subscribe,
    set(value: string | ArrayBuffer | SharedArrayBuffer | Blob | ArrayBufferView) {
      ws.send(value)
    }
  }
  const message: WebSocketMessageStore = {
    subscribe: _message.subscribe,
    set<T>(value: T) {
      websocket.set(JSON.stringify(value))
    }
  }
  return {
    message,
    websocket
  }
}

