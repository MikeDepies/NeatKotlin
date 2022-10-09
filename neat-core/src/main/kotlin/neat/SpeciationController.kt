package neat

import neat.model.NeatMutator
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

//val neat.Species?.notNull get() = null != this

class SpeciationController(
    private var speciesId: Int
) {
    private val neatMutatorToSpeciesMap = mutableMapOf<NeatMutator, Species>()
    private val speciesMap = mutableMapOf<Species, MutableList<NeatMutator>>()
    private fun nextSpecies(): Species = Species(speciesId++)
    fun hasSpeciesFor(neatMutator: NeatMutator) = neatMutatorToSpeciesMap.containsKey(neatMutator)
    fun speciate(
        population: List<NeatMutator>,
        speciesLineage: SpeciesLineage,
        generation: Int,
        compatibilityTest: CompatibilityTest
    ): Map<Species, MutableList<NeatMutator>> {
        speciesMap.clear()

        neatMutatorToSpeciesMap.clear()
        population.forEach { neatMutator ->
            val compatibleSpecies = speciesLineage.compatibleSpecies(neatMutator, compatibilityTest)
            val species = if (compatibleSpecies != null) compatibleSpecies else {
                val nextSpecies = nextSpecies()
                speciesLineage.addSpecies(nextSpecies, generation, neatMutator)
                nextSpecies
            }
            if (!speciesMap.containsKey(species)) {
                speciesMap[species] = mutableListOf()
            }
            addSpecies(neatMutator, species)
        }
        return speciesMap.toMap()
    }

    private fun addSpecies(
        neatMutator: NeatMutator,
        species: Species
    ): Species {
        val speciesSet = speciesMap.getValue(species)
        speciesSet += neatMutator
        neatMutatorToSpeciesMap[neatMutator] = species
        return species
    }

    val speciesSet: Set<Species> get() = speciesMap.keys.toSet()
    fun getSpeciesPopulation(species: Species) = speciesMap.getValue(species)
    fun sortSpeciesByFitness(fitnessForModelFn: (NeatMutator) -> Float) {
        speciesSet.forEach {
            speciesMap[it] = getSpeciesPopulation(it).sortedByDescending(fitnessForModelFn).toMutableList()
        }
    }

    fun species(neatMutator: NeatMutator) = neatMutatorToSpeciesMap.getValue(neatMutator)
    fun population(): List<NeatMutator> {
        return speciesMap.values.flatten()
    }

}