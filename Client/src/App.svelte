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
 interface SimulationEndpoints {

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
  amount : number,
 }
interface EvaluationScore {
  contributions : EvaluationScoreContribution[]
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
   species: number,
   runningScore: EvaluationScore,
   startTime: Date,
   clocks: EvaluationClock[]
 }
 interface Population {
   generation : number,
   agents : AgentModel[]
 }

 const r = reader<SimulationEndpoints>(message)
 let currentGeneration = tweened(0)
 let species = 0
 let runningScore = 0
</script>

<div>
  <h1 class="text-2xl">Current Generation:</h1>
  <div>
    <div>
      {$currentGeneration}
    </div>
  </div>
</div>

<div>
  <h1>Active Agent</h1>
  <div>
    <div>
      Species: {species}
    </div>
    <div>
      runningScore: {runningScore}
    </div>
  </div>
</div>