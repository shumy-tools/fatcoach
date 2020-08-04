<script lang="ts">
	import Client from './api'
	import CodeMirror from './comp/CodeMirror.svelte'
	
	let result = ''

	const api = new Client(error => {
		console.log('ERROR: ', error)
		result = error
	})

	async function handleRun(event: CustomEvent) {
		let res = await api.exec(event.detail.cmd, event.detail.code)
		if (res !== undefined) {
			console.log("RESULT: ", res)
			result = JSON.stringify(res, null, 2)
		}
	}
</script>

<main class="flex justify-center items-center w-full mt-2">
	<div class="w-1/2">
		<CodeMirror on:run={handleRun} />
		<div class="block font-bold mt-2">Result:</div>
		<pre class="block bg-gray-100 mt-2">{result}</pre>
	</div>
</main>