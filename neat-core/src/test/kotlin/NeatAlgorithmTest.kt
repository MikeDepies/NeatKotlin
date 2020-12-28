import io.mockk.*
import neat.*
import neat.model.ConnectionGene
import neat.model.neatMutator
import setup.*
import kotlin.random.*
import kotlin.test.*

data class CompatibilityDistanceData(val E: Int, val N: Int, val D: Int, val W: Float)


class NeatAlgorithmTest {
    private val activationFunctions = listOf(Activation.identity)

    @Test
    fun `mutate add node to model`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        every { random.nextInt(8) } returnsMany listOf(0)
        every { random.nextInt(any()) } returns 0
        val neatMutator = neatMutator(4, 2, random)
        val innovation = lastInnovation(neatMutator) + 1
        val nodeId = 6
        val experiment = simpleNeatExperiment(random, innovation, nodeId, activationFunctions)
        val expectedSize = 1
        experiment.mutateAddNode(neatMutator)
        val newNode = neatMutator.lastNode
        assertEquals(expectedSize, neatMutator.hiddenNodes.size)
        assertEquals(1, neatMutator.connectionsFrom(newNode).size)
        assertEquals(1, neatMutator.connectionsTo(newNode).size)
        assertEquals(false, neatMutator.connections[0].enabled)
    }

    @Test
    fun `mutate add connection to model`() {
        val random = Random(0)
        val neatMutator = initializeNeatModel(random)
        val expectedNumberOfConnections = 6
        val innovation = lastInnovation(neatMutator) + 1
        val nodeId = 5
        val experiment = simpleNeatExperiment(random, innovation, nodeId, activationFunctions)
        experiment.mutateAddConnection(neatMutator)
        assertEquals(expectedNumberOfConnections, neatMutator.connections.size)
        assertEquals(neatMutator.connections.size, neatMutator.connections.distinctBy { it.innovation }.size)
    }

    @Test
    fun `compatibility distance measure`() {
        val c1 = 1f
        val c2 = 1f
        val c3 = 1f
        val (E, N, D, W) = compatibilityDistanceData()
        val expected = 9.72f//(c1 * E).div(N) + (c2 * D).div(N) + (c3 * W)
        assertEquals(expected, compatibilityDifference(E, D, W, N, c1, c2, c3))
    }

    private fun compatibilityDistanceData() = CompatibilityDistanceData(E = 5, N = 1, D = 4, W = .72f)

    @Test
    fun `compatibility distance measure c1=1 c2=0 c3=0`() {
        val c1 = 1f
        val c2 = 0f
        val c3 = 0f
        val (E, N, D, W) = compatibilityDistanceData()
        val expected = 5f//(c1 * E).div(N) + (c2 * D).div(N) + (c3 * W)
        assertEquals(expected, compatibilityDifference(E, D, W, N, c1, c2, c3))
    }

    @Test
    fun `compatibility distance measure c1=0 c2=1 c3=0`() {
        val c1 = 0f
        val c2 = 1f
        val c3 = 0f
        val (E, N, D, W) = compatibilityDistanceData()
        val expected = 4f//(c1 * E).div(N) + (c2 * D).div(N) + (c3 * W)
        assertEquals(expected, compatibilityDifference(E, D, W, N, c1, c2, c3))
    }

    @Test
    fun `compatibility distance measure c1=0 c2=0 c3=1`() {
        val c1 = 0f
        val c2 = 0f
        val c3 = 1f
        val (E, N, D, W) = compatibilityDistanceData()
        val expected = .72f//(c1 * E).div(N) + (c2 * D).div(N) + (c3 * W)
        assertEquals(expected, compatibilityDifference(E, D, W, N, c1, c2, c3))
    }

    @Test
    fun `compatibility distance measure with two neat Genomes with less genes than normalization threshold`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val compatibilityDistance = compatibilityDistance(parent1, parent2, 1f, 1f, 1f)
        assertEquals(5f, compatibilityDistance)
    }

    @Test
    fun `compatibility distance measure with two neat Genomes with more genes than normalization threshold`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val compatibilityDistance = compatibilityDistance(parent1, parent2, 1f, 1f, 1f, 5)
        assertEquals(0.5555556f, compatibilityDistance)
    }

    @Test
    fun `crossover two equal genomes take no excess`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val booleanSequence = listOf(true, true, true, true, true, true, true, true, false, false)
        every { random.nextBoolean() } returnsMany booleanSequence

        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val neatExperiment = simpleNeatExperiment(random, 11, 7, activationFunctions)
        val expectedMatchingGenes =
            matchingGenes(parent1, parent2).mapIndexed { index, pair -> pair.take(booleanSequence[index]) }
        val crossover = neatExperiment.crossover(FitnessModel(parent1, 1f), FitnessModel(parent2, 1f))
        val (disjoint1, disjoint2) = disjoint(
            parent1,
            parent2
        )
        val expectedConnectionGenes = (expectedMatchingGenes + disjoint1 + disjoint2).sortedBy { it.innovation }
        assertEquals(expectedConnectionGenes, crossover.connections)

    }

    @Test
    fun `crossover parent 1 more fit than parent 2 genomes`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val booleanSequence = listOf(false, true, true, false, false)
        every { random.nextBoolean() } returnsMany booleanSequence

        val neatExperiment = simpleNeatExperiment(random, 11, 7, activationFunctions)
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val expectedMatchingGenes =
            matchingGenes(parent1, parent2).mapIndexed { index, pair -> pair.take(booleanSequence[index]) }
        val crossover = neatExperiment.crossover(FitnessModel(parent1, 2f), FitnessModel(parent2, 1f))
        val expectedConnectionGenes = (expectedMatchingGenes + excess(parent1, parent2).excess1 + disjoint(
            parent1,
            parent2
        ).disjoint1).sortedBy { it.innovation }
        assertEquals(expectedConnectionGenes, crossover.connections)
        verify(exactly = booleanSequence.size) {
            random.nextBoolean()
        }
    }

    @Test
    fun `crossover parent 1 less fit than parent 2 genomes has correct connection genes`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val booleanSequence = listOf(false, true, true, false, false)
        every { random.nextBoolean() } returnsMany booleanSequence

        val neatExperiment = simpleNeatExperiment(random, 11, 7, activationFunctions)
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val expectedMatchingGenes =
            matchingGenes(parent1, parent2).mapIndexed { index, pair -> pair.take(booleanSequence[index]) }
        val crossover = neatExperiment.crossover(FitnessModel(parent1, 1f), FitnessModel(parent2, 2f))
        val expectedConnectionGenes = (expectedMatchingGenes + excess(parent1, parent2).excess2 + disjoint(
            parent1,
            parent2
        ).disjoint2).sortedBy { it.innovation }
        assertEquals(expectedConnectionGenes, crossover.connections)
        verify(exactly = booleanSequence.size) {
            random.nextBoolean()
        }
    }

    @Test
    fun `disjoint genes between two genomes`() {
        val random = mockk<Random>()
        every { random.nextFloat() } returns 1f
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val (disjoint1, disjoint2) = disjoint(parent1, parent2)
        val expectedDisjoint1Count = 1
        val expectedDisjoint2Count = 2
        val expectedDisjoint1 = parent1.connections.takeInnovations(listOf(8))
        val expectedDisjoint2 = parent2.connections.takeInnovations(listOf(6, 7))
        assertEquals(expectedDisjoint1Count, disjoint1.size)
        assertEquals(expectedDisjoint1, disjoint1)
        assertEquals(expectedDisjoint2, disjoint2)
        assertEquals(expectedDisjoint2Count, disjoint2.size)
    }

    @Test
    fun `excess genes between two genomes`() {
        val random = Random(0)
        val (parent1, parent2) = createTwoRelatedGenomes(random)
        val excess = excess(parent1, parent2)
        val excessInnovationNumbers = listOf(9, 10)
        val expectedExcess = parent2.connections.takeInnovations(excessInnovationNumbers)
        val expectedExcessCount = 2
        assertEquals(expectedExcessCount, excess.size())
        assertEquals(expectedExcess, excess.excess2)
    }

    private fun List<ConnectionGene>.takeInnovations(
        excessInnovationNumbers: List<Int>
    ) = filter { c -> excessInnovationNumbers.any { c.innovation == it } }
    //Do we use a Score Registry instead of a neat.FitnessModel object? that way we can generalize it to a
    //lambda to resolve the score for a given model.


}

private fun <A> Pair<A, A>.take(boolean: Boolean): A {
    return if (boolean) first else second
}

