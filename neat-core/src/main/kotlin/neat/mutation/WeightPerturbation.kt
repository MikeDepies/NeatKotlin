package neat.mutation

import neat.NeatExperiment

fun NeatExperiment.weightPerturbation(range: Float) = (random.nextFloat() * (range * 2)) - range