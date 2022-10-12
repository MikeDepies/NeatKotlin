<script context="module" lang="ts">
	export const load: Load = async ({ fetch }) => {
		const r = await fetch('/api/model?cid=0');
		const model1 = (await r.json()) as ModelController;
		const r2 = await fetch('/api/model?cid=1');
		const model2 = (await r2.json()) as ModelController;
		return {
			status: 200,
			props: {
                models: [model1]
            }
		};
	};
</script>

<script lang="ts">
	import ModelStatus from '$lib/ModelStatus.svelte';
	import type { ModelController } from '$lib/statStore';
	import type { Load } from '@sveltejs/kit';
	import { onDestroy, onMount } from 'svelte';
	export let models: ModelController[];
    let timeout
	onMount(async () => {
		timeout = setInterval(async () => {
			const r = await fetch('/api/model?cid=0');
			const model1 = (await r.json()) as ModelController;
			// const r2 = await fetch('/api/model?cid=1');
			// const model2 = (await r2.json()) as ModelController;
			models = [model1];
		}, 500);
		
	});
    onDestroy(() => {
			clearInterval(timeout);
		});
</script>

<div class="h-screen bg-black">
    <div class="flex pl-28 space-x-12 text-white bg-black">
        {#each models as model}
        <ModelStatus {...model} />
        {/each}
    </div>
    
</div>
