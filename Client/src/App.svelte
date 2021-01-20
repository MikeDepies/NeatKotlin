<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';
import * as Pancake from '@sveltejs/pancake';
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
 $: {
 
   numberOfSpecies = countUnique(currentPopulation.agents.map(a => a.species))
 }
 function countUnique<T> (iterable : T[]) {
  return new Set(iterable).size;
}
</script>

<div>
  <div>Generations: {currentGeneration}</div>
  <div>Population Size: {populationSize}</div>
  <div>Species In Population: {numberOfSpecies}</div>
  <div>Current Agent: {currentAgent.id }</div>
  <div>Current Agent Species: {currentAgent.species }</div>
  <div>Current Score: {$newScore?.score }</div>
  <div>
    <h1>Population Scores</h1>
    <div class="w-full h-96">
      <div class="chart">
        <Pancake.Chart x1={0} x2={populationSize} y1={0} y2={highestPopulationScore}>
          <Pancake.Box x2={populationSize} y2={highestPopulationScore}>
            <div class="axes"></div>
          </Pancake.Box>
      
          <Pancake.Grid vertical count={5} let:value>
            <span class="x label">{value}</span>
          </Pancake.Grid>
      
          <Pancake.Grid horizontal count={3} let:value>
            <span class="y label">{value}</span>
          </Pancake.Grid>
      
          <Pancake.Svg>
            <Pancake.SvgLine data={data} let:d>
              <path class="data" {d}/>
            </Pancake.SvgLine>
          </Pancake.Svg>
        </Pancake.Chart>
      </div>
    </div>
    <div class="w-full h-96">
      <div class="chart">
        <Pancake.Chart x1={0} x2={populationSize} y1={0} y2={Math.log(highestPopulationScore)}>
          <Pancake.Box x2={populationSize} y2={Math.log(highestPopulationScore)}>
            <div class="axes"></div>
          </Pancake.Box>
      
          <Pancake.Grid vertical count={5} let:value>
            <span class="x label">{value}</span>
          </Pancake.Grid>
      
          <Pancake.Grid horizontal count={3} let:value>
            <span class="y label">{value}</span>
          </Pancake.Grid>
      
          <Pancake.Svg>
            <Pancake.SvgLine data={data.map(a => {
              const y = (a.y <= 0) ? 0 : Math.log(a.y)
              return {
              x: a.x,
              y: y
            }
            })} let:d>
              <path class="data" {d}/>
            </Pancake.SvgLine>
          </Pancake.Svg>
        </Pancake.Chart>
      </div>
    </div>
  </div>
</div>

<style>
  .chart {
    height: 100%;
    padding: 3em 2em 2em 3em;
    box-sizing: border-box;
  }

  .axes {
    width: 100%;
    height: 100%;
    border-left: 1px solid black;
    border-bottom: 1px solid black;
  }

  .y.label {
    position: absolute;
    left: -2.5em;
    width: 2em;
    text-align: right;
    bottom: -0.5em;
  }

  .x.label {
    position: absolute;
    width: 4em;
    left: -2em;
    bottom: -22px;
    font-family: sans-serif;
    text-align: center;
  }

  path.data {
    stroke: red;
    stroke-linejoin: round;
    stroke-linecap: round;
    stroke-width: 2px;
    fill: none;
  }
</style>