export type Character =
    | "Doc"
    | "Mario"
    | "Luigi"
    | "Bowser"
    | "Peach"
    | "Yoshi"
    | "DK"
    | "Falcon"
    | "Gannondorf"
    | "Falco"
    | "Fox"
    | "Ness"
    | "Ice Climbers"
    | "Kirby"
    | "Samus"
    | "Zelda"
    | "Link"
    | "Young Link"
    | "Pichu"
    | "Pikachu"
    | "Jigglypuff"
    | "Mewtwo"
    | "Mr. Game & Watch"
    | "Marth"
    | "Roy";


    export type NewSimulationRequest = {
        stage: Stage;
        evaluations: Evaluation[];
    };
    export type Evaluator = {};
    export type Evaluation = {
        controllers: Controller[];
        neatParameters: NeatParameters;
        activationFunctions: string[];
        mutationDictionary: MutationEntry[];
        // evaluator: Evaluator;
    };
    export type MutationEntry = {
        chance: number;
        mutation: string;
    };
    export type NeatParameters = {
        seed: number;
        speciesDistance: number;
        speciesDistanceWeights: {
            excess: number;
            disjoint: number;
            avgSharedWeight: number;
        };
        survivalThreshold: number;
        mateChance: number;
        populationSize: number;
    };
    export type Controller = {
        character: Character;
        cpu: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9;
    };
    export type Stage =
        | "Battlefield"
        | "Final Destination"
        | "Dreamland"
        | "Fountain Of Dreams"
        | "Pokemon Stadium"
        | "Yoshi Story"
        | "Random Stage";