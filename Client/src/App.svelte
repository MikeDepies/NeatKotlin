<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';
import * as Pancake from '@sveltejs/pancake';
import { fly, crossfade } from 'svelte/transition';
import ScoreChart from './ScoreChart.svelte';
import Stat from './Stat.svelte';
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
 type ControllerDigitalButton = "a" | "b" |"y" | "z"
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
 type ColorMap = {
   [K in number] : [number, number, number]
 }
 let colorMap : ColorMap= {}
 function getColor(speciesId : number, colorMap : ColorMap) {
   if (colorMap[speciesId]) {
     return colorMap[speciesId]
   } else {
     colorMap[speciesId] = [Math.floor(Math.random() * 256),Math.floor(Math.random() * 256),Math.floor(Math.random() * 256)]
     return colorMap[speciesId]
   }
 }
 function resetColors(event : KeyboardEvent) {
   if (event.key === 'r') {
     colorMap = {}
   }
 }
 const r = reader<SimulationEndpoints>(message)
  const newScore = r.read("simulation.event.score.new")
  const newAgent = r.read("simulation.event.agent.new")
  const newPopulation = r.read("simulation.event.population.new")
  const controllerOutput = r.read("simulation.frame.output")
 let currentGeneration = 0
 let populationSize = 200
 let currentPopulation : Population = {generation: 0, agents: []}
 let currentAgent : AgentModel = {
   id: 0, species: 0
 }
 $:{
   const population = $newPopulation
   const newGeneration = population?.generation || 0
   if (newGeneration != currentGeneration) {
     populationScoreHistory = []
     highestPopulationScore = 0
   }
   if (population !== undefined) {
    populationSize = population.agents.length
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
   let i = 0
   data = populationScoreHistory.map(s => ({x : i++, y: s}))
  }
  let agentScoreHistory : number[]= []
  let agentScoreModel : AgentModel = currentAgent
  let agentLastScore : number = 0
$: {
  const agent = currentAgent
  // console.log(agent);
  const score=$newScore?.score || 0;
  if (agent !== agentScoreModel) {
    populationScoreHistory = [...populationScoreHistory, agentLastScore]
    if (highestPopulationScore < agentLastScore) {
      highestPopulationScore = agentLastScore
    }
    agentLastScore = 0
    agentScoreHistory = []
    agentScoreModel = agent
  }
  if (agentLastScore !== score) {
    agentLastScore = score
    agentScoreHistory = [...agentScoreHistory, score]
  }
}
 $: {
   const agent = $newAgent
   if (agent !== undefined && agent !== currentAgent)
    currentAgent = agent
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
   numberOfSpecies = countUnique(currentPopulation.agents.map(a => a.species))
 }
 function countUnique<T> (iterable : T[]) {
  return new Set(iterable).size;
}
</script>
<div class="flex">
  <div class="flex flex-col w-4">
    {#each currentPopulation.agents as agent}
      {#if agent.id === currentAgent.id}
      <div class="flex-grow border my-1 border-red-500 transform translate-x-2 scale-150" style="background-color: rgb({getColor(agent.species, colorMap).join(",")})"></div>
      {:else}
      <div class="flex-grow" style="background-color: rgb({getColor(agent.species, colorMap).join(",")})"></div>
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
    </div>
    <div class="flex">
      <div>
        <h1 class="text-xl">Population Scores</h1>
        <div class="text-lg text-gray-600">Y axis is score for the agent(second chart is in log scale).</div>
        <div class="text-lg text-gray-600">X axis is agent number in the population.</div>
        <ScoreChart {populationSize} {highestPopulationScore} {data} />
        <ScoreChart {populationSize} highestPopulationScore={Math.log(highestPopulationScore)} data={data.map(a => {
          const y = (a.y <= 0) ? 0 : Math.log(a.y)
          return {
          x: a.x,
          y: y
        }
        })} />
        
      </div>
    </div>
  </div>
</div>
<div class="flex h-24 mt-2">
  {#each historyOfPopulations as population}
    <div class="flex flex-col w-full">
      {#each population.agents as agent}
        <div class="flex-grow" style="background-color: rgb({getColor(agent.species, colorMap).join(",")})"></div>
      {/each}
    </div>
  {/each}
</div>
<svelte:window on:keydown="{resetColors}"/>