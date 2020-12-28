package setup

import io.mockk.*
import neat.Activation
import neat.model.neatMutator
import neat.SigmoidalTransferFunction
import neat.toNetwork
import kotlin.random.*
import kotlin.test.*

class EvaluationTest {
    @Test
    fun `Cyclic connection causing a +1 sequence when input = 1`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returnsMany listOf(1f)

        val neatMutator = initializeCyclicConnectionsNeatModel(random)
        val input = listOf(1f)
        val network = neatMutator.toNetwork()
        repeat(5) {
            network.evaluate(input)

            val result = network.output()

            val expected = listOf(1f + it.toFloat())
            assertEquals(expected, result)
            println("value=${network.outputNodes.map { it.value }}\nactivated=$result")
        }
//        neat.model.neatMutator.evaluate(input)
//        assertEquals(1f, neat.model.neatMutator.outputNodes[0].value)
    }

    @Test
    fun `evaluate network (1,1) with weights (1, 1) input 1f`() {
        val expected = listOf(1f)
        val random = mockk<Random>()
        every { random.nextFloat() } returnsMany listOf(1f, 1f)
        val network = neatMutator(1, 1, random).toNetwork()
        val input = listOf(1f)
        network.evaluate(input)
        val result = network.output()
//        val result = neat.model.neatMutator.evaluate(input)
        assertEquals(expected, result)
    }

    @Test
    fun `evaluate network (1,1) with weights (,1, ,1) input 1f Output Fn=Identity`() {
        val expected = listOf(.1f)
        val random = mockk<Random>()
        every { random.nextFloat() } returnsMany listOf(.1f)
        val network = neatMutator(1, 1, random).toNetwork()
        val input = listOf(1f)
        network.evaluate(input)
        val result = network.output()
//        val result = neat.model.neatMutator.evaluate(input)
        assertEquals(expected, result)
    }

    @Test
    fun `evaluate network (1,1) with weights (,1, ,1) input 1f Output Fn=Sigmoidal`() {
        val expected = listOf(.5f)
        val random = mockk<Random>()
        every { random.nextFloat() } returnsMany listOf(.1f)
        val network = neatMutator(1, 1, random, Activation.sigmoidal).toNetwork()
        val input = listOf(0f)
        network.evaluate(input)
        val result = network.output()
//        val result = neat.model.neatMutator.evaluate(input)
        assertEquals(expected, result)
    }

    @Test
    fun `evaluate network (1,1) with weights (1, 1) input 2`() {
        val expected = listOf(2f)
        val b = mockk<Random>()
        every { b.nextFloat() } returnsMany listOf(1f)
//        b.nextFloat()

//        every { neat.random.nextFloat() } returnsMany listOf(.3f, .6f)
        val network = neatMutator(1, 1, b).toNetwork()
        val input = listOf(2f)
        network.evaluate(input)
        val result = network.output()
//        val result = neat.model.neatMutator.evaluate(input)
        assertEquals(expected, result)
    }

}

