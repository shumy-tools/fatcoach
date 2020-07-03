import Vue from 'vue'

export interface MenuItem {
  text: string,
  active: boolean,
  route: string
}

export interface Menu {
  title: string
  icon?: string,
  items: MenuItem[]
}

export const EventBus = new Vue()
