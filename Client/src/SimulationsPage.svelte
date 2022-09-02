<script lang="ts">
import { onMount } from 'svelte';

let simulations : SimulationResponse[] = []
onMount(() => {
    //Get simulations
})

type SimulationResponse = {
    
};

</script>

<div>
   <div>
       <div>Simulations</div>
       <div>
           <ul>
               {#each simulations as simulation}
                   <li></li>
               {/each}
           </ul>
       </div>
   </div> 
</div>