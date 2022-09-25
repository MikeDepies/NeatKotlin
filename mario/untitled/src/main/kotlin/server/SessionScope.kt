import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import kotlin.reflect.KClass

class SessionScopeImpl(override val scope: Scope, override val messageWriter: MessageWriter) : SessionScope
interface SessionScope {
    val scope: Scope
    val messageWriter: MessageWriter

    /**
     * Get a Koin instance if available
     * @param qualifier
     * @param scope
     * @param parameters
     *
     * @return instance of type T or null
     */
    fun <T : Any> getOrNull(
        clazz: KClass<T>,
        qualifier: Qualifier? = null,
        parameters: ParametersDefinition? = null
    ): T? = scope.getOrNull(clazz, qualifier, parameters)

    /**
     * Get a Koin instance
     * @param clazz
     * @param qualifier
     * @param parameters
     *
     * @return instance of type T
     */
    fun <T : Any> get(
        clazz: KClass<T>,
        qualifier: Qualifier? = null,
        parameters: ParametersDefinition? = null
    ): T = scope.get(clazz, qualifier, parameters = parameters)

    /**
     * Get a Koin instance
     * @param java class
     * @param qualifier
     * @param parameters
     *
     * @return instance of type T
     */
    fun <T : Any> get(
        clazz: Class<T>,
        qualifier: Qualifier? = null,
        parameters: ParametersDefinition? = null
    ): T = scope.get(clazz, qualifier, parameters = parameters)
}

/**
 * Get a Koin instance if available
 * @param qualifier
 * @param scope
 * @param parameters
 *
 * @return instance of type T or null
 */
inline fun <reified T> SessionScope.getOrNull(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): T? = scope.getOrNull(qualifier, parameters)

/**
 * Get a Koin instance
 * @param qualifier
 * @param scope
 * @param parameters
 */
inline fun <reified T> SessionScope.get(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): T = scope.get(qualifier, parameters)

/**
 * Lazy inject a Koin instance if available
 * @param qualifier
 * @param scope
 * @param parameters
 *
 * @return Lazy instance of type T or null
 */
inline fun <reified T> SessionScope.injectOrNull(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T?> = scope.injectOrNull(qualifier, parameters = parameters)

/**
 * Lazy inject a Koin instance
 * @param qualifier
 * @param scope
 * @param parameters
 *
 * @return Lazy instance of type T
 */
inline fun <reified T> SessionScope.inject(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = scope.inject(qualifier, parameters = parameters)
