<script>
  import { tweened } from 'svelte/motion';
import { reader } from './store/websocket/MessageRouter';
import { message } from './store/WebsocketStore';

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
   score: EvaluationScore,
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
 let currentPopulation : Population = {generation: 0, agents: []}
 let currentAgent : AgentModel = {
   id: 0, species: 0, score : {evaluationScoreContributions: [], score: 0}
 }
 $:{
   currentGeneration = $newPopulation?.generation || 0 
 }
 $: {
   const population = $newPopulation
   if (population && population !== currentPopulation) {
     currentPopulation = population
   }
 }

 $: {
   const agent = $newAgent
   if (agent && agent !== currentAgent)
    currentAgent = $newAgent!!
 }
</script>

<div>
  <div>Controller: {JSON.stringify($controllerOutput)}</div>
  <div>Generations: {currentGeneration}</div>
  <div>Population Size: {currentPopulation.agents.length}</div>
  <div>Current Agent: {currentAgent.id }</div>
  <div>Current Agent Species: {currentAgent.species }</div>
  <div>Current Score: {$newScore?.score }</div>
  <div>
    
  </div>
</div>