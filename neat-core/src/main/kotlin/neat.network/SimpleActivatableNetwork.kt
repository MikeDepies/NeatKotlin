package neat.network

import neat.ActivatableNetwork

class SimpleActivatableNetwork(
    private val inputNodes: List<NetworkNode>,
    override val outputNodes: List<NetworkNode>,
    private val computationStrategy: ComputationStrategy
) : ActivatableNetwork {
    private fun applyInputValues(inValues: List<Float>) {
        inValues.indices.forEach { inputNodes[it].value = inValues[it] }
    }

    override fun evaluate(input: List<Float>) {
        applyInputValues(input)
        computationStrategy()
    }

    override fun output(): List<Float> {
        return outputNodes.map { it.activatedValue }
    }

}