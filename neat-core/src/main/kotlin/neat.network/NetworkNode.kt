package neat.network

import neat.ActivationFunction

data class NetworkNode(val activationFunction: ActivationFunction, var value: Float, var activatedValue: Float, val bias : Float)

fun NetworkNode.activate() {
    value += bias
    activatedValue = this.activationFunction(value)
    value = 0f
}