import { writable } from 'svelte/store'

export type ColorMap = {
    [K in number]: [number, number, number]
}
let colorMap = writable<ColorMap>({})
function getColor(speciesId: number, colorMap: ColorMap) {
    if (colorMap[speciesId]) {
        return colorMap[speciesId]
    } else {
        colorMap[speciesId] = [Math.floor(Math.random() * 256), Math.floor(Math.random() * 256), Math.floor(Math.random() * 256)]
        return colorMap[speciesId]
    }
}
function resetColors(event: KeyboardEvent) {
    if (event.key === 'r') {
        colorMap.set({})
    }
}

export {
    colorMap,
    resetColors,
    getColor
}