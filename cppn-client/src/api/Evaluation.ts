import { ColorMap, rgbColorString } from '@app/store/ColorMapStore'
import type { EvaluationClocksUpdate } from './types/Evaluation'
import type { Series } from './types/Series'

export function createClockHistorySeriesMap(clockHistory: EvaluationClocksUpdate[], $clockColorMap: ColorMap<string>) {
    let clockHistorySeriesMap: Series = {}
    clockHistory.forEach(update => {
        update.clocks.forEach(clock => {
            if (clockHistorySeriesMap[clock.clock] === undefined) {
                const color = $clockColorMap[clock.clock]
                clockHistorySeriesMap[clock.clock] = {
                    color: (color) ? rgbColorString(color) : "",
                    name: clock.clock,
                    series: []
                }
            }
            const clockSeries = clockHistorySeriesMap[clock.clock].series
            clockHistorySeriesMap[clock.clock].series = [...clockSeries, { x: update.frame, y: clock.framesRemaining }]
            console.log(clockSeries);
        })
    })
    return clockHistorySeriesMap
}

export function countUnique<T>(iterable: T[]) {
    return new Set(iterable).size;
}