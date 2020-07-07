<template>
  <q-page>
    <q-breadcrumbs class="q-pt-md q-px-md q-pb-xs">
      <template v-slot:separator>
        <q-icon name="chevron_right"/>
      </template>
      <q-breadcrumbs-el label="Home" icon="home" />
      <q-breadcrumbs-el label="Model" />
      <q-breadcrumbs-el label="Query" icon="las la-search" />
    </q-breadcrumbs>

    <q-card class="q-ma-md q-pa-md no-border-radius" style="height: calc(100vh - (58px + 41px + 2*16px))">
      <q-toolbar class="q-my-xs bg-grey-2 border-left">
        <q-toolbar-title>
          Query via WebDSL
        </q-toolbar-title>
        <q-btn flat dense class="text-primary" icon="las la-question-circle"/>
        <q-btn flat dense class="text-primary" icon="las la-play" @click="run()"/>
      </q-toolbar>

      <q-scroll-area class="border" style="height: 200px">
        <code-mirror-view mode="javascript" v-model="query"></code-mirror-view>
      </q-scroll-area>

      <div class="q-my-xs border">
        <q-tabs v-model="tab" dense class="text-grey text-weight-bold" active-color="orange" indicator-color="orange" align="justify">
          <q-tab name="ui" label="UI Result View" />
          <q-tab name="json" label="JSON Result View" />
        </q-tabs>
        <q-separator />

        <q-scroll-area style="height: calc(100vh - 460px)">
          <q-tab-panels v-model="tab" class="no-border-radius" animated>
            <q-tab-panel name="ui" class="row no-wrap">
              <div class="q-pa-md q-gutter-sm">
                <div v-if="!result" class="text-center text-weight-bold text-uppercase">No results ...</div>
                <q-tree v-if="result" :nodes="result" node-key="label" default-expand-all/>
              </div>
            </q-tab-panel>
            <q-tab-panel name="json">
              <div v-if="!result" class="text-center text-weight-bold text-uppercase">no results ...</div>
              <code-mirror-view v-if="result" mode="json"></code-mirror-view>
            </q-tab-panel>
          </q-tab-panels>
        </q-scroll-area>
      </div>
    </q-card>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator'
import CodeMirrorView from 'components/CodeMirrorView.vue'

@Component({ components: { CodeMirrorView } })
export default class QueryPage extends Vue {
  tab = 'ui'
  query = 'User | @id == 1 | { * }'

  result = [
    {
      label: 'Satisfied customers (with avatar)',
      avatar: 'https://cdn.quasar.dev/img/boy-avatar.png',
      children: [
        {
          label: 'Good food (with icon)',
          icon: 'restaurant_menu',
          children: [
            { label: 'Quality ingredients' },
            { label: 'Good recipe' }
          ]
        },
        {
          label: 'Good service (disabled node with icon)',
          icon: 'room_service',
          disabled: true,
          children: [
            { label: 'Prompt attention' },
            { label: 'Professional waiter' }
          ]
        },
        {
          label: 'Pleasant surroundings (with icon)',
          icon: 'photo',
          children: [
            {
              label: 'Happy atmosphere (with image)',
              img: 'https://cdn.quasar.dev/img/logo_calendar_128px.png'
            },
            { label: 'Good table presentation' },
            { label: 'Pleasing decor' }
          ]
        },
        {
          label: 'Pleasant surroundings (with icon copy)',
          icon: 'photo',
          children: [
            {
              label: 'Happy atmosphere (with image)',
              img: 'https://cdn.quasar.dev/img/logo_calendar_128px.png'
            },
            { label: 'Good table presentation' },
            { label: 'Pleasing decor' }
          ]
        }
      ]
    }
  ]

  async run () {
    console.log(this.query)
    const result = await this.$api.query(this.query, { 'id': 1 })
    console.log(result)
  }

  created () {

  }
}
</script>

<style lang="scss">
.border {
  border-style: solid;
  border-width: 1px;
  border-color: $grey-4;
}
</style>