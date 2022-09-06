package server.model

import kotlinx.serialization.Serializable


@Serializable
enum class NodeTypeModel {
    Input, Hidden, Output
}

@Serializable
data class NeatModel(val nodes: List<NodeGeneModel>, val connections: List<ConnectionGeneModel>)

@Serializable
data class NodeGeneModel(val node: Int, val bias: Float, val nodeType: NodeTypeModel, val activationFunction: String)

@Serializable
data class ConnectionGeneModel(
    val inNode: Int, val outNode: Int, val weight: Float, val enabled: Boolean, val innovation: Int
)

@Serializable
enum class Character {
    Pikachu, Link, Bowser, CaptainFalcon, DonkeyKong, DoctorMario,
    Falco, Fox, GameAndWatch, GannonDorf,
    JigglyPuff, Kirby, Luigi, Mario, Marth, MewTwo, Nana,
    Ness, Peach, Pichu, Popo,
    Roy, Samus, Sheik, YoungLink, Yoshi, Zelda
}

@Serializable
data class Model(val id: String, val neatModel: NeatModel, val character: Character, val score: Float)
@Serializable
data class ModelOwner(val id : String, val userName : String)
@Serializable
data class ModelMeta(val owner: ModelOwner, val model: Model, val modelName : String)