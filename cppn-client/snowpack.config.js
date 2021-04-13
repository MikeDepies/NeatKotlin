module.exports = {
  mount: {
    public: '/',
    src: '/_dist_',
  },
  alias: {
    "@components": "./src/components",
    "@app": "./src",
  },
  plugins: [
    '@snowpack/plugin-svelte',
    '@snowpack/plugin-dotenv',
    "@snowpack/plugin-typescript",
    ["@snowpack/plugin-build-script", {"cmd": "postcss", "input": [".css"], "output": [".css"]}],
    [
      '@snowpack/plugin-run-script',
      {cmd: 'svelte-check --output human', watch: '$1 --watch', output: 'stream'},
    ],
  ],
};
