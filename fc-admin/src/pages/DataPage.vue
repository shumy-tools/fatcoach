<template>
  <q-page class="row items-center justify-evenly">
    <table-view :entity="selected"></table-view>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component, Watch } from 'vue-property-decorator'
import { EventBus, Menu } from '../api/state'
import TableView from 'components/TableView.vue'

@Component({ components: { TableView } })
export default class DataPage extends Vue {
  selected?: any = null
  schema?: any = null

  @Watch('$route', { immediate: true, deep: true })
  onUrlChange () {
    this.updateSelected()
  }

  created () {
    this.$api.schema().then((schema: any) => {
      this.schema = schema
      const menu: Menu = {
        title: 'schema',
        icon: 'widgets',
        items: Object.keys(schema).map(entity => { return { text: entity, active: false, route: '/data/' + entity } })
      }

      EventBus.$emit('$menu', menu)
      this.updateSelected()
    })
  }

  private updateSelected() {
    const entity = this.$router.currentRoute.params.entity
    if (entity && this.schema) {
      const props = new Map(Object.entries(this.schema[entity]))
      this.selected = { name: entity, props: props }
      console.log(this.selected)
    }
  }
}
</script>
