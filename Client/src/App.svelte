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

 interface AgentModelScore {}
 interface AgentModel {}
 interface Population {}

 const r = reader<SimulationEndpoints>(message)
 let currentGeneration = tweened(0)
</script>

<div>
  <h1 class="text-2xl">Current Generation:</h1>
  <div>
    <div>
      ${currentGeneration}
    </div>
  </div>
</div>