<script lang="ts">
    export let cpuLevel : number
</script>
<div>
    <label
        for="cpu"
        class="block text-sm font-medium text-gray-700"
    >Type</label>
    <select
        bind:value="{cpuLevel}"
        id="cpu"
        name="cpu"
        class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
    >
        {#each new Array(10) as _, i}
            <option value={i}>{i === 0 ? 'Ai' : 'CPU ' + i}</option>
        {/each}
    </select>
</div>
