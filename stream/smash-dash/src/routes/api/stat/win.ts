import { controllerManager, type ControllerRequest } from "$lib/statStore";
import type { RequestHandler } from "@sveltejs/kit";

export const post: RequestHandler = async ({ request }) => {
    const { controllerId } = await request.json() as ControllerRequest
    controllerManager[controllerId].wins += 1
    
    return {
        headers: {},
        status: 200,
        body: "{}"
    }

}