export interface WebSocketFeature {
  onConnect?(ws : WebSocket) : void
  onClose?(ws : WebSocket) : void
  onError?(ws : WebSocket) : void
}