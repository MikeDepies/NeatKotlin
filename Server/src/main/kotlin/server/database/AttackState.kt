package server.database

enum class AttackState {
    ATTACKING, COOLDOWN, NOT_ATTACKING, WINDUP
}

val AttackState.id
    get() = when (this) {
        AttackState.ATTACKING -> 1
        AttackState.COOLDOWN -> 2
        AttackState.NOT_ATTACKING -> 3
        AttackState.WINDUP -> 0
    }