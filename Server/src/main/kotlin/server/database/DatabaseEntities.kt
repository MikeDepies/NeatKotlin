package server.database

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import server.Sex

//enum class StudentSex {
//    Male, Female;
//
//    companion object {
//
//    }
//}

private fun Sex.toVarchar() = when (this) {
    Sex.Male -> "male"
    Sex.Female -> "female"
}

private fun Sex.Companion.fromVarchar(string: String) = when (string) {
    "male" -> Sex.Male
    "female" -> Sex.Female
    else -> throw Exception()
}

class StudentEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StudentEntity>(SpedStudents)

    var firstName by SpedStudents.firstName
    var lastName by SpedStudents.lastName
    var sex by (SexEntity referencedOn SpedStudents.sex)
    var esl by SpedStudents.esl
    var spedID by SpedStudents.spedID
    var grade by SpedStudents.grade
//    var meta by (StudentMetaEntity referencedOn )
//    val meta get() = StudentMetaEntity.find { StudentMeta.student eq SpedStudents.id }.first()
}

class StudentMetaEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StudentMetaEntity>(StudentMeta)
    var homeroomLetter by StudentMeta.homeroomLetter
    var student by (StudentEntity referencedOn StudentMeta.student)
    var classBlock by (ClassBlockEntity referencedOn StudentMeta.classBlock)
    var homeroom by (ClassRoomEntity referencedOn StudentMeta.homeroom)
}

data class BiDirectionalMapper<T, V>(val to: (T) -> V, val from: (V) -> T)

fun <A, B> Pair<A, B>.flip() = second to first
fun <T : Enum<T>, V> twoWayMapper(vararg pair: Pair<T, V>): BiDirectionalMapper<T, V> {
    val map = mapOf(*pair)
    val mapReverse = mapOf(*pair.map { it.flip() }.toTypedArray())
    val to = { it: T -> map.getValue(it) }
    val from = { it: V -> mapReverse.getValue(it) }
    return BiDirectionalMapper(
        to,
        from
    )
}

class SexEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SexEntity>(MasterSex)

    var value by MasterSex.value.transform({ it.toVarchar() }, { Sex.fromVarchar(it) })
}

class ClassTrackEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassTrackEntity>(ClassTracks)

    var type by ClassTracks.type
    var classType by ClassTracks.classType
    var school by ClassTracks.school
    val classBlocks by ClassBlockEntity referrersOn ClassBlocks.classTrack
}

class ClassBlockMetaEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassBlockMetaEntity>(ClassBlocksMeta)
    var classBlock by ClassBlockEntity referencedOn ClassBlocksMeta.classBlock
    var groupRoom by ClassBlocksMeta.groupRoom
    var block by ClassBlocksMeta.block
    var group by ClassBlocksMeta.group
    var homeroom by ClassRoomEntity referencedOn ClassBlocksMeta.homeroom
}

class ClassBlockEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassBlockEntity>(ClassBlocks)

    var classTrack by ClassTrackEntity referencedOn ClassBlocks.classTrack
    var classRoom by ClassRoomEntity referencedOn ClassBlocks.classroom
    var subject by SubjectEntity referencedOn ClassBlocks.subject
    var teacher by ClassTeacherEntity.referencedOn(ClassBlocks.teacher)
    var grade by ClassBlocks.grade
    val meta by ClassBlockMetaEntity referrersOn ClassBlocksMeta.classBlock
    val classSegments by ClassSegmentEntity referrersOn ClassSegments.classBlock
}

class ClassRoomEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassRoomEntity>(Classrooms)

    var name by Classrooms.name
}

class ClassSegmentEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassSegmentEntity>(ClassSegments)

    var classBlock by ClassBlockEntity referencedOn ClassSegments.classBlock
    var startTime by ClassSegments.startTime
    var endTime by ClassSegments.endTime
}

class ClassTeacherEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClassTeacherEntity>(ClassTeachers)

    var bilingual by ClassTeachers.bilingual
    var name by ClassTeachers.name
}


class SubjectEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SubjectEntity>(Subjects)

    var name by Subjects.name
}

class SupportMethodEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SupportMethodEntity>(SupportMethods)

    var name by SupportMethods.name
}

class MasterDisabilityEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MasterDisabilityEntity>(MasterDisabilities)

    var name by MasterDisabilities.name
    var description by MasterDisabilities.description
}

class ServiceTypeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ServiceTypeEntity>(ServiceTypes)

    var name by ServiceTypes.name
    var subject by SubjectEntity referencedOn ServiceTypes.subject
}

class StudentRxEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StudentRxEntity>(StudentRxs)

    var student by StudentEntity referencedOn StudentRxs.student
    var serviceType by ServiceTypeEntity referencedOn StudentRxs.serviceType
    var supportMethod by SupportMethodEntity referencedOn StudentRxs.supportMethod
    var minutes by StudentRxs.minutes
}

class StudentScheduleTrackEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StudentScheduleTrackEntity>(StudentScheduleTracks)

    var student by StudentEntity referencedOn StudentScheduleTracks.student
    var classTrack by ClassTrackEntity referencedOn StudentScheduleTracks.classTrack
    var grade by StudentScheduleTracks.grade
}

class SpedTeacherEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SpedTeacherEntity>(SpedTeachers)

    var firstName by SpedTeachers.firstName
    var lastName by SpedTeachers.lastName
    var sex by SexEntity referencedOn SpedTeachers.sex
    var speaksSpanish by SpedTeachers.spanish
}

class SpedTeacherAvailabilityEntryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SpedTeacherAvailabilityEntryEntity>(SpedTeacherAvailability)

    var teacher by SpedTeacherEntity referencedOn SpedTeacherAvailability.teacher
    var startTime by SpedTeacherAvailability.startTime
    var endTime by SpedTeacherAvailability.endTime
    var available by SpedTeacherAvailability.available
}