package server.database

import com.zaxxer.hikari.*
import kotlinx.serialization.*
import org.jetbrains.exposed.dao.id.*

/*
    Evaluator: ResourceEvaluator + its configs/parameters (which will grow more than they are now with dynamic rules)
    NeatAlgorithm Configurations:
        Species distance, distanceFunctionParameters(excess,disjoint,avgWeights)
        mutationDictionary
        populationEvolver configurations (survivalThreshold, mateChance, ...)
        randomSeed
        Network Input & Output mappings (frameData, controller output, and/or other mechanisms)
 */

object EvaluationPopulationTable : IntIdTable() {
    val evaluation = reference("evaluation", EvaluationTable)
    val generation = integer("generation")
}

object EvaluationSpeciesTable : IntIdTable() {
    val evaluation = reference("evaluation", EvaluationTable)
    val speciesId = integer("speciesId")
    val generationBorn = integer("generationBorn")
    val mascot = reference("mascot", AgentTable)
}

object EvaluationSpeciesScoreTable : IntIdTable() {
    val species = reference("species", EvaluationSpeciesTable)
    val score = float("score")
    val agent = reference("agent", AgentTable)
}

object MeleeStageTable : IntIdTable() {
    val name = varchar("name", 20)
    val stageId = integer("stageId")
}

object MeleeCharacterTable : IntIdTable() {
    val name = varchar("name", 20)
    val characterId = integer("characterId")
}

object EvaluationConfigurationTable : IntIdTable() {
    val evaluation = reference("evaluation", EvaluationTable)
}

object EvaluationConfigurationParameterTable : IntIdTable() {
    val seed = integer("seed")
    val evaluationConfig = reference("evaluationConfig", EvaluationConfigurationTable)
    val speciesDistance = float("speciesDistance")
    val speciationExcess = float("speciationExcess")
    val speciationDisjoint = float("speciationDisjoint")
    val speciationAvgConnectionWeight = float("AvgConnectionWeight")
    val survivalThreshold = float("survivalThreshold")
    val mateChance = float("mateChance")
    val size = integer("size")
}

object EvaluationConfigurationActivationFunctionTable : IntIdTable() {
    val evaluationConfig = reference("evaluationConfig", EvaluationConfigurationTable)
    val activationFunction = reference("activationFunction", ActivationFunctionTable)
}
object EvaluationConfigurationMutationDictionaryEntryTable : IntIdTable() {
    val evaluationConfig = reference("evaluationConfig", EvaluationConfigurationTable)
    val chanceToMutate = float("chanceToMutate")
    val mutation = varchar("mutation", 30)
}

object EvaluationConfigurationStagesTable : IntIdTable() {
    val configuration = reference("configuration", EvaluationConfigurationTable)
    val stage = reference("stage", MeleeStageTable)
}

object EvaluationConfigurationControllerTable : IntIdTable() {
    val configuration = reference("configuration", EvaluationConfigurationTable)
    val controllerId = integer("controllerId")
    val character = reference("character", MeleeCharacterTable)
}


object AgentTable : IntIdTable() {
    val population = reference("population", EvaluationPopulationTable)
    val species = integer("species")
}

object AgentNodeTable : IntIdTable() {
    val agent = reference("agent", AgentTable)
    val nodeId = integer("nodeId")
    val nodeType = reference("nodeType", NodeTypeTable)
    val activationFunction = reference("activationFunction", ActivationFunctionTable)
}

object AgentConnectionTable : IntIdTable() {
    val agent = reference("agent", AgentTable)
    val inNode = integer("inNode")
    val outNode = integer("outNode")
    val weight = float("weight")
    val enabled = bool("enabled")
    val innovation = integer("innovation")
}

object NodeTypeTable : IntIdTable() {
    val name = varchar("value", 10)
}

object ActivationFunctionTable : IntIdTable() {
    val name = varchar("name", 30)

}

object EvaluationTable : IntIdTable() {
    val simulation = reference("simulation", SimulationTable)

}

object SimulationTable : IntIdTable() {
    val startDate = long("startDate")
}

fun DbConfig.toHikariConfig(): HikariConfig {
    val config = HikariConfig()
    config.driverClassName = "com.mysql.jdbc.Driver"
    config.jdbcUrl =
        "jdbc:mysql://$host:$port/$schema?rewriteBatchedStatements=true&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC"
    config.username = username
    config.password = password
    config.maximumPoolSize = maxPoolSize
    config.minimumIdle = minIdle
    config.isAutoCommit = false
    return config
}

@Serializable
data class DbConfig(
    val host: String,
    val port: Int,
    val schema: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 100,
    val minIdle: Int = 2
)

fun main() {
    val s = """
    """.trimIndent()
    val list = s.split("\n").map { it.split(" = ") }.map { it[0] to it[1].toInt() }
    println(list.map { it.first }) //enums
    list.map { "${it.first} -> ${it.second}" }.joinToString("\n").also { println(it) }

}

