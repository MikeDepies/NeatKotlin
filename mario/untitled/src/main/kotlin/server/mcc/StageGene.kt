package server.mcc

import kotlinx.serialization.Serializable
import neat.MutationRoll
import neat.NeatExperiment
import neat.rollFrom
import java.lang.Integer.max
import java.util.*

fun createStageMutationDictionary(): List<StageMutationEntry> {
    return listOf(
        .5f chanceToMutate NeatExperiment::mutateStage,
//        .05f chanceToMutate NeatExperiment::mutateCoin,
//        .1f chanceToMutate NeatExperiment::mutateScore,
        .9f chanceToMutate NeatExperiment::mutateDistance
    )
}


fun createStageTrackMutationDictionary(stageGeneMutations: List<StageMutationEntry>): List<StageTrackMutationEntry> {
    return listOf(
        .9f chanceToMutate mutateStageGenes(stageGeneMutations),
        .05f chanceToMutate NeatExperiment::mutateAddStage,
        .1f chanceToMutate NeatExperiment::mutateShuffleStages
    )
}

fun mutateStageGenes(stageGeneMutations: List<StageMutationEntry>): StageTrackMutation {
    return { stageTrackGene ->
        val newStageGenes = stageTrackGene.stages.map {
            mutateStageGene(it, stageGeneMutations)
        }
        StageTrackGene(newStageGenes, UUID.randomUUID().toString())
    }
}

private fun NeatExperiment.mutateStageGene(
    it: StageGene,
    stageGeneMutations: List<StageMutationEntry>
): StageGene {
    var stageGene = it
    for (it in stageGeneMutations) {
        if (it.roll(this)) {
            stageGene = it.mutation(this, stageGene)
        }
    }
    return stageGene
}

infix fun Float.chanceToMutate(mutation: StageMutation) = StageMutationEntry(rollFrom(this), mutation)
infix fun Float.chanceToMutate(mutation: StageTrackMutation) = StageTrackMutationEntry(rollFrom(this), mutation)
data class StageMutationEntry(val roll: MutationRoll, val mutation: StageMutation)
data class StageTrackMutationEntry(val roll: MutationRoll, val mutation: StageTrackMutation)
typealias StageMutation = NeatExperiment.(StageGene) -> StageGene
typealias StageTrackMutation = NeatExperiment.(StageTrackGene) -> StageTrackGene

data class StageID(val world: Int, val stage: Int)

@Serializable
data class StageGene(val world: Int, val stage: Int, val distance: Int, val coins: Int, val score: Int)

@Serializable
data class StageTrackGene(val stages: List<StageGene>, val id: String)

val StageGene.stageID get() = StageID(world, stage)
val StageGene.lengthOfStage get() = stageLengthMap.getValue(stageID)
fun StageGene.canMutateStage() = !(world == 8 && stage == 4)
fun NeatExperiment.mutateAddStage(stageTrackGene: StageTrackGene, distance: Int = 200): StageTrackGene {
    var stageGene = StageGene(
        random.nextInt(1, 9),
        random.nextInt(1, 5),
        distance,
        0,
        0
    )
    while (isWaterLevel(stageGene.world, stageGene.stage)) {
        stageGene = StageGene(
            random.nextInt(1, 9),
            random.nextInt(1, 5),
            distance,
            0,
            0
        )
    }
    return StageTrackGene(
        stageTrackGene.stages + stageGene,
        UUID.randomUUID().toString()
    )
}

private fun isWaterLevel(world: Int, stage: Int): Boolean {
    return stage == 2 && world == 2 || stage == 2 && world == 7
}

fun NeatExperiment.mutateRemoveStage(stageTrackGene: StageTrackGene): StageTrackGene {
    return if (stageTrackGene.stages.size > 1) StageTrackGene(
        stageTrackGene.stages.minus(
            stageTrackGene.stages.random(
                random
            )
        ), UUID.randomUUID().toString()
    ) else stageTrackGene
}


fun NeatExperiment.mutateShuffleStages(stageTrackGene: StageTrackGene): StageTrackGene {
    return if (stageTrackGene.stages.size > 1) StageTrackGene(
        stageTrackGene.stages.shuffled(random), UUID.randomUUID().toString()
    ) else stageTrackGene
}

fun NeatExperiment.mutateStage(stageGene: StageGene): StageGene {
//    if (!stageGene.canMutateStage()) return stageGene
    var nextStage = random.nextInt(1, 5)
    var world = random.nextInt(1, 9)
//    var distance = 100
    return if (nextStage != stageGene.stage && world != stageGene.world && !isWaterLevel(world, nextStage))
        stageGene.copy(world = world, stage = nextStage, distance = max(400, (stageGene.distance *.7).toInt()))
    else stageGene
}

fun NeatExperiment.mutateDistance(stageGene: StageGene): StageGene {
    if (stageGene.distance > stageGene.lengthOfStage) return stageGene
    var nextStage = stageGene.stage
    var world = stageGene.world

    var distance = stageGene.distance + random.nextInt(1, 16) * 16

    return stageGene.copy(world = world, stage = nextStage, distance = distance)
}

fun NeatExperiment.mutateCoin(stageGene: StageGene): StageGene {
    return stageGene.copy(coins = stageGene.coins + if (stageGene.distance > 256) random.nextInt(1, 3) else 0)
}

fun NeatExperiment.mutateScore(stageGene: StageGene): StageGene {

    return stageGene.copy(score = stageGene.score + if (stageGene.distance > 256) random.nextInt(1, 3) * 50 else 0)
}


val stageLengthMap = mapOf(
    StageID(1, 1) to 3376,
    StageID(1, 2) to 3072,
    StageID(1, 3) to 2624,
    StageID(1, 4) to 2560,

    StageID(2, 1) to 3408,
    StageID(2, 2) to 3072,
    StageID(2, 3) to 3792,
    StageID(2, 4) to 2560,

    StageID(3, 1) to 3408,
    StageID(3, 2) to 3552,
    StageID(3, 3) to 2608,
    StageID(3, 4) to 2560,


    StageID(4, 1) to 3808,
    StageID(4, 2) to 3584,
    StageID(4, 3) to 2544,
    StageID(4, 4) to 3072,


    StageID(5, 1) to 3392,
    StageID(5, 2) to 3408,
    StageID(5, 3) to 2624,
    StageID(5, 4) to 2560,


    StageID(6, 1) to 3216,
    StageID(6, 2) to 3664,
    StageID(6, 3) to 2864,
    StageID(6, 4) to 2560,


    StageID(7, 1) to 3072,
    StageID(7, 2) to 3072,
    StageID(7, 3) to 3792,
    StageID(7, 4) to 3584,


    StageID(8, 1) to 6224,
    StageID(8, 2) to 3664,
    StageID(8, 3) to 3664,
    StageID(8, 4) to 5120,
)