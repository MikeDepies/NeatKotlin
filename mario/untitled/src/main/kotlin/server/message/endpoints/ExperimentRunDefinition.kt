package server.message.endpoints

import kotlinx.serialization.Serializable

@Serializable
data class ExperimentRunDefinition(
    val generations: Int,
    val populationSize: Int,
    val sharingThreshold: Float,
    val mateChance: Float,
    val survivalThreshold: Float,
    val activationFunctions: List<ActivationDefinition>,
    val mutations: List<MutationDefinition>,
    val randomSeed: Int = 0,
    val useBiasNode: Boolean = true
)
@Serializable
data class ActivationDefinition(val name : String)