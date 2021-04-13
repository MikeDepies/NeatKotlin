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
  function bgFor(
    score: { species: number; generation: number },
    currentAgent: AgentModel
  ) {
    const isActiveSpecies = score.species === currentAgent.species;
    const is5FromLastTurn = score.generation < currentGeneration - 7;
    const iswarningTurn = score.generation < currentGeneration - 3;
    const isCurrentTurn = score.generation === currentGeneration;
    const isLastTurn = score.generation === currentGeneration - 12;
    return `bg-${
      isLastTurn
        ? "red"
        : is5FromLastTurn
        ? "pink"
        : iswarningTurn
        ? "yellow"
        : isCurrentTurn
        ? "green"
        : "gray"
    }-${isActiveSpecies ? "400" : "100"} ${
      isLastTurn ? "animate-pulse" : ""
    } ${isActiveSpecies ? "" : ""}`;
    // { ? "bg-gray-300" : "bg-gray-100"}
  }
  const r = reader<SimulationEndpoints>(message);
  const newScore = r.read("simulation.event.score.new");
  const newAgent = r.read("simulation.event.agent.new");
  const newPopulation = r.read("simulation.event.population.new");
  // const controllerOutput = r.read("simulation.frame.output")
  // const clockUpdate = r.read("simulation.event.clock.update")
  let currentGeneration = -1;
  let populationSize = 0;
  let currentPopulation: Population = { generation: 0, agents: [] };
  const colorMap = getColorMap("species");
  //  const clockColorMap = getColorMap("clocks")
  let currentAgent: AgentModel = {
    id: 0,
    species: 0,
  };

  let speciesScoreHistory: {
    [K in number]: { score: number; generation: number; species: number }[];
  } = {};
  let speciesScoreLeaderboard: {
    [K in number]: { score: number; generation: number; species: number };
  } = {};
  $: top20SpeciesScores = Object.values(speciesScoreLeaderboard)
    .filter((s) => currentGeneration - s.generation <= 12)
    .sort((a, b) => b.score - a.score)
    .slice(0, 20);

  $: {
    const newestScore = $newScore;
    if (newestScore && currentPopulation.agents) {
      let agent = currentAgent;
      if (agent.id !== newestScore.agentId) {
        agent =
          currentPopulation.agents.find((a) => a.id === agent.id) ||
          currentAgent;
      }
      if (
        agent.id === newestScore.agentId &&
        (speciesScoreLeaderboard[agent.species]?.score || 0) < newestScore.score
      ) {
        const newScoreEntry = {
          score: newestScore.score,
          generation: currentGeneration,
          species: agent.species,
        };
        speciesScoreLeaderboard[agent.species] = newScoreEntry;
        if (speciesScoreHistory[agent.species] === undefined)
          speciesScoreHistory[agent.species] = [];
        speciesScoreHistory[agent.species] = [
          ...speciesScoreHistory[agent.species],
          newScoreEntry,
        ];
      }
    }
  }
  $: {
    const population = $newPopulation;
    const newGeneration = population?.generation || 0;

    if (population !== undefined) {
      populationSize = population.agents.length;
    }
    if (newGeneration != currentGeneration) {
      populationScoreHistory = new Array<number>(populationSize + 1).fill(0);
      highestPopulationScore = 0;
    }
    currentGeneration = newGeneration;
  }
  $: {
    const population = $newPopulation;
    if (population && population !== currentPopulation) {
      currentPopulation.agents.sort((a, b) => a.species - b.species);
      historyOfPopulations = [...historyOfPopulations, currentPopulation];
      currentPopulation = population;
    }
  }
  let populationScoreHistory: number[] = [];
  let data: { x: number; y: number; color: string }[] = [];
  let highestPopulationScore = 0;
  $: {
    if (populationScoreHistory) {
      highestPopulationScore = 0;
      let i = 0;
      data = populationScoreHistory.slice(0, populationSize).map((s) => {
        let index = i++;
        let species = currentPopulation.agents[index].species;
        let color = `rgb(${getColor(species, $colorMap).join(",")})`;
        // console.log(color)
        return { x: index, y: s || 0, color: color };
      });
      //  console.log(data);
      for (let score of data) {
        if (highestPopulationScore < score.y) {
          highestPopulationScore = score.y;
        }
      }
    }
  }
  let agentScoreHistory: number[] = [];
  let agentScoreModel: AgentModel = currentAgent;
  // let agentLastScore : number = 0
  $: {
    const agent = currentAgent;
    // console.log(agent);
    const score = $newScore?.score || 2;
    if (agent !== agentScoreModel) {
      // populationScoreHistory = [...populationScoreHistory, agentLastScore]
      // if (highestPopulationScore < agentLastScore) {
      //   highestPopulationScore = agentLastScore
      // }
      // agentLastScore = 0
      agentScoreHistory = [];
      agentScoreModel = agent;
    }
    // if (agentLastScore !== score) {
    //   agentLastScore = score
    //   agentScoreHistory = [...agentScoreHistory, score]
    // }
  }
  $: {
    const agent = $newAgent;
    if (agent !== undefined && (agent.id > currentAgent.id || agent.id === 0))
      currentAgent = agent;
  }
  $: {
    const score = $newScore;
    if (score !== undefined && populationScoreHistory.length > score.agentId) {
      populationScoreHistory[score.agentId] = score.score;
    }
  }
  let numberOfSpecies = 0;
  let historyOfPopulations: Population[] = [];
  let historyOfCounts: {
    [K in number]: number;
  }[] = [];
  let counts: {
    [K in number]: number;
  } = {};
  $: {
    historyOfCounts = [...historyOfCounts, counts];
    counts = {};
    for (let i = 0; i < currentPopulation.agents.length; i++) {
      counts[currentPopulation.agents[i].species] =
        1 + (counts[currentPopulation.agents[i].species] || 0);
    }
    numberOfSpecies = countUnique(
      currentPopulation.agents.map((score) => score.species)
    );
  }
  function countUnique<T>(iterable: T[]) {
    return new Set(iterable).size;
  }
  function resetColors() {
    colorMap.set({});
  }
  function keyDown(event: KeyboardEvent) {
    console.log("keydown");
    if (event.key === "r") {
      resetColors();
    } else if (event.key === "c") {
      clearAll();
    }
  }
  function clearAll() {
    resetColors();
    historyOfPopulations = [];
    // populationScoreHistory = new Array(populationScoreHistory.length)
  }
</script>

<div class="h-screen w-full flex flex-col overflow-hidden">
  <div class="flex">
    <div class="flex flex-col w-4">
      {#each currentPopulation.agents as agent}
        {#if agent.id === currentAgent.id}
          <div
            class="flex-grow border my-1 border-red-500 transform translate-x-2 scale-150"
            style="background-color: rgb({getColor(agent.species, $colorMap).join(',')})"
          />
        {:else}
          <div
            class="flex-grow"
            style="background-color: rgb({getColor(agent.species, $colorMap).join(',')})"
          />
        {/if}
      {/each}
    </div>
    <div>
      <div class="flex flex-wrap">
        <Stat title="Generations" value={currentGeneration + 0} />
        <Stat title="Population Size" value={populationSize} />
        <Stat title="Sepecies In Population" value={numberOfSpecies} />
        <Stat title="Current Agent" value={currentAgent.id} />
        <Stat title="Species ID" value={currentAgent.species} />
        <Stat title="Current Score" value={$newScore?.score?.toFixed(2) || 0} />
        <!-- scores: {populationScoreHistory} -->
      </div>
      <div class="flex">
        <div class=" w-full">
          <div class="ml-4">
            <h1 class="text-xl">Population Scores</h1>
            <div class="text-lg text-gray-600">
              Y axis is score for the agent(second chart is in log scale).
            </div>
            <div class="text-lg text-gray-600">
              X axis is agent number in the population.
            </div>
          </div>
          {#if data.length > 1}
            <ScoreChart
              populationSize={data.length}
              {highestPopulationScore}
              {data}
            />
            <!-- <ScoreChart
            populationSize={data.length}
            highestPopulationScore={Math.log(highestPopulationScore)}
            data={data.map((score) => {
              const y = score.y <= 0 ? 0 : Math.log(score.y);
              return { x: score.x, y: y, color: score.color };
            })}
          /> -->
          {/if}
          <!-- <MultiSeries frameNumber={$clockUpdate?.frame || 0} longestClockTimeSeen={longestClockTimeSeen} data={clockHistorySeriesMap}/> -->
        </div>
      </div>
    </div>
  </div>
  <div>
    <h2 class="text-2xl text-center">Historical Species Map</h2>
    <div class="text-sm text-center">
      Top Species (Species [Generation] = Score)
    </div>
    <div class="text-sm text-center">
      Stagnation after 12 generations without new best score
    </div>
    <div class="w-full">
      {#each top20SpeciesScores as score, index (score.species)}
        <div
        
          class="relative inline-block text-sm font-medium w-1/6 m-2 {bgFor(score, currentAgent)} px-2 py-1 rounded transition-colors duration-300"
        >
          <div
            class="{score.species === currentAgent.species ? "animate-bounce bg-blue-200  text-sm w-6 py-0.5 " : "bg-green-200  text-xs w-4 "} absolute -right-1 -top-1 transition-colors duration-300 rounded-full text-center font-bold "
          >
            {index + 1}
          </div>
          <div class="flex">
            <div
              class="w-4 h-4 inline-block border border-white mr-1"
              style="background-color: rgb({getColor(score.species, $colorMap).join(',')})"
            />
            <span class="">{score.species}[{score.generation}]</span>
          </div>
          <div>{score.score.toFixed(2) || 0}</div>
        </div>
      {/each}
    </div>
  </div>
  <div class="flex h-full mt-2">
    {#each historyOfPopulations.filter((p, index) => p.agents.length > 0).slice(-100) as population}
      <div class="flex flex-col w-full">
        {#each population.agents as agent (agent.id)}
          <div
            class="flex-grow"
            style="background-color: rgb({getColor(agent.species, $colorMap).join(',')})"
          />
        {/each}
      </div>
    {/each}
  </div>
</div>
<!-- <svelte:window on:keydown="{colorMap.resetColors}"/> -->
<svelte:window on:keydown={keyDown} />
