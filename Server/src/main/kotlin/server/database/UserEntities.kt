package server.database

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntity>(UserTable)

    var firstName by UserTable.firstName
    var lastName by UserTable.lastName
    var roles by RoleEntity.via(UserRoleTable)
}

class RoleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RoleEntity>(RoleTable)

    var role by RoleTable.role.transform(toColumn = { it.toColumnString() }, toReal = { it.toUserRole() })

    private fun UserRole.toColumnString(): String {
        return when (this) {
            UserRole.Teacher -> "teacher"
            UserRole.Admin -> "admin"
            UserRole.Parent -> "parent"
            UserRole.Student -> "student"
        }
    }

    private fun String.toUserRole(): UserRole {
        return when (this) {
            "admin" -> UserRole.Admin
            "teacher" -> UserRole.Teacher
            "parent" -> UserRole.Parent
            "student" -> UserRole.Student
            else -> error("No matching User role for $this")
        }
    }

}


