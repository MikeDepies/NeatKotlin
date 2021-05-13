/**
 * Peer encoding (watch others play and learn?) and indirect encoding (genetic algorithm/NEAT)
 *  - could we apply some distance measuring function as a way to judge output difference? what can we do with the data?
 */

import { arrayWritable, arrayWritableFixedSize } from '@app/api/Store/arrayWritable'
import { mapWritable } from '@app/api/Store/mapWritable'
import type { AgentModel, Population, SimulationEndpoints } from '@app/api/types/Evaluation'
import { derived, Readable, Writable } from 'svelte/store'
import { reader } from './websocket/MessageRouter'
import { message } from './WebsocketStore'
type AgentController = {
    agent: AgentModel,
    controllerId: number
}
const r = reader<SimulationEndpoints>(message)
const populationStore = r.read("simulation.event.population.new")
const newAgentStore = r.read("simulation.event.agent.new")
const newScoreStore = r.read("simulation.event.score.new")
export function evaluationPopulation(evaluationId: number, controllerIds: number[]) {
    const pop = population(evaluationId)
    const generation = derived(pop, ($pop: Population | undefined, set: (v: number) => void) => {
        set($pop?.generation || 0)
    })
    const size = derived(pop, ($pop: Population | undefined, set: (v: number) => void) => {
        set($pop?.agents.length || 0)
    })
    const popHistory = populationHistory(pop)
    const controllerMap = controllerAgents(evaluationId)
    const scores = populationScores(evaluationId, generation)
    return {
        pop,
        generation,
        size,
        popHistory,
        controllerMap,
        scores
    }
}

function population(evaluationId: number) {
    const evaluationPopulation = derived(populationStore, ($populationStore: Population | undefined, set: (value: Population | undefined) => void) => {
        if ($populationStore && $populationStore.evaluationId === evaluationId) {
            set($populationStore)
        }
    })
    return evaluationPopulation
}

function populationHistory(population: Readable<Population | undefined>) {
    const populationHistory = arrayWritableFixedSize<Population>([], 10)
    population.subscribe($population => {
        // $population?.agents.sort((a, b) => a.species - b.species);

        if ($population) {
            populationHistory.push({
                ...$population,
                agents: [...$population?.agents || []].sort((a, b) => a.species - b.species)
            })

        }
    })
    return populationHistory
}

function populationScores(evaluationId: number, generations: Readable<number>) {
    const newScore = filterForEvaluation(evaluationId, newScoreStore)
    const agentScoreArray = arrayWritable<number>([])
    
    return {
        subscribe: (set: Subcriber<number[]>) => {
            let lastGeneration = -1
            const unsubscribeNewScore = newScore.subscribe(r => {
                if (r) {
                    while (agentScoreArray.length() <= r.agentId) {
                        agentScoreArray.push(0)
                    }
                    agentScoreArray.setElement(r.agentId, r.score)
                }
            })
            const unsubScoreArray = agentScoreArray.subscribe(set)
            const unsusbGenerations = generations.subscribe(g => {
                if (g != lastGeneration) {
                    lastGeneration = g
                    agentScoreArray.set([])
                }
            })
            return () => {
                unsubScoreArray()
                unsubscribeNewScore()
                unsusbGenerations()
            }
        }
    }
}
function filterForEvaluation<T extends { evaluationId: number } | undefined>(evaluationId: number, store: Readable<T>) {
    return derived(store, (value: T, set: (v: T) => void) => {
        if (value && value.evaluationId === evaluationId) {
            set(value)
        }
    })
}

const evaluationNewAgentMap = new Map<number, Readable<AgentModel | undefined>>()

function controllerAgents(evaluationId: number) {
    let newAgent: Readable<AgentModel | undefined>
    if (evaluationNewAgentMap.has(evaluationId)) {
        newAgent = evaluationNewAgentMap.get(evaluationId)!!
    } else {
        newAgent = filterForEvaluation(evaluationId, r.read("simulation.event.agent.new"))
        evaluationNewAgentMap.set(evaluationId, newAgent)
    }
    const controllerAgentMap = mapWritable<number, AgentController>(new Map())
    return {
        subscribe: (set: Subcriber<Map<number, AgentController>>) => {
            const unsubscribeNewAgent = newAgent.subscribe($newAgent => {
                if ($newAgent != undefined) {
                    controllerAgentMap.put($newAgent.controllerId, {
                        agent: $newAgent,
                        controllerId: $newAgent.controllerId
                    })
                }
            })
            const unsubscribeControllerMap = controllerAgentMap.subscribe(set)
            return () => {
                unsubscribeControllerMap
                unsubscribeNewAgent()
            }
        }
    }
}
type Subcriber<T> = (value: T) => void | Unsubscriber
type Unsubscriber = () => void