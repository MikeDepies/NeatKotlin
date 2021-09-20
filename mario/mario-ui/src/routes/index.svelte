<script context="module" lang="ts">
	import { getBehaviors } from '$lib/marioApi';

	import type { Load } from '@sveltejs/kit';

	export const load: Load = async ({}) => {
		return {
			props: {
				marios: (await getBehaviors())
			}
		};
	};
</script>

<script lang="ts">
	import type { MarioInfo } from 'src/type/marioInfo';
import { onMount } from 'svelte';

	let width: number;
	let height: number;
	const actualWidth = 3376;
	const actualHeight = 480;
	$: xRatio = width / actualWidth;
	$: yRatio = height / actualHeight;
    let updateNumber = 0
    let marioDict : Record<string, number>= {}
	function getMarioInfo(x: number, y: number): MarioInfo {
		return {
			coins: 0,
			id: 'test',
			score: 0,
			stage: 0,
			status: 'small',
			time: 0,
			world: 0,
			x_pos: x,
			y_pos: 261 - y
		};
	}
	export let marios: MarioInfo[];
    updateMarios()
	let selectedMario: MarioInfo | null = null;
	function mouseOverMario(mario: MarioInfo) {
		console.log(mario.y_pos);
		selectedMario = mario;
	}
    async function refresh() {
        marios = await getBehaviors()
        updateMarios()
    }
    function updateMarios() {
        const mariosToRecord = marios.filter(m => marioDict[m.id] == undefined);
        mariosToRecord.forEach(m => {
            marioDict[m.id] = updateNumber
        })
        if (mariosToRecord.length > 0)
            updateNumber++;
    }
    function getRGB(mario : MarioInfo) {
        const ratio = marioDict[mario.id] / updateNumber
        const r = 0 + (255 * ratio)
        const g = 100
        const b = 100
        return `rgb(${r},${g},${b})`
    }
    onMount(() => {
        setInterval(() => {
            refresh()
            
        }, 10000)
    })
    let useFilter = false
    let updateNumberFilter = 0
</script>

<div
	bind:clientHeight={height}
	bind:clientWidth={width}
	class="w-full bg-contain bg-no-repeat relative"
	style="background-image: url(mario-1-1-stage.png);"
>
	<img src="mario-1-1-stage.png" alt="" />
	{#each marios.filter(m => !useFilter || marioDict[m.id] == updateNumberFilter) as mario}
		<div
			class="w-2 h-2  absolute"
			style="background: {getRGB(mario)};top: {mario.y_pos * yRatio}px; left: {mario.x_pos * xRatio}px;"
			on:mousemove={() => mouseOverMario(mario)}
		/>
	{/each}
</div>
<div>
    <div>Marios: {marios.length}</div>
    <div>Updates Performed: {updateNumber}</div>
    <div><button on:click="{() => refresh()}">Refresh</button></div>
    <input type="checkbox" id="filterBySelection" bind:checked="{useFilter}">
    <label for="filterBySelection">Filter By Update Number</label>
    <input type="number" bind:value={updateNumberFilter}>
    
	{#if selectedMario !== null}
		<div>x: {selectedMario.x_pos}</div>
		<div>y: {selectedMario.y_pos}</div>
        <div>stage: {selectedMario.stage}</div>
        <div>world: {selectedMario.world}</div>
        <div>score: {selectedMario.score}</div>
        <div>status: {selectedMario.status}</div>
        <div>id: {selectedMario.id}</div>
        <div>updateNumber: {marioDict[selectedMario.id]}</div>
	{/if}
</div>
