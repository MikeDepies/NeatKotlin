package neat

import neat.model.NeatMutator

//data class Population(val nodeInnovation: Int, val connectionInnovation: Int, val population: List<neat.model.NeatMutator>)
data class GenerationRules(
//    val activationFunctions: List<neat.neat.ActivationFunction>,
    val speciationController: SpeciationController,
    val adjustedFitness: AdjustedFitnessCalculation,
    val reproductionStrategy: ReproductionStrategy,
    val populationEvaluator: PopulationEvaluator
)

typealias ReproductionStrategy = NeatExperiment.(SpeciationController, List<ModelScore>) -> List<NeatMutator>
typealias PopulationEvaluator = (List<NeatMutator>) -> List<FitnessModel<NeatMutator>>