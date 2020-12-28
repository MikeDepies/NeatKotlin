import neat.Identity
import neat.sigmoidalTransferFunction
import kotlin.test.*


class ActivationFunctionTest {
    @Test
    fun `modified sig function x approaches -infinity`() {
        assertEquals(.5f, sigmoidalTransferFunction(Float.MIN_VALUE))
    }

    @Test
    fun `modified sig function approaches +infinity`() {
        assertEquals(1f, sigmoidalTransferFunction(Float.MAX_VALUE))
    }

    @Test
    fun `identity function`() {
        assertEquals(1f, Identity(1f))
    }
}
