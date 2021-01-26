package server

import kotlinx.serialization.Serializable
import server.message.endpoints.NeatModel

@Serializable
data class AgentEvaluationData(val score: EvaluationScore, val agentId: Int)

@Serializable
data class PopulationDataCollection(
    var topEvaluation: AgentEvaluationData?,
    val scoreMap: MutableMap<Int, AgentEvaluationData>
)

@Serializable
data class PopulationModels(val agents: List<AgentModel>, val generation: Int)

@Serializable
data class AgentModel(val id: Int, val species: Int/*, val model: NeatModel*/)

@Serializable
data class EvaluationScore(val agentId: Int, val score: Float, val evaluationScoreContributions: List<EvaluationScoreContribution>)

@Serializable
data class EvaluationScoreContribution(val name: String, val score: Float, val contribution: Float)

@Serializable
data class EvaluationRecord(
    val populationModels: PopulationModels,
    val populationDataCollection: PopulationDataCollection
)

class EvaluationDataCollector {
    val evaluationRecordMap: MutableMap<Int, EvaluationRecord> = mutableMapOf()
    fun newPopulation(population: List<NeatModel>) {

    }
}
/**
 * Event system?
 *  New Species
 *  Species Died out
 *  New Top Agent For Population
 *  New Best Species
 *  Damage Done W/ Action ID and Amount
 */