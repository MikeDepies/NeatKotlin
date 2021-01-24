
 export type ControllerDigitalButton = "score" | "b" |"y" | "z"
 export type ControllerAnalogButton = "cStickX" | "cStickY" |"mainStickX" | "mainStickY"
 export type ControllerButton = ControllerDigitalButton | ControllerAnalogButton
 export interface SimulationEndpoints {
  "simulation.frame.output" : {
    [K in ControllerButton] : K extends ControllerDigitalButton ? boolean : number
  }
  "simulation.event.population.new": Population
  "simulation.event.agent.new" : AgentModel
  "simulation.event.score.new" : EvaluationScore
  "simulation.event.boundary.clocks.new" : EvaluationClocksUpdate
 }
 export interface PlayerDataFramePart {}
 export interface ActionDataFramePart {}
 export interface GameFrame {
  frame : number,
  distance : number,
  player1 : PlayerDataFramePart,
  player2 : PlayerDataFramePart,
  action1 : ActionDataFramePart,
  action2 : ActionDataFramePart,
 }
 export interface AgentEvaluationFrame {

 }

 export interface EvaluationScoreContribution {
  name : string,
  score : number,
  contribution : number
 }
export interface EvaluationScore {
  agentId : number,
  evaluationScoreContributions : EvaluationScoreContribution[]
  score : number
}

export interface EvaluationClock {
  name: string,
  frameLength: number,
  startFrame: number,
  framesRemaining: number,
}
export interface EvaluationClocksUpdate {
  clocks : EvaluationClock[]
  frame : number
  generation : number
}
 export interface AgentModel {
   id: number,
   species: number,
  //  score: EvaluationScore,
  //  clocks: EvaluationClock[]
 }
 export interface Population {
   generation : number,
   agents : AgentModel[]
 }