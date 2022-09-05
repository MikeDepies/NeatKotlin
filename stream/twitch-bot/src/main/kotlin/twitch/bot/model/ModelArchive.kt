package twitch.bot.model

class ModelArchive(archiveList : List<Model>, var archiveCapacity: Int) {
    val archiveList = archiveList.toMutableList()
    fun commitModel(model : Model): Model? {
        archiveList += archiveList
        return if (archiveList.size > archiveCapacity) {
            archiveList.removeAt(0)
        } else null
    }

    fun removeModel(model : Model) {
        archiveList.remove(model)
    }
}