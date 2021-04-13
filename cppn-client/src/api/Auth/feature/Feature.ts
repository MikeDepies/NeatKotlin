import type { AuthData, AuthFeature } from "@app/api/types/Auth"
import { ExpirationAuthFeature } from "./ExpirationAuthFeature"

export const LocalStoreFeature: AuthFeature = {
  set: $authData => {
    if ($authData) {
      localStorage.setItem("auth", JSON.stringify($authData))
    }
    else {
      localStorage.removeItem("auth")
    }
  },
  get: () => {
    const storageAuthString = localStorage.getItem("auth")
    if (storageAuthString) {
      const parsedStorage: AuthData = JSON.parse(storageAuthString)
      parsedStorage.expiration = new Date(parsedStorage.expiration)
      if (!isExpired(parsedStorage)) {
        return parsedStorage
      }
    }
    else return undefined
  }
}

function isExpired(auth: AuthData) {
  const now = new Date().getTime()
  const expriationMilis = auth.expiration.getTime()
  return expriationMilis < now
}
export const ExpirationFeature = new ExpirationAuthFeature()
