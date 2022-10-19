package server

import kotlinx.serialization.Serializable
import server.message.endpoints.SpeciesLineageModel
import server.message.endpoints.SpeciesScoreKeeperModel

@Serializable
data class Manifest(val generation: Int, val scoreKeeperModel: SpeciesScoreKeeperModel, val scoreLineageModel: SpeciesLineageModel)