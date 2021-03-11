import type { MixedWritable } from "./MixedWritable"

export type WebSocketInput = string | ArrayBuffer | SharedArrayBuffer | Blob | ArrayBufferView
export type WebSocketStore =  MixedWritable<WebSocket | undefined, WebSocketInput>

