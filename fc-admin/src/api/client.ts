import axios from 'axios'

/* eslint-disable */
export class FcClient {
  constructor(public readonly onError: (msg: string) => void) {
    axios.interceptors.response.use(response => {
      console.log('API-RESPONSE: ', response.data)
      return response
    }, error => this.onError(error.msg))

  }

  async schema () {
    const res = await axios.get('api/schema')
    if (res.data['@type'] !== 'ok') {
      this.onError(res.data.result)
    }

    return res.data.result
  }
}

export default FcClient
/* eslint-enable */
