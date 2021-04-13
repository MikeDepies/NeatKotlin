import type { AuthData, AuthFeature } from "@app/api/types/Auth"
import type { Writable } from "svelte/store"

export class ExpirationAuthFeature implements AuthFeature {
  authData?: Writable<AuthData | undefined>
  expirationHandlerTimeout: NodeJS.Timeout | undefined
  install(authData: Writable<AuthData | undefined>): void {
    this.authData = authData
  }
  handleExpiration() {
    this.authData?.set(undefined)
    this.expirationHandlerTimeout = undefined
  }
  set($authdata: AuthData) {
    if ($authdata) {
      const renewInterval = $authdata.expiration.getTime() - new Date().getTime()
      this.expirationHandlerTimeout = setTimeout(this.handleExpiration, renewInterval)
    }
  }
}
