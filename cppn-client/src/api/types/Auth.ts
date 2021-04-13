import type { Writable } from "svelte/store"

export type AuthData = {
  accessToken: string,
  expiration: Date,
}


export interface AuthFeature {
  install?(authData: Writable<AuthData | undefined>): void
  uninstall?(authData: Writable<AuthData | undefined>): void
  set?($authData: AuthData | undefined): void
  get?(): AuthData | Promise<AuthData> | undefined
}

export type AcessTokenSessionData = {
  accessToken: string,
  scope: string,
  tokenType: "Bearer",
  expiresIn: number,
  appState: string,
  state: string
}