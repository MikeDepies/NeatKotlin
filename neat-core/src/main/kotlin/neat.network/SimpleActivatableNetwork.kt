package neat.network

import neat.ActivatableNetwork

class SimpleActivatableNetwork(
    private val inputNodes: List<NetworkNode>,
    override val outputNodes: List<NetworkNode>,
    private val computationStrategy: ComputationStrategy
) : ActivatableNetwork {
    private fun applyInputValues(inValues: List<Float>, bias: Boolean) {
        if (bias) {
            inputNodes[0].value = 1f
            inValues.indices.forEach { inputNodes[it + 1 ].value = inValues[it] }
        } else {
            inValues.indices.forEach { inputNodes[it].value = inValues[it] }
        }
    }

    override fun evaluate(input: List<Float>, bias : Boolean) {
        applyInputValues(input, bias)
        computationStrategy()
    }

    override fun output(): List<Float> {
        return outputNodes.map { it.activatedValue }
    }

}