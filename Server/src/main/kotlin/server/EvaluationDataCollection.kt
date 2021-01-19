package server

import server.message.endpoints.*

data class AgentEvaluationData(val score: EvaluationScore, val agentId: Int)
data class PopulationDataCollection(
    var topEvaluation: AgentEvaluationData?,
    val scoreMap: MutableMap<Int, AgentEvaluationData>
)

data class PopulationModels(val models: Map<Int, AgentModel>)
data class AgentModel(val id: Int, val model: NeatModel)
data class EvaluationScore(val score: Float, val evaluationScoreContributions: List<EvaluationScoreContribution>)
data class EvaluationScoreContribution(val name: String, val score: Float, val contribution : Float)
data class EvaluationRecord(
    val generation: Int,
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