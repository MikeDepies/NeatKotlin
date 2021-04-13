package server.database

enum class MeleeProjectiles {
    ARROW, BEAMSWORD, BOB_OMB, BOWSER_FLAME, DR_MARIO_CAPE, DR_MARIO_CAPSULE, FALCO_LASER, FALCO_SHADOW, FIREFIGHTER,
    FIRE_ARROW, FOX_LASER, FOX_SHADOW, GW_FIRE, ICE_BLOCK, IC_BLIZZARD, IC_UP_B, JUDGE, KIRBY_BOWSER_FLAME,
    KIRBY_CUTTER, KIRBY_DR_MARIO_FIRE, KIRBY_FALCO_LASER, KIRBY_FOX_LASER, KIRBY_HAMMER, KIRBY_IC_BLOCK,
    KIRBY_LINK_ARROW, KIRBY_LINK_ARROW_2, KIRBY_LUIGI_FIRE, KIRBY_MARIO_FIRE, KIRBY_PICHU_THUNDERJOLT_1,
    KIRBY_PICHU_THUNDERJOLT_2, KIRBY_PIKACHU_THUNDERJOLT_1, KIRBY_PIKACHU_THUNDERJOLT_2, KIRBY_PK_FLASH,
    KIRBY_PK_FLASH_EXPLOSION, KIRBY_SAMUS_CHARGESHOT, KIRBY_SAUSAGE, KIRBY_SHADOWBALL, KIRBY_SHEIK_NEEDLE_GROUND,
    KIRBY_SHEIK_NEEDLE_THROWN, KIRBY_TOAD_SPORE, KIRBY_YLINK_ARROW, KIRBY_YLINK_ARROW_2, KIRBY_YOSHI_TONGUE,
    LINK_ARROW, LINK_BOMB, LINK_BOOMERANG, LINK_HOOKSHOT, LUIGI_FIRE, MANHOLE, MARIO_CAPE, MARIO_FIREBALL,
    MR_SATURN, NEEDLE_THROWN, NESS_BATT, NESS_YOYO, PARACHUTE, PEACH_PARASOL, PESTICIDE, PICHU_THUNDER,
    PICHU_THUNDERJOLT_1, PICHU_THUNDERJOLT_2, PIKACHU_THUNDER, PIKACHU_THUNDERJOLT_1, PIKACHU_THUNDERJOLT_2,
    PK_FIRE, PK_FLASH_1, PK_FLASH_2, PK_FLASH_EXPLOSION, PK_THUNDER_HEAD, PK_THUNDER_TAIL_1, PK_THUNDER_TAIL_2,
    PK_THUNDER_TAIL_3, PK_THUNDER_TAIL_4, SAMUS_BOMB, SAMUS_CHARGE_BEAM, SAMUS_GRAPPLE_BEAM, SAMUS_MISSLE, SAUSAGE,
    SHADOWBALL, SHEIK_CHAIN, SHEIK_SMOKE, SPERKY, TOAD_SPORE, TURNIP, TURTLE, UNKNOWN_PROJECTILE, YLINK_ARROW,
    YLINK_BOMB, YLINK_BOOMERANG, YLINK_HOOKSHOT, YLINK_MILK, YOSHI_EGG_THROWN, YOSHI_STAR, YOSHI_TONGUE, ZELDA_FIRE,
    ZELDA_FIRE_EXPLOSION
}

val MeleeProjectiles.id
    get() = when (this) {
        MeleeProjectiles.ARROW -> 64
        MeleeProjectiles.BEAMSWORD -> 12
        MeleeProjectiles.BOB_OMB -> 6
        MeleeProjectiles.BOWSER_FLAME -> 100
        MeleeProjectiles.DR_MARIO_CAPE -> 84
        MeleeProjectiles.DR_MARIO_CAPSULE -> 49
        MeleeProjectiles.FALCO_LASER -> 55
        MeleeProjectiles.FALCO_SHADOW -> 57
        MeleeProjectiles.FIREFIGHTER -> 124
        MeleeProjectiles.FIRE_ARROW -> 65
        MeleeProjectiles.FOX_LASER -> 54
        MeleeProjectiles.FOX_SHADOW -> 56
        MeleeProjectiles.GW_FIRE -> 116
        MeleeProjectiles.ICE_BLOCK -> 106
        MeleeProjectiles.IC_BLIZZARD -> 107
        MeleeProjectiles.IC_UP_B -> 113
        MeleeProjectiles.JUDGE -> 120
        MeleeProjectiles.KIRBY_BOWSER_FLAME -> 154
        MeleeProjectiles.KIRBY_CUTTER -> 50
        MeleeProjectiles.KIRBY_DR_MARIO_FIRE -> 131
        MeleeProjectiles.KIRBY_FALCO_LASER -> 137
        MeleeProjectiles.KIRBY_FOX_LASER -> 136
        MeleeProjectiles.KIRBY_HAMMER -> 51
        MeleeProjectiles.KIRBY_IC_BLOCK -> 133
        MeleeProjectiles.KIRBY_LINK_ARROW -> 140
        MeleeProjectiles.KIRBY_LINK_ARROW_2 -> 142
        MeleeProjectiles.KIRBY_LUIGI_FIRE -> 132
        MeleeProjectiles.KIRBY_MARIO_FIRE -> 130
        MeleeProjectiles.KIRBY_PICHU_THUNDERJOLT_1 -> 149
        MeleeProjectiles.KIRBY_PICHU_THUNDERJOLT_2 -> 150
        MeleeProjectiles.KIRBY_PIKACHU_THUNDERJOLT_1 -> 147
        MeleeProjectiles.KIRBY_PIKACHU_THUNDERJOLT_2 -> 148
        MeleeProjectiles.KIRBY_PK_FLASH -> 145
        MeleeProjectiles.KIRBY_PK_FLASH_EXPLOSION -> 146
        MeleeProjectiles.KIRBY_SAMUS_CHARGESHOT -> 151
        MeleeProjectiles.KIRBY_SAUSAGE -> 155
        MeleeProjectiles.KIRBY_SHADOWBALL -> 144
        MeleeProjectiles.KIRBY_SHEIK_NEEDLE_GROUND -> 153
        MeleeProjectiles.KIRBY_SHEIK_NEEDLE_THROWN -> 152
        MeleeProjectiles.KIRBY_TOAD_SPORE -> 135
        MeleeProjectiles.KIRBY_YLINK_ARROW -> 141
        MeleeProjectiles.KIRBY_YLINK_ARROW_2 -> 143
        MeleeProjectiles.KIRBY_YOSHI_TONGUE -> 157
        MeleeProjectiles.LINK_ARROW -> 76
        MeleeProjectiles.LINK_BOMB -> 58
        MeleeProjectiles.LINK_BOOMERANG -> 60
        MeleeProjectiles.LINK_HOOKSHOT -> 62
        MeleeProjectiles.LUIGI_FIRE -> 105
        MeleeProjectiles.MANHOLE -> 115
        MeleeProjectiles.MARIO_CAPE -> 83
        MeleeProjectiles.MARIO_FIREBALL -> 48
        MeleeProjectiles.MR_SATURN -> 7
        MeleeProjectiles.NEEDLE_THROWN -> 79
        MeleeProjectiles.NESS_BATT -> 101
        MeleeProjectiles.NESS_YOYO -> 102
        MeleeProjectiles.PARACHUTE -> 117
        MeleeProjectiles.PEACH_PARASOL -> 103
        MeleeProjectiles.PESTICIDE -> 114
        MeleeProjectiles.PICHU_THUNDER -> 82
        MeleeProjectiles.PICHU_THUNDERJOLT_1 -> 91
        MeleeProjectiles.PICHU_THUNDERJOLT_2 -> 92
        MeleeProjectiles.PIKACHU_THUNDER -> 81
        MeleeProjectiles.PIKACHU_THUNDERJOLT_1 -> 89
        MeleeProjectiles.PIKACHU_THUNDERJOLT_2 -> 90
        MeleeProjectiles.PK_FIRE -> 66
        MeleeProjectiles.PK_FLASH_1 -> 67
        MeleeProjectiles.PK_FLASH_2 -> 68
        MeleeProjectiles.PK_FLASH_EXPLOSION -> 78
        MeleeProjectiles.PK_THUNDER_HEAD -> 69
        MeleeProjectiles.PK_THUNDER_TAIL_1 -> 70
        MeleeProjectiles.PK_THUNDER_TAIL_2 -> 71
        MeleeProjectiles.PK_THUNDER_TAIL_3 -> 72
        MeleeProjectiles.PK_THUNDER_TAIL_4 -> 73
        MeleeProjectiles.SAMUS_BOMB -> 93
        MeleeProjectiles.SAMUS_CHARGE_BEAM -> 94
        MeleeProjectiles.SAMUS_GRAPPLE_BEAM -> 96
        MeleeProjectiles.SAMUS_MISSLE -> 95
        MeleeProjectiles.SAUSAGE -> 122
        MeleeProjectiles.SHADOWBALL -> 112
        MeleeProjectiles.SHEIK_CHAIN -> 97
        MeleeProjectiles.SHEIK_SMOKE -> 85
        MeleeProjectiles.SPERKY -> 119
        MeleeProjectiles.TOAD_SPORE -> 111
        MeleeProjectiles.TURNIP -> 99
        MeleeProjectiles.TURTLE -> 118
        MeleeProjectiles.UNKNOWN_PROJECTILE -> 255
        MeleeProjectiles.YLINK_ARROW -> 77
        MeleeProjectiles.YLINK_BOMB -> 59
        MeleeProjectiles.YLINK_BOOMERANG -> 61
        MeleeProjectiles.YLINK_HOOKSHOT -> 63
        MeleeProjectiles.YLINK_MILK -> 123
        MeleeProjectiles.YOSHI_EGG_THROWN -> 86
        MeleeProjectiles.YOSHI_STAR -> 88
        MeleeProjectiles.YOSHI_TONGUE -> 87
        MeleeProjectiles.ZELDA_FIRE -> 108
        MeleeProjectiles.ZELDA_FIRE_EXPLOSION -> 109
    }