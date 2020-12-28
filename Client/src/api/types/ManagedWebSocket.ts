import type { WebSocketMessageStore } from "./WebSocketMessageStore"
import type { WebSocketStore } from "./WebSocketStore"


export type ManagedWebSocket = {
  websocket: WebSocketStore
  message: WebSocketMessageStore
}
