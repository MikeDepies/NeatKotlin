<script lang="ts">
import { onMount } from 'svelte';


import PopulationComponent from './PopulationComponent.svelte';

export let simulationId : number
onMount(() => {

})

</script>

<div>
    {simulationId}
    <!-- <PopulationComponent controllerIds={[0,1]} evaluationId={evaluationIds}/> -->
</div>