<template>
  <div id="q-app">
    <router-view />
  </div>
</template>

<script>
/* eslint-disable */
export default {
  name: 'App',

  created () {
    this.$q.dark.set(true)
  }
}
/* eslint-enable */
</script>
