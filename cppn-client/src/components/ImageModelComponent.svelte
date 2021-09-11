<script>
    import type { ImageModel } from "@app/api/types/CPPN";
    import { onMount } from "svelte";
    export let width : number;
    export let height: number;
    export let imageModel: ImageModel;
    let canvas: HTMLCanvasElement;
    $: if (canvas !== undefined) {
        const ctx = canvas.getContext("2d")!!;
        const width = imageModel.imageData.length;
        const height = imageModel.imageData[0].length;
        for (let x = 0; x < width; x++) {
            for (let y = 0; y < height; y++) {
                const v = imageModel.imageData[x][y]
                ctx.fillStyle = `hsl(${toColorValue(v[0], 360)},${toColorValue(v[1], 100)}%,${toColorValue(v[2], 100)}%)`;
                ctx.fillRect(x, y, 1, 1);
            }
        }
    }
    function toColorValue(value : number, scaleTo : number) {
        return Math.min(
                    (1 - Math.abs(value)) * scaleTo,
                    scaleTo
                );
    }
</script>

<canvas class="" {width} {height} on:click bind:this={canvas} />
