package neat.hyperneat

import neat.*
import neat.model.*


data class HyperNeatXYDef(
    val connectionMagnitude: Float,
    val connectionThreshold: Float,
    val boundary: Boundary,
    val resolution: Float
)

data class Boundary(
    val x1: Float,
    val x2: Float,
    val y1: Float,
    val y2: Float
)

fun boundary(width: Float, height: Float, centerX: Float = 0f, centerY: Float = 0f): Boundary {
    return Boundary(
        centerX - width / 2,
        centerX + width / 2,
        centerY - height / 2,
        centerY + height / 2
    )
}

fun hyperNeatXY(neatMutator: NeatMutator, def: HyperNeatXYDef) {
    val (x1, x2, y1, y2) = def.boundary
    val resolution = def.resolution
    var x = x1;
    var y = y1;
    while (x < x2) {

        while (y < y2) {

            y += resolution
        }
        x += resolution
    }
}