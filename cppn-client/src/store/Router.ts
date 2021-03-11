import { createRouteStore } from 'svelte-store-router'

export const route = createRouteStore({
  delay: 300,
  queryClean: true,
  fragmentClean: true
})

