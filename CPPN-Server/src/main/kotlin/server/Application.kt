package server


import Auth0Config
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.*
import neat.*
import neat.model.*
import neat.mutation.*
import org.koin.core.parameter.*
import org.koin.ktor.ext.*
import org.slf4j.event.*
import server.message.endpoints.*
import java.time.*
import kotlin.math.*
import kotlin.random.*


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
    install(ContentNegotiation) {
        json(get())
    }
    val (initialPopulation, populationEvolver, adjustedFitnessCalculation, evaluationId) = get<Simulation>(parameters = {
        DefinitionParameters(
            listOf(0)
        )
    })
    val neatExperiment = populationEvolver.neatExperiment
    val json = get<Json>()
    var population = initialPopulation
//    launch {
//        val network = population[0].toNetwork()
//        network.evaluate(listOf(1f, 0f, 1f))
//        println(network.output())
//        network.evaluate(listOf(0f, 100f, 1f))
//        println(network.output())
//
//    }

    routing {
        get("image") {
            println("image hit")
            val selection = call.parameters["id"]?.toInt() ?: error("no id provided")
            val width = call.parameters["w"]?.toInt() ?: error("no width provided")
            val height = call.parameters["h"]?.toInt() ?: error("no height provided")
            val network = population[selection].toNetwork()
            val halfHeight = height / 2
            val halfWidth = width / 2
            val imageData = ((width/2) * -1 until width/2).map { x ->
                ((height/2) *-1 until height/2).map { y ->
                    network.run {
                        val xFloat = (x.toFloat())
                        val yFloat = (y.toFloat())
                        evaluate(listOf((xFloat), (yFloat), distanceFromCenter(xFloat,yFloat)))
//                            println("${listOf(xFloat, yFloat, distanceFromCenter(xFloat,yFloat))} -> ${output()}")
                        output().map {
                            when {
                                it.isNaN() -> 0f
                                it.isInfinite() -> 1f
                                else -> it
                            }
                        }
                    }
                }

            }

            call.respond(ModelImage(selection, imageData))
        }
        val poolContext = newFixedThreadPoolContext(20, "network-output").asExecutor()

        get("population") {
            val flow = population.mapIndexed { index, neatMutator -> index to neatMutator }.asFlow()

            call.respond(flow.flowOn(Dispatchers.Default).map { (index, neatMutator) ->
                val width = call.parameters["w"]?.toFloat()?.toInt() ?: 820 / 16
                val height = call.parameters["h"]?.toFloat()?.toInt() ?: 360 / 16
                val network = neatMutator.toNetwork()
                println("unfold $index network")
                val halfHeight = height / 2
                val halfWidth = width / 2
                val imageData = (halfWidth * -1 until halfWidth).map { x ->
                    (halfHeight *-1 until halfHeight).map { y ->
                        network.run {
                            val xFloat = (x.toFloat())
                            val yFloat = (y.toFloat())
                            evaluate(listOf((xFloat), (yFloat), distanceFromCenter(xFloat,yFloat)))

//                            println("${listOf(xFloat, yFloat, distanceFromCenter(xFloat,yFloat))} -> ${output()}")
                            output().map {
                                when {
                                    it.isNaN() -> 0f
                                    it.isInfinite() -> 1f
                                    else -> it
                                }
                            }
                        }
                    }

                }

                ModelImage(index, imageData)
            }.flowOn(Dispatchers.Default).toList())
        }
        get("selection") {
            val selection = call.parameters["id"]?.split(",")?.map { it?.toInt() } ?: error("no id provided")
            val size = call.parameters["n"]?.toInt() ?: population.size
            println("starting evolution")
            population = neatExperiment.evolvePopulation(population.filterIndexed { index, neatMutator -> selection.any { it == index } }, size, 1)
            println("finish evolution")
            call.respond(population.map { it.toModel() })
//            val selection = call.parameters["id"] ?: error("id parameter was not provided")
        }
    }
}

fun distanceFromCenter(x : Float, y: Float) = sqrt((x * x) + (y * y))
fun mutationDictionary(): List<MutationEntry> {
    return listOf(
        .8f chanceToMutate getMutateConnections(.1f),
        .4f chanceToMutate mutateAddNode,
        .4f chanceToMutate mutateAddConnection,
        .8f chanceToMutate mutatePerturbBiasConnections(),
        .21f chanceToMutate mutateToggleConnection,
        .15f chanceToMutate mutateNodeActivationFunction(),
    )
}
fun NeatExperiment.evolvePopulation(selection: List<NeatMutator>, numberOfOffspring: Int, evolutions: Int): List<NeatMutator> {
    val mutationDictionary = mutationDictionary()
    return (0 until numberOfOffspring).map {
        var s = selectOrMate(this, selection).mutateModel(mutationDictionary, this)
        repeat(evolutions) {
            s = s.mutateModel(mutationDictionary, this) }
        s
    }
}
fun probabilityToMate(neatExperiment: NeatExperiment) = rollFrom(.1f)(neatExperiment)

fun selectOrMate(neatExperiment: NeatExperiment, selection: List<NeatMutator>): NeatMutator {
    return when {
        probabilityToMate(neatExperiment) && selection.size > 1 -> {
            val randomParent1 = selection.random(neatExperiment.random)
            val randomParent2 = (selection - randomParent1).random(neatExperiment.random)
            neatExperiment.crossover(
                FitnessModel(randomParent1, 2f),
                FitnessModel(randomParent2, 1f)
            )
        }
        else -> selection.random(neatExperiment.random).clone()
    }
}

@Serializable
data class ModelSelection(val id: Int)

@Serializable
data class ModelImage(val id: Int, val imageData: List<List<List<Float>>>)