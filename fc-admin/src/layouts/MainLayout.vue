<template>
  <q-layout view="hHh lpR fFf" class="bg-grey-2">
    <q-header elevated class="bg-white text-grey-8 q-py-xs" height-hint="58">
      <q-toolbar>
        <q-btn color="grey" flat dense round @click="leftDrawerOpen = !leftDrawerOpen" aria-label="Menu" icon="menu" />

        <q-btn flat no-caps no-wrap class="q-ml-xs" v-if="$q.screen.gt.xs">
          <!--<q-icon :name="fabYoutube" color="red" size="28px" />-->
          <q-toolbar-title shrink class="text-weight-bold">
            FatCoach
          </q-toolbar-title>
        </q-btn>

        <q-tabs shrink class="text-weight-bold">
          <q-route-tab label="Data" class="text-orange" to="/data" />
        </q-tabs>

        <q-space />

        <div class="q-gutter-sm row items-center no-wrap">
          <q-btn round dense flat color="grey" icon="apps" v-if="$q.screen.gt.sm">
            <q-tooltip>Apps</q-tooltip>
          </q-btn>
          <q-btn round dense flat color="grey" icon="message" v-if="$q.screen.gt.sm">
            <q-tooltip>Messages</q-tooltip>
          </q-btn>
          <q-btn round dense flat color="grey" icon="notifications">
            <q-badge color="red" text-color="white" floating>
              2
            </q-badge>
            <q-tooltip>Notifications</q-tooltip>
          </q-btn>
          <q-btn round flat>
            <q-avatar size="26px">
              <img src="https://cdn.quasar.dev/img/boy-avatar.png">
            </q-avatar>
            <q-tooltip>Account</q-tooltip>
          </q-btn>
        </div>
      </q-toolbar>
    </q-header>

    <q-drawer v-model="leftDrawerOpen" show-if-above bordered :width="240" >
      <q-scroll-area class="fit">
        <q-list padding>
          <q-item-label header class="text-weight-bold text-uppercase">
            {{menu.title}}
          </q-item-label>

          <q-item v-for="item in menu.items" :key="item.text" :active="item.active" active-class="bg-grey-2 text-orange" clickable @click="route(item)">
            <q-item-section avatar>
              <q-icon color="orange" :name="menu.icon" />
            </q-item-section>
            <q-item-section>
              <q-item-label>{{ item.text }}</q-item-label>
            </q-item-section>
          </q-item>

          <q-separator class="q-my-md" />

        </q-list>
      </q-scroll-area>
    </q-drawer>

    <q-page-container>
      <div class="q-pa-md q-gutter-sm">
        <q-breadcrumbs>
          <template v-slot:separator>
            <q-icon name="chevron_right"/>
          </template>
          <q-breadcrumbs-el label="Home" icon="home" />
          <q-breadcrumbs-el v-for="item in crumbs" :key="item.text" :label="item.text" :icon="item.icon" />
        </q-breadcrumbs>
        <router-view />
      </div>
    </q-page-container>

  </q-layout>
</template>

<script lang="ts">
import { Vue, Component, Watch } from 'vue-property-decorator'
import { EventBus, Menu, MenuItem } from '../api/state'

@Component
export default class MainLayout extends Vue {
  leftDrawerOpen = false

  selected?: MenuItem = undefined
  crumbs: { text: string, icon?: string }[] = []
  menu: Menu = { title: 'empty', icon: undefined, items: [] }

  @Watch('$route', { immediate: true, deep: true })
  onUrlChange () {
    this.menu.items.forEach(item => item.active = false)
    if (this.selected)
      this.selected.active = true

    this.updateCrumbs()
  }

  route (item: MenuItem) {
    this.selected = item
    if (item.route !== this.$router.currentRoute.fullPath)
      this.$router.push({ path: item.route })
  }

  created () {
    EventBus.$on('$menu', ($menu: any) => {
      this.menu = $menu
      this.updateCrumbs()
    })
  }

  private updateCrumbs() {
    const path = this.$router.currentRoute.fullPath
    let list = path.split('/')
    list.shift()
    this.crumbs = list.map((item, index) => {
      const icon = (index == list.length - 1 ? this.menu.icon : undefined)
      return { text: item, icon: icon }
    })
  }
}
</script>
