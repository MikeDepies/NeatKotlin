<script context="module" lang="ts">
	import { getBehaviors, getSettings, host } from '$lib/marioApi';
	import type { Settings } from 'src/type/marioInfo';
	import type { Load } from '@sveltejs/kit';

	export const load: Load = async ({}) => {
		return {
			props: {
				marios: await getBehaviors(1000),
				settings: await getSettings()
			}
		};
	};
</script>

<script lang="ts">
	import type { MarioInfo } from 'src/type/marioInfo';
	import { onMount } from 'svelte';
	import { fly, fade } from 'svelte/transition';

	let width: number;
	let height: number;
	const stage = [
		[
			{
				width: 3376,
				height: 480,
				image: 'mario-1-1-stage.png',
				offset: 0
			},
			{
				width: 3072,
				height: 720,
				image: 'mario-1-2-stage.png',
				offset: 240
			},
			{
				width: 2624,
				height: 240,
				image: 'mario-1-3-stage.png',
				offset: 0
			},
			{
				width: 2560,
				height: 240,
				image: 'mario-1-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3408,
				height: 720,
				image: 'mario-2-1-stage.png',
				offset: 240
			},
			{
				width: 3072,
				height: 480,
				image: 'mario-2-2-stage.png',
				offset: 240
			},
			{
				width: 3792,
				height: 240,
				image: 'mario-2-3-stage.png',
				offset: 0
			},
			{
				width: 3782,
				height: 240,
				image: 'mario-2-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3408,
				height: 720,
				image: 'mario-3-1-stage.png',
				offset: 240
			},
			{
				width: 2552,
				height: 240,
				image: 'mario-3-2-stage.png',
				offset: 0
			},
			{
				width: 2608,
				height: 240,
				image: 'mario-3-3-stage.png',
				offset: 0
			},
			{
				width: 2560,
				height: 240,
				image: 'mario-3-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3808,
				height: 480,
				image: 'mario-4-1-stage.png',
				offset: 0
			},
			{
				width: 3584,
				height: 720,
				image: 'mario-4-2-stage.png',
				offset: 240
			},
			{
				width: 2544,
				height: 240,
				image: 'mario-4-3-stage.png',
				offset: 0
			},
			{
				width: 3072,
				height: 240,
				image: 'mario-4-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3392,
				height: 480,
				image: 'mario-5-1-stage.png',
				offset: 0
			},
			{
				width: 3408,
				height: 720,
				image: 'mario-5-2-stage.png',
				offset: 240
			},
			{
				width: 2624,
				height: 240,
				image: 'mario-5-3-stage.png',
				offset: 0
			},
			{
				width: 2560,
				height: 240,
				image: 'mario-5-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3216,
				height: 240,
				image: 'mario-6-1-stage.png',
				offset: 0
			},
			{
				width: 3664,
				height: 720,
				image: 'mario-6-2-stage.png',
				offset: 240
			},
			{
				width: 2864,
				height: 240,
				image: 'mario-6-3-stage.png',
				offset: 0
			},
			{
				width: 2560,
				height: 240,
				image: 'mario-6-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 3072,
				height: 480,
				image: 'mario-7-1-stage.png',
				offset: 0
			},
			{
				width: 3072,
				height: 480,
				image: 'mario-7-2-stage.png',
				offset: 240
			},
			{
				width: 3792,
				height: 240,
				image: 'mario-7-3-stage.png',
				offset: 0
			},
			{
				width: 3584,
				height: 240,
				image: 'mario-7-4-stage.png',
				offset: 0
			}
		],
		[
			{
				width: 6224,
				height: 480,
				image: 'mario-8-1-stage.png',
				offset: 0
			},
			{
				width: 3664,
				height: 480,
				image: 'mario-8-2-stage.png',
				offset: 0
			},
			{
				width: 3664,
				height: 240,
				image: 'mario-8-3-stage.png',
				offset: 0
			},
			{
				width: 5120,
				height: 480,
				image: 'mario-8-4-stage.png',
				offset: 0
			}
		]
	];
	let worldIndex = 0;
	let stageIndex = 0;
	let useAutoStageRotation = false;
	$: {
		if (stageIndex > 3) {
			stageIndex = 0;
			worldIndex++;
		}
		if (stageIndex < 0) {
			stageIndex = 3;
			worldIndex--;
		}
		if (worldIndex > 7) {
			worldIndex = 0;
		}
		if (worldIndex < 0) {
			worldIndex = 7;
		}
	}
	$: actualWidth = stage[worldIndex][stageIndex].width; //3376;
	$: actualHeight = stage[worldIndex][stageIndex].height; //480;
	$: xRatio = width / actualWidth;
	$: yRatio = height / actualHeight;
	let updateNumber = 0;
	let marioDict: Record<string, number> = {};

	export let marios: MarioInfo[];
	let topMarios: MarioInfo[] = [];
	let map = new Map();
	$: {
		map = new Map();
		const result: MarioInfo[] = [];
		for (const item of [...filteredMarios].reverse()) {
			const key = marioInfoPositionString(item);
			if (!map.has(key)) {
				map.set(key, 1); // set any value to Map
				result.push({
					...item
				});
			} else {
				map.set(key, map.get(key) + 1);
			}
		}
		topMarios = result
			.sort((a, b) => {
				return (
					a?.xPos +
					a.stage * 10000 +
					a.world * 100_000 -
					(b?.xPos + b.stage * 10000 + b.world * 100_000)
				);
			})
			.slice(-20)
			.reverse();
	}
	function marioInfoPositionString(item: MarioInfo): string {
		return (
			item.xPos +
			' ' +
			item.yPos +
			' ' +
			item.stage +
			' ' +
			item.world +
			' ' +
			item.status +
			' ' +
			item.coins +
			' ' +
			item.score
		);
	}
	let filteredMarios = [];
	$: filteredMarios = marios
		.filter((m) => m.stage - 1 == stageIndex && m.world - 1 == worldIndex)
		.slice(-1000);

	let selectedMario: MarioInfo | null = null;
	function mouseOverMario(mario: MarioInfo) {
		console.log(mario.yPos);
		selectedMario = mario;
	}
	async function refresh() {
		let behaviors = (await getBehaviors(100)) as MarioInfo[];
		behaviors.forEach((b) => {
			if (marioDict[b.id] == undefined) {
				marios = [...marios, b];
			}
		});
		updateMarios(behaviors);
	}
	function updateMarios(behaviors: MarioInfo[]) {
		const mariosToRecord = marios.filter((m) => marioDict[m.id] == undefined);
		mariosToRecord.forEach((m) => {
			marioDict[m.id] = updateNumber;
		});
		if (mariosToRecord.length > 0) updateNumber++;
	}
	function getRGB(mario: MarioInfo, index: number) {
		const ratio = index / (filteredMarios.length + 1);
		const r = 0 + 255 * (mario.coins / maxCoins);
		const g = 0 + 255 * ratio;
		const b = 0 + 255 * scoreRatio(mario);
		return `rgb(${r},${g},${b})`;
	}
	onMount(() => {
		updateMarios(marios);
		setInterval(() => {
			refresh();
		}, 2000);
		setInterval(() => {
			if (useAutoStageRotation) stageIndex++;
		}, 6_000);
	});
	let useFilter = false;
	let updateNumberFilter = 0;
	function size(mario: MarioInfo) {
		return Math.floor(mario.time / 10) + 12;
	}
	let maxScore = filteredMarios.map((m) => m.score).reduce((a, b) => Math.max(a, b), 0.1);
	$: maxScore = filteredMarios.map((m) => m.score).reduce((a, b) => Math.max(a, b), 0.1);
	let maxCoins = filteredMarios.map((m) => m.coins).reduce((a, b) => Math.max(a, b), 0.1);
	$: maxCoins = filteredMarios.map((m) => m.coins).reduce((a, b) => Math.max(a, b), 0.1);
	let minTime = filteredMarios.map((m) => m.time).reduce((a, b) => Math.min(a, b), 400);
	$: minTime = filteredMarios.map((m) => m.time).reduce((a, b) => Math.min(a, b), 400);
	function scoreRatio(mario: MarioInfo) {
		return mario.score / maxScore;
	}

	function timeRatio(mario: MarioInfo) {
		return (mario.time - minTime) / (400 - minTime);
	}

	function recentGroup(mario: MarioInfo) {
		return (
			marioDict[mario.id] <= updateNumber - 1 &&
			marioDict[mario.id] >= updateNumber - 20 &&
			marioDict[mario.id] > 1
		);
	}
	export let settings: Settings;
	async function updateSettings() {
		console.log('update');

		let res = await fetch('/settings', {
			method: 'POST',
			headers: {
				'content-type': 'application/json'
			},
			body: JSON.stringify(settings)
		});
	}
</script>

<div
	bind:clientHeight={height}
	bind:clientWidth={width}
	class="w-full bg-contain bg-no-repeat relative"
>
	<img class="w-full" src={stage[worldIndex][stageIndex].image} alt="" />
	{#each filteredMarios as mario, index (mario)}
		<div
			in:fly={{ y: 200, duration: 2000 }}
			out:fade
			class="{mario.dstage != 0 || mario.dworld != 0 ? '' : ''} absolute {recentGroup(mario)
				? ' border-2 border-black'
				: 'border-white border'}"
			style="background: {getRGB(mario, index)}; opacity: {!recentGroup(mario)
				? Math.max(Math.min(index / filteredMarios.length / 10, 0.5), 0.2)
				: 1}; width: {size(mario) * xRatio}px; height: {size(mario) *
				yRatio *
				(mario.status == 'small' ? 1 : 2) *
				yRatio}px;  margin-top: -{(size(mario) / 2) *
				(mario.status == 'small' ? 1 : 2) *
				yRatio}px; top: {(272 - mario.yPos) * yRatio +
				stage[worldIndex][mario.stage - 1]?.offset * yRatio}px; left: {mario.xPos * xRatio}px;"
			on:mousemove={() => mouseOverMario(mario)}
		/>
	{/each}
</div>
<div>
	<div class="flex">
		<div>
			<div>
				World: <input type="number" bind:value={worldIndex} />
				Stage: <input type="number" bind:value={stageIndex} />
			</div>
			<div>
				<label for="autoRotate"> Auto Rotate </label>
				<input type="checkbox" bind:checked={useAutoStageRotation} id="autoRotate" />
			</div>
			<div>Threshold:</div>
			<div><input type="number" bind:value={settings.noveltyThreshold} /></div>
			<div><button on:click={updateSettings}>Update Settings</button></div>
			<div>Marios: {marios.length}</div>
			<div>Updates Performed: {updateNumber}</div>
			<div><button on:click={() => refresh()}>Refresh</button></div>
			<input type="checkbox" id="filterBySelection" bind:checked={useFilter} />
			<label for="filterBySelection">Filter By Update Number</label>
			<input type="number" bind:value={updateNumberFilter} />
			<div class="flex">
				<div>
					{#if selectedMario !== null}
						<div>x: {selectedMario.xPos}</div>
						<div>y: {selectedMario.yPos}</div>
						<div>stage: {selectedMario.stage}</div>
						<div>world: {selectedMario.world}</div>
						<div>score: {selectedMario.score}</div>
						<div>status: {selectedMario.status}</div>
						<div>time: {selectedMario.time}</div>
						<div>coins: {selectedMario.coins}</div>
						<div>id: {selectedMario.id}</div>
						<div>updateNumber: {marioDict[selectedMario.id]}</div>
					{/if}
				</div>
				
			</div>
		</div>
		<div>
			<ul>
				{#each topMarios as m}
					<li class="flex gap-x-4 border p-2">
						<div>x: {m.xPos}</div>
						<div>y: {m.yPos}</div>
						<div>stage: {m.stage}</div>
						<div>world: {m.world}</div>
						<div>score: {m.score}</div>
						<div>status: {m.status}</div>
						<div>time: {m.time}</div>
						<div>coins: {m.coins}</div>
						<div>flags: {m.flags}</div>
						<div>count: {map.get(marioInfoPositionString(m))}</div>
						<div>updateNumber: {marioDict[m.id]}</div>
					</li>
				{/each}
			</ul>
		</div>
	</div>
</div>
