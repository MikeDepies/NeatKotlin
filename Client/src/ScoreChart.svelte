<script>
    import * as Pancake from '@sveltejs/pancake';
    export let populationSize : number
    export let highestPopulationScore : number
    export let data : {x :number, y:number}[]
    export let index : number

    let min = 0
    $: {
        min = 0
        for (let d of data) {
            if (d.y < min) min = d.y
        }
    }
</script>
<div class="w-full h-96">
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
          <Pancake.SvgLine data={data.slice(index)} let:d>
            <path class="data2" {d}/>
          </Pancake.SvgLine>
          <Pancake.SvgLine data={data.slice(0, index +1)} let:d>
            <path class="data" {d}/>
          </Pancake.SvgLine>
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
      stroke: red;
      stroke-linejoin: round;
      stroke-linecap: round;
      stroke-width: 2px;
      fill: none;
    }
    path.data2 {
      stroke: blue;
      stroke-linejoin: round;
      stroke-linecap: round;
      stroke-width: 1px;
      fill: none;
    }
  </style>