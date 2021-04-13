package server.database

enum class MeleeStage {
    BATTLEFIELD, DREAMLAND, FINAL_DESTINATION, FOUNTAIN_OF_DREAMS, NO_STAGE, POKEMON_STADIUM, RANDOM_STAGE, YOSHIS_STORY;
    companion object {
        val map = values().map { it.id to it }.toMap()
        fun forId(stageId : Int): MeleeStage {
            return map[stageId] ?: error("No melee stage for id $stageId")
        }
    }
}

val MeleeStage.id get() = when(this) {
    MeleeStage.BATTLEFIELD -> 24
    MeleeStage.DREAMLAND -> 26
    MeleeStage.FINAL_DESTINATION -> 25
    MeleeStage.FOUNTAIN_OF_DREAMS -> 8
    MeleeStage.NO_STAGE -> 0
    MeleeStage.POKEMON_STADIUM -> 18
    MeleeStage.RANDOM_STAGE -> 29
    MeleeStage.YOSHIS_STORY -> 6
}