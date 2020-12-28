package neat

fun XORTruthTable(): List<() -> Pair<List<Float>, List<Float>>> {
    return listOf(
        { listOf(0f, 0f) to listOf(0f) },
        { listOf(0f, 1f) to listOf(0f) },
        { listOf(1f, 0f) to listOf(1f) },
        { listOf(1f, 1f) to listOf(1f) },
    )
}

fun XORTruthTable2(): List<Pair<List<Float>, List<Float>>> {
    return listOf(
        listOf(0f, 0f) to listOf(0f),
        listOf(0f, 1f) to listOf(0f),
        listOf(1f, 0f) to listOf(1f),
        listOf(1f, 1f) to listOf(1f),
    )
}