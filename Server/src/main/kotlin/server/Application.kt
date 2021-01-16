package server

import Auth0Config
import server.message.endpoints.Simulation
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
import neat.*
import neat.model.NeatMutator
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.get
import org.slf4j.event.Level
import server.message.endpoints.receivedAnyMessages
import server.message.endpoints.*
import server.server.WebSocketManager
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*


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
            val modelScores = evaluationArena.evaluatePopulation(population) { simulationFrame ->
                inAirFromKnockback(simulationFrame)
                opponentInAirFromKnockback(simulationFrame)
                processDamageDone(simulationFrame)
                processStockTaken(simulationFrame)
                processStockLoss(simulationFrame)
                if (simulationFrame.wasGameLost) {
                    gameLostFlag = true
                    lastDamageDealt = 0f
                }
            }.toModelScores(adjustedFitness)
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

fun SimulationState.opponentInAirFromKnockback(simulationFrameData: SimulationFrameData) {
    if (!inAirFromKnockBack)
        inAirFromKnockBack = !simulationFrameData.opponentOnGround && prevTookDamage
    if (simulationFrameData.aiOnGround)
        inAirFromKnockBack = false
    if (distanceTime != null && Duration.between(distanceTime, simulationFrameData.now).seconds > distanceTimeGain) {
        distanceTime = null
        distanceTimeGain = 0f
    }
    if (distanceTime != null && simulationFrameData.distance > linearTimeGainDistanceEnd) {
        distanceTime = null
        distanceTimeGain = 0f
    }
}

fun SimulationState.inAirFromKnockback(simulationFrameData: SimulationFrameData) {
    if (!inAirFromKnockBack)
        inAirFromKnockBack = !simulationFrameData.aiOnGround && prevTookDamage
    if (simulationFrameData.aiOnGround)
        inAirFromKnockBack = false
    if (distanceTime != null && Duration.between(distanceTime, simulationFrameData.now).seconds > distanceTimeGain) {
        distanceTime = null
        distanceTimeGain = 0f
    }
    if (distanceTime != null && simulationFrameData.distance > linearTimeGainDistanceEnd) {
        distanceTime = null
        distanceTimeGain = 0f
    }
}

fun SimulationState.processDamageDone(simulationFrameData: SimulationFrameData) {
//    log.info { simulationFrameData.damageDoneFrame }
    if (simulationFrameData.damageDoneFrame > 0) {
        damageDoneTime = simulationFrameData.now

        val damage = if (gameLostFlag) 0f.also {
            gameLostFlag = false
        } else simulationFrameData.damageDoneFrame

        val secondsAiPlay = Duration.between(agentStart, simulationFrameData.now).seconds
        log.info { "Damage at $secondsAiPlay seconds" }
        log.info {
            "DamageAccumulation: $cumulativeDamageDealt + $damage -> (${cumulativeDamageDealt + damage})"
        }
        currentStockDamageDealt += damage
        cumulativeDamageDealt += damage
        if (secondsAiPlay < 0) {
            bonusGraceDamageApplied = true
            val gracePeriodHitBonus = gracePeriodHitBonus(
                t = secondsAiPlay.toFloat(),
                gracePeriod = 2f,
                bonus = 10
            )
            log.info { "Grace period damage bonus: $cumulativeDamageDealt + $gracePeriodHitBonus -> (${cumulativeDamageDealt + gracePeriodHitBonus})" }
            cumulativeDamageDealt += gracePeriodHitBonus
        }
    }
}

fun SimulationState.processStockTaken(simulationFrameData: SimulationFrameData) {
    if (simulationFrameData.wasOneStockTaken) {
        if (lastOpponentPercent < 100f && currentStockDamageDealt > 0) {
            val earlyKillBonus =
                (((100f - lastOpponentPercent) / max(1f, currentStockDamageDealt)) * 12)
            log.info {
                """
                        PercentCap: 100
                        LastEnemyPercent: $lastOpponentPercent
                        currentDamageDealt: $currentStockDamageDealt
                        Early Kill: $cumulativeDamageDealt + $earlyKillBonus -> (${cumulativeDamageDealt + earlyKillBonus})
                    """.trimIndent()
            }
            cumulativeDamageDealt += earlyKillBonus
            earlyKillBonusApplied = true
        } else log.info { "Kill but percent was over 100." }
        val doubledDamage = cumulativeDamageDealt * 2
        log.info {
            """
                            Stock taken: $lastOpponentStock -> ${simulationFrameData.opponentStockFrame} (${simulationFrameData.wasOneStockTaken})
                            Damage: $lastDamageDealt -> ${simulationFrameData.damageDoneFrame}
                            CumulativeDamage: $cumulativeDamageDealt -> $doubledDamage
                        """.trimIndent()
        }

        cumulativeDamageDealt = doubledDamage
        stockTakenTime = simulationFrameData.now

    }
}

fun SimulationState.processStockLoss(simulationFrameData: SimulationFrameData) {
    if (lastAiStock != simulationFrameData.aiStockFrame) {
        log.info { "Stocks not equal! $lastAiStock -> ${simulationFrameData.aiStockFrame}" }
    }
    if (simulationFrameData.stockLoss)
        log.info { "Stock was lost: $cumulativeDamageDealt > 0 (${cumulativeDamageDealt > 0}) - ${simulationFrameData.stockLoss} - ${simulationFrameData.wasStockButNotGameLost}" }
    if (simulationFrameData.stockLoss && cumulativeDamageDealt > 0) {
        damageTimeFrame -= .25f
        timeGainMax -= 2f
        val sqrt = if (inAirFromKnockBack) (cumulativeDamageDealt) / 8 else sqrt(cumulativeDamageDealt)
        log.info {
            """
                diedFromKnockBack: $inAirFromKnockBack
                Stock Lost: $lastAiStock -> ${simulationFrameData.aiStockFrame} (${simulationFrameData.wasStockButNotGameLost})
                CumulativeDamage: $cumulativeDamageDealt -> $sqrt
            """.trimIndent()
        }
        if (stockLostTime != null && Duration.between(simulationFrameData.now, stockLostTime).seconds < 4) {
            log.info { "Double quick death... be gone" }
            doubleDeathQuick = true
        }
        stockLostTime = simulationFrameData.now
        cumulativeDamageDealt = sqrt
        currentStockDamageDealt = 0f
    }
}

fun gracePeriodHitBonus(t: Float, gracePeriod: Float, bonus: Int = 20): Float {
    return bonus * (1 - (t / gracePeriod))
}