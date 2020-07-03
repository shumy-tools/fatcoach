import Client from '../api/client'
import { boot } from 'quasar/wrappers'

declare module 'vue/types/vue' {
  interface Vue {
    $api: Client;
  }
}

export default boot(({ Vue }) => {
  Vue.prototype.$api = new Client(error => {
    console.log('ERROR: ', error)
  })
})
