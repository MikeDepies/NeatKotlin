import { host } from "$lib/marioApi"
import type { RequestHandler } from "@sveltejs/kit"

export const post : RequestHandler = async ({body}) => {
  const res = await fetch( host + ":8094/settings", {
    method: "POST",
    headers: {
      "content-type": "application/json"
    },
    body: JSON.stringify(body),
    
  })
  return {
    status: 200
  }
}