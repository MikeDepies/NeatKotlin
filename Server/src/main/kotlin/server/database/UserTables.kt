package server.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object UserTable : IntIdTable() {
    val firstName = varchar("firstName", 35)
    val lastName = varchar("lastName", 35)
}

object RoleTable : IntIdTable() {
    val role = varchar("role", 20)
}

object UserRoleTable : Table() {
    val user = reference("user", UserTable)
    val role = reference("role", RoleTable)
}

fun createUserRoles() {
    transaction {
        RoleTable.insert {
            it[role] = "teacher"
        }
        RoleTable.insert {
            it[role] = "admin"
        }
    }
}

sealed class UserRole {
    object Teacher : UserRole()
    object Admin : UserRole()
    object Parent : UserRole()
    object Student : UserRole()

}