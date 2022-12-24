package server.mcc

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import server.NetworkBlueprint

import java.util.*

import kotlin.random.Random
@Serializable
enum class PopulationType {
    Agent, //Yoshi
    Environment // Falcon
}

private val logger = KotlinLogging.logger { }

data class NetworkMCC(val neatMutator: NeatMutator, val id: String, var age: Int)
class MinimalCriterion(
    val random: Random,
    val batchSize: Int,
    val batchSizeEnv: Int,
    val resourceLimit: Int,
    val agentSeeds: List<NetworkMCC>,
    val agentEnvironmentSeeds: List<NetworkMCC>,

    val populationCap: Int
) {
    val agentPopulationQueue: Queue<NetworkMCC> = LinkedList(agentSeeds)
    val environmentPopulationQueue: Queue<NetworkMCC> = LinkedList(agentEnvironmentSeeds)
    val environmentPopulationResourceMap = agentEnvironmentSeeds.map { it.id to 0 }.toMap().toMutableMap()
    var activePopulation = PopulationType.Agent

    val activePopulationQueue
        get() = queueForType(activePopulation)

    private fun queueForType(type: PopulationType) = when (type) {
        PopulationType.Agent -> agentPopulationQueue
        PopulationType.Environment -> environmentPopulationQueue
    }

    val activeBatchSize
        get() = when (activePopulation) {
            PopulationType.Agent -> batchSize
            PopulationType.Environment -> batchSizeEnv
        }

    //grab batchsize from active population
    //produce children
    //pair children with agent from opposite population
    //  if primary population, then only select agents that have resource available

    //Maybe associate pairs with some sort of ID so we can track the asynchronous progress of the given pair
    //Once all pairs are evaluated, add the children that satisfy the MC. Alternate the active population and repeat step
    fun step(
        neatExperiment: NeatExperiment,
        batchNumber: Int,
        offspringFunction: OffspringFunctionTargeted,
    ): MCCBatch {
        val parents = (0 until activeBatchSize).map { activePopulationQueue.poll() }
        parents.forEach {
            activePopulationQueue.add(it)
        }
        val batchType = activePopulation
        val resourceViableEnvironments = when (batchType) {
            PopulationType.Agent -> environmentPopulationQueue.filter { environmentPopulationResourceMap.getValue(it.id) < resourceLimit }
            PopulationType.Environment -> agentPopulationQueue
        }
        logger.info { "$activePopulation -> ${resourceViableEnvironments.size}" }
        val children = createChildren(neatExperiment, parents, offspringFunction, batchNumber)
        val pairedAgents = children.map {
            val agentEnvironment = resourceViableEnvironments.random(random)
            PairedAgents(it, agentEnvironment, batchType)
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
        reproducingPopulation: List<NetworkMCC>,
        offspringFunction: OffspringFunctionTargeted,
        batchNumber: Int
    ): List<NetworkMCC> {
        //insert reproduction logic
        val modelScores = activePopulationQueue.map {
            val fakeScore = random.nextFloat()
            ModelScore(it.neatMutator, fakeScore, fakeScore)
        }
        return reproducingPopulation.map {
            val score = random.nextFloat()
            val neatMutator = offspringFunction(neatExperiment, modelScores, ModelScore(it.neatMutator, score, score))
            NetworkMCC(neatMutator, neatMutator.id.toString(), batchNumber)
        }
    }

    fun processBatch(batchResult: MCCBatchResult) {
        logger.info { "processing ${batchResult.batchPopulationType}" }
        val queueForType = queueForType(batchResult.batchPopulationType)
        val childrenThatSatisfiedMC = batchResult.pairedAgents.filter { batchResult.mccMap.getValue(it.child.id) }
        if (batchResult.batchPopulationType == PopulationType.Agent) {
            childrenThatSatisfiedMC.forEach {
                val resourceUsed = environmentPopulationResourceMap.getValue(it.agent.id) + 1
                environmentPopulationResourceMap[it.agent.id] =
                    resourceUsed
                if (resourceUsed >= resourceLimit) {
                    logger.info { "Agent ${it.agent.id} has hit resource limit $resourceUsed" }
                }
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
            logger.info { "Agents to be removed:\n ${agentsToRemove.map { it.id to it.age }}" }
            queueForType.removeAll(agentsToRemove.toSet())
            logger.info { "Removed Old Genes. Size of queue: ${queueForType.size}" }
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

data class PairedAgents(val child: NetworkMCC, val agent: NetworkMCC, val type : PopulationType)


@Serializable
data class PairedHyperAgents(val agent: NetworkBlueprint, val agentEnvironment: NetworkBlueprint)
typealias OffspringFunctionTargeted = NeatExperiment.(Collection<ModelScore>, ModelScore) -> NeatMutator

fun offspringFunctionMCC(chance: Float, mutationEntries: List<MutationEntry>): OffspringFunctionTargeted {
    val probabilityToMate = rollFrom(chance)
    return { modelScoreList, target ->
        newOffspring(
            probabilityToMate,
            this,
            modelScoreList,
            mutationEntries,
            target
        )
    }
}

private fun newOffspring(
    probabilityToMate: MutationRoll,
    neatExperiment: NeatExperiment,
    speciesPopulation: Collection<ModelScore>,
    mutationEntries: List<MutationEntry>,
    modelScore: ModelScore
): NeatMutator {
    return when {
        probabilityToMate(neatExperiment) && speciesPopulation.size > 1 -> {
            val randomParent1 = modelScore
            val randomParent2 = (speciesPopulation - randomParent1).random(neatExperiment.random)
//            neat.validateNeatModel(randomParent1.neat.model.neatMutator)
//            neat.validateNeatModel(randomParent2.neat.model.neatMutator)
            neatExperiment.crossover(
                FitnessModel(randomParent1.neatMutator, randomParent1.adjustedFitness),
                FitnessModel(randomParent2.neatMutator, randomParent2.adjustedFitness),
                UUID.randomUUID()
            )
//                .also {
//                    try {
//                        neat.validateNeatModel(it)
//                    } catch (e: Exception) {
//                        println("Parent 1 - ${randomParent1.adjustedFitness}: ${randomParent1.neat.model.neatMutator}")
//                        println("Parent 2 - ${randomParent2.adjustedFitness}: ${randomParent2.neat.model.neatMutator}")
//                        throw(e)
//                    }
//                }
        }

        else -> modelScore.neatMutator.clone(UUID.randomUUID())
            .mutateModel(mutationEntries, neatExperiment)//.also { println("clone") }
    }
}
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

