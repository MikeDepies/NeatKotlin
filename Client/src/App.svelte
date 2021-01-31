<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';
import * as Pancake from '@sveltejs/pancake';
import { fly, crossfade } from 'svelte/transition';
import ScoreChart from './ScoreChart.svelte';
import Stat from './Stat.svelte';
import {getColorMap, getColor, rgbColorString} from "./store/ColorMapStore"
import type { AgentModel, EvaluationClocksUpdate, Population, SimulationEndpoints } from './api/types/Evaluation';
import type { Series } from './api/types/Series';
import MultiSeries from './MultiSeries.svelte';
import { prevent_default } from 'svelte/internal';
import { countUnique, createClockHistorySeriesMap } from './api/Evaluation';
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
 
 const r = reader<SimulationEndpoints>(message)
  const newScore = r.read("simulation.event.score.new")
  const newAgent = r.read("simulation.event.agent.new")
  const newPopulation = r.read("simulation.event.population.new")
  let populationScoreHistory : number[] = []
  let historyOfPopulations : Population[] = []
 let data : {x : number, y: number, color: string }[]= []
 let highestPopulationScore = 0
 let currentGeneration = -1
 let populationSize = 0
 let currentPopulation : Population = {generation: 0, agents: [], evaluationId: -1}
 const colorMap = getColorMap("species")
 let currentAgent : AgentModel = {
   id: 0, species: 0, evaluationId: -1
 }
 $:{
   const population = $newPopulation
   const newGeneration = population?.generation || 0
   
   if (population !== undefined) {
    populationSize = population.agents.length
   }
   if (newGeneration != currentGeneration) {
     populationScoreHistory = new Array<number>(populationSize).fill(0)
     highestPopulationScore = 0
   }
   currentGeneration = newGeneration 
 }
 $: {
   const population = $newPopulation
   if (population && population !== currentPopulation) {
    historyOfPopulations = [...historyOfPopulations, population]
     currentPopulation = population
   }
 }
 
 $: {
   if (populationScoreHistory) {
   let i = 0
   data = populationScoreHistory.slice(0, populationSize).map(s => {
    let index =i++
    let species = currentPopulation.agents[index].species
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
  let agentScoreHistory : number[]= []
  let agentScoreModel : AgentModel = currentAgent
$: {
  const agent = currentAgent
  // console.log(agent);
  const score=$newScore?.score || 0;
  if (agent !== agentScoreModel) {
    agentScoreHistory = []
    agentScoreModel = agent
  }
}
 $: {
   const agent = $newAgent
   if (agent !== undefined && (agent.id > currentAgent.id || agent.id === 0))
    currentAgent = agent
 }
 $: {
    const score = $newScore
    if (score !== undefined && populationScoreHistory.length > score.agentId) {
      populationScoreHistory[score.agentId] = score.score
    }
  }

 let numberOfSpecies = 0
 $: {
   numberOfSpecies = countUnique(currentPopulation.agents.map(score => score.species))
 }

</script>

<div class="flex">
  <div class="flex flex-col w-4">
    {#each currentPopulation.agents as agent}
      {#if agent.id === currentAgent.id}
      <div class="flex-grow border my-1 border-red-500 transform translate-x-2 scale-150" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
      {:else}
      <div class="flex-grow" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
      {/if}
    {/each}
  </div>
  <div>
    <div class="flex flex-wrap">
      <Stat title="Generations" value={currentGeneration +179} />
      <Stat title="Population Size" value={populationSize} />
      <Stat title="Sepecies In Population" value={numberOfSpecies} />
      <Stat title="Current Agent" value={currentAgent.id} />
      <Stat title="Species ID" value={currentAgent.species} />
      <Stat title="Current Score" value={$newScore?.score || 0} />
      <!-- scores: {populationScoreHistory} -->
    </div>
    <div class="flex">
      <div class=" w-full">
        <div class="ml-4">

          <h1 class="text-xl">Population Scores</h1>
          <div class="text-lg text-gray-600">Y axis is score for the agent(second chart is in log scale).</div>
          <div class="text-lg text-gray-600">X axis is agent number in the population.</div>
        </div>
        {#if data.length > 1}
        <ScoreChart populationSize={data.length}  highestPopulationScore={highestPopulationScore} {data} />
        <ScoreChart populationSize={data.length}  highestPopulationScore={Math.log(highestPopulationScore)} data={data.map(score => {
          const y = (score.y <= 0) ? 0 : Math.log(score.y)
          return {
          x: score.x,
          y: y,
          color: score.color
        }
        })} />
        {/if}
        <!-- <MultiSeries frameNumber={$clockUpdate?.frame || 0} longestClockTimeSeen={longestClockTimeSeen} data={clockHistorySeriesMap}/> -->
      </div>
    </div>
  </div>
</div>
<div class="flex h-24 mt-2">
  {#each historyOfPopulations as population}
    <div class="flex flex-col w-full">
      {#each population.agents as agent}
        <div class="flex-grow" style="background-color: rgb({getColor(agent.species, $colorMap).join(",")})"></div>
      {/each}
    </div>
  {/each}
</div>
<svelte:window on:keydown="{colorMap.resetColors}"/>