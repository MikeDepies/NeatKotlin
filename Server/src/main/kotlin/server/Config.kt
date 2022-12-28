package server

import kotlinx.serialization.Serializable

@Serializable
data class UrlConfig(val twitchBot : String)
@Serializable
data class Config(val url : UrlConfig)

fun test(data : List<Double>) {
    val method: (List<Double>) -> List<Double> = {
        someMethod(it)
    }
    data.map {

    }
    val ema = Ema(10)
    convert(data, ema::calculate)
//    data.map(transform = { it * 2})
}

fun convert(someData : List<Double>, method : (List<Double>) -> List<Double>) {
    method(someData)
}

fun someMethod(data : List<Double>): List<Double> {
    return data.map { it * 2 }
}
class Ema(val lookBack : Int) {
    fun calculate(data : List<Double>): List<Double> {
        return data.map { it * 2 }
    }
}
