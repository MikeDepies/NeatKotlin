<script context="module" lang="ts">
	import { getBehaviors, getSettings, host } from '$lib/marioApi';
	import type { Settings } from "src/type/marioInfo"
	import type { Load } from '@sveltejs/kit';
	
	export const load: Load = async ({}) => {
		return {
			props: {
				marios: (await getBehaviors(1000)),
				settings: (await getSettings())
			}
		};
	};
</script>

<script lang="ts">
	import type { MarioInfo } from 'src/type/marioInfo';
import { onMount } from 'svelte';

	let width: number;
	let height: number;
	const actualWidth = 3072;//3376;
	const actualHeight = 720;//480;
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
    
	let selectedMario: MarioInfo | null = null;
	function mouseOverMario(mario: MarioInfo) {
		console.log(mario.y_pos);
		selectedMario = mario;
	}
    async function refresh() {
			let behaviors = await getBehaviors(100) as MarioInfo[]
			behaviors.forEach(b => {
				if (marioDict[b.id] == undefined) {
					marios = [...marios, b]
				}
			})
        updateMarios(behaviors)
    }
    function updateMarios(behaviors : MarioInfo[]) {
        const mariosToRecord = marios.filter(m => marioDict[m.id] == undefined);
        mariosToRecord.forEach(m => {
            marioDict[m.id] = updateNumber
        })
        if (mariosToRecord.length > 0)
            updateNumber++;
    }
    function getRGB(mario : MarioInfo, index : number) {
        const ratio = index / (marios.length + 1)
        const r = 50 + mario.stage * 60
        const g = 0 + (255 * ratio)
        const b = 0 + (255 * scoreRatio(mario))
        return `rgb(${r},${g},${b})`
    }
    onMount(() => {
			updateMarios(marios)
        setInterval(() => {
            refresh()
            
        }, 2000)
    })
    let useFilter = false
    let updateNumberFilter = 0
		function size(mario : MarioInfo) {
			return Math.floor(timeRatio(mario) * 16) + 12
		}
		let maxScore = marios.map(m => m.score).reduce((a,b) => Math.max(a,b), .1)
		$: maxScore = marios.map(m => m.score).reduce((a,b) => Math.max(a,b), .1)
		let minTime = marios.map(m => m.time).reduce((a,b) => Math.min(a,b), 400)
		$: minTime = marios.map(m => m.time).reduce((a,b) => Math.min(a,b), 400)
		function scoreRatio(mario : MarioInfo) {
			return mario.score / maxScore
		}

		function timeRatio(mario : MarioInfo) {
			return (mario.time - minTime) / (400 - minTime)
		}
		
		function recentGroup(mario : MarioInfo) {
			return (marioDict[mario.id] <= updateNumber -1 && marioDict[mario.id] >= updateNumber -20 && marioDict[mario.id] > 1)
		}
		export let settings : Settings
		async function updateSettings() {
			console.log("update");
			
			let res = await fetch( "/settings", {
				method: "POST",
				headers: {
					"content-type": "application/json"
				},
				body: JSON.stringify(settings),
				
			})
			
		}
</script>

<div
	bind:clientHeight={height}
	bind:clientWidth={width}
	class="w-full bg-contain bg-no-repeat relative"
	style="background-image: url(mario-1-1-stage.png);"
>
	<img src="mario-1-2-stage.png" alt="" />
	{#each marios.filter(m => !useFilter || marioDict[m.id] == updateNumberFilter) as mario, index (mario)}
		<div
			class="absolute {recentGroup(mario) ? "animate-bounce border-2 border-black" : ""}"
			style="background: {getRGB(mario, index)}; opacity: {(!recentGroup(mario)) ? Math.max(index / marios.length / 10, .5) : 1}; width: {size(mario)}px; height: {size(mario) * (mario.status == "small" ? 1 : 2)}px;  margin-top: -{size(mario)/2  * (mario.status == "small" ? 1 : 2)}px; top: {mario.y_pos * yRatio + 210 * (mario.stage - 1) }px; left: {mario.x_pos * xRatio}px;"
			on:mousemove={() => mouseOverMario(mario)}
		/>
	{/each}
</div>
<div>
		<div>Threshold: </div>
		<div><input type="number" bind:value="{settings.noveltyThreshold}"></div>
		<div><button on:click="{updateSettings}">Update Settings</button></div>
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
