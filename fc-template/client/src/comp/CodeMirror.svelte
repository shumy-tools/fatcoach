<script lang="ts">
	import { onMount } from 'svelte'
	import { createEventDispatcher } from 'svelte';

	import CodeMirror from 'codemirror'
	//import 'codemirror/mode/javascript/javascript.js'
    import 'codemirror/lib/codemirror.css'

    const dispatch = createEventDispatcher();

    let code: string
	let ide: HTMLElement

	let onRun = (event: KeyboardEvent) => {
        // on F9 press -> run code
		if(event.keyCode == 120) {
            const theCode = code.trimStart()

            let cmd = ''
            if (theCode.startsWith('create')) {
                cmd = 'create'
            } else if (theCode.startsWith('update')) {
                cmd = 'update'
            } else if (theCode.startsWith('delete')) {
                cmd = 'delete'
            } else if (theCode.startsWith('query')) {
                cmd = 'query'
            } else {
                console.log('Could not identify command! Requires one of (create, update, delete, query)!')
                return
            }
            
            const split = theCode.split(cmd)
            dispatch('run', { cmd, code: split[1] })
		}
	}

	document.addEventListener('keydown', onRun, false)

	onMount(async () => {
		const config: CodeMirror.EditorConfiguration = {
            tabSize: 2,
            lineNumbers: true,
            theme: 'default',
            mode: 'javascript',
            extraKeys: { 'Ctrl-Space': 'autocomplete' }
		}
		
		const editor = CodeMirror.fromTextArea(ide as HTMLTextAreaElement, config)
		editor.on('change', _ => code = editor.getValue())
	})
</script>

<div class="h-full w-1/2 border border-gray-300">
    <textarea bind:this={ide}></textarea>
</div>

<style>
:global(.CodeMirror) {
	height: 100%;
}

:global(.CodeMirror-sizer) {
    min-height: 200px !important;
	height: fit-content;
}
</style>