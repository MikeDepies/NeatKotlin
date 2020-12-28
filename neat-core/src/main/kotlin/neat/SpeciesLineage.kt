package neat

import neat.model.NeatMutator

class SpeciesLineage(species: List<SpeciesGene> = listOf()) {
    private val map: MutableMap<Species, SpeciesGene> = species.toMap { it.species }.toMutableMap()
    fun addSpecies(species: Species, generationBorn: Int, mascot: NeatMutator) {
        map[species] = SpeciesGene(species, generationBorn, mascot)
    }
    val species get() = map.keys

    fun mascot(species: Species) = map.getValue(species).mascot

//    fun createSpecies(neat.model.neatMutator: neat.model.NeatMutator): neat.Species = neat.Species(speciesInnovation++).also { addSpecies(it, 0, neat.model.neatMutator) }

    fun compatibleSpecies(neatMutator: NeatMutator, compatible: CompatibilityTest): Species? {
        return map.keys.firstOrNull {
            val avatarForSpecies = map[it]?.mascot
            avatarForSpecies != null && compatible(neatMutator, avatarForSpecies)
        }
    }

    fun updateMascot(species: Species, mascot: NeatMutator) {
        map[species] = map.getValue(species).copy(mascot = mascot)
    }
}

class SpeciesScoreKeeper {
    private val scoreMap = mutableMapOf<Species, ModelScore>()
    fun getModelScore(species: Species?) = scoreMap[species]
    fun updateScores(modelScores: List<Pair<Species, ModelScore>>) {
        modelScores.forEach { (species, modelScore) ->
            if (scoreMap.containsKey(species)) {
                if (modelScore.fitness > scoreMap.getValue(species).fitness) {
//                    println("update $species - ${modelScore.fitness} > ${scoreMap.getValue(species).fitness}")
                    scoreMap[species] = modelScore
                }
            } else {
                scoreMap[species] = modelScore
            }
        }
    }

    fun bestSpecies(): Species? {
        return scoreMap.maxByOrNull { it.value.fitness }?.key
    }
}

/**
 *
, speciesProvider: (neat.model.NeatMutator) -> neat.Species) {
modelScores.groupBy { speciesProvider(it.neat.model.neatMutator) }
.map {
val key = it.key
val bestInSpecies = it.value.maxByOrNull { modelScore -> modelScore.adjustedFitness }
key to bestInSpecies
}
}
 */

fun <T, K> List<T>.toMap(key: (T) -> K): Map<K, T> {
    return map { key(it) to it }.toMap()
}

data class SpeciesGene(val species: Species, val generationBorn: Int, val mascot: NeatMutator)