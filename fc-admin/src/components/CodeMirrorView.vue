<template>
  <div class="full-height">
    <textarea ref=ide hidden></textarea>
  </div>
</template>

<script lang="ts">
/* eslint-disable */
import CodeMirror from 'codemirror'
import 'codemirror/mode/javascript/javascript.js'
import 'codemirror/lib/codemirror.css'
//import 'codemirror/theme/icecoder.css'

import { Vue, Component, Prop, Model } from 'vue-property-decorator'

@Component
export default class CodeMirrorView extends Vue {
  @Prop({ required: true }) readonly mode!: string
  @Model('code') readonly code!: string

  mounted () {
    const thisMode = (this.mode == 'json') ? 'javascript' : this.mode
    const config: CodeMirror.EditorConfiguration = {
      tabSize: 2,
      lineNumbers: true,
      theme: 'default',
      mode: thisMode,
      extraKeys: { 'Ctrl-Space': 'autocomplete' }
    }

    const ide = this.$refs.ide as HTMLTextAreaElement
    const editor = CodeMirror.fromTextArea(ide, config)
    editor.on('change', _ => this.$emit('code', editor.getValue()))

    if (this.code)
      editor.setValue(this.code)

    console.log('Select: ', document.querySelector('CodeMirror-sizer'))
  }
}
/* eslint-enable */
</script>

<style lang="scss">
.CodeMirror {
  height: 100%;
}

.CodeMirror-sizer {
  min-height: 198px !important;
  height: fit-content;
}
</style>