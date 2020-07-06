import Client from '../api/client'
import State from '../api/state'
import { boot } from 'quasar/wrappers'

declare module 'vue/types/vue' {
  interface Vue {
    $api: Client;
    $state: State;
  }
}

export default boot(async ({ Vue }) => {
  const api = new Client(error => {
    console.log('ERROR: ', error)
  })

  Vue.prototype.$api = api

  const loaded = await api.schema()
  console.log('Loaded Schema: ', loaded)
  Vue.prototype.$state = { schema: loaded } as State
})
