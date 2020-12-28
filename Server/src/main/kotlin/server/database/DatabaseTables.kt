package server.database

import com.zaxxer.hikari.HikariConfig
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable


object StudentMeta : IntIdTable() {
    val student = reference("student", SpedStudents)
    val classBlock = reference("classBlock", ClassBlocks)
    val homeroomLetter = varchar("homeroomLetter", 5)
    val homeroom = reference("homeroom", Classrooms)
}

object ClassBlocksMeta : IntIdTable() {
    val classBlock = reference("classBlock", ClassBlocks)
    val groupRoom = varchar("groupRoom", 30)
    val block = integer("block")
    val group = integer("group")
    val homeroom = reference("homeroom", Classrooms)
}

object ClassBlocks : IntIdTable() {
    val classTrack = reference("classTrack", ClassTracks)
    val classroom = reference("classRoom", Classrooms)
    val subject = reference("subject", Subjects)
    val teacher = reference("teacher" , ClassTeachers)
    val grade = integer("grade")
}
object Classrooms : IntIdTable() {
    val name = varchar("name", 30)
}
object ClassSegments : IntIdTable() {
    val classBlock = reference("classBlock", ClassBlocks)
    val startTime = integer("startTime")
    val endTime = integer("endTime")
}
object ClassTeachers : IntIdTable() {
    val name = varchar("name", 60)
    val bilingual = bool("bilingual")
}
object ClassTracks : IntIdTable() {
    val school = varchar("school", 30)
    val type = varchar("type", 4)
    val classType = varchar("classType", 12)
}

object MasterDisabilities : IntIdTable() {
    val name = varchar("name", 30)
    val description = text("description")
}

/**
 * Pullout (PO)
 * In class  (ICTS)
 */
object SupportMethods : IntIdTable() {
    val name = varchar("name", 30)
}
object ServiceTypes : IntIdTable() {
    val name = varchar("name", 30)
    val subject = reference("subject", Subjects)
}
object MasterSex : IntIdTable() {
    val value = varchar("value", 10)
}
object SpedTeachers : IntIdTable() {
    val firstName = varchar("firstName", 30)
    val lastName = varchar("lastName", 30)
    val sex = reference("sex", MasterSex)
    val spanish = bool("spanish")
}
object SpedTeacherAvailability : IntIdTable() {
    val teacher = reference("teacher", SpedTeachers)
    val startTime = integer("startTime")
    val endTime = integer("endTime")
    val available = bool("available")
}
object SpedStudents : IntIdTable() {
    val firstName = varchar("firstName", 30)
    val lastName = varchar("lastName", 30)
    val sex = reference("sex", MasterSex)
    val esl = bool("esl")
    val spedID = integer("spedID")//.uniqueIndex()
    val grade = integer("grade")
}
object StudentDisabilities : IntIdTable() {
    val student = reference("student", SpedStudents)
    val disability = reference("disability", MasterDisabilities)
}
object StudentRxs : IntIdTable() {
    val student = reference("student", SpedStudents)
    val serviceType = reference("serviceType", ServiceTypes) //English, Reading, Math, Science...
    val supportMethod = reference("supportMethod", SupportMethods) //PO/ICTS
    val minutes = integer("minutes")
}
object StudentScheduleTracks : IntIdTable() {
    val student = reference("student", SpedStudents)
    val classTrack = reference("classTrack", ClassTracks)
    val grade = integer("grade")
}
object Subjects : IntIdTable() {
    val name = varchar("name", 30)
}

fun DbConfig.toHikariConfig(): HikariConfig {
    val config = HikariConfig()
    config.driverClassName = "com.mysql.jdbc.Driver"
    config.jdbcUrl =
        "jdbc:mysql://$host:$port/$schema?rewriteBatchedStatements=true&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC"
    config.username = username
    config.password = password
    config.maximumPoolSize = maxPoolSize
    config.minimumIdle = minIdle
    config.isAutoCommit = false
    return config
}

@Serializable
data class DbConfig(val host: String,
                    val port: Int,
                    val schema: String,
                    val username: String,
                    val password: String,
                    val maxPoolSize: Int = 100,
                    val minIdle: Int = 2)