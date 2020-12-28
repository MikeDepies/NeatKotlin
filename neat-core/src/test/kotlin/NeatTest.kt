import neat.*
import neat.model.*
import kotlin.test.*

class NeatTest {
    @Test
    fun `Add connection with node ids that aren't in neat mutator`() {
        val neat: NeatMutator = neatMutator(4, 2)
        val connectionGene = ConnectionGene(100, 101, 1f, true, 1)
        neat.apply {
            assertFails("Connection can not be added because connection gene refers to nodes that aren't in the neat.neat mutator") {
                neat.addConnection(connectionGene)
            }
        }
    }

    @Test
    fun `Add connection with source node id not in neat mutator`() {
        val neat: NeatMutator = neatMutator(4, 2)
        val connectionGene = ConnectionGene(100, 1, 1f, true, 1)
        neat.apply {
            assertFails("Connection can not be added because connection gene refers to nodes that aren't in the neat.neat mutator") {
                neat.addConnection(connectionGene)
            }
        }
    }

    @Test
    fun `Add connection with target node id not in neat mutator`() {
        val neat: NeatMutator = neatMutator(4, 2)
        val connectionGene = ConnectionGene(1, 100, 1f, true, 1)
        neat.apply {
            assertFails("Connection can not be added because connection gene refers to nodes that aren't in the neat.neat mutator") {
                neat.addConnection(connectionGene)
            }
        }
        //new single connection gene added connecting two previously unconnected ndoes.
    }

    @Test
    fun `Add connection between two nodes already connected to neat mutator`() {
        val neat: NeatMutator = neatMutator(4, 2)

        val connectionGene = ConnectionGene(neat.inputNodes.first().node, neat.outputNodes.first().node, 1f, true, 1)

        assertFails {
            neat.addConnection(connectionGene)
        }
        //new single connection gene added connecting two previously unconnected ndoes.
    }

    @Test
    fun `Neat Mutator has proper number of nodes on construction`() {
        val neatMutator = neatMutator(4, 2)
        assertEquals(4, neatMutator.inputNodes.size)
        assertEquals(2, neatMutator.outputNodes.size)
        assertEquals(0, neatMutator.hiddenNodes.size)
    }

    @Test
    fun `Neat Mutator has proper connection setup between nodes on construction`() {
        val neatMutator = neatMutator(4, 2)
        //assert input to hidden connections
        //assert hidden to output connections

        val connectionsToOutputNode1 = neatMutator.connectionsTo(neatMutator.outputNodes[0])
        val connectionsToOutputNode2 = neatMutator.connectionsTo(neatMutator.outputNodes[1])
        assertEquals(4, connectionsToOutputNode1.size)
        assertEquals(4, connectionsToOutputNode2.size)
        assertTrue(connectionsToOutputNode2.all { it.enabled })
        assertTrue(connectionsToOutputNode1.all { it.enabled })
    }

    @Test
    fun `Node without connections`() {
        val neatMutator = neatMutator(2, 2)
        neatMutator.apply {
            val nodeId = 4
            val node = NodeGene(nodeId, NodeType.Hidden, Activation.identity)
            addNode(node)
            assertTrue(neatMutator.connectionsFrom(node).isEmpty())
            assertTrue(neatMutator.connectionsTo(node).isEmpty())
        }
    }
}