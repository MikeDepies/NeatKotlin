// import { createAuth } from "@app/api/Auth.ts"
import { WEBSOCKET } from "@app/api/Config"
import { lift } from "@app/api/Store/nestedReadable"
import type { ManagedWebSocket } from "@app/api/types/ManagedWebSocket"
import type { WebSocketFeature } from "@app/api/WebSocket/WebSocketFeature"
import { derived, readable } from "svelte/store"
import { createManagedWebsocket } from "../api/WebSocket/WebSocketCore"

// export const auth = createAuth()
export const managedWebsocket = readable(createManagedWebsocket(WEBSOCKET, [createClientIdFeature("dashboard")]), start => {
  
})
export const webSocket = lift<ManagedWebSocket, "websocket">(managedWebsocket, "websocket")
export const message = lift<ManagedWebSocket, "message">(managedWebsocket, "message")

function createClientIdFeature(clientId: string): WebSocketFeature {
  return {
    onConnect: ws => {
      ws.send(JSON.stringify({ clientId }))
    }
  }
}

