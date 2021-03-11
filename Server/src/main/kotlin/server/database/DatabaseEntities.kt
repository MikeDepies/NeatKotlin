package server.database

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import java.time.*


class SimulationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SimulationEntity>(SimulationTable)

    val stage by MeleeStageEntity referencedOn SimulationTable.stage
    var startDate by SimulationTable.startDate.transform({ it.toEpochMilli() }, { Instant.ofEpochMilli(it) })
    val evaluations by EvaluationEntity referrersOn EvaluationTable.simulation
}

class EvaluationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationEntity>(EvaluationTable)

    var simulation by SimulationEntity referencedOn EvaluationTable.simulation
    val populations by EvaluationPopulationEntity referrersOn EvaluationPopulationTable.evaluation
    val species by EvaluationSpeciesEntity referrersOn EvaluationSpeciesTable.evaluation
    val configurations by EvaluationConfigurationEntity referrersOn EvaluationConfigurationTable.evaluation
}

class EvaluationPopulationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationPopulationEntity>(EvaluationPopulationTable)

    var evaluation by EvaluationEntity referencedOn EvaluationPopulationTable.evaluation
    var generation by EvaluationPopulationTable.generation
    val species by EvaluationSpeciesEntity referrersOn EvaluationSpeciesTable.evaluation
    val agents by AgentEntity referrersOn AgentTable.population
}

class EvaluationSpeciesEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationSpeciesEntity>(EvaluationSpeciesTable)

    var evaluation by EvaluationSpeciesEntity referencedOn EvaluationSpeciesTable.evaluation
    var speciesId by EvaluationSpeciesTable.speciesId
    var generationBorn by EvaluationSpeciesTable.generationBorn
    var mascot by AgentEntity referencedOn EvaluationSpeciesTable.mascot
    val scoreHistory by EvaluationSpeciesScoreEntity referrersOn EvaluationSpeciesScoreTable.species
}

class EvaluationSpeciesScoreEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationSpeciesScoreEntity>(EvaluationSpeciesScoreTable)

    var species by EvaluationSpeciesEntity referencedOn EvaluationSpeciesScoreTable.species
    var score by EvaluationSpeciesScoreTable.score
    var agent by AgentEntity referencedOn EvaluationSpeciesScoreTable.agent
    var generation by EvaluationSpeciesScoreTable.generation
}

class MeleeStageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MeleeStageEntity>(MeleeStageTable)

    var name by MeleeStageTable.name
    var stageId by MeleeStageTable.stageId
}

class MeleeCharacterEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MeleeCharacterEntity>(MeleeCharacterTable)

    var name by MeleeCharacterTable.name
    var characterId by MeleeCharacterTable.characterId
}

class EvaluationConfigurationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationConfigurationEntity>(EvaluationConfigurationTable)

    var evaluation by EvaluationEntity referencedOn EvaluationConfigurationTable.evaluation
    val parameters by EvaluationConfigurationParameterEntity referencedOn EvaluationConfigurationParameterTable.evaluationConfig
    val activationFunction by EvaluationConfigurationActivationFunctionEntity referrersOn EvaluationConfigurationActivationFunctionTable.evaluationConfig
    val mutationDictionary by EvaluationConfigurationMutationDictionaryEntryEntity referrersOn EvaluationConfigurationMutationDictionaryEntryTable.evaluationConfig
    val controllers by EvaluationConfigurationControllerEntity referrersOn EvaluationConfigurationControllerTable.configuration
}

class EvaluationConfigurationParameterEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationConfigurationParameterEntity>(EvaluationConfigurationParameterTable)

    var evaluationConfiguration by EvaluationConfigurationEntity referencedOn EvaluationConfigurationParameterTable.evaluationConfig
    var seed by EvaluationConfigurationParameterTable.seed
    var speciesDistance by EvaluationConfigurationParameterTable.speciesDistance
    var speciationExcess by EvaluationConfigurationParameterTable.speciationExcess
    var speciationDisjoint by EvaluationConfigurationParameterTable.speciationDisjoint
    var speciationAvgConnectionWeight by EvaluationConfigurationParameterTable.speciationAvgConnectionWeight
    var survivalThreshold by EvaluationConfigurationParameterTable.survivalThreshold
    var mateChance by EvaluationConfigurationParameterTable.mateChance
    var size by EvaluationConfigurationParameterTable.size
}

class EvaluationConfigurationActivationFunctionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object :
        IntEntityClass<EvaluationConfigurationActivationFunctionEntity>(EvaluationConfigurationActivationFunctionTable)

    var evaluationConfig by EvaluationConfigurationEntity referencedOn EvaluationConfigurationActivationFunctionTable.evaluationConfig
    var activationFunction by ActivationFunctionEntity referencedOn EvaluationConfigurationActivationFunctionTable.activationFunction
}

class EvaluationConfigurationMutationDictionaryEntryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationConfigurationMutationDictionaryEntryEntity>(
        EvaluationConfigurationMutationDictionaryEntryTable
    )

    var evaluationConfig by EvaluationConfigurationEntity referencedOn EvaluationConfigurationMutationDictionaryEntryTable.evaluationConfig
    var chanceToMutate by EvaluationConfigurationMutationDictionaryEntryTable.chanceToMutate
    var mutation by EvaluationConfigurationMutationDictionaryEntryTable.mutation
}

//class EvaluationConfigurationStagesEntity(id: EntityID<Int>) : IntEntity(id) {
//    companion object : IntEntityClass<EvaluationConfigurationStagesEntity>(EvaluationConfigurationStagesTable)
//
//    var evaluationConfig by EvaluationConfigurationEntity referencedOn EvaluationConfigurationStagesTable.configuration
//    var stage by (MeleeStageEntity referencedOn EvaluationConfigurationStagesTable.stage)
//}

class EvaluationConfigurationControllerEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EvaluationConfigurationControllerEntity>(EvaluationConfigurationControllerTable)

    var evaluationConfig by EvaluationConfigurationEntity referencedOn EvaluationConfigurationControllerTable.configuration
    var controllerId by EvaluationConfigurationControllerTable.controllerId
    var character by MeleeCharacterEntity referencedOn EvaluationConfigurationControllerTable.character
}

class AgentEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AgentEntity>(AgentTable)

    var population by EvaluationPopulationEntity referencedOn AgentTable.population
    var species by AgentTable.species
    val nodes by AgentNodeEntity referrersOn AgentNodeTable.agent
    val connections by AgentConnectionEntity referrersOn AgentConnectionTable.agent
}


class AgentNodeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AgentNodeEntity>(AgentNodeTable)

    var agent by AgentEntity referencedOn AgentNodeTable.agent
    var nodeId by AgentNodeTable.nodeId
    var type by NodeTypeEntity referencedOn AgentNodeTable.nodeType
    var activationFunction by ActivationFunctionEntity referencedOn AgentNodeTable.activationFunction
}

class AgentConnectionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AgentConnectionEntity>(AgentConnectionTable)

    var agent by AgentEntity referencedOn AgentConnectionTable.agent
    var inNode by AgentConnectionTable.inNode
    var outNode by AgentConnectionTable.outNode
    var weight by AgentConnectionTable.weight
    var enabled by AgentConnectionTable.enabled
    var innovation by AgentConnectionTable.innovation

}

class NodeTypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NodeTypeEntity>(NodeTypeTable)

    var name by NodeTypeTable.name
}

class ActivationFunctionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ActivationFunctionEntity>(ActivationFunctionTable)

    var name by ActivationFunctionTable.name
}


val DATABASE_TABLES = listOf(
    EvaluationPopulationTable,
    EvaluationSpeciesTable,
    EvaluationSpeciesScoreTable,
    MeleeStageTable,
    MeleeCharacterTable,
    EvaluationConfigurationTable,
    EvaluationConfigurationParameterTable,
    EvaluationConfigurationActivationFunctionTable,
    EvaluationConfigurationMutationDictionaryEntryTable,
    EvaluationConfigurationControllerTable,
    AgentTable,
    AgentNodeTable,
    AgentConnectionTable,
    NodeTypeTable,
    ActivationFunctionTable,
    EvaluationTable
)