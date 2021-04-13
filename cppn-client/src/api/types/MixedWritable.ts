import type { Readable, Writable } from "svelte/store"


export type MixedWritable<R, W> = Pick<Readable<R>, "subscribe"> & Pick<Writable<W>, "set">
