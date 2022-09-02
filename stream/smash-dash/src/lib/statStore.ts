export type ControllerNumber = 0 | 1
export type ControllerManager = Record<ControllerNumber, ModelController>
export class ModelController {
    get games(): number  {
        return this.wins + this.losses
    }
    constructor(public controllerId: number, public modelId: string, public kills: number, public deaths: number, public wins : number, public losses : number, public time : number, public generation : number) { 
        
    }
}
const dt = new Date().getTime()
const model1 = new ModelController(0, "", 0, 0, 0, 0, dt, 0)
const model2 = new ModelController(1, "", 0, 0, 0, 0, dt, 0)
export const controllerManager : ControllerManager = {
    0 : model1,
    1 : model2
}
export type ControllerRequest = {
    controllerId: ControllerNumber,
}
export type ControllerUpdate<K extends keyof ModelController> =ControllerRequest  & Record<K, number>