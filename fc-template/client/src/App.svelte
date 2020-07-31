<script lang="ts">
	import Client from './api'
	import CodeMirror from './comp/CodeMirror.svelte'
	
	const api = new Client('http://localhost:9090', error => {
		console.log('ERROR: ', error)
	})

	async function handleRun(event: CustomEvent) {
		const res = await api.exec(event.detail.cmd, event.detail.code)
		console.log("RESULT: ", res)
	}
</script>

<main class="flex justify-center items-center w-full">
	<CodeMirror on:run={handleRun} />
</main>