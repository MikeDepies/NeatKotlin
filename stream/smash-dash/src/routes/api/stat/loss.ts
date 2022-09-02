import { controllerManager, type ControllerRequest, type ControllerUpdate } from "$lib/statStore";
import type { RequestHandler } from "@sveltejs/kit";

export const post: RequestHandler = async ({ request }) => {
    const {controllerId} = await request.json() as ControllerRequest
    controllerManager[controllerId].losses += 1
    return {
        headers: {},
        status: 200,
        body: "{}"
    }

}