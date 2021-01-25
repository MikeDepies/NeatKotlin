<script>
    import * as Pancake from '@sveltejs/pancake';
import type { EvaluationClocksUpdate } from './api/types/Evaluation';
import type { Series } from './api/types/Series';

    export let data : Series
    export let frameNumber : number
    export let longestClockTimeSeen : number
    let seriesNames : string[] = []
    let min = 0
    $: seriesNames = Object.keys(data)
    
    function preparedData(seriesName : string) {
      const series=data[seriesName].series;
      return [...(series), {x: series.length, y: series[series.length -1].y, color: series[series.length -1].color}]
    }
</script>
<div class="w-full h-96">
    <!-- {data.map(a => a.color)} -->
    <div class="chart">
      <Pancake.Chart x1={0} x2={frameNumber} y1={min} y2={longestClockTimeSeen}>
        <Pancake.Box x2={frameNumber} y1={min} y2={longestClockTimeSeen}>
          <div class="axes"></div>
        </Pancake.Box>
    
        <Pancake.Grid vertical count={5} let:value>
          <span class="x label">{value}</span>
        </Pancake.Grid>
    
        <Pancake.Grid horizontal count={3} let:value>
          <span class="y label">{value}</span>
        </Pancake.Grid>
    
        {#each seriesNames.filter(s => data[s].series.length > 0) as seriesName}
        <Pancake.Svg>
          {#each preparedData(seriesName) as ele, i}
          <Pancake.SvgLine data={data[seriesName].series.slice((i -1) < 0 ? 0 : i -1, (i < data[seriesName].series.length) ? i +1 : i)} let:d>
            <path class="data" style="stroke: {ele?.color || data[seriesName].color};" d={d}/>
          </Pancake.SvgLine>
          
          {/each}
        </Pancake.Svg>
        {/each}
      </Pancake.Chart>
    </div>
  </div>

  
<style>
    .chart {
      height: 100%;
      padding: 3em 2em 2em 3em;
      box-sizing: border-box;
    }
  
    .axes {
      width: 100%;
      height: 100%;
      border-left: 1px solid black;
      border-bottom: 1px solid black;
    }
  
    .y.label {
      position: absolute;
      left: -2.5em;
      width: 2em;
      text-align: right;
      bottom: -0.5em;
    }
  
    .x.label {
      position: absolute;
      width: 4em;
      left: -2em;
      bottom: -22px;
      font-family: sans-serif;
      text-align: center;
    }
  
    path.data {
      /* stroke: red; */
      stroke-linejoin: round;
      stroke-linecap: round;
      stroke-width: 2px;
      fill: none;
    }
    
  </style>