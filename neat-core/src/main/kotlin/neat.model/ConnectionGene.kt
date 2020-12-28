package neat.model

data class ConnectionGene(
    var inNode: Int,
    var outNode: Int,
    var weight: Float,
    var enabled: Boolean,
    var innovation: Int
)