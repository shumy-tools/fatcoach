import axios from 'axios'

export interface Schema {
  all: {[key: string]: SEntity}
  masters: string[]
}

export interface SEntity {
  name: string
  type: string
  fields: {[key: string]: SField}
  refs: {[key: string]: SReference}
  cols: {[key: string]: SCollection}
}

export interface SField {
  name: string
  type: string
  optional: boolean
  input: boolean
  unique: boolean
}

export interface SReference {
  name: string
  type: string
  ref: string
  optional: boolean
  input: boolean
}

export interface SCollection {
  name: string
  type: string
  ref: string
  input: boolean
}

export class FcClient {
  constructor(public readonly onError: (msg: string) => void) {
    axios.interceptors.response.use(response => {
      console.log('API-RESPONSE: ', response.data)
      return response
    }, error => this.onError(error.msg))

  }

  async schema (): Promise<Schema> {
    const res = await axios.get('api/schema')
    if (res.data['@type'] !== 'ok') {
      this.onError(res.data.result)
    }

    return res.data.result
  }
}

export default FcClient
