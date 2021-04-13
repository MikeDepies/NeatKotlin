<script>
    import { characters, spriteX, spriteY } from "@app/api/Melee";

    import type { Character } from "@app/api/types/Melee";
    export let character: Character;
    let showSelection = false;
    function selectCharacter(event : MouseEvent, selection : Character) {
        character = selection
        showSelection =false
    }
    function openSelection(event : MouseEvent) {
        showSelection = true;
    }
</script>

<div class="relative">

{#if showSelection}
    <div class="flex flex-wrap absolute w-96 bg-gray-300 z-20">
        {#each characters as c}
            <div class="">
                <div
                    class="cursor-pointer"
                    on:click="{e => selectCharacter(e, c)}"
                    style="background: url('/images/ssbm-character-icon-sheet.png') {spriteX(c)}px {spriteY(c)}px; width: 64px; height:56px;"
                />
            </div>
        {/each}
    </div>
    
{/if}
<div
    on:click="{openSelection}"
    class="cursor-pointer"
    style="background: url('/images/ssbm-character-icon-sheet.png') {spriteX(character)}px {spriteY(character)}px; width: 64px; height:56px;"
/>
</div>