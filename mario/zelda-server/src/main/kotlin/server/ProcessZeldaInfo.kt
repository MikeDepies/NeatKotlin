package server

import java.util.*

const val zeldaInfo = """current_level	int	The current level Link is in (0 for overworld)
x_pos	int	Link's x position in the screen (from the left)
y_pos	int	Link's y position in the screen (from the top)
direction	str	Link's direction as one of {"N", "S", "E", "W"}
has_candled	bool	Whether Link has candled the current room
pulse_1	str	The signal playing through pulse 1
pulse_2	str	The signal playing through pulse 2
killed_enemies	int	The number of enemies killed
number_of_deaths	int	The number of times Link has died
sword	str	The kind of sword Link has
number_of_bombs	int	The number of bombs in Link's inventory
arrows_type	str	The kind of arrows in Link's inventory
has_bow	bool	Whether Link has the bow in his inventory
candle_type	str	The type of candle in Link's inventory
has_whistle	bool	Whether Link has the whistle in his inventory
has_food	bool	Whether Link has food in his inventory
potion_type	str	The type of potion in Link's inventory
has_magic_rod	bool	Whether Link has the magic rod in his inventory
has_raft	bool	Whether Link has the raft in his inventory
has_magic_book	bool	Whether Link has the magic book in his inventory
ring_type	str	The type of ring in Link's inventory
has_step_ladder	bool	Whether Link has the step ladder in his inventory
has_magic_key	bool	Whether Link has the magic key in his inventory
has_power_bracelet	bool	Whether Link has the power bracelet in his inventory
has_letter	bool	Whether Link has the letter in his inventory
is_clock_possessed	bool	Whether the clock is possessed
rupees	int	The number of rupess Link has collected
keys	int	The number of keys in Link's inventory
heart_containers	int	The number of heart containers that Link has
hearts	float	The number of remaining health Link has
has_boomerang	bool	Whether Link has the boomerang in his inventory
has_magic_boomerang	bool	Whether Link has the magic boomerang in his inventory
has_magic_shield	bool	Whether Link has the magic shield in his inventory
max_number_of_bombs	int	The maximum number of bombs Link can carry"""

fun main() {
    fun processType(t : String): String {
        return when(t) {
            "bool" -> "Boolean"
            "int" -> "Int"
            "str" -> "String"
            else -> t
        }
    }
    fun camelCaseFromUnderScores(variableName : String): String {
        return variableName.split("_").joinToString("") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }.replaceFirstChar { it.lowercase() }
    }
    zeldaInfo.split("\n").map {
        val s = it.split("\t")
        "${camelCaseFromUnderScores(s[0])} : ${processType(s[1])},"
    }.forEach {
        println(it)
    }
}