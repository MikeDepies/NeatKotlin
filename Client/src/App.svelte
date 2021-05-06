<script>
  import { tweened } from "svelte/motion";
  import { reader } from "./store/websocket/MessageRouter";
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
  const evaluationSet = new Set()
 
</script>
<div class="flex">
  <PopulationComponent evaluationId={0} controllerIds={[0]}/>
  <PopulationComponent evaluationId={1} controllerIds={[1]}/>
</div>