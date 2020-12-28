package server

import kotlinx.serialization.Serializable
import org.optaplanner.core.api.domain.entity.PlanningEntity
import org.optaplanner.core.api.domain.variable.PlanningVariable

enum class Sex { Male, Female; companion object }

enum class DayOfWeek { Monday, Tuesday, Wednesday, Thursday, Friday}

@Serializable
data class Student(val id: Int, val name: String, val sex: Sex)
@Serializable
data class Teacher(val id: Int, val name: String)
@Serializable
data class SpedTeacher(val id: Int, val name: String, val sex: Sex, val bilingual: Boolean)
@Serializable
data class Classroom(val id: Int, val number: String)
@Serializable
data class Class(
    val id: Int,
    val classroom: Classroom,
    val classTrack: ClassTrack,
    val teacher: Teacher,
    val subject: Subject,
    val dayOfWeek: DayOfWeek,
    val segments: List<ClassSegment>
)
@Serializable
data class ClassSegment(val startTime: Int, val endTime: Int)
@Serializable
data class ClassTrack(val id: Int)
@Serializable
data class Subject(val id: Int, val name: String)
@Serializable data class ServiceType(val id: Int, val name: String, val subject: Subject)

@Serializable
enum class SupportMethod { InClass, Pullout }

@Serializable
data class ServicePrescription(val student: Student, val serviceType: ServiceType, val supportMethod: SupportMethod, val minutes: Int)
//data class ServiceDetail(val student: Student, val serviceType: ServiceType, val supportMethod: SupportMethod, val minutes: Int)
@Serializable
data class ServiceSlot(val startTime: Int, val endTime: Int, val classroom: Class)

@PlanningEntity
class ServiceAssignment constructor(
    val servicePrescription: ServicePrescription,
    @PlanningVariable(valueRangeProviderRefs = ["serviceSlotRange"])
    val serviceSlot: ServiceSlot,
    @PlanningVariable(valueRangeProviderRefs = ["spedTeacherRange"])
    val spedTeacher: SpedTeacher
)


//@PlanningSolution
//class GeneratedSchedule constructor(
//    @PlanningEntityCollectionProperty
//    val serviceAssignments: List<ServiceAssignment> = emptyList(),
//
//    @ValueRangeProvider(id = "serviceSlotRange")
//    @ProblemFactCollectionProperty
//    val serviceSlots: List<ServiceSlot> = emptyList(),
//
//    @ValueRangeProvider(id = "spedTeacherRange")
//    @ProblemFactCollectionProperty
//    val spedTeachers: List<SpedTeacher> = emptyList()
//)
//class ScoreCalculator :
//    EasyScoreCalculator<GeneratedSchedule> {
//    override fun calculateScore(solution: GeneratedSchedule?): Score<out Score<*>> =
//        HardSoftScore.of(0, 0)
//}
//fun main() {
//    val solverFactory: SolverFactory<GeneratedSchedule> =
//}