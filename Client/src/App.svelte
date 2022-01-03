<script>
  import { tweened } from "svelte/motion";
  import { reader, writer } from "./store/websocket/MessageRouter";
  import { message } from "./store/WebsocketStore";
  import * as Pancake from "@sveltejs/pancake";
  import { fly, crossfade, fade } from "svelte/transition";
  import ScoreChart from "./ScoreChart.svelte";
  import Stat from "./Stat.svelte";
  import { getColorMap, getColor, rgbColorString } from "./store/ColorMapStore";
  import type {
    AgentModel,
    EvaluationClocksUpdate,
    Population,
    SimulationEndpoints,
  } from "./api/types/Evaluation";
  import type { Series } from "./api/types/Series";
  import MultiSeries from "./MultiSeries.svelte";
  import { prevent_default } from "svelte/internal";
  import { flip } from "svelte/animate";
  import PopulationComponent from "./PopulationComponent.svelte";
  const r = reader<SimulationEndpoints>(message);
  const newScore = r.read("simulation.event.score.new");
  const newAgent = r.read("simulation.event.agent.new");
  const newPopulation = r.read("simulation.event.population.new");
  const evaluationSet = new Set();
  type TimerControllerEndpoint = {
    
    timer: { timer: number };
    maxTimer :  { timer: number };
  };
  const w = writer<TimerControllerEndpoint>(message);
  let timer = 30;
  let maxTimer = 30;
  function updateTimer() {
    w.write("timer", { timer });
    w.write("maxTimer", { timer: maxTimer });
  }
  
</script>

<div>
  Timer:
  <input type="text" bind:value={timer} class="w-16" />
  Max Timer:
  <input type="text" bind:value={maxTimer} />
  <button on:click={updateTimer}>Update</button>
</div>
<div class="flex flex-col">
  <!-- <PopulationComponent evaluationId={0} controllerIds={[0]} /> -->
  <PopulationComponent evaluationId={1} controllerIds={[1]} />
</div>
