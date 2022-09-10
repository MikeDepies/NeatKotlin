package server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.div
import org.litote.kmongo.eq
import server.model.Character
import server.model.ModelMeta
import server.model.ModelOwner


class DatabaseHelper(private val database : CoroutineDatabase) {
    suspend fun writeModel(model : ModelMeta) {
        val collection = database.getCollection<ModelMeta>()
        collection.insertOne(model)
    }
    suspend fun getModel(id: String): ModelMeta? {
        val collection = database.getCollection<ModelMeta>()
        return collection.findOne(ModelMeta::id eq id)
    }

    suspend fun getModelsForOwnerId(id : String): List<ModelMeta> {
        val collection = database.getCollection<ModelMeta>()
        return collection.find(ModelMeta::owner / ModelOwner::id eq id).toList()
    }
}
class ModelRoutes(private val databaseHelper: DatabaseHelper) {

    fun configureRouting(application: Application) = with(application) {
        routing {
            post<ModelMeta>("/model/store") {
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
            get("/model/check/{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("no id provided")
                val model = databaseHelper.getModel(id)
                if (model != null)
                    call.respond(ModelStatus(true))
                else
                    call.respond(ModelStatus(false))
            }
            get("/modeldescriptions/{ownerId}") {
                val ownerId = call.parameters["ownerId"] ?: throw Exception("Missing ownerID query parameter")
                val modelsForOwnerId = databaseHelper.getModelsForOwnerId(ownerId)
                val modelDescriptions = modelsForOwnerId.map { ModelDescription(it.id, it.modelName, it.character) }
                call.respond(modelDescriptions)
            }
        }
    }
}
@Serializable
data class ModelStatus(val exists : Boolean)

@Serializable
data class ModelDescription(val modelId: String, val modelName : String, val modelCharacter : Character)