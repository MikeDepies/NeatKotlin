import type { MarioInfo, Settings } from 'src/type/marioInfo'

export const host = "http://192.168.0.132"
export async function getBehaviors(numberOfBehaviors : number | null) {
    const res = await fetch(host + ":8094/behaviors" + ((numberOfBehaviors) ? "?n=" + numberOfBehaviors : ""))
    if (res.ok) {
        const behaviors = await res.json() as MarioInfo[]
        return behaviors.map((m) => {
            return { ...m, y_pos:  277 - m.y_pos };
        })
    }
    else {
        console.error("No Response from server... ");
        return []
    }
}

export async function getSettings() {
    const res = await fetch(host + ":8094/settings")
    if (res.ok) {
        const settings = await res.json() as Settings
        console.log(settings);
        return settings 
    }
}