package server

import kotlinx.coroutines.*
import server.message.endpoints.*

class NoveltyEvaluator(
) : Evaluator<MinimaCriteria<ActionBehavior>> {
    override fun isFinished(): Boolean {
        TODO("Not yet implemented")
    }

    override val score: MinimaCriteria<ActionBehavior>
        get() {
            TODO()
        }

    override suspend fun evaluateFrame(frameUpdate: MarioData) {
        TODO("Not yet implemented")
    }

    override fun finishEvaluation() {
        TODO("Not yet implemented")
    }

}
