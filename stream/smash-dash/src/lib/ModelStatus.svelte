<script lang="ts">
import { onMount } from "svelte";
import { fade } from "svelte/transition";

    
	export let modelId: string;
	export let kills: number;
	export let deaths: number;
	export let wins: number;
	export let losses: number;
    export let time: number;
    export let generation: number
    let timeLeft = Math.floor((new Date().getTime() - time) / 1000)
    onMount(() => {
        setInterval(() => {
            timeLeft = Math.floor((new Date().getTime() - time) / 1000)
        }, 50)
    })
</script>

<div class="flex flex-row space-x-2 relative">
    <div class="absolute -left-4">
        {timeLeft}
    </div>
    <!-- <div class="absolute h-2 inset-0 bg-red-700" style="width: {1 - (timeLeft / 30)}%;">

    </div> -->
	<div class="w-64">
		<div class="text-lg">Generation: {generation}</div>
        {#key modelId}
		<div class="break-words text-blue-300" in:fade>{modelId}</div>
        {/key}
	</div>
	<div class="grid grid-cols-4 w-full">
		<div class="text-gray-400 text-2xl">Wins:</div>
		<div class="text-gray-500 text-2xl font-bold">{wins}</div>
		
        <div class="text-gray-400 text-2xl">Kills:</div>
		<div class="text-gray-500 text-2xl font-bold">{kills}</div>
        <div class="text-gray-400 text-2xl">Losses:</div>
		<div class="text-gray-500 text-2xl font-bold">{losses}</div>
		
        <div class="text-gray-400 text-2xl">Deaths:</div>
		<div class="text-gray-500 text-2xl font-bold">{deaths}</div>
	</div>
</div>
