package server.database

enum class MeleeActions {
    AIRDODGE, BACKWARD_TECH, BAIR, BAIR_LANDING, BARREL_CANNON_WAIT, BARREL_WAIT, BAT_SWING_1, BAT_SWING_2, BAT_SWING_3,
    BAT_SWING_4, BEAM_SWORD_SWING_1, BEAM_SWORD_SWING_2, BEAM_SWORD_SWING_3, BEAM_SWORD_SWING_4, BOUNCE_CEILING,
    BOUNCE_WALL, BUMP_CIELING, BUMP_WALL, BURY, BURY_JUMP, BURY_WAIT, CAPTURE_CAPTAIN, CAPTURE_CRAZYHAND,
    CAPTURE_DAMAGE_CRAZYHAND, CAPTURE_DAMAGE_KOOPA, CAPTURE_DAMAGE_KOOPA_AIR, CAPTURE_DAMAGE_MASTERHAND, CAPTURE_KIRBY,
    CAPTURE_KIRBY_YOSHI, CAPTURE_KOOPA, CAPTURE_KOOPA_AIR, CAPTURE_KOOPA_AIR_HIT, CAPTURE_LEA_DEAD, CAPTURE_LIKE_LIKE,
    CAPTURE_MASTERHAND, CAPTURE_MEWTWO, CAPTURE_MEWTWO_AIR, CAPTURE_WAIT_CRAZYHAND, CAPTURE_WAIT_KIRBY,
    CAPTURE_WAIT_KOOPA, CAPTURE_WAIT_KOOPA_AIR, CAPTURE_WAIT_MASTERHAND, CAPTURE_YOSHI, CEILING_TECH, CROUCHING,
    CROUCH_END, CROUCH_START, DAIR, DAIR_LANDING, DAMAGE_AIR_1, DAMAGE_AIR_2, DAMAGE_AIR_3, DAMAGE_BIND,
    DAMAGE_FLY_HIGH, DAMAGE_FLY_LOW, DAMAGE_FLY_NEUTRAL, DAMAGE_FLY_ROLL, DAMAGE_FLY_TOP, DAMAGE_GROUND, DAMAGE_HIGH_1,
    DAMAGE_HIGH_2, DAMAGE_HIGH_3, DAMAGE_ICE, DAMAGE_ICE_JUMP, DAMAGE_LOW_1, DAMAGE_LOW_2, DAMAGE_LOW_3,
    DAMAGE_NEUTRAL_1, DAMAGE_NEUTRAL_2, DAMAGE_NEUTRAL_3, DAMAGE_SCREW, DAMAGE_SCREW_AIR, DAMAGE_SONG, DAMAGE_SONG_RV,
    DAMAGE_SONG_WAIT, DASHING, DASH_ATTACK, DEAD_DOWN, DEAD_FALL, DEAD_FLY, DEAD_FLY_SPLATTER, DEAD_FLY_SPLATTER_FLAT,
    DEAD_FLY_SPLATTER_FLAT_ICE, DEAD_FLY_SPLATTER_ICE, DEAD_FLY_STAR, DEAD_FLY_STAR_ICE, DEAD_LEFT, DEAD_RIGHT, DEAD_UP,
    DOWNSMASH, DOWNTILT, DOWN_B_AIR, DOWN_B_GROUND, DOWN_B_GROUND_START, DOWN_B_STUN, DOWN_REFLECT, EDGE_ATTACK_QUICK,
    EDGE_ATTACK_SLOW, EDGE_CATCHING, EDGE_GETUP_QUICK, EDGE_GETUP_SLOW, EDGE_HANGING, EDGE_JUMP_1_QUICK,
    EDGE_JUMP_1_SLOW, EDGE_JUMP_2_QUICK, EDGE_JUMP_2_SLOW, EDGE_ROLL_QUICK, EDGE_ROLL_SLOW, EDGE_TEETERING,
    EDGE_TEETERING_START, ENTRY, ENTRY_END, ENTRY_START, FAIR, FAIR_LANDING, FALLING, FALLING_AERIAL,
    FALLING_AERIAL_BACKWARD, FALLING_AERIAL_FORWARD, FALLING_BACKWARD, FALLING_FORWARD, FAN_SWING_1, FAN_SWING_2,
    FAN_SWING_3, FAN_SWING_4, FIREFOX_AIR, FIREFOX_GROUND, FIREFOX_WAIT_AIR, FIREFOX_WAIT_GROUND, FIRE_FLOWER_SHOOT,
    FIRE_FLOWER_SHOOT_AIR, FORWARD_TECH, FOX_ILLUSION, FOX_ILLUSION_SHORTENED, FOX_ILLUSION_START, FSMASH_HIGH,
    FSMASH_LOW, FSMASH_MID, FSMASH_MID_HIGH, FSMASH_MID_LOW, FTILT_HIGH, FTILT_HIGH_MID, FTILT_LOW, FTILT_LOW_MID,
    FTILT_MID, GETUP_ATTACK, GRAB, GRABBED, GRABBED_WAIT_HIGH, GRAB_BREAK, GRAB_ESCAPE, GRAB_FOOT, GRAB_JUMP,
    GRAB_NECK, GRAB_PULL, GRAB_PULLING, GRAB_PULLING_HIGH, GRAB_PUMMEL, GRAB_PUMMELED, GRAB_RUNNING,
    GRAB_RUNNING_PULLING, GRAB_WAIT, GROUND_ATTACK_UP, GROUND_GETUP, GROUND_ROLL_BACKWARD_DOWN,
    GROUND_ROLL_BACKWARD_UP, GROUND_ROLL_FORWARD_DOWN, GROUND_ROLL_FORWARD_UP, GROUND_ROLL_SPOT_DOWN, GROUND_SPOT_UP,
    GUN_SHOOT, GUN_SHOOT_AIR, GUN_SHOOT_AIR_EMPTY, GUN_SHOOT_EMPTY, HAMMER_FALL, HAMMER_JUMP, HAMMER_KNEE_BEND,
    HAMMER_LANDING, HAMMER_TURN, HAMMER_WAIT, HAMMER_WALK, ITEM_PARASOL_DAMAGE_FALL, ITEM_PARASOL_FALL,
    ITEM_PARASOL_FALL_SPECIAL, ITEM_PARASOL_OPEN, ITEM_PICKUP_HEAVY, ITEM_PICKUP_LIGHT, ITEM_SCOPE_AIR_END,
    ITEM_SCOPE_AIR_END_EMPTY, ITEM_SCOPE_AIR_FIRE, ITEM_SCOPE_AIR_FIRE_EMPTY, ITEM_SCOPE_AIR_RAPID,
    ITEM_SCOPE_AIR_RAPID_EMPTY, ITEM_SCOPE_AIR_START, ITEM_SCOPE_AIR_START_EMPTY, ITEM_SCOPE_END, ITEM_SCOPE_END_EMPTY,
    ITEM_SCOPE_FIRE, ITEM_SCOPE_FIRE_EMPTY, ITEM_SCOPE_RAPID, ITEM_SCOPE_RAPID_EMPTY, ITEM_SCOPE_START,
    ITEM_SCOPE_START_EMPTY, ITEM_SCREW, ITEM_SCREW_AIR, ITEM_THROW_HEAVY_AIR_SMASH_BACK,
    ITEM_THROW_HEAVY_AIR_SMASH_FORWARD, ITEM_THROW_HEAVY_AIR_SMASH_HIGH, ITEM_THROW_HEAVY_AIR_SMASH_LOW,
    ITEM_THROW_HEAVY_BACK, ITEM_THROW_HEAVY_FORWARD, ITEM_THROW_HEAVY_HIGH, ITEM_THROW_HEAVY_LOW,
    ITEM_THROW_LIGHT_AIR_BACK, ITEM_THROW_LIGHT_AIR_FORWARD, ITEM_THROW_LIGHT_AIR_HIGH, ITEM_THROW_LIGHT_AIR_LOW,
    ITEM_THROW_LIGHT_AIR_SMASH_BACK, ITEM_THROW_LIGHT_AIR_SMASH_FORWARD, ITEM_THROW_LIGHT_AIR_SMASH_HIGH,
    ITEM_THROW_LIGHT_AIR_SMASH_LOW, ITEM_THROW_LIGHT_BACK, ITEM_THROW_LIGHT_DASH, ITEM_THROW_LIGHT_DROP,
    ITEM_THROW_LIGHT_FORWARD, ITEM_THROW_LIGHT_HIGH, ITEM_THROW_LIGHT_LOW, ITEM_THROW_LIGHT_SMASH_BACK,
    ITEM_THROW_LIGHT_SMASH_DOWN, ITEM_THROW_LIGHT_SMASH_FORWARD, ITEM_THROW_LIGHT_SMASH_UP, JUMPING_ARIAL_BACKWARD,
    JUMPING_ARIAL_FORWARD, JUMPING_BACKWARD, JUMPING_FORWARD, KINOKO_GIANT_END, KINOKO_GIANT_END_AIR,
    KINOKO_GIANT_START, KINOKO_GIANT_START_AIR, KINOKO_SMALL_END, KINOKO_SMALL_END_AIR, KINOKO_SMALL_START,
    KINOKO_SMALL_START_AIR, KIRBY_YOSHI_EGG, KNEE_BEND, LANDING, LANDING_SPECIAL, LASER_GUN_PULL, LIFT_TURN, LIFT_WAIT,
    LIFT_WALK_1, LIFT_WALK_2, LIP_STICK_SWING_1, LIP_STICK_SWING_2, LIP_STICK_SWING_3, LIP_STICK_SWING_4,
    LOOPING_ATTACK_END, LOOPING_ATTACK_MIDDLE, LOOPING_ATTACK_START, LYING_GROUND_DOWN, LYING_GROUND_UP,
    LYING_GROUND_UP_HIT, MARTH_COUNTER, MARTH_COUNTER_FALLING, NAIR, NAIR_LANDING, NESS_SHEILD, NESS_SHEILD_AIR,
    NESS_SHEILD_AIR_END, NESS_SHEILD_START, NEUTRAL_ATTACK_1, NEUTRAL_ATTACK_2, NEUTRAL_ATTACK_3, NEUTRAL_B_ATTACKING,
    NEUTRAL_B_ATTACKING_AIR, NEUTRAL_B_CHARGING, NEUTRAL_B_CHARGING_AIR, NEUTRAL_B_FULL_CHARGE,
    NEUTRAL_B_FULL_CHARGE_AIR, NEUTRAL_GETUP, NEUTRAL_TECH, NOTHING_STATE, ON_HALO_DESCENT, ON_HALO_WAIT,
    PARASOL_FALLING, PARASOL_SWING_1, PARASOL_SWING_2, PARASOL_SWING_3, PARASOL_SWING_4, PLATFORM_DROP, PUMMELED_HIGH,
    REBOUND, REBOUND_STOP, ROLL_BACKWARD, ROLL_FORWARD, RUNNING, RUN_BRAKE, RUN_DIRECT, SHIELD, SHIELD_BREAK_DOWN_D,
    SHIELD_BREAK_DOWN_U, SHIELD_BREAK_FALL, SHIELD_BREAK_FLY, SHIELD_BREAK_STAND_D, SHIELD_BREAK_STAND_U,
    SHIELD_BREAK_TEETER, SHIELD_REFLECT, SHIELD_RELEASE, SHIELD_START, SHIELD_STUN, SHINE_RELEASE_AIR, SHINE_TURN,
    SHOULDERED_TURN, SHOULDERED_WAIT, SHOULDERED_WALK_FAST, SHOULDERED_WALK_MIDDLE, SHOULDERED_WALK_SLOW,
    SLIDING_OFF_EDGE, SPECIAL_FALL_BACK, SPECIAL_FALL_FORWARD, SPOTDODGE, STANDING, STAR_ROD_SWING_1,
    STAR_ROD_SWING_2, STAR_ROD_SWING_3, STAR_ROD_SWING_4, SWORD_DANCE_1, SWORD_DANCE_1_AIR, SWORD_DANCE_2_HIGH,
    SWORD_DANCE_2_HIGH_AIR, SWORD_DANCE_2_MID, SWORD_DANCE_2_MID_AIR, SWORD_DANCE_3_HIGH, SWORD_DANCE_3_HIGH_AIR,
    SWORD_DANCE_3_LOW, SWORD_DANCE_3_LOW_AIR, SWORD_DANCE_3_MID, SWORD_DANCE_3_MID_AIR, SWORD_DANCE_4_HIGH,
    SWORD_DANCE_4_HIGH_AIR, SWORD_DANCE_4_LOW, SWORD_DANCE_4_LOW_AIR, SWORD_DANCE_4_MID, SWORD_DANCE_4_MID_AIR,
    TAUNT_LEFT, TAUNT_RIGHT, TECH_MISS_DOWN, TECH_MISS_UP, THROWN_BACK, THROWN_COPY_STAR, THROWN_CRAZY_HAND,
    THROWN_DOWN, THROWN_DOWN_2, THROWN_FB, THROWN_FF, THROWN_FORWARD, THROWN_F_HIGH, THROWN_F_LOW, THROWN_KIRBY,
    THROWN_KIRBY_DRINK_S_SHOT, THROWN_KIRBY_SPIT_S_SHOT, THROWN_KIRBY_STAR, THROWN_KOOPA_AIR_B, THROWN_KOOPA_AIR_END_B,
    THROWN_KOOPA_AIR_END_F, THROWN_KOOPA_AIR_F, THROWN_KOOPA_B, THROWN_KOOPA_END_B, THROWN_KOOPA_END_F, THROWN_KOOPA_F,
    THROWN_MASTERHAND, THROWN_MEWTWO, THROWN_MEWTWO_AIR, THROWN_UP, THROW_BACK, THROW_DOWN, THROW_FORWARD, THROW_UP,
    TUMBLING, TURNING, TURNING_RUN, UAIR, UAIR_LANDING, UNKNOWN_ANIMATION, UPSMASH, UPTILT, UP_B_AIR, UP_B_GROUND,
    WAIT_ITEM, WALK_FAST, WALK_MIDDLE, WALK_SLOW, WALL_TECH, WALL_TECH_JUMP, WARP_STAP_FALL, WARP_STAR_JUMP, YOSHI_EGG,
    ZITABATA
}

val MeleeActions.id get() = when(this) {
    MeleeActions.AIRDODGE -> 236
    MeleeActions.BACKWARD_TECH -> 201
    MeleeActions.BAIR -> 67
    MeleeActions.BAIR_LANDING -> 72
    MeleeActions.BARREL_CANNON_WAIT -> 340
    MeleeActions.BARREL_WAIT -> 293
    MeleeActions.BAT_SWING_1 -> 124
    MeleeActions.BAT_SWING_2 -> 125
    MeleeActions.BAT_SWING_3 -> 126
    MeleeActions.BAT_SWING_4 -> 127
    MeleeActions.BEAM_SWORD_SWING_1 -> 120
    MeleeActions.BEAM_SWORD_SWING_2 -> 121
    MeleeActions.BEAM_SWORD_SWING_3 -> 122
    MeleeActions.BEAM_SWORD_SWING_4 -> 123
    MeleeActions.BOUNCE_CEILING -> 248
    MeleeActions.BOUNCE_WALL -> 247
    MeleeActions.BUMP_CIELING -> 250
    MeleeActions.BUMP_WALL -> 249
    MeleeActions.BURY -> 294
    MeleeActions.BURY_JUMP -> 296
    MeleeActions.BURY_WAIT -> 295
    MeleeActions.CAPTURE_CAPTAIN -> 275
    MeleeActions.CAPTURE_CRAZYHAND -> 336
    MeleeActions.CAPTURE_DAMAGE_CRAZYHAND -> 337
    MeleeActions.CAPTURE_DAMAGE_KOOPA -> 279
    MeleeActions.CAPTURE_DAMAGE_KOOPA_AIR -> 284
    MeleeActions.CAPTURE_DAMAGE_MASTERHAND -> 328
    MeleeActions.CAPTURE_KIRBY -> 288
    MeleeActions.CAPTURE_KIRBY_YOSHI -> 331
    MeleeActions.CAPTURE_KOOPA -> 278
    MeleeActions.CAPTURE_KOOPA_AIR -> 283
    MeleeActions.CAPTURE_KOOPA_AIR_HIT -> 378
    MeleeActions.CAPTURE_LEA_DEAD -> 333
    MeleeActions.CAPTURE_LIKE_LIKE -> 334
    MeleeActions.CAPTURE_MASTERHAND -> 327
    MeleeActions.CAPTURE_MEWTWO -> 301
    MeleeActions.CAPTURE_MEWTWO_AIR -> 302
    MeleeActions.CAPTURE_WAIT_CRAZYHAND -> 338
    MeleeActions.CAPTURE_WAIT_KIRBY -> 289
    MeleeActions.CAPTURE_WAIT_KOOPA -> 280
    MeleeActions.CAPTURE_WAIT_KOOPA_AIR -> 285
    MeleeActions.CAPTURE_WAIT_MASTERHAND -> 329
    MeleeActions.CAPTURE_YOSHI -> 276
    MeleeActions.CEILING_TECH -> 204
    MeleeActions.CROUCHING -> 40
    MeleeActions.CROUCH_END -> 41
    MeleeActions.CROUCH_START -> 39
    MeleeActions.DAIR -> 69
    MeleeActions.DAIR_LANDING -> 74
    MeleeActions.DAMAGE_AIR_1 -> 84
    MeleeActions.DAMAGE_AIR_2 -> 85
    MeleeActions.DAMAGE_AIR_3 -> 86
    MeleeActions.DAMAGE_BIND -> 300
    MeleeActions.DAMAGE_FLY_HIGH -> 87
    MeleeActions.DAMAGE_FLY_LOW -> 89
    MeleeActions.DAMAGE_FLY_NEUTRAL -> 88
    MeleeActions.DAMAGE_FLY_ROLL -> 91
    MeleeActions.DAMAGE_FLY_TOP -> 90
    MeleeActions.DAMAGE_GROUND -> 193
    MeleeActions.DAMAGE_HIGH_1 -> 75
    MeleeActions.DAMAGE_HIGH_2 -> 76
    MeleeActions.DAMAGE_HIGH_3 -> 77
    MeleeActions.DAMAGE_ICE -> 325
    MeleeActions.DAMAGE_ICE_JUMP -> 326
    MeleeActions.DAMAGE_LOW_1 -> 81
    MeleeActions.DAMAGE_LOW_2 -> 82
    MeleeActions.DAMAGE_LOW_3 -> 83
    MeleeActions.DAMAGE_NEUTRAL_1 -> 78
    MeleeActions.DAMAGE_NEUTRAL_2 -> 79
    MeleeActions.DAMAGE_NEUTRAL_3 -> 80
    MeleeActions.DAMAGE_SCREW -> 156
    MeleeActions.DAMAGE_SCREW_AIR -> 157
    MeleeActions.DAMAGE_SONG -> 297
    MeleeActions.DAMAGE_SONG_RV -> 299
    MeleeActions.DAMAGE_SONG_WAIT -> 298
    MeleeActions.DASHING -> 20
    MeleeActions.DASH_ATTACK -> 50
    MeleeActions.DEAD_DOWN -> 0
    MeleeActions.DEAD_FALL -> 35
    MeleeActions.DEAD_FLY -> 6
    MeleeActions.DEAD_FLY_SPLATTER -> 7
    MeleeActions.DEAD_FLY_SPLATTER_FLAT -> 8
    MeleeActions.DEAD_FLY_SPLATTER_FLAT_ICE -> 10
    MeleeActions.DEAD_FLY_SPLATTER_ICE -> 9
    MeleeActions.DEAD_FLY_STAR -> 4
    MeleeActions.DEAD_FLY_STAR_ICE -> 5
    MeleeActions.DEAD_LEFT -> 1
    MeleeActions.DEAD_RIGHT -> 2
    MeleeActions.DEAD_UP -> 3
    MeleeActions.DOWNSMASH -> 64
    MeleeActions.DOWNTILT -> 57
    MeleeActions.DOWN_B_AIR -> 366
    MeleeActions.DOWN_B_GROUND -> 361
    MeleeActions.DOWN_B_GROUND_START -> 360
    MeleeActions.DOWN_B_STUN -> 365
    MeleeActions.DOWN_REFLECT -> 335
    MeleeActions.EDGE_ATTACK_QUICK -> 257
    MeleeActions.EDGE_ATTACK_SLOW -> 256
    MeleeActions.EDGE_CATCHING -> 252
    MeleeActions.EDGE_GETUP_QUICK -> 255
    MeleeActions.EDGE_GETUP_SLOW -> 254
    MeleeActions.EDGE_HANGING -> 253
    MeleeActions.EDGE_JUMP_1_QUICK -> 262
    MeleeActions.EDGE_JUMP_1_SLOW -> 260
    MeleeActions.EDGE_JUMP_2_QUICK -> 263
    MeleeActions.EDGE_JUMP_2_SLOW -> 261
    MeleeActions.EDGE_ROLL_QUICK -> 259
    MeleeActions.EDGE_ROLL_SLOW -> 258
    MeleeActions.EDGE_TEETERING -> 246
    MeleeActions.EDGE_TEETERING_START -> 245
    MeleeActions.ENTRY -> 322
    MeleeActions.ENTRY_END -> 324
    MeleeActions.ENTRY_START -> 323
    MeleeActions.FAIR -> 66
    MeleeActions.FAIR_LANDING -> 71
    MeleeActions.FALLING -> 29
    MeleeActions.FALLING_AERIAL -> 32
    MeleeActions.FALLING_AERIAL_BACKWARD -> 34
    MeleeActions.FALLING_AERIAL_FORWARD -> 33
    MeleeActions.FALLING_BACKWARD -> 31
    MeleeActions.FALLING_FORWARD -> 30
    MeleeActions.FAN_SWING_1 -> 132
    MeleeActions.FAN_SWING_2 -> 133
    MeleeActions.FAN_SWING_3 -> 134
    MeleeActions.FAN_SWING_4 -> 135
    MeleeActions.FIREFOX_AIR -> 356
    MeleeActions.FIREFOX_GROUND -> 355
    MeleeActions.FIREFOX_WAIT_AIR -> 354
    MeleeActions.FIREFOX_WAIT_GROUND -> 353
    MeleeActions.FIRE_FLOWER_SHOOT -> 152
    MeleeActions.FIRE_FLOWER_SHOOT_AIR -> 153
    MeleeActions.FORWARD_TECH -> 200
    MeleeActions.FOX_ILLUSION -> 351
    MeleeActions.FOX_ILLUSION_SHORTENED -> 352
    MeleeActions.FOX_ILLUSION_START -> 350
    MeleeActions.FSMASH_HIGH -> 58
    MeleeActions.FSMASH_LOW -> 62
    MeleeActions.FSMASH_MID -> 60
    MeleeActions.FSMASH_MID_HIGH -> 59
    MeleeActions.FSMASH_MID_LOW -> 61
    MeleeActions.FTILT_HIGH -> 51
    MeleeActions.FTILT_HIGH_MID -> 52
    MeleeActions.FTILT_LOW -> 55
    MeleeActions.FTILT_LOW_MID -> 54
    MeleeActions.FTILT_MID -> 53
    MeleeActions.GETUP_ATTACK -> 195
    MeleeActions.GRAB -> 212
    MeleeActions.GRABBED -> 227
    MeleeActions.GRABBED_WAIT_HIGH -> 224
    MeleeActions.GRAB_BREAK -> 218
    MeleeActions.GRAB_ESCAPE -> 229
    MeleeActions.GRAB_FOOT -> 232
    MeleeActions.GRAB_JUMP -> 230
    MeleeActions.GRAB_NECK -> 231
    MeleeActions.GRAB_PULL -> 226
    MeleeActions.GRAB_PULLING -> 213
    MeleeActions.GRAB_PULLING_HIGH -> 223
    MeleeActions.GRAB_PUMMEL -> 217
    MeleeActions.GRAB_PUMMELED -> 228
    MeleeActions.GRAB_RUNNING -> 214
    MeleeActions.GRAB_RUNNING_PULLING -> 215
    MeleeActions.GRAB_WAIT -> 216
    MeleeActions.GROUND_ATTACK_UP -> 187
    MeleeActions.GROUND_GETUP -> 186
    MeleeActions.GROUND_ROLL_BACKWARD_DOWN -> 197
    MeleeActions.GROUND_ROLL_BACKWARD_UP -> 189
    MeleeActions.GROUND_ROLL_FORWARD_DOWN -> 196
    MeleeActions.GROUND_ROLL_FORWARD_UP -> 188
    MeleeActions.GROUND_ROLL_SPOT_DOWN -> 198
    MeleeActions.GROUND_SPOT_UP -> 190
    MeleeActions.GUN_SHOOT -> 148
    MeleeActions.GUN_SHOOT_AIR -> 149
    MeleeActions.GUN_SHOOT_AIR_EMPTY -> 151
    MeleeActions.GUN_SHOOT_EMPTY -> 150
    MeleeActions.HAMMER_FALL -> 311
    MeleeActions.HAMMER_JUMP -> 312
    MeleeActions.HAMMER_KNEE_BEND -> 310
    MeleeActions.HAMMER_LANDING -> 313
    MeleeActions.HAMMER_TURN -> 309
    MeleeActions.HAMMER_WAIT -> 307
    MeleeActions.HAMMER_WALK -> 308
    MeleeActions.ITEM_PARASOL_DAMAGE_FALL -> 147
    MeleeActions.ITEM_PARASOL_FALL -> 145
    MeleeActions.ITEM_PARASOL_FALL_SPECIAL -> 146
    MeleeActions.ITEM_PARASOL_OPEN -> 144
    MeleeActions.ITEM_PICKUP_HEAVY -> 93
    MeleeActions.ITEM_PICKUP_LIGHT -> 92
    MeleeActions.ITEM_SCOPE_AIR_END -> 165
    MeleeActions.ITEM_SCOPE_AIR_END_EMPTY -> 173
    MeleeActions.ITEM_SCOPE_AIR_FIRE -> 164
    MeleeActions.ITEM_SCOPE_AIR_FIRE_EMPTY -> 172
    MeleeActions.ITEM_SCOPE_AIR_RAPID -> 163
    MeleeActions.ITEM_SCOPE_AIR_RAPID_EMPTY -> 171
    MeleeActions.ITEM_SCOPE_AIR_START -> 162
    MeleeActions.ITEM_SCOPE_AIR_START_EMPTY -> 170
    MeleeActions.ITEM_SCOPE_END -> 161
    MeleeActions.ITEM_SCOPE_END_EMPTY -> 169
    MeleeActions.ITEM_SCOPE_FIRE -> 160
    MeleeActions.ITEM_SCOPE_FIRE_EMPTY -> 168
    MeleeActions.ITEM_SCOPE_RAPID -> 159
    MeleeActions.ITEM_SCOPE_RAPID_EMPTY -> 167
    MeleeActions.ITEM_SCOPE_START -> 158
    MeleeActions.ITEM_SCOPE_START_EMPTY -> 166
    MeleeActions.ITEM_SCREW -> 154
    MeleeActions.ITEM_SCREW_AIR -> 155
    MeleeActions.ITEM_THROW_HEAVY_AIR_SMASH_BACK -> 117
    MeleeActions.ITEM_THROW_HEAVY_AIR_SMASH_FORWARD -> 116
    MeleeActions.ITEM_THROW_HEAVY_AIR_SMASH_HIGH -> 118
    MeleeActions.ITEM_THROW_HEAVY_AIR_SMASH_LOW -> 119
    MeleeActions.ITEM_THROW_HEAVY_BACK -> 105
    MeleeActions.ITEM_THROW_HEAVY_FORWARD -> 104
    MeleeActions.ITEM_THROW_HEAVY_HIGH -> 106
    MeleeActions.ITEM_THROW_HEAVY_LOW -> 107
    MeleeActions.ITEM_THROW_LIGHT_AIR_BACK -> 101
    MeleeActions.ITEM_THROW_LIGHT_AIR_FORWARD -> 100
    MeleeActions.ITEM_THROW_LIGHT_AIR_HIGH -> 102
    MeleeActions.ITEM_THROW_LIGHT_AIR_LOW -> 103
    MeleeActions.ITEM_THROW_LIGHT_AIR_SMASH_BACK -> 113
    MeleeActions.ITEM_THROW_LIGHT_AIR_SMASH_FORWARD -> 112
    MeleeActions.ITEM_THROW_LIGHT_AIR_SMASH_HIGH -> 114
    MeleeActions.ITEM_THROW_LIGHT_AIR_SMASH_LOW -> 115
    MeleeActions.ITEM_THROW_LIGHT_BACK -> 95
    MeleeActions.ITEM_THROW_LIGHT_DASH -> 98
    MeleeActions.ITEM_THROW_LIGHT_DROP -> 99
    MeleeActions.ITEM_THROW_LIGHT_FORWARD -> 94
    MeleeActions.ITEM_THROW_LIGHT_HIGH -> 96
    MeleeActions.ITEM_THROW_LIGHT_LOW -> 97
    MeleeActions.ITEM_THROW_LIGHT_SMASH_BACK -> 109
    MeleeActions.ITEM_THROW_LIGHT_SMASH_DOWN -> 111
    MeleeActions.ITEM_THROW_LIGHT_SMASH_FORWARD -> 108
    MeleeActions.ITEM_THROW_LIGHT_SMASH_UP -> 110
    MeleeActions.JUMPING_ARIAL_BACKWARD -> 28
    MeleeActions.JUMPING_ARIAL_FORWARD -> 27
    MeleeActions.JUMPING_BACKWARD -> 26
    MeleeActions.JUMPING_FORWARD -> 25
    MeleeActions.KINOKO_GIANT_END -> 316
    MeleeActions.KINOKO_GIANT_END_AIR -> 317
    MeleeActions.KINOKO_GIANT_START -> 314
    MeleeActions.KINOKO_GIANT_START_AIR -> 315
    MeleeActions.KINOKO_SMALL_END -> 320
    MeleeActions.KINOKO_SMALL_END_AIR -> 321
    MeleeActions.KINOKO_SMALL_START -> 318
    MeleeActions.KINOKO_SMALL_START_AIR -> 319
    MeleeActions.KIRBY_YOSHI_EGG -> 332
    MeleeActions.KNEE_BEND -> 24
    MeleeActions.LANDING -> 42
    MeleeActions.LANDING_SPECIAL -> 43
    MeleeActions.LASER_GUN_PULL -> 341
    MeleeActions.LIFT_TURN -> 177
    MeleeActions.LIFT_WAIT -> 174
    MeleeActions.LIFT_WALK_1 -> 175
    MeleeActions.LIFT_WALK_2 -> 176
    MeleeActions.LIP_STICK_SWING_1 -> 140
    MeleeActions.LIP_STICK_SWING_2 -> 141
    MeleeActions.LIP_STICK_SWING_3 -> 142
    MeleeActions.LIP_STICK_SWING_4 -> 143
    MeleeActions.LOOPING_ATTACK_END -> 49
    MeleeActions.LOOPING_ATTACK_MIDDLE -> 48
    MeleeActions.LOOPING_ATTACK_START -> 47
    MeleeActions.LYING_GROUND_DOWN -> 192
    MeleeActions.LYING_GROUND_UP -> 184
    MeleeActions.LYING_GROUND_UP_HIT -> 185
    MeleeActions.MARTH_COUNTER -> 369
    MeleeActions.MARTH_COUNTER_FALLING -> 371
    MeleeActions.NAIR -> 65
    MeleeActions.NAIR_LANDING -> 70
    MeleeActions.NESS_SHEILD -> 372
    MeleeActions.NESS_SHEILD_AIR -> 373
    MeleeActions.NESS_SHEILD_AIR_END -> 375
    MeleeActions.NESS_SHEILD_START -> 372
    MeleeActions.NEUTRAL_ATTACK_1 -> 44
    MeleeActions.NEUTRAL_ATTACK_2 -> 45
    MeleeActions.NEUTRAL_ATTACK_3 -> 46
    MeleeActions.NEUTRAL_B_ATTACKING -> 343
    MeleeActions.NEUTRAL_B_ATTACKING_AIR -> 347
    MeleeActions.NEUTRAL_B_CHARGING -> 342
    MeleeActions.NEUTRAL_B_CHARGING_AIR -> 346
    MeleeActions.NEUTRAL_B_FULL_CHARGE -> 344
    MeleeActions.NEUTRAL_B_FULL_CHARGE_AIR -> 348
    MeleeActions.NEUTRAL_GETUP -> 194
    MeleeActions.NEUTRAL_TECH -> 199
    MeleeActions.NOTHING_STATE -> 11
    MeleeActions.ON_HALO_DESCENT -> 12
    MeleeActions.ON_HALO_WAIT -> 13
    MeleeActions.PARASOL_FALLING -> 370
    MeleeActions.PARASOL_SWING_1 -> 128
    MeleeActions.PARASOL_SWING_2 -> 129
    MeleeActions.PARASOL_SWING_3 -> 130
    MeleeActions.PARASOL_SWING_4 -> 131
    MeleeActions.PLATFORM_DROP -> 244
    MeleeActions.PUMMELED_HIGH -> 225
    MeleeActions.REBOUND -> 238
    MeleeActions.REBOUND_STOP -> 237
    MeleeActions.ROLL_BACKWARD -> 234
    MeleeActions.ROLL_FORWARD -> 233
    MeleeActions.RUNNING -> 21
    MeleeActions.RUN_BRAKE -> 23
    MeleeActions.RUN_DIRECT -> 22
    MeleeActions.SHIELD -> 179
    MeleeActions.SHIELD_BREAK_DOWN_D -> 208
    MeleeActions.SHIELD_BREAK_DOWN_U -> 207
    MeleeActions.SHIELD_BREAK_FALL -> 206
    MeleeActions.SHIELD_BREAK_FLY -> 205
    MeleeActions.SHIELD_BREAK_STAND_D -> 210
    MeleeActions.SHIELD_BREAK_STAND_U -> 209
    MeleeActions.SHIELD_BREAK_TEETER -> 211
    MeleeActions.SHIELD_REFLECT -> 182
    MeleeActions.SHIELD_RELEASE -> 180
    MeleeActions.SHIELD_START -> 178
    MeleeActions.SHIELD_STUN -> 181
    MeleeActions.SHINE_RELEASE_AIR -> 368
    MeleeActions.SHINE_TURN -> 364
    MeleeActions.SHOULDERED_TURN -> 270
    MeleeActions.SHOULDERED_WAIT -> 266
    MeleeActions.SHOULDERED_WALK_FAST -> 269
    MeleeActions.SHOULDERED_WALK_MIDDLE -> 268
    MeleeActions.SHOULDERED_WALK_SLOW -> 267
    MeleeActions.SLIDING_OFF_EDGE -> 251
    MeleeActions.SPECIAL_FALL_BACK -> 37
    MeleeActions.SPECIAL_FALL_FORWARD -> 36
    MeleeActions.SPOTDODGE -> 235
    MeleeActions.STANDING -> 14
    MeleeActions.STAR_ROD_SWING_1 -> 136
    MeleeActions.STAR_ROD_SWING_2 -> 137
    MeleeActions.STAR_ROD_SWING_3 -> 138
    MeleeActions.STAR_ROD_SWING_4 -> 139
    MeleeActions.SWORD_DANCE_1 -> 349
    MeleeActions.SWORD_DANCE_1_AIR -> 358
    MeleeActions.SWORD_DANCE_2_HIGH -> 350
    MeleeActions.SWORD_DANCE_2_HIGH_AIR -> 359
    MeleeActions.SWORD_DANCE_2_MID -> 351
    MeleeActions.SWORD_DANCE_2_MID_AIR -> 360
    MeleeActions.SWORD_DANCE_3_HIGH -> 352
    MeleeActions.SWORD_DANCE_3_HIGH_AIR -> 361
    MeleeActions.SWORD_DANCE_3_LOW -> 354
    MeleeActions.SWORD_DANCE_3_LOW_AIR -> 363
    MeleeActions.SWORD_DANCE_3_MID -> 353
    MeleeActions.SWORD_DANCE_3_MID_AIR -> 362
    MeleeActions.SWORD_DANCE_4_HIGH -> 355
    MeleeActions.SWORD_DANCE_4_HIGH_AIR -> 364
    MeleeActions.SWORD_DANCE_4_LOW -> 357
    MeleeActions.SWORD_DANCE_4_LOW_AIR -> 366
    MeleeActions.SWORD_DANCE_4_MID -> 356
    MeleeActions.SWORD_DANCE_4_MID_AIR -> 365
    MeleeActions.TAUNT_LEFT -> 265
    MeleeActions.TAUNT_RIGHT -> 264
    MeleeActions.TECH_MISS_DOWN -> 191
    MeleeActions.TECH_MISS_UP -> 183
    MeleeActions.THROWN_BACK -> 240
    MeleeActions.THROWN_COPY_STAR -> 291
    MeleeActions.THROWN_CRAZY_HAND -> 339
    MeleeActions.THROWN_DOWN -> 242
    MeleeActions.THROWN_DOWN_2 -> 243
    MeleeActions.THROWN_FB -> 272
    MeleeActions.THROWN_FF -> 271
    MeleeActions.THROWN_FORWARD -> 239
    MeleeActions.THROWN_F_HIGH -> 273
    MeleeActions.THROWN_F_LOW -> 274
    MeleeActions.THROWN_KIRBY -> 292
    MeleeActions.THROWN_KIRBY_DRINK_S_SHOT -> 381
    MeleeActions.THROWN_KIRBY_SPIT_S_SHOT -> 382
    MeleeActions.THROWN_KIRBY_STAR -> 290
    MeleeActions.THROWN_KOOPA_AIR_B -> 287
    MeleeActions.THROWN_KOOPA_AIR_END_B -> 380
    MeleeActions.THROWN_KOOPA_AIR_END_F -> 379
    MeleeActions.THROWN_KOOPA_AIR_F -> 286
    MeleeActions.THROWN_KOOPA_B -> 282
    MeleeActions.THROWN_KOOPA_END_B -> 377
    MeleeActions.THROWN_KOOPA_END_F -> 376
    MeleeActions.THROWN_KOOPA_F -> 281
    MeleeActions.THROWN_MASTERHAND -> 330
    MeleeActions.THROWN_MEWTWO -> 303
    MeleeActions.THROWN_MEWTWO_AIR -> 304
    MeleeActions.THROWN_UP -> 241
    MeleeActions.THROW_BACK -> 220
    MeleeActions.THROW_DOWN -> 222
    MeleeActions.THROW_FORWARD -> 219
    MeleeActions.THROW_UP -> 221
    MeleeActions.TUMBLING -> 38
    MeleeActions.TURNING -> 18
    MeleeActions.TURNING_RUN -> 19
    MeleeActions.UAIR -> 68
    MeleeActions.UAIR_LANDING -> 73
    MeleeActions.UNKNOWN_ANIMATION -> 65535
    MeleeActions.UPSMASH -> 63
    MeleeActions.UPTILT -> 56
    MeleeActions.UP_B_AIR -> 368
    MeleeActions.UP_B_GROUND -> 367
    MeleeActions.WAIT_ITEM -> 345
    MeleeActions.WALK_FAST -> 17
    MeleeActions.WALK_MIDDLE -> 16
    MeleeActions.WALK_SLOW -> 15
    MeleeActions.WALL_TECH -> 202
    MeleeActions.WALL_TECH_JUMP -> 203
    MeleeActions.WARP_STAP_FALL -> 306
    MeleeActions.WARP_STAR_JUMP -> 305
    MeleeActions.YOSHI_EGG -> 277
    MeleeActions.ZITABATA -> 374
}