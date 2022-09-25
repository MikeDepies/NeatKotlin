package server.refactor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import server.NetworkWithId
import server.message.endpoints.NeatModel
import java.io.File

data class PersistableModel(val id : String, val neatModel: NeatModel)

class ModelWriter(val json : Json) {

}