import type { MarioInfo } from 'src/type/marioInfo'

const host = "http://192.168.1.132"
export async function getBehaviors() {
    const res = await fetch(host + ":8094/behaviors")
    if (res.ok) {
        const behaviors = await res.json() as MarioInfo[]
        return behaviors.map((m) => {
            return { ...m, y_pos:  261 - m.y_pos };
        })
    }
    else {
        console.error("No Response from server... ");
        return []
    }
}