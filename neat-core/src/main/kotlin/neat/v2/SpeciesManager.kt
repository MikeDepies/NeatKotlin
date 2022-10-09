package neat.v2

import neat.ModelScore
import neat.Species
import neat.model.NeatMutator
data class SpeciesDescription(val mascot : NeatMutator, val generationCreated : Int)
data class SingleSpeciesReport(
    val champions: List<NeatMutator>,
    val speciesOffspringMap: Int,
    val overallAverageFitness: Double,
    val populationScore: List<ModelScore>
)
class SpeciesManager(species : List<Species>) {
}