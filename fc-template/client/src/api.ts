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
    url = ''

    constructor(readonly onError: (msg: string) => void) {
        axios.interceptors.response.use(response => {
            console.log('API-RESPONSE: ', response.data)
            if (response.data['@type'] == 'error') {
                console.log('API-ERROR: ', response.data.msg)
                this.onError(response.data.msg)
            }

            return response
        }, error => this.onError(error.msg))
    }

    async schema(): Promise<Schema> {
        const res = await axios.get(this.url + '/api/schema')
        return res.data.result
    }

    async exec(cmd: 'create' | 'update' | 'delete' | 'query', code: string, args?: any): Promise<any> {
        switch (cmd) {
            case 'create': return this.create(code, args)
            case 'update': return this.update(code, args)
            case 'delete': return this.delete(code, args)
            case 'query': return this.query(code, args)
        }
    }

    async create(code: string, args?: any): Promise<any> {
        const res = await axios.post(this.url + '/api/create', { code, args })
        return res.data.result
    }

    async update(code: string, args?: any): Promise<any> {
        const res = await axios.post(this.url + '/api/update', { code, args })
        return res.data.result
    }

    async delete(code: string, args?: any): Promise<any> {
        const res = await axios.post(this.url + '/api/delete', { code, args })
        return res.data.result
    }

    async query(code: string, args?: any): Promise<any> {
        const res = await axios.post(this.url + '/api/query', { code, args })
        return res.data.result
    }
}

export default FcClient