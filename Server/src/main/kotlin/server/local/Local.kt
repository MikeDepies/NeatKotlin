package server.local

import kotlinx.serialization.Serializable
import neat.model.NeatMutator
import server.ActionBehavior

@Serializable
data class ModelTestResult(val available : Boolean, val scored : Boolean, val valid : Boolean)


@Serializable
data class ModelEvaluationResult(val controllerId: Int, val modelId: String, val score : ActionBehavior)


@Serializable
data class ModelRequest(val controllerId: Int, val modelId: String)


@Serializable
data class ModelsRequest(val controllerId: Int)


@Serializable
data class ModelStatus(var available : Boolean, var score: Float?, var neatMutator : NeatMutator?)
@Serializable
data class ModelsStatusResponse (val ready : Boolean, val modelIds : List<String>)