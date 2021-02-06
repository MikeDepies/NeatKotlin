<script>
  import PopulationComponent from './PopulationComponent.svelte';
import { getColorMap } from "./store/ColorMapStore";

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
    Resource
    Score Breakdown
    Total Score
  */
</script>
<PopulationComponent controllerIds={[0,1]} evaluationId={0}/>
<svelte:window on:keydown="{getColorMap("species").resetColors}"/>