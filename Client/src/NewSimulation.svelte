<script>
    import CharacterSelectionComponent from "@components/CharacterSelectionComponent.svelte";
    import CpuLevelSelectComponent from "@components/CpuLevelSelectComponent.svelte";
    import { onMount } from "svelte";
    import { stages } from "./api/Melee";
    import { createEvaluation } from "./api/Simulation";
    import type {
        Character,
        Controller,
        Evaluation,
        NewSimulationRequest,
    } from "./api/types/Melee";

    let simulation: NewSimulationRequest = {
        evaluations: [createEvaluation()],
        stage: "Final Destination",
    };
    onMount(() => {
        //Get simulations
    });
    let evaluationSelected: Evaluation | undefined;
    $: {
        if (
            evaluationSelected === undefined &&
            simulation.evaluations.length > 0
        ) {
            evaluationSelected = simulation.evaluations[0];
        }
    }

    function addEvaluation() {
        simulation.evaluations = [
            ...simulation.evaluations,
            createEvaluation(),
        ];
    }

    function addController(evaluation: Evaluation | undefined) {
        const newController: Controller = {
            character: "Fox",
            cpu: 0,
        };
        if (evaluation) {
            evaluation.controllers = [...evaluation.controllers, newController];
            simulation.evaluations = [...simulation.evaluations];
            evaluationSelected = evaluationSelected;
        }
    }
</script>

<!-- This example requires Tailwind CSS v2.0+ -->
<div class="m-4">
    <div
        class="pb-5 border-b border-gray-200 sm:flex sm:items-center sm:justify-between"
    >
        <h3 class="text-lg leading-6 font-medium text-gray-900">
            New Simulation
        </h3>
        <div class="mt-3 sm:mt-0 sm:ml-4">
            <button
                on:click={() => addEvaluation()}
                type="button"
                class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
                Add New Evaluation
            </button>
        </div>
    </div>
    <div>
        <div class="inline-block">
            <label
                for="location"
                class="block text-sm font-medium text-gray-700"
            >Stage -
                {simulation.stage}</label>
            <select
                bind:value={simulation.stage}
                id="location"
                name="location"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
            >
                {#each stages as stage}
                    <option value={stage}>{stage}</option>
                {/each}
            </select>
        </div>
    </div>
    <!-- This example requires Tailwind CSS v2.0+ -->
    <div class="border-b border-gray-200">
        <div class="sm:flex sm:items-baseline">
            <h3 class="text-lg leading-6 font-medium text-gray-900">
                Evaluations
            </h3>
            <div class="mt-4 sm:mt-0 sm:ml-10">
                <nav class="-mb-px flex space-x-8">
                    <!-- Current: "border-indigo-500 text-indigo-600", Default: "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" -->
                    {#each simulation.evaluations as evaluation}
                        <a
                            href="#"
                            on:click={() => (evaluationSelected = evaluation)}
                            class="{evaluationSelected === evaluation ? 'border-indigo-500 text-indigo-600 ' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'} whitespace-nowrap pb-4 px-1 border-b-2 font-medium text-sm"
                            aria-current="page"
                        >
                            Evaluation
                        </a>
                    {/each}
                </nav>
            </div>
        </div>
    </div>
    {#if evaluationSelected !== undefined}
        <div class="flex w-full flex-wrap">
            <div id="controllers" class="w-1/2 rounded-lg bg-gray-200 p-2">
                <div class="flex w-full mb-2">
                    <div class="flex-grow text-lg font-medium">Controllers</div>
                    <div>
                        <button
                            on:click={(e) => addController(evaluationSelected)}
                            type="button"
                            class="inline-flex items-center px-2 py-1 border border-transparent rounded-md shadow-sm text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                        >
                            Add Controller
                        </button>
                    </div>
                </div>
                <div>
                    {#each evaluationSelected?.controllers as controller}
                        <CharacterSelectionComponent
                            bind:character={controller.character}
                        />
                        <CpuLevelSelectComponent
                            bind:cpuLevel={controller.cpu}
                        />
                    {/each}
                </div>
            </div>
            <div id="neatParameters" class="w-1/2">
                <div class="p-2">
                    <div class="flex-grow text-lg font-medium">
                        Neat Parameters
                    </div>
                    <div class="flex flex-wrap space-x-2">
                        <div>
                            <label
                                for="mateChance"
                                class="block text-sm font-medium text-gray-700"
                            >Mate Chance</label>
                            <div class="mt-1">
                                <input
                                    bind:value={evaluationSelected.neatParameters.mateChance}
                                    type="text"
                                    name="mateChance"
                                    id="mateChance"
                                    class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                    placeholder="30"
                                />
                            </div>
                        </div>
                        <div>
                            <label
                                for="populationSize"
                                class="block text-sm font-medium text-gray-700"
                            >Population Size</label>
                            <div class="mt-1">
                                <input
                                    bind:value={evaluationSelected.neatParameters.populationSize}
                                    type="text"
                                    name="populationSize"
                                    id="populationSize"
                                    class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                    placeholder=".6"
                                />
                            </div>
                        </div>
                        <div>
                            <label
                                for="seed"
                                class="block text-sm font-medium text-gray-700"
                            >Random Seed</label>
                            <div class="mt-1">
                                <input
                                    bind:value={evaluationSelected.neatParameters.seed}
                                    type="text"
                                    name="seed"
                                    id="seed"
                                    class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                    placeholder="0"
                                />
                            </div>
                        </div>
                        <div>
                            <label
                                for="survivalThreshold"
                                class="block text-sm font-medium text-gray-700"
                            >Survival Threshold</label>
                            <div class="mt-1">
                                <input
                                    bind:value={evaluationSelected.neatParameters.survivalThreshold}
                                    type="text"
                                    name="survivalThreshold"
                                    id="survivalThreshold"
                                    class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                    placeholder=".6"
                                />
                            </div>
                        </div>
                        <div>
                            <div>Weights for distance function</div>
                            <div>
                                <div>
                                    <div>
                                        <label
                                            for="disjoint"
                                            class="block text-sm font-medium text-gray-700"
                                        >Disjoint Genes</label>
                                        <div class="mt-1">
                                            <input
                                                bind:value={evaluationSelected.neatParameters.speciesDistanceWeights.disjoint}
                                                type="text"
                                                name="disjoint"
                                                id="disjoint"
                                                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                                placeholder="1"
                                            />
                                        </div>
                                    </div>
                                    <div>
                                        <label
                                            for="excess"
                                            class="block text-sm font-medium text-gray-700"
                                        >Excess Genes</label>
                                        <div class="mt-1">
                                            <input
                                                bind:value={evaluationSelected.neatParameters.speciesDistanceWeights.excess}
                                                type="text"
                                                name="excess"
                                                id="excess"
                                                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                                placeholder="1"
                                            />
                                        </div>
                                    </div>
                                    <div>
                                        <label
                                            for="avgSharedWeight"
                                            class="block text-sm font-medium text-gray-700"
                                        >Average Weight of Shared Connections</label>
                                        <div class="mt-1">
                                            <input
                                                bind:value={evaluationSelected.neatParameters.speciesDistanceWeights.avgSharedWeight}
                                                type="text"
                                                name="avgSharedWeight"
                                                id="avgSharedWeight"
                                                class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                                                placeholder="1"
                                            />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div id="mutationDictionary" class="w-1/2">
                {#each evaluationSelected.mutationDictionary as mutation}
                    <div>
                        <div class="mt-1">
                            <input
                                bind:value={mutation.chance}
                                type="text"
                                name="mateChance"
                                id="mateChance"
                                class=" w-16 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block sm:text-sm border-gray-300 rounded-md"
                                placeholder="30"
                            />
                        </div>
                    </div>
                {/each}
            </div>
            <div id="activationFunctions" class="w-1/2" />
        </div>
    {/if}
</div>
