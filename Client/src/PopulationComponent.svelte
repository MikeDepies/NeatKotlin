<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';
import * as Pancake from '@sveltejs/pancake';
import { fly, crossfade } from 'svelte/transition';
import ScoreChart from './ScoreChart.svelte';
import Stat from './Stat.svelte';
import {getColorMap, getColor, rgbColorString} from "./store/ColorMapStore"
import type { AgentModel, Population, SimulationEndpoints } from './api/types/Evaluation';
import type { Series } from './api/types/Series';
import { prevent_default } from 'svelte/internal';
import { countUnique } from './api/Evaluation';
import { evaluationPopulation } from './store/SimulationStore';
  /*
  Current Generation:
  Population Size:
  Species:
  Top X Species:
  Species Summary
  Population Summary
  Simulation Summary

  Active Agent Evaluation:
    Agent Network Visualizer and Activation
    Clocks (as bar graphs?)
    Score Breakdown
    Total Score
  */
export let evaluationId : number
export let controllerIds : number[]
const colorMap = getColorMap("species")
const { controllerMap,pop,generation,popHistory,scores,size  } =evaluationPopulation(evaluationId, controllerIds);
$: numberOfSpecies = countUnique($pop?.agents?.map(p => p.species) || [])
$: activeAgents = ($controllerMap ? [...$controllerMap.values()] : [])
let data : {x : number, y: number, color: string }[]= []
let highestPopulationScore = 0
$: {
  const agents = $pop?.agents || []
  const populationScoreHistory = $scores
  if (populationScoreHistory) {
    let i = 0
    highestPopulationScore=0
    data = populationScoreHistory.slice(0, $size).map(s => {
      let index =i++
      let species = agents[index].species
      let color=`rgb(${getColor(species, $colorMap).join(",")})`
      return {x : index, y: s || 0, color: color}
    })
    for(let score of data) {
      if (highestPopulationScore < score.y) {
        highestPopulationScore = score.y
      }
    }
  }
}
  </script>
<div>
  <div class="flex">
    <div class="flex flex-col w-4">
      {#each $pop?.agents || [] as agent}
        {#if activeAgents.find(a => a.agent.id == agent.id)}
        <div class="flex-grow border my-1 border-red-500 transform translate-x-2 scale-150" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
        {:else}
        <div class="flex-grow" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
        {/if}
      {/each}
    </div>
    <div>
      <div class="flex flex-wrap">
        <Stat title="Generations" value={$generation} />
        <Stat title="Population Size" value={$size} />
        <Stat title="Sepecies In Population" value={numberOfSpecies} />
        <Stat title="Current Agent" value={activeAgents.map(a => {
          return a.agent.id
        }).join(",")} />
        <Stat title="Species ID" value={activeAgents.map(a => a.agent.species).join(", ")} />
        <Stat title="Current Score" value={activeAgents.map(a => $scores[a.agent.id]?.toFixed(4)).join(", ")} />
        <!-- scores: {populationScoreHistory} -->
      </div>
      <div class="flex">
        <div class=" w-full">
          <!-- <div class="ml-4">
  
            <h1 class="text-xl">Population Scores</h1>
            <div class="text-lg text-gray-600">Y axis is score for the agent(second chart is in log scale).</div>
            <div class="text-lg text-gray-600">X axis is agent number in the population.</div>
          </div> -->
          {#if data.length > 1}
          <ScoreChart populationSize={data.length}  highestPopulationScore={highestPopulationScore} {data} />
          <!-- <ScoreChart populationSize={data.length}  highestPopulationScore={Math.log(highestPopulationScore)} data={data.map(score => {
            const y = (score.y <= 0) ? 0 : Math.log(score.y)
            return {
            x: score.x,
            y: y,
            color: score.color
          }
          })} /> -->
          {/if}
          <!-- <MultiSeries frameNumber={$clockUpdate?.frame || 0} longestClockTimeSeen={longestClockTimeSeen} data={clockHistorySeriesMap}/> -->
        </div>
      </div>
    </div>
  </div>
  <div class="flex h-24 mt-2">
    {#each $popHistory.filter((p, index) => p.agents.length > 0).slice(-100) as population}
      <div class="flex flex-col w-full">
        {#each population.agents as agent}
          <div class="flex-grow" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
        {/each}
      </div>
    {/each}
  </div>
</div>