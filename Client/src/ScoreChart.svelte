<script lang="ts">
    import * as Pancake from '@sveltejs/pancake';
    export let populationSize : number
    export let highestPopulationScore : number
    export let data : {x :number, y:number, color: string}[]
    let min = 0
    $: {
        min = 0
        for (let d of data) {
            if (d.y < min) min = d.y
        }
    }
</script>
<div class="w-full h-96">
    <!-- {data.map(a => a.color)} -->
    <div class="chart">
      <Pancake.Chart x1={0} x2={populationSize} y1={min} y2={highestPopulationScore}>
        <Pancake.Box x2={populationSize} y1={min} y2={highestPopulationScore}>
          <div class="axes"></div>
        </Pancake.Box>
    
        <Pancake.Grid vertical count={5} let:value>
          <span class="x label">{value}</span>
        </Pancake.Grid>
    
        <Pancake.Grid horizontal count={3} let:value>
          <span class="y label">{value}</span>
        </Pancake.Grid>
    
        <Pancake.Svg>
          {#each [...data, {x: data.length, y: data[data.length -1].y, color: ""}] as ele, i}
          <Pancake.SvgLine data={data.slice((i -1) < 0 ? 0 : i -1, (i < data.length) ? i +1 : i)} let:d>
            <path class="data" style="stroke: {ele.color};" d={d}/>
          </Pancake.SvgLine>
          
          {/each}
        </Pancake.Svg>
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
    path.data2 {
      stroke-linejoin: round;
      stroke-linecap: round;
      stroke-width: 2px;
      fill: none;
    }
  </style>