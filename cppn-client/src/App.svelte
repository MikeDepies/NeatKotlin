<script>
  import ImageModelComponent from "@components/ImageModelComponent.svelte";
  import { onMount } from "svelte";

  import { match } from "svelte-store-router";
  import type { ImageModel } from "./api/types/CPPN";
  import { route } from "./store/Router";
  let selectedImage: ImageModel | undefined;
  let width = 820 / 4;
  let height = 360 / 4;
  let previewWidth = 51.25
  let previewHeight = 22.5
  let size = 10
  let imagePopulation: ImageModel[] = [];
  let selection : ImageModel[] = []
  onMount(async () => {
    getPopulation();
  });
  async function select(e: MouseEvent, imageModel: ImageModel) {
    if (e.ctrlKey) {
      await getImageModel(imageModel.id, width, height);
    } else {
      if (!selection.includes(imageModel))
        selection = [...selection, imageModel]
      else
        selection = selection.filter(s => s.id != imageModel.id)
    }
  }
  async function sendSelection() {
      await fetch("http://localhost:8091/selection?id=" + selection.map(imageModel => imageModel.id).join(",") + "&n=" + size);
      await getPopulation();
      selection = []
  }
  async function getPopulation() {
    const response = await fetch(`http://localhost:8091/population?w=${previewWidth}&h=${previewHeight}&n=${size}`);
    const data: ImageModel[] = await response.json();

    imagePopulation = data;
  }

  async function getImageModel(id: number, width: number, height: number) {
    const response = await fetch(
      `http://localhost:8091/image?id=${id}&w=${width}&h=${height}`
    );
    const data: ImageModel = await response.json();
    selectedImage = data;
    return selectedImage;
  }
  let params;
</script>

<div class="flex">
  <div>
    <input type="text" bind:value={previewWidth} />
    <input type="text" bind:value={previewHeight} />
    <input type="text" bind:value={size} />
    <button on:click="{e => getPopulation()}">Get Population</button>
    <button on:click="{e => sendSelection()}">Evolve Selection</button>
    <div class="w-full flex flex-wrap">
      {#each imagePopulation as imageModel}
        <div class=" {selection.includes(imageModel) ? " border-green-400 border-4" : ""}">
          <ImageModelComponent
            width={previewWidth}
            height={previewHeight}
            {imageModel}
            on:click={(e) => select(e, imageModel)}
          />
        </div>
      {/each}
    </div>
  </div>
  <div>
    <input type="text" bind:value={width} />
    <input type="text" bind:value={height} />
    {#if selectedImage}
      <div class="w-full flex flex-wrap">
        <div class="border">
          <ImageModelComponent {width} {height} imageModel={selectedImage} />
        </div>
      </div>
    {/if}
  </div>
</div>
