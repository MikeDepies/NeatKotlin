package neat

import neat.model.NeatMutator
import neat.mutation.*

fun mutationDictionary(): List<MutationEntry> {
    return listOf(
        .8f chanceToMutate getMutateConnections(.05f),
        .4f chanceToMutate mutateAddNode,
        .4f chanceToMutate mutateAddConnection,
        .1f chanceToMutate mutatePerturbBiasConnections(),
        .11f chanceToMutate mutateToggleConnection,
        .1f chanceToMutate mutateNodeActivationFunction(),
    )
}


fun NeatBuilder.generationRules(): GenerationRules {
    val speciationController =
        SpeciationController(0)
    return GenerationRules(
        speciationController,
        adjustedFitnessCalculation(speciationController, distanceFunction, sharingFunction),
        reproductionStrategy,
        evaluationFunction
    )
}
//
//fun neat(speciesScoreKeeper: SpeciesScoreKeeper, mutationEntries: List<MutationEntry>, builder: NeatBuilder.() -> Unit): Neat {
//    val neatBuilder = NeatBuilder(mutationEntries, speciesScoreKeeper)
//    neatBuilder.builder()
//    return Neat(neatBuilder.generationRules())
//}

class NeatBuilder(mutationEntries: List<MutationEntry>, speciesScoreKeeper: SpeciesScoreKeeper) {
    var sharingFunction: SharingFunction = shFunction(3f)
    var distanceFunction: DistanceFunction = compatibilityDistanceFunction(1f, 1f, 1f)
    var reproductionStrategy: ReproductionStrategy = weightedReproduction(
        speciesScoreKeeper,
        mutationEntries,
        .4f,
        .6f,
        15,
        1
    )
    var evaluationFunction: PopulationEvaluator = { error("need to provide a evaluator function") }
}

fun compatibilityDistanceFunction(c1: Float, c2: Float, c3: Float): DistanceFunction = { a, b ->
    compatibilityDistance(a, b, c1, c2, c3)
}


fun weightedReproduction(
    speciesScoreKeeper: SpeciesScoreKeeper,
    mutationEntries: List<MutationEntry>,
    mateChance: Float,
    survivalThreshold: Float,
    stagnation: Int,
    championThreshold : Int
): NeatExperiment.(SpeciationController, List<ModelScore>, Int) -> List<NeatMutator> {
    return { speciationController, modelScoreList, generation ->
        populateNextGeneration(
            generation,
            speciationController, speciesScoreKeeper, modelScoreList, mutationEntries, this,
            mateChance,
            survivalThreshold,
            stagnation,
            championThreshold
        )
    }
}

fun adjustedFitnessCalculation(
    speciationController: SpeciationController,
    df: DistanceFunction,
    sharingFunction: SharingFunction
): AdjustedFitnessCalculation =
    { fitnessModel ->
        val adjustedFitnessCalculation =
            adjustedFitnessCalculation(speciationController.population(), fitnessModel, df, sharingFunction)
        adjustedFitnessCalculation
    }

fun NeatExperiment.generateInitialPopulation(
    populationSize: Int, numberOfInputNodes: Int, numberOfOutputNodes: Int, function: ActivationGene
): List<NeatMutator> {
    val neatMutator = createNeatMutator(numberOfInputNodes, numberOfOutputNodes, random, function)
    return (0 until populationSize).map {
        val clone = neatMutator.clone()
        getMutateConnections(1f)(this, clone)
        clone
    }
}

fun standardCompatibilityTest(
    sharingFunction: SharingFunction,
    df: DistanceFunction
): CompatibilityTest = { neat1, neat2 ->
    val distance = df(neat1, neat2)

    CompatibilityResult(distance, sharingFunction(distance) == 1)
}

