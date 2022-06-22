package server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelWindower(
    val modelListSize: Int, val modelIdChannel: Channel<List<String>> = Channel<List<String>>(Channel.UNLIMITED)
) {
    var windowed: List<List<String>> = listOf()
    suspend fun fill(population: List<NetworkWithId>) = withContext(Dispatchers.Default) {
        windowed = population.map { it.id }.windowed(modelListSize, modelListSize, true)
        launch {
            for (w in windowed) {
                modelIdChannel.send(w)
            }
        }
    }
    fun poll() = modelIdChannel.poll()
}