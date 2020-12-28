import { createAuth } from "@app/api/Auth.ts"
import { WEBSOCKET } from "@app/api/Config"
import { lift } from "@app/api/Store/nestedReadable"
import type { ManagedWebSocket } from "@app/api/types/ManagedWebSocket"
import type { WebSocketFeature } from "@app/api/WebSocket/WebSocketFeature"
import { derived } from "svelte/store"
import { createManagedWebsocket } from "../api/WebSocket/WebSocketCore"

export const auth = createAuth()
export const managedWebsocket = derived(auth.authData, (authData, set : (v : ManagedWebSocket | undefined) => void) => {
  if (authData) {
    const AuthWebSocketFeature = createAuthWebSocketFeature(authData.accessToken)
    const ws = createManagedWebsocket(WEBSOCKET, [AuthWebSocketFeature])
    set(ws)
  } else {
    set(undefined)
  }
}, undefined)
export const webSocket = lift<ManagedWebSocket, "websocket">(managedWebsocket, "websocket")
export const message = lift<ManagedWebSocket, "message">(managedWebsocket, "message")
function createAuthWebSocketFeature(token: string): WebSocketFeature {
  return {
    onConnect: ws => {
      ws.send(JSON.stringify({ token }))
    }
  }
}
