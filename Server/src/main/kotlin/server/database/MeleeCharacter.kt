package server.database

enum class MeleeCharacter {
    BOWSER, CPTFALCON, DK, DOC, FALCO, FOX, GAMEANDWATCH, GANONDORF, GIGA_BOWSER, JIGGLYPUFF, KIRBY, LINK, LUIGI, MARIO,
    MARTH, MEWTWO, NANA, NESS, PEACH, PICHU, PIKACHU, POPO, ROY, SAMUS, SANDBAG, SHEIK, UNKNOWN_CHARACTER,
    WIREFRAME_FEMALE, WIREFRAME_MALE, YLINK, YOSHI, ZELDA;
    companion object {
        val map = MeleeStage.values().map { it.id to it }.toMap()
        fun forId(stageId : Int): MeleeCharacter {
            return map[stageId] ?: error("No melee stage for id $stageId")
        }
    }
}

val MeleeCharacter.id get() = when(this) {
    MeleeCharacter.BOWSER -> 5
    MeleeCharacter.CPTFALCON -> 2
    MeleeCharacter.DK -> 3
    MeleeCharacter.DOC -> 21
    MeleeCharacter.FALCO -> 22
    MeleeCharacter.FOX -> 1
    MeleeCharacter.GAMEANDWATCH -> 24
    MeleeCharacter.GANONDORF -> 25
    MeleeCharacter.GIGA_BOWSER -> 31
    MeleeCharacter.JIGGLYPUFF -> 15
    MeleeCharacter.KIRBY -> 4
    MeleeCharacter.LINK -> 6
    MeleeCharacter.LUIGI -> 17
    MeleeCharacter.MARIO -> 0
    MeleeCharacter.MARTH -> 18
    MeleeCharacter.MEWTWO -> 16
    MeleeCharacter.NANA -> 11
    MeleeCharacter.NESS -> 8
    MeleeCharacter.PEACH -> 9
    MeleeCharacter.PICHU -> 23
    MeleeCharacter.PIKACHU -> 12
    MeleeCharacter.POPO -> 10
    MeleeCharacter.ROY -> 26
    MeleeCharacter.SAMUS -> 13
    MeleeCharacter.SANDBAG -> 32
    MeleeCharacter.SHEIK -> 7
    MeleeCharacter.UNKNOWN_CHARACTER -> 255
    MeleeCharacter.WIREFRAME_FEMALE -> 30
    MeleeCharacter.WIREFRAME_MALE -> 29
    MeleeCharacter.YLINK -> 20
    MeleeCharacter.YOSHI -> 14
    MeleeCharacter.ZELDA -> 19
}