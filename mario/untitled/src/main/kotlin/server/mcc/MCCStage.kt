package server.mcc

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import neat.*
import neat.model.NeatMutator
import server.NetworkBlueprint
import server.mcc.smash.PopulationType

import java.util.*
import kotlin.math.min

import kotlin.random.Random


private val logger = KotlinLogging.logger { }


class MinimalCriterionStage(
    val random: Random,
    val batchSize: Int,
    val batchSizeEnv: Int,
    val resourceLimit: Int,
    agentSeeds: List<MCCElement<NeatMutator>>,
    agentEnvironmentSeeds: List<MCCElement<StageTrackGene>>,
    val populationCap: Int,
    var environmentSampleSize: Float,
    var agentSampleSize: Float
) {
    val agentPopulationQueue: Queue<MCCElement<NeatMutator>> = LinkedList(agentSeeds)
    val environmentPopulationQueue: Queue<MCCElement<StageTrackGene>> = LinkedList(agentEnvironmentSeeds)
    val environmentPopulationResourceMap = agentEnvironmentSeeds.associate { it.data.id to 0 }.toMutableMap()
//    var activePopulation = PopulationType.Agent


    fun stepAgent(
        neatExperiment: NeatExperiment,
        batchNumber: Int,
        offspringFunction: OffspringFunctionTargeted,
    ): MCCStageBatch {
        val parents = (0 until batchSize).map { agentPopulationQueue.poll() }
        parents.forEach {
            agentPopulationQueue.add(it)
        }

        val resourceViableEnvironments = environmentPopulationQueue.filter {
            environmentPopulationResourceMap.getValue(it.data.id) < resourceLimit
        }
        logger.info { "${PopulationType.Agent} -> ${resourceViableEnvironments.size}" }
        val children = createAgentChildren(neatExperiment, parents, offspringFunction, batchNumber)
//        val pairedAgents = children.map {
//            val agentEnvironment = resourceViableEnvironments.random(random)
//            PairedAgentsStage(it, agentEnvironment, PopulationType.Agent)
//        }
        val sampleSize = min((agentSampleSize * populationCap).toInt(), resourceViableEnvironments.size)
        val pairedAgents = children.flatMap { agent ->

            resourceViableEnvironments.shuffled(random).take(sampleSize).map { environment ->

                PairedAgentsStage(agent, environment, PopulationType.Agent)
            }
        }
        return MCCStageBatch(pairedAgents, PopulationType.Agent)
    }

//    fun togglePopulation() {
//        activePopulation = when (activePopulation) {
//            PopulationType.Agent -> PopulationType.Environment
//            PopulationType.Environment -> PopulationType.Agent
//        }
//    }

    private fun createAgentChildren(
        neatExperiment: NeatExperiment,
        reproducingPopulation: List<MCCElement<NeatMutator>>,
        offspringFunction: OffspringFunctionTargeted,
        batchNumber: Int
    ): List<MCCElement<NeatMutator>> {
        //insert reproduction logic
        val modelScores = agentPopulationQueue.map {
            val fakeScore = random.nextFloat()
            ModelScore(it.data, fakeScore, fakeScore)
        }
        return reproducingPopulation.map {
            val score = random.nextFloat()
            val neatMutator = offspringFunction(neatExperiment, modelScores, ModelScore(it.data, score, score))
            MCCElement(batchNumber, neatMutator)
        }
    }

    fun processBatchAgent(batchResult: MCCStageBatchResult) {
        logger.info { "processing ${batchResult.batchPopulationType}" }
        val childrenThatSatisfiedMC =
            batchResult.pairedAgents.filter { batchResult.mccMap[it.agent.data.id.toString()] ?: false }
                .distinctBy { it.agent.data.id }

        childrenThatSatisfiedMC.forEach {
            val resourceUsed = environmentPopulationResourceMap.getValue(it.environment.data.id) + 1
            environmentPopulationResourceMap[it.environment.data.id] =
                resourceUsed
            if (resourceUsed >= resourceLimit) {
                logger.info { "Agent ${it.environment.data.id} has hit resource limit $resourceUsed" }
            }
        }

        agentPopulationQueue.addAll(childrenThatSatisfiedMC.map { it.agent })
        if (agentPopulationQueue.size > populationCap) {
            val numberOfAgentsToRemove = agentPopulationQueue.size - populationCap
            val agentsToRemove = agentPopulationQueue.sortedBy { it.age }.take(numberOfAgentsToRemove)
            logger.info { "Agents to be removed:\n ${agentsToRemove.map { it.data.id.toString() to it.age }}" }
            agentPopulationQueue.removeAll(agentsToRemove.toSet())
            logger.info { "Removed Old Genes. Size of queue: ${agentPopulationQueue.size}" }
        }
    }

    fun stepEnvironment(
        neatExperiment: NeatExperiment,
        batchNumber: Int,
        offspringFunction: NeatExperiment.(StageTrackGene) -> StageTrackGene,
    ): MCCStageBatch {
        val parents = (0 until batchSizeEnv).map { environmentPopulationQueue.poll() }
        parents.forEach {
            environmentPopulationQueue.add(it)
        }
        val sampleSize = (environmentSampleSize * populationCap).toInt()
        logger.info { "${PopulationType.Environment} -> ${agentPopulationQueue.size}" }
        val children = createEnvironmentChildren(neatExperiment, parents, offspringFunction, batchNumber)

        val pairedAgents = children.flatMap { environment ->
            agentPopulationQueue.shuffled(random).take(sampleSize).map { agent ->

                PairedAgentsStage(agent, environment, PopulationType.Environment)
            }
        }
        return MCCStageBatch(pairedAgents, PopulationType.Environment)
    }

    private fun createEnvironmentChildren(
        neatExperiment: NeatExperiment,
        reproducingPopulation: List<MCCElement<StageTrackGene>>,
        offspringFunction: NeatExperiment.(StageTrackGene) -> StageTrackGene,
        batchNumber: Int
    ): List<MCCElement<StageTrackGene>> {
        //insert reproduction logic

        return reproducingPopulation.map {
            val neatMutator = offspringFunction(neatExperiment, it.data)
            MCCElement(batchNumber, neatMutator)
        }
    }

    fun processBatchEnvironment(batchResult: MCCStageBatchResult) {
        logger.info { "processing ${batchResult.batchPopulationType}" }
        val childrenThatSatisfiedMC =
            batchResult.pairedAgents.filter { batchResult.mccMap[it.environment.data.id] ?: false }
                .distinctBy { it.environment.data.id }

        childrenThatSatisfiedMC.forEach {
            environmentPopulationResourceMap[it.environment.data.id] = 0
        }

        environmentPopulationQueue.addAll(childrenThatSatisfiedMC.map { it.environment })
        if (environmentPopulationQueue.size > populationCap) {
            val numberOfAgentsToRemove = environmentPopulationQueue.size - populationCap
            val agentsToRemove = environmentPopulationQueue.sortedBy { it.age }.take(numberOfAgentsToRemove)
            logger.info { "Agents to be removed:\n ${agentsToRemove.map { it.data.id.toString() to it.age }}" }
            environmentPopulationQueue.removeAll(agentsToRemove.toSet())
            logger.info { "Removed Old Genes. Size of queue: ${environmentPopulationQueue.size}" }
        }
    }

}

//@Serializable
data class MCCElement<T>(val age: Int, val data: T)
data class MCCStageBatchResult(
    val pairedAgents: List<PairedAgentsStage>,
    val mccMap: Map<String, Boolean>,
    val batchPopulationType: PopulationType
)

data class MCCStageBatch(val pairedAgents: List<PairedAgentsStage>, val batchPopulationType: PopulationType)


data class PairedAgentsStage(
    val agent: MCCElement<NeatMutator>,
    val environment: MCCElement<StageTrackGene>,
    val type: PopulationType
)

@Serializable
data class PairedHyperAgentEnvironment(
    val agent: NetworkBlueprint,
    val environment: StageTrackGene,
    val type: PopulationType
)