package server

import kotlinx.serialization.Serializable

@Serializable
data class EvaluatorSettings(val attackTime: Int, val maxTime: Int, val actionLimit: Int)

@Serializable
enum class MeleeStage {
    FinalDestination, BattleField, PokemonStadium, Dreamland, FountainOfDreams
}

@Serializable
data class ControllerConfiguration(val character: Character, val cpuLevel: Int)

@Serializable
data class PythonConfiguration(
    val evaluatorSettings: EvaluatorSettings,
    val player1: ControllerConfiguration,
    val player2: ControllerConfiguration,
    val stage: MeleeStage,
    val frameDelay : Int
)