import neat.model.*
data class ControllerMapping(val controllerValues: List<String>)
class ControllerOutputMapper(val controllerMap: MutableMap<NeatMutator, ControllerMapping>) {
    companion object {
        val controllerPool = listOf(
            "a",
            "b",
            "x",
            "y",
            "z",
            "mainStickX",
            "mainStickY",
            "cStickx",
            "cStickY",
            "leftShoulder",
            "rightShoulder"
        )
    }

    /**
     * Returns a controller map for the corresponding neatMutator. If the neat mutator is not yet in
     * the controller map, it will generate one at random from the supplied pool of controller outputs.
     */
    fun controllerMap(neatMutator: NeatMutator): ControllerMapping {
        return if (controllerMap.containsKey(neatMutator)) {
            controllerMap.getValue(neatMutator)
        } else {
            val controllerMapping = ControllerMapping(listOf())
            controllerMap[neatMutator] = controllerMapping
            controllerMapping
        }
    }
}