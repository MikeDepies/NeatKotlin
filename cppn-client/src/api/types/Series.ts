export type SeriesPoint = { x: number, y: number, color?: string }
export type Series = {
    [K in string]: {
        color: string,
        name: string,
        series: SeriesPoint[]
    }
}