<template>
  <div v-if="entity">
    <div class="text-h5 q-py-xs q-px-md bg-grey-2 border-left">
      Fields
    </div>
    <q-separator class="q-my-xs" />
    <div v-for="prop of entity.fields" :key="prop.name" class="row q-pa-xs q-gutter-xs">
      <q-input :value="prop.name" label="Name" square outlined :dense="true" class="col-2" />
      <q-select :value="prop.type" :options="fieldTypes" label="Type" square outlined :dense="true" class="col-2" />
      <q-checkbox :value="prop.input" label="Input"  class="col-1" />
      <q-checkbox :value="prop.optional" label="Optional" class="col-1" />
      <q-checkbox :value="prop.unique" label="Unique" class="col-1" />
      <q-btn-group outline>
        <q-btn icon="las la-tags" />
        <q-btn icon="lar la-trash-alt" class="text-red" />
      </q-btn-group>
    </div>

    <div class="text-h5 q-py-xs q-px-md bg-grey-2 border-left">
      References
    </div>
    <q-separator class="q-my-xs" />
    <div v-for="prop of entity.refs" :key="prop.name" class="row q-pa-xs q-gutter-xs">
      <q-input :value="prop.name" label="Name" square outlined :dense="true" class="col-2" />
      <q-select :value="prop.type" :options="relTypes" label="Type" square outlined :dense="true" class="col-2" />
      <q-select :value="prop.ref" :options="entities" label="Ref" square outlined :dense="true" class="col-2" />
      <q-btn flat icon="las la-angle-double-right" class="text-orange" @click="navigate(prop.ref)"/>

      <q-checkbox :value="prop.input" label="Input"  class="col-1" />
      <q-checkbox :value="prop.optional" label="Optional" class="col-1" />
      <q-btn-group outline>
        <q-btn icon="las la-tags" />
        <q-btn icon="lar la-trash-alt" class="text-red" />
      </q-btn-group>
    </div>

    <div class="text-h5 q-py-xs q-px-md bg-grey-2 border-left">
      Collections
    </div>
    <q-separator class="q-my-xs" />
    <div v-for="prop of entity.cols" :key="prop.name" class="row q-pa-xs q-gutter-xs">
      <q-input :value="prop.name" label="Name" square outlined :dense="true" class="col-2" />
      <q-select :value="prop.type" :options="relTypes" label="Type" square outlined :dense="true" class="col-2" />
      <q-select :value="prop.ref" :options="entities" label="Ref" square outlined :dense="true" class="col-2" />
      <q-btn flat icon="las la-angle-double-right" class="text-orange" @click="navigate(prop.ref)"/>

      <q-checkbox :value="prop.input" label="Input" class="col-1" />
      <q-btn-group outline>
        <q-btn icon="las la-tags" />
        <q-btn icon="lar la-trash-alt" class="text-red" />
      </q-btn-group>
    </div>

  </div>
</template>

<script lang="ts">
import { Vue, Component, Prop, Emit } from 'vue-property-decorator'
import { SEntity } from '../api/client'

@Component
export default class EntityView extends Vue {
  @Prop() entity?: SEntity

  entities = Object.keys(this.$state.schema.all) //Object.keys(this.$state.schema.all).map(key => ({ name: key, type: this.$state.schema.all[key].type }))

  relTypes = [ 'linked', 'owned' ]

  fieldTypes = [
    'text', 'int', 'long', 'float', 'double', 'bool',
    'time', 'date', 'datetime',
    'list', 'map'
  ]

  @Emit('navigate')
  navigate (ref: string) {
    console.log("REF: ", ref)
  }
}
</script>

<style lang="scss">
.border-left {
  border-left: $orange;
  border-left-width: 5px;
  border-left-style: solid;
}

.vertical-align-unset {
  vertical-align: unset !important;
}
</style>
