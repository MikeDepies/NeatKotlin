import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mu.KotlinLogging
import java.util.*

private val log = KotlinLogging.logger { }

//Need a table-like storage implementation. Extract the
interface UserTokenResolver {
    /**
     * return Player associated with the token. null if there is no player that can be resolved.
     */
    suspend fun resolve(token: String): UserRef
}

//TODO Rewrite to improve readability / asynchronous design logic
//  Maybe there is a way to do asynchronous but "order guaranteed by Topic". Where in this case the topic might be
//  the game session.
class UserTokenResolverImpl(private val authService: AuthService) : UserTokenResolver {
    private val map = mutableMapOf<String, UserRef>()
    override suspend fun resolve(potentialToken: String): UserRef {
        //First check internal cache
        //then check database
        //then check auth0

        return if (map.containsKey(potentialToken)) {
            map.getValue(potentialToken)
        } else {
//            val entity = transaction { PlayerEntity.find { PlayerTable.token eq potentialToken }.firstOrNull() }
//            return if (entity != null) {
//                entity.token
//            } else {
//                val authPlayer = authService.authPlayer(potentialToken)
//                UserRef(potentialToken)//TODO implement backend data storage to associate auth id
//            }
            UserRef(potentialToken)//TODO implement backend data storage to associate auth id
        }
    }
}

interface AuthService {
    suspend fun authPlayer(potentialToken: String): AuthPlayer
}

class AuthServiceSimple : AuthService {
    override suspend fun authPlayer(potentialToken: String): AuthPlayer {
        return AuthPlayer("newUser_${UUID.randomUUID()}", null)
    }
}

@Serializable
data class Auth0TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    val scope: String,
    @SerialName("expires_in")
    val expiresIn: Long
)
data class Auth0Config(val clientId : String, val clientSecret: String, val audience: String, val grantType : String)
class AuthServiceAuth0(val client: HttpClient, val auth0Config: Auth0Config) : AuthService {
    override suspend fun authPlayer(potentialToken: String): AuthPlayer {
        val tokenResponse = client.post<Auth0TokenResponse>("https://werewolf-v2.auth0.com/oauth/token") {
            contentType(ContentType.Application.Json)
            val data = buildJsonObject {
                put("client_id", auth0Config.clientId)
                put("client_secret", auth0Config.clientSecret)
                put("audience", auth0Config.audience)
                put("grant_type", auth0Config.grantType)
            }
            body = data//json.stringify(JsonObject.serializer(), )
        }
        log.info { tokenResponse }
        /*val jsonObject = client.get<JsonArray>("https://werewolf-v2.auth0.com/api/v2/users") {
            val mgmtToken = tokenResponse.accessToken
            header("authorization", "Bearer ${mgmtToken.encodeURLPath()}")
        }*/
        val jsonObject2 = client.get<JsonObject>("https://werewolf-v2.auth0.com/api/v2/users/$potentialToken") {
            val mgmtToken = tokenResponse.accessToken
            header("authorization", "Bearer ${mgmtToken.encodeURLPath()}")
        }
        return AuthPlayer(
            jsonObject2["nickname"]!!.jsonPrimitive.content,
            jsonObject2["picture"]!!.jsonPrimitive.content
        )
    }

}

//PlayerRef(token)

data class AuthPlayer(val username: String, val picture: String?)