package server

import Auth0Config
import Simulation
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import neat.ModelScore
import neat.SpeciationController
import neat.model.NeatMutator
import neat.toMap
import neat.toModelScores
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import org.slf4j.event.Level
import receivedAnyMessages
import server.message.endpoints.SpeciesLineageModel
import server.message.endpoints.SpeciesScoreKeeperModel
import server.message.endpoints.toModel
import server.server.WebSocketManager
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        allowSameOrigin = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
    install(CallLogging) {
        level = Level.INFO
    }
    val application = this
    install(Koin) {
        modules(applicationModule, org.koin.dsl.module {
            single { application }
            single {
                Json {
                    encodeDefaults = true
                }
            }
            single {
                with(environment.config) {
                    Auth0Config(
                        property("ktor.auth0.clientID").getString(),
                        property("ktor.auth0.clientSecret").getString(),
                        property("ktor.auth0.audience").getString(),
                        property("ktor.auth0.grantType").getString()
                    )
                }
            }
        })
    }
    val format = DateTimeFormatter.ofPattern("YYYYMMdd-HHmm")
    val runFolder = LocalDateTime.now().let { File("runs/run-${it.format(format)}") }
    runFolder.mkdirs()
    get<WebSocketManager>().attachWSRoute()
    val (initialPopulation, evaluationArena, populationEvolver, adjustedFitness) = get<Simulation>()
    launch(Dispatchers.IO) {
        var population = initialPopulation
        while (!receivedAnyMessages) {
            delay(100)
        }
        while (true) {
            launch(Dispatchers.IO) {
                val modelPopulationPersist = population.toModel()
                val savePopulationFile = runFolder.resolve("${populationEvolver.generation}.json")
                val json = Json { prettyPrint = true }
                val encodedModel = json.encodeToString(modelPopulationPersist)
                savePopulationFile.bufferedWriter().use {
                    it.write(encodedModel)
                    it.flush()
                }
                val manifestFile = runFolder.resolve("manifest.json")
                val manifestData = Manifest(
                    populationEvolver.scoreKeeper.toModel(),
                    populationEvolver.speciesLineage.toModel()
                )
                manifestFile.bufferedWriter().use {
                    it.write(json.encodeToString(manifestData))
                    it.flush()
                }

            }
            val modelScores = evaluationArena.evaluatePopulation(population).toModelScores(adjustedFitness)
            populationEvolver.sortPopulationByAdjustedScore(modelScores)
            populationEvolver.updateScores(modelScores)
            var newPopulation = populationEvolver.evolveNewPopulation(modelScores)
            populationEvolver.speciate(newPopulation)
            while (newPopulation.size < population.size) {
                newPopulation = newPopulation + newPopulation.first().clone()
            }
            population = newPopulation
        }
    }
}

private fun sortModelsByAdjustedFitness(
    speciationController: SpeciationController,
    modelScoreList: List<ModelScore>
): List<ModelScore> {
    val adjustedPopulationScore = modelScoreList.toMap { modelScore -> modelScore.neatMutator }
    val fitnessForModel: (NeatMutator) -> Float = { neatMutator ->
        adjustedPopulationScore.getValue(neatMutator).adjustedFitness
    }
    speciationController.sortSpeciesByFitness(fitnessForModel)
    return modelScoreList
}

fun previewMessage(frame: Frame.Text): String {
    val readText = frame.readText()
    val frameLength = readText.length
    return when {
        frameLength < 101 -> readText
        else -> {
            val messagePreview = readText.take(100)
            "$messagePreview...\n[[[rest of message has been trimmed]]]"
        }
    }
}

@Serializable
data class Manifest(val scoreKeeperModel: SpeciesScoreKeeperModel, val scoreLineageModel: SpeciesLineageModel)