import { controllerManager, ModelController, type ControllerManager, type ControllerNumber } from "$lib/statStore";
import type { RequestHandler } from "@sveltejs/kit";

export const get: RequestHandler = async (event) => {
    const controllerId = +(event.url.searchParams.get("cid")) as ControllerNumber;
    const res = await fetch("http://192.168.0.139:8091/model/generation", {
        method: "POST",
        headers: {
            "content-type": "application/json"
        },
        body: JSON.stringify({
            controllerId: controllerId
        })
    })
    
    
    
    const model = controllerManager[controllerId]
    model.generation = await res.json() 
    if (controllerId == 0) {
        model.generation += 6100
    } else {
        model.generation += 6100
    }
    return {
        headers: {},
        status: 200,
        body: JSON.stringify(model)
    }

}

export const post: RequestHandler = async (event) => {
    const modelUpdate = await event.request.json() as {
        controllerId: ControllerNumber,
        modelId: string
    }
    controllerManager[modelUpdate.controllerId].modelId = modelUpdate.modelId
    controllerManager[modelUpdate.controllerId].time = new Date().getTime()
    return {
        headers: {},
        status: 200,
        body: "{}"
    }

}