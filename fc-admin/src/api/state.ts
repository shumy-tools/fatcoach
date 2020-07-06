import Vue from 'vue'
import { Schema, SEntity } from './client'

export interface State {
  schema: Schema,
  crumbs: string[]
}

export const EventBus = new Vue()

export default State


