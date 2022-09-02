package server.mcc

import createMutationDictionary
import kotlinx.serialization.Serializable
import neat.*
import neat.model.NeatMutator
import server.NetworkBlueprint
import server.generateInitialPopulation2
import java.util.*

import kotlin.random.Random

enum class PopulationType {
    Agent, Environment
}

data class NetworkWithId(val neatMutator: NeatMutator, val id: String, var age: Int)
class MinimalCriterion(
    val random: Random,
    val batchSize: Int,
    val batchSizeEnv: Int,
    val resourceLimit: Int,
    val agentSeeds: List<NetworkWithId>,
    val agentEnvironmentSeeds: List<NetworkWithId>,

    val populationCap: Int
) {
    val agentPopulationQueue: Queue<NetworkWithId> = LinkedList(agentSeeds)
    val environmentPopulationQueue: Queue<NetworkWithId> = LinkedList(agentEnvironmentSeeds)
    val environmentPopulationResourceMap = agentEnvironmentSeeds.map { it.id to 0 }.toMap().toMutableMap()
    var activePopulation = PopulationType.Agent

    val activePopulationQueue
        get() = queueForType(activePopulation)

    private fun queueForType(type: PopulationType) = when (type) {
        PopulationType.Agent -> agentPopulationQueue
        PopulationType.Environment -> environmentPopulationQueue
    }

    val activeBatchSize get() = when(activePopulation) {
        PopulationType.Agent -> batchSize
        PopulationType.Environment -> batchSizeEnv
    }

    //grab batchsize from active population
    //produce children
    //pair children with agent from opposite population
    //  if primary population, then only select agents that have resource available

    //Maybe associate pairs with some sort of ID so we can track the asynchronous progress of the given pair
    //Once all pairs are evaluated, add the children that satisfy the MC. Alternate the active population and repeat step
    fun step(neatExperiment: NeatExperiment, batchNumber: Int, offspringFunction: OffspringFunction,): MCCBatch {
        val parents = (0 until activeBatchSize).map { activePopulationQueue.poll() }
        parents.forEach {
            activePopulationQueue.add(it)
        }
        val batchType = activePopulation
        val resourceViableEnvironments = when (batchType) {
            PopulationType.Agent -> environmentPopulationQueue.filter { environmentPopulationResourceMap.getValue(it.id) < resourceLimit }
            PopulationType.Environment -> environmentPopulationQueue
        }
        val children = createChildren(neatExperiment, parents, offspringFunction, batchNumber)
        val pairedAgents = children.map {
            val agentEnvironment = resourceViableEnvironments.random(random)
            PairedAgents(it, agentEnvironment)
        }
        return MCCBatch(pairedAgents, batchType)
    }

    fun togglePopulation() {
        activePopulation = when (activePopulation) {
            PopulationType.Agent -> PopulationType.Environment
            PopulationType.Environment -> PopulationType.Agent
        }
    }

    fun createChildren(
        neatExperiment: NeatExperiment,
        reproducingPopulation: List<NetworkWithId>,
        offspringFunction: OffspringFunction,
        batchNumber: Int
    ): List<NetworkWithId> {
        //insert reproduction logic
        val modelScores = activePopulationQueue.map { ModelScore(it.neatMutator, 0f, 0f) }
        return reproducingPopulation.map {
            val neatMutator = offspringFunction(neatExperiment, modelScores)
            NetworkWithId(neatMutator, UUID.randomUUID().toString(), batchNumber)
        }
    }

    fun processBatch(batchResult: MCCBatchResult) {
        val queueForType = queueForType(batchResult.batchPopulationType)
        val childrenThatSatisfiedMC = batchResult.pairedAgents.filter { batchResult.mccMap.getValue(it.child.id) }
        if (batchResult.batchPopulationType == PopulationType.Agent) {
            childrenThatSatisfiedMC.forEach {
                environmentPopulationResourceMap[it.agent.id] =
                    environmentPopulationResourceMap.getValue(it.agent.id) + 1
            }
        } else if (batchResult.batchPopulationType == PopulationType.Environment) {
            childrenThatSatisfiedMC.forEach {
                environmentPopulationResourceMap[it.child.id] = 0
            }
        }
        queueForType.addAll(childrenThatSatisfiedMC.map { it.child })
        if (queueForType.size > populationCap) {
            val numberOfAgentsToRemove = queueForType.size - populationCap
            val agentsToRemove = queueForType.sortedBy { it.age }.take(numberOfAgentsToRemove)
            queueForType.removeAll(agentsToRemove.toSet())
        }
    }

}

class MCSeeder(val seedSize: Int) {

}

fun startMC(resourceLimit: Int, batchSize: Int, seedPopulation1: MCSeeder, seedPopulation2: MCSeeder) {
    //Evolve both populations

}

fun mcMain() {


}

data class MCCBatchResult(
    val pairedAgents: List<PairedAgents>,
    val mccMap: Map<String, Boolean>,
    val batchPopulationType: PopulationType
)

data class MCCBatch(val pairedAgents: List<PairedAgents>, val batchPopulationType: PopulationType)

data class PairedAgents(val child: NetworkWithId, val agent: NetworkWithId)


@Serializable
data class PairedHyperAgents(val agent: NetworkBlueprint, val agentEnvironment: NetworkBlueprint)

/*
    Need to seed two populations that that produce X agents that satisfy a provided minimal-criterion.
    The X agents of each population are placed into a queue
    Each population queue is alternately drawn from in batches
        A given batch is pulled from a queue, and children produced from it are tested.
        The batch that was pulled from the queue is immediately added back to the queue.
        For each child produced, we select an agent from the opposite population.
            If the opposite population is the resource limited population, we ensure that the selected agent has resource
            remaining. If not, we continue to select another agent until we come across one that is not used up.
        We then evaluate the two agents together (by together this means they are paired in their task in some way)
        If the newly created child is able to satisfy the minimal criterion, we add it to the population queue.
 */

