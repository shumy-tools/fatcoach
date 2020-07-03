<template>
  <div v-if="entity">
    <q-input v-for="[key, val] of entity.props" :key="key" :label="key" outlined :dense="true" />
  </div>
</template>

<script lang="ts">
import { Vue, Component, Prop } from 'vue-property-decorator'

interface SEntity {
  name: string,
  props: Map<string, any>
}

@Component
export default class TableView extends Vue {
  @Prop() entity?: SEntity
}
</script>
