package server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import server.model.NeatModel

@Serializable
data class Model(val id : String, val neatModel : NeatModel)
class DatabaseHelper(private val database : CoroutineDatabase) {
    suspend fun writeModel(model : Model) {
        val collection = database.getCollection<Model>()
        collection.insertOne(model)
    }
    suspend fun getModel(id: String): Model? {
        val collection = database.getCollection<Model>()
        return collection.findOne(Model::id eq id)
    }
}
class ModelRoutes(val databaseHelper: DatabaseHelper) {

    fun Application.configureRouting() {
        launch {
            val coroutine = KMongo.createClient().coroutine
//            coroutine.getDatabase()
        }
        routing {
            post<Model>("/model/store") {
                //store model to database
                databaseHelper.writeModel(it)
            }
            get("/model/{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("no id provided")
                val model = databaseHelper.getModel(id)
                if (model != null)
                    call.respond(model)
                else
                    call.respond(HttpStatusCode.BadRequest, "Bad request for $id")
            }
        }
    }
}
