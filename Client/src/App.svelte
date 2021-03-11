<script>
  import { match } from "svelte-store-router";

  import PopulationComponent from "./PopulationComponent.svelte";
  import ViewSimulationPage from "./ViewSimulationPage.svelte";
  import { getColorMap } from "./store/ColorMapStore";
  import { route } from "./store/Router";
import SimulationsPage from './SimulationsPage.svelte';
import NewSimulation from './NewSimulation.svelte';

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
  let params;
</script>
{#if (params = match('/simulation/create', $route.path, false))}
  <NewSimulation/>
{:else if (params = match('/simulation/:id', $route.path, true))}
  <ViewSimulationPage simulationId={params.id} />
{:else if (params = match('/simulations', $route.path, true))}
  <SimulationsPage/>
  
{/if}

<svelte:window on:keydown={getColorMap('species').resetColors} />
