package server.message.endpoints

import PopulationEvolver
import neat.AdjustedFitnessCalculation
import neat.CompatibilityTest
import neat.model.NeatMutator

data class Simulation(
    val initialPopulation: List<NeatMutator>,
    val populationEvolver: PopulationEvolver,
    val adjustedFitnessCalculation: AdjustedFitnessCalculation,
    val evaluationId: Int,
    val standardCompatibilityTest: CompatibilityTest
)