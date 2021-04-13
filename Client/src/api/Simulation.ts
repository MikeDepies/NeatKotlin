import type { Evaluation } from './types/Melee';

export function createEvaluation(): Evaluation {
    return {
        activationFunctions: [],
        controllers: [],
        mutationDictionary: [{
            chance: .5,
            mutation: "sigmoidal"
        }],
        neatParameters: {
            mateChance: 0.6,
            populationSize: 30,
            seed: 0,
            speciesDistance: 10,
            speciesDistanceWeights: {
                avgSharedWeight: 1,
                disjoint: 1,
                excess: 1,
            },
            survivalThreshold: 0.6,
        },
    };
}
