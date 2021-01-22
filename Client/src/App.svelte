<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';
import * as Pancake from '@sveltejs/pancake';
import { fly, crossfade } from 'svelte/transition';
import ScoreChart from './ScoreChart.svelte';
import Stat from './Stat.svelte';
import {colorMap, resetColors, getColor} from "./store/ColorMapStore"
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
 type ControllerDigitalButton = "score" | "b" |"y" | "z"
 type ControllerAnalogButton = "cStickX" | "cStickY" |"mainStickX" | "mainStickY"
 type ControllerButton = ControllerDigitalButton | ControllerAnalogButton
 interface SimulationEndpoints {
  "simulation.frame.output" : {
    [K in ControllerButton] : K extends ControllerDigitalButton ? boolean : number
  }
  "simulation.event.population.new": Population
  "simulation.event.agent.new" : AgentModel
  "simulation.event.score.new" : EvaluationScore
 }
 interface PlayerDataFramePart {}
 interface ActionDataFramePart {}
 interface GameFrame {
  frame : number,
  distance : number,
  player1 : PlayerDataFramePart,
  player2 : PlayerDataFramePart,
  action1 : ActionDataFramePart,
  action2 : ActionDataFramePart,
 }
 interface AgentEvaluationFrame {

 }

 interface EvaluationScoreContribution {
  name : string,
  score : number,
  contribution : number
 }
interface EvaluationScore {
  agentId : number,
  evaluationScoreContributions : EvaluationScoreContribution[]
  score : number
}

interface EvaluationClock {
  name: string,
  frameLength: number,
  startFrame: number,
  framesRemaining: number,
  expired: boolean
}
 interface AgentModel {
   id: number,
   species: number,
  //  score: EvaluationScore,
  //  clocks: EvaluationClock[]
 }
 interface Population {
   generation : number,
   agents : AgentModel[]
 }
 
 const r = reader<SimulationEndpoints>(message)
  const newScore = r.read("simulation.event.score.new")
  const newAgent = r.read("simulation.event.agent.new")
  const newPopulation = r.read("simulation.event.population.new")
  const controllerOutput = r.read("simulation.frame.output")
 let currentGeneration = -1
 let populationSize = 0
 let currentPopulation : Population = {generation: 0, agents: []}
 let currentAgent : AgentModel = {
   id: 0, species: 0
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
 let populationScoreHistory : number[] = []
 let data : {x : number, y: number }[]= []
 let highestPopulationScore = 0
 $: {
   if (populationScoreHistory) {
   let i = 0
   data = populationScoreHistory.slice(0, populationSize).map(s => ({x : i++, y: s || 0}))
  //  console.log(data);
   for(let score of data) {
     if (highestPopulationScore < score.y) {
      highestPopulationScore = score.y
     }

   }
  }
  }
  let agentScoreHistory : number[]= []
  let agentScoreModel : AgentModel = currentAgent
  // let agentLastScore : number = 0
$: {
  const agent = currentAgent
  // console.log(agent);
  const score=$newScore?.score || 0;
  if (agent !== agentScoreModel) {
    // populationScoreHistory = [...populationScoreHistory, agentLastScore]
    // if (highestPopulationScore < agentLastScore) {
    //   highestPopulationScore = agentLastScore
    // }
    // agentLastScore = 0
    agentScoreHistory = []
    agentScoreModel = agent
  }
  // if (agentLastScore !== score) {
  //   agentLastScore = score
  //   agentScoreHistory = [...agentScoreHistory, score]
  // }
}
 $: {
   const agent = $newAgent
   if (agent !== undefined && agent !== currentAgent)
    currentAgent = agent
 }
 $: {
    const score = $newScore
    if (score !== undefined && populationScoreHistory.length > score.agentId) {
      populationScoreHistory[score.agentId] = score.score
    }
  }
 let numberOfSpecies = 0
 let historyOfPopulations : Population[] = []
 let historyOfCounts : {
   [K in number] : number
 }[] = []
 let counts : {
   [K in number] : number
 } = {};
 $: {
   historyOfCounts = [...historyOfCounts, counts]
   counts = {}
  for (let i = 0; i < currentPopulation.agents.length; i++) {
    counts[currentPopulation.agents[i].species] = 1 + (counts[currentPopulation.agents[i].species] || 0);
}
   numberOfSpecies = countUnique(currentPopulation.agents.map(score => score.species))
 }
 function countUnique<T> (iterable : T[]) {
  return new Set(iterable).size;
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
      <Stat title="Generations" value={currentGeneration} />
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
        {#if currentAgent.id > 0 && data.length > 1}
        <ScoreChart color="rgb({getColor(currentAgent.species, $colorMap).join(",")})" populationSize={data.length} index={currentAgent.id} highestPopulationScore={highestPopulationScore} {data} />
        <ScoreChart color="rgb({getColor(currentAgent.species, $colorMap).join(",")})" populationSize={data.length} index={currentAgent.id} highestPopulationScore={Math.log(highestPopulationScore)} data={data.map(score => {
          const y = (score.y <= 0) ? 0 : Math.log(score.y)
          return {
          x: score.x,
          y: y
        }
        })} />
        {/if}
        
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
<svelte:window on:keydown="{resetColors}"/>