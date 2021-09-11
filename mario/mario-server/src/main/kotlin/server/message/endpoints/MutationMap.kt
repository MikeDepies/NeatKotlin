package server.message.endpoints

import neat.mutation.*

fun mutationMap(): Map<String, Mutation> {
    return mapOf(
        "mutateConnections" to getMutateConnections(.1f),
        "mutateAddNode" to mutateAddNode,
        "mutateAddConnection" to mutateAddConnection,
        "mutatePerturbBiasConnections" to mutatePerturbBiasConnections(),
        "mutateToggleConnection" to mutateToggleConnection,
        "mutateNodeActivationFunction" to mutateNodeActivationFunction(),
    )
}