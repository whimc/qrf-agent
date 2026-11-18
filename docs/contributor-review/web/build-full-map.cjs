/* Requires @viz-js/viz; add --png with sharp available for a raster export. */
const fs = require('node:fs');
const path = require('node:path');
(async () => {
  const viz = await require('@viz-js/viz').instance();
  const dot = fs.readFileSync(path.join(__dirname,'whimc-full-relationships.dot'),'utf8');
  let svg = viz.renderString(dot,{format:'svg',engine:'dot'});
  svg = svg.replace(/<svg\b/, '<svg role="img" aria-label="WHIMC full plugin relationship map"');
  fs.writeFileSync(path.join(__dirname,'whimc-full-relationships.svg'),svg);
  const fragment = svg.slice(svg.indexOf('<svg'));
  fs.writeFileSync(path.join(__dirname,'whimc-full-relationships.html'),`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>WHIMC full relationship map</title><style>body{margin:0;padding:20px;font-family:Arial,sans-serif;color:#123c75}svg{display:block;width:100%;height:auto;min-width:1100px}.map{overflow-x:auto}a{color:#174f94}</style></head><body><div class="map" tabindex="0" role="region" aria-label="Full relationship map; scroll horizontally on narrow screens">${fragment}</div><p><a href="https://github.com/whimc/qrf-agent/blob/a91628e/docs/contributor-review/relationship-map.md">Full relationship inventory and source evidence</a></p></body></html>`);
  if(process.argv.includes('--png')) await require('sharp')(Buffer.from(svg)).resize(3200).png().toFile(path.join(__dirname,'whimc-full-relationships.png'));
  console.log('Built full blue relationship map.');
})().catch(e=>{console.error(e);process.exitCode=1});
