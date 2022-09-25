import { httpServer } from "src/Config";
export type ModelRequest = {
    controllerId: number
}
export class ModelHelper {
    constructor(public controllerId: number) {

    }

    async getModel() {
        const body = JSON.stringify({
            controllerId: this.controllerId
        } as ModelRequest)
        const res = fetch(httpServer, {
            method: "POST",
            body
        })
    }
}