import { AUTH0_CLIENT, AUTH0_DOMAIN, REDIRECT_URL } from "@app/api/Config"
import type { Auth0Error, Auth0UserProfile, } from "auth0-js"
import { WebAuth } from 'auth0-js'
import { derived } from "svelte/store"
import { ExpirationFeature, LocalStoreFeature } from "./Auth/feature/Feature"
import { delegate } from "./DelegateStore"
import { isPromise } from "./Promise"
import type { AcessTokenSessionData, AuthData, AuthFeature } from "./types/Auth"

export function createAuth(features: AuthFeature[] = [ExpirationFeature, LocalStoreFeature]) {
  const webAuth = new WebAuth({
    domain: AUTH0_DOMAIN,
    clientID: AUTH0_CLIENT,
    redirectUri: REDIRECT_URL,
    responseType: "token",
    scope: "openid profile email"
  })
  const authData = delegate<AuthData | undefined>(undefined, {
    set: (value) => {
      features.forEach(feature => { if (feature.set) feature.set(value) })
    }
  })

  const newData = getAuthData(webAuth, features)
  if (isPromise(newData)) {
    newData.then(authData.set).catch(console.error)
  } else {
    authData.set(newData)
  }

  return {
    authData: { subscribe: authData.subscribe },
    isAuthenticated: derived(authData, ($authData: AuthData | undefined, set: (value: boolean) => void) => {
      set($authData !== undefined)
    }, false),
    userInfo: derived(authData, ($authData: AuthData | undefined, set: (value: Auth0UserProfile) => void) => {
      function updateUserInfo() {
        if ($authData) {
          webAuth.client.userInfo($authData.accessToken, (error, result) => {
            if (error) {
              console.error(error)
            } else {
              set(result)
            }
          })
        }
      }
      updateUserInfo()
    }, undefined),
    redirectAndAuthorize: () => {
      webAuth.authorize()
    },
    logout: () => {
      localStorage.clear()
      webAuth.logout({})
    }
  }
}

function getAuthData(webAuth: WebAuth, features: AuthFeature[]): AuthData | Promise<AuthData> {
  for (let index = 0; index < features.length; index++) {
    const feature = features[index]
    if (feature.get) {
      const webAuth = feature.get()
      if (webAuth) return webAuth
    }
  }
  //fallback
  return new Promise<AuthData>((resolve, reject) => {
    webAuth.checkSession({
      responseType: "token"
    }, (error: Auth0Error | null, result: AcessTokenSessionData) => {
      if (error) {
        reject(error)
      } else {
        const miliInSeconds = 1_000
        const newAuthData: AuthData = {
          accessToken: result.accessToken,
          expiration: new Date(new Date().getTime() + miliInSeconds * result.expiresIn),
        }
        resolve(newAuthData)
      }
    })
  })
}
