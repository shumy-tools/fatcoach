<template>
  <div>
    <q-drawer show-if-above bordered :width="240" >
      <q-scroll-area class="fit">
        <q-list padding>
          <q-item-label header class="text-weight-bold text-uppercase">
            DATA
          </q-item-label>

          <q-separator class="q-my-md" />

          <q-item-label header class="text-weight-bold text-uppercase">
            SCHEMA
          </q-item-label>

          <q-item v-for="item of menu" :key="item.entity" :active="item == selected" active-class="bg-grey-2 text-orange" clickable @click="select(item)">
            <q-item-section avatar>
              <q-icon color="orange" name="widgets" />
            </q-item-section>
            <q-item-section>
              <q-item-label>{{ item.entity }}</q-item-label>
            </q-item-section>
          </q-item>

          <q-separator class="q-my-md" />

        </q-list>
      </q-scroll-area>
    </q-drawer>

    <q-page-container>
      <router-view :key="$route.fullPath"/>
    </q-page-container>
  </div>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator'

interface MenuItem {
  entity: string,
  route: string
}

@Component
export default class ModelLayout extends Vue {
  selected: MenuItem | null = null
  menu: MenuItem[] = []

  select (item: MenuItem) {
    this.selected = item
    if (item.route !== this.$router.currentRoute.fullPath){
      this.$router.push(item.route)
      this.$state.crumbs = [item.entity]
    }
  }

  created () {
    this.menu = this.$state.schema.masters.map(it => ({ entity: it, route: '/model/entity/' + it }))
  }
}
</script>