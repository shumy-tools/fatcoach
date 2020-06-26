<template>
  <q-page class="row items-center justify-evenly">
    <dslview title="Example component" active></dslview>
  </q-page>
</template>

<script lang="ts">
import { Vue, Component } from 'vue-property-decorator'
import dslview from 'components/DSLView.vue'

@Component({ components: { dslview } })
export default class PageIndex extends Vue {

}
</script>
