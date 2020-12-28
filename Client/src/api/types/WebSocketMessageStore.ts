import type { Readable } from "svelte/store"
import type { MixedWritable } from "./MixedWritable"


export type WebSocketMessageStore = MixedWritable<string | undefined, any>
