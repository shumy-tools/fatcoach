<template>
  <textarea ref=ide></textarea>
</template>

<script lang="ts">
/* eslint-disable */
import CodeMirror from 'codemirror'
import 'codemirror/mode/javascript/javascript.js'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/icecoder.css'

import { Vue, Component, Prop } from 'vue-property-decorator'

@Component
export default class DSLView extends Vue {
  @Prop({ type: String, required: true }) readonly title!: string

  mounted () {
    const config: CodeMirror.EditorConfiguration = {
      tabSize: 2,
      lineNumbers: true,
      theme: 'icecoder',
      mode: 'javascript',
      extraKeys: {
        'Ctrl-Space': 'autocomplete'
      }
    }

    const ide = this.$refs.ide as HTMLTextAreaElement
    const editor = CodeMirror.fromTextArea(ide, config)

    editor.setValue('function myScript () { return 100; }\n')
  }
}
/* eslint-enable */
</script>

<style lang="sass">
.CodeMirror
  margin: 10px
  width: 100%
</style>
