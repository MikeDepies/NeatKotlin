import { Writable, writable } from 'svelte/store'

export type ColorMap<T extends string | number> = {
    [K in T]?: [number, number, number]
}
type ColorMapName = "clocks" | "species"
let mapOfColorMaps = new Map<ColorMapName, Writable<ColorMap<string | number>>>()
function getColorMap<T extends string | number>(name: ColorMapName) {
    
    const colorMap = mapOfColorMaps.get(name) || writable<ColorMap<T>>({})
    function resetColors(event: KeyboardEvent) {
        if (event.key === 'r') {
            colorMap.set({})
        }
    }
    return {
        ...colorMap,
        resetColors
    }
}

function getColor<T extends string | number>(clockId: T, colorMap: ColorMap<T>) {
    if (colorMap[clockId]) {
        return colorMap[clockId]!!
    } else {
        colorMap[clockId] = [Math.floor(Math.random() * 256), Math.floor(Math.random() * 256), Math.floor(Math.random() * 256)]
        return colorMap[clockId]!!
    }
}


function rgbColorString(rgb: [number, number, number]) {
    return `rgb(${rgb.join(",")})`
}

export {
    getColorMap,
    getColor,
    rgbColorString
}