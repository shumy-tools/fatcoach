<template>
  <q-page>
    <q-breadcrumbs class="q-pt-md q-px-md q-pb-xs">
      <template v-slot:separator>
        <q-icon name="chevron_right"/>
      </template>
      <q-breadcrumbs-el label="Home" icon="home" />
      <q-breadcrumbs-el label="Model" />
      <q-breadcrumbs-el v-for="(item, index) in $state.crumbs" :key="item" :label="item" icon="widgets" :class="{ 'cursor-pointer': (index != $state.crumbs.length - 1) }" clickable @click="navigate(item)" />
    </q-breadcrumbs>

    <q-card class="q-ma-md no-border-radius" style="height: calc(100vh - (58px + 41px + 2*16px))">
      <q-tabs v-model="tab" dense class="text-grey text-weight-bold" active-color="orange" indicator-color="orange" align="justify">
          <q-tab name="properties" label="Properties" />
          <q-tab name="alarms" label="Alarms" />
          <q-tab name="movies" label="Movies" />
      </q-tabs>
      <q-separator />

      <q-scroll-area style="height: calc(100vh - 168px)">
        <q-tab-panels v-model="tab" class="no-border-radius" animated>
          <q-tab-panel name="properties">
            <entity-view :entity="entity" @navigate="navigate"/>
          </q-tab-panel>

          <q-tab-panel name="alarms">
            <div class="text-h6">Alarms</div>
            Lorem ipsum dolor sit amet consectetur adipisicing elit.
          </q-tab-panel>

          <q-tab-panel name="movies">
            <div class="text-h6">Movies</div>
            Lorem ipsum dolor sit amet consectetur adipisicing elit.
          </q-tab-panel>
        </q-tab-panels>
      </q-scroll-area>
    </q-card>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator'
import EntityView from 'components/EntityView.vue'
import { SEntity } from '../api/client'

@Component({ components: { EntityView } })
export default class EntityPage extends Vue {
  tab = 'properties'
  entity?: SEntity

  navigate(ref: string) {
    const path = '/model/entity/' + ref
    if (path !== this.$router.currentRoute.fullPath) {
      this.$router.push('/model/entity/' + ref)
    }
  }

  created () {
    const entity = this.$router.currentRoute.params.id
    this.entity = this.$state.schema.all[entity]
    
    this.$state.crumbs = []
    let back = entity
    let ent = this.$state.schema.all[back]
    while (ent) {
      this.$state.crumbs.push(back)
      back = ent.refs['@parent']?.ref
      ent = this.$state.schema.all[back]
    }

    this.$state.crumbs.reverse()
  }
}
</script>
