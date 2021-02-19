package neat.model

import neat.ActivationGene


data class NodeGene(var node: Int, var bias: Float, val nodeType: NodeType, var activationFunction: ActivationGene) {
//    var value: Float = 0f
}


data class SerializableNodeGene(val node: Int, var bias: Float, val nodeType: NodeType, val activationFunction: String)


fun SerializableNodeGene.toNodeGene(activationFunctionDictionary: Map<String, ActivationGene>): NodeGene {
    return NodeGene(node, bias, nodeType, activationFunctionDictionary.getValue(activationFunction))
}

fun NodeGene.toSerializable() = SerializableNodeGene(node, bias, nodeType, activationFunction.name)
