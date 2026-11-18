/* Rebuild: node build-map.cjs. Optional PNG: node build-map.cjs --png (requires sharp). */
const fs = require('node:fs');
const path = require('node:path');
const esc = s => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const out = __dirname;
const source = 'https://github.com/whimc/qrf-agent/blob/a91628e/docs/contributor-review/relationship-map.md';
const parts = [];
const text = (x,y,s,cls='body') => parts.push(`<text x="${x}" y="${y}" class="${cls}">${esc(s)}</text>`);
const rect = (x,y,w,h,fill='#ffffff',stroke='#d9e3e8',r=18) => parts.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}"/>`);
const line = (d,dashed=false) => parts.push(`<path d="${d}" fill="none" stroke="${dashed?'#8a6870':'#277c82'}" stroke-width="3" ${dashed?'stroke-dasharray="7 6"':''} marker-end="url(#${dashed?'declared':'arrow'})"/>`);
const panel = (x,y,w,h,kicker,title) => {rect(x,y,w,h);text(x+24,y+34,kicker,'kicker');text(x+24,y+69,title,'paneltitle');};
const row = (x,y,name,description) => {text(x,y,name,'label');text(x,y+27,description,'small');};
parts.push(`<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1600" height="1480" viewBox="0 0 1600 1480" role="img" aria-labelledby="map-title map-desc">
<title id="map-title">WHIMC qrf-agent relationship map</title>
<desc id="map-desc">Science Tools, Observations and Position Tracker write shared MySQL tables. Student Feedback reads activity and writes progress. qrf-agent can read those tables for learner context and writes research records. Runtime integrations include Citizens, Journey, WorldGuard, CoreProtect and ProtocolLib. Observations requires DecentHolograms and ProtocolLib. Configured LLMs, reference files and Habitat Assesser connect additional content and services. HolographicDisplays and SpeechReceiver declarations need verification. Source snapshot November 18, 2026; implemented paths may require configuration.</desc>
<defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M0,0 L8,4 L0,8" fill="none" stroke="#277c82" stroke-width="2"/></marker><marker id="declared" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M0,0 L8,4 L0,8" fill="none" stroke="#8a6870" stroke-width="2"/></marker></defs>
<style>text{font-family:Arial,Helvetica,sans-serif;fill:#173343}.title{font-size:42px;font-weight:700;letter-spacing:-1px}.subtitle{font-size:21px;fill:#49616f}.kicker{font-size:15px;letter-spacing:2px;font-weight:700;fill:#277c82}.paneltitle{font-size:25px;font-weight:700}.label{font-size:21px;font-weight:700}.body{font-size:20px}.small{font-size:18px;fill:#49616f}.tiny{font-size:16px;fill:#49616f}.mono{font-size:17px;font-family:Consolas,monospace;fill:#49616f}.agenttitle{font-size:32px;font-weight:700;fill:#fff}.agenttext{font-size:20px;fill:#d9f0f0}.pill{font-size:15px;font-weight:700;fill:#72505a}</style>`);
rect(0,0,1600,1480,'#f3f7f8','#f3f7f8',0);
text(48,49,'WHIMC / CONTRIBUTOR ORIENTATION','kicker');
text(48,108,'How qrf-agent connects to WHIMC','title');
text(48,148,'Plugins, shared data, runtime capabilities and external services','subtitle');
line('M1050 53 H1100');text(1110,59,'Implemented path','tiny');
line('M1050 85 H1100',true);text(1110,91,'Declared / needs verification','tiny');
text(1050,133,'DRAFT • SOURCE SNAPSHOT 18 NOV 2026','kicker');

panel(48,196,450,390,'01 / ACTIVITY PRODUCERS','WHIMC learning activity');
row(72,310,'Science Tools','Measurements and tool use');
row(72,386,'Observations','Student observations and spatial context');
row(72,462,'Position Tracker','Movement samples and region events');
row(72,538,'Student Feedback','Reads activity; writes progress scores');
line('M498 364 H556');text(504,345,'SQL','tiny');

panel(566,196,450,390,'02 / SHARED CONTRACTS','MySQL tables');
text(590,310,'whimc_sciencetools','mono');
text(590,347,'whimc_observations','mono');
text(590,384,'whimc_player_positions','mono');
text(590,421,'whimc_progress','mono');
text(590,483,'Producer and reader schemas must agree.','small');
text(590,515,'Region events are separate; qrf-agent','small');
text(590,542,'learner context reads position samples.','small');
line('M1016 350 H1074');text(1020,330,'read','tiny');

rect(1084,196,468,390,'#164d59','#164d59');
text(1110,241,'03 / AGENT EXPERIENCE','agenttext');
text(1110,298,'qrf-agent','agenttitle');
text(1110,343,'NPC dialogue and guidance','agenttext');
text(1110,383,'Optional learner activity context','agenttext');
text(1110,423,'Local PMML or configured LLM','agenttext');
text(1110,493,'Writes agent, dialogue and','agenttext');
text(1110,525,'chat research records to MySQL','agenttext');
line('M1318 586 V620 H790 V586');text(836,613,'research records → shared database','tiny');

panel(48,658,478,380,'04 / RUNTIME CAPABILITIES','Direct qrf-agent integrations');
row(72,773,'Citizens → qrf-agent','NPCs and traits; required at startup');
row(72,839,'Journey ↔ qrf-agent','Destination catalog and waypoint commands');
row(72,905,'WorldGuard → qrf-agent','Regions, points of interest and habitat teams');
row(72,971,'CoreProtect → qrf-agent','Block history for building and replay');

panel(550,658,478,380,'05 / SUPPORTING PLUGINS','Connections beyond the agent');
row(574,773,'DecentHolograms + ProtocolLib','Both required by Observations');
row(574,839,'ProtocolLib → qrf-agent','Packet-based sign input menu');
row(574,905,'WorldGuard → Tools / Tracker','Region-aware tools; optional region tracking');
row(574,971,'Quests / Multiverse / WorldEdit','Additional branches, declarations and adapters');

panel(1052,658,500,380,'06 / CONTENT AND SERVICES','External and file-based inputs');
row(1076,773,'Reference files → qrf-agent','World prompts and bounded text excerpts');
row(1076,839,'qrf-agent ↔ hosted / local LLM','Configured prompts, selected context and replies');
row(1076,905,'qrf-agent → Habitat Assesser','BuildAssessEvent → Socket.IO assessment service');
row(1076,971,'Citizens / Mineskin','Optional HTTPS image URLs for player skins');

rect(48,1070,1504,150,'#f8f1f2','#e4d4d8');
text(72,1104,'DECLARED OR LEGACY / VERIFY BEFORE RELYING ON THESE','pill');
line('M74 1140 H128',true);
text(144,1147,'HolographicDisplays: declared in qrf-agent; no direct API calls identified.','body');
text(144,1184,'SpeechReceiver and the old science-tool event listener: inspected hookup code is commented out.','body');

text(48,1271,'Shared tables are plugin interfaces, even without a direct Java dependency.','paneltitle');
text(48,1310,'Implemented means present in source, not confirmed enabled on a live server. Optional features need configuration.','small');
text(48,1342,'Data arrows assume compatible tables are accessible through the configured database connection.','small');
text(48,1374,'Capabilities shown here are grouped; see the full inventory for manifest gaps, build dependencies and validation checks.','small');
parts.push(`<a href="${esc(source)}" xlink:href="${esc(source)}" target="_blank" rel="noopener noreferrer">`);
text(48,1433,'Full relationship inventory and source evidence ↗','label');parts.push('</a>');
text(1110,1433,'WHIMC • Review draft','small');
parts.push('</svg>');
const svg = parts.join('\n');
fs.writeFileSync(path.join(out,'whimc-qrf-relationships.svg'),svg);
const css = `#whimc-map{--wm-ink:#173343;--wm-bg:#f3f7f8;color:var(--wm-ink);font-family:Arial,Helvetica,sans-serif;margin:0 auto;max-width:1600px}#whimc-map *{box-sizing:border-box}#whimc-map .wm-scroll{overflow-x:auto;border-radius:16px;background:var(--wm-bg)}#whimc-map svg{display:block;width:100%;height:auto;min-width:900px}#whimc-map details{padding:16px 4px;line-height:1.6}#whimc-map summary{cursor:pointer;font-weight:700}#whimc-map a{color:#166674}#whimc-map .wm-hint{font-size:14px;margin:12px 4px;color:#49616f}@media(min-width:900px){#whimc-map .wm-hint{display:none}}@media print{#whimc-map svg{min-width:0}#whimc-map .wm-scroll{overflow:visible}#whimc-map details,#whimc-map .wm-hint{display:none}}`;
const fragment = `<section id="whimc-map" aria-label="WHIMC qrf-agent relationships">\n<style>${css}</style>\n<p class="wm-hint">Scroll horizontally to explore the full map. A text description follows below.</p>\n<div class="wm-scroll" role="region" aria-label="Relationship diagram; scroll horizontally on small screens" tabindex="0">${svg}</div>\n<details><summary>Read the relationships as text</summary><ul><li>Science Tools, Observations and Position Tracker write measurements, observations and movement into shared MySQL tables. Student Feedback reads activity and writes progress scores.</li><li>qrf-agent optionally reads learner context and writes agent/dialogue/chat research records. Position-region events are distinct from its position-sample input.</li><li>Citizens supplies NPCs and traits; Journey supplies destinations and accepts navigation commands; WorldGuard supplies regions and POIs; CoreProtect supplies building history; ProtocolLib supports sign input.</li><li>Observations requires DecentHolograms and ProtocolLib. WorldGuard also supports Science Tools and optional Position Tracker region tracking.</li><li>Reference files supply bounded excerpts. A configured hosted or local LLM receives prompts and selected context and returns replies. A local PMML dialogue path also exists.</li><li>Habitat Assesser receives BuildAssessEvent and bridges an external Socket.IO assessment service. Citizens' Mineskin integration can resolve optional HTTPS skin images.</li><li>HolographicDisplays is declared but direct calls were not identified. SpeechReceiver hookup and the old science-tool event listener are commented out.</li></ul><p>Source snapshot: November 18, 2026. Implemented paths may depend on optional configuration; this is not a live deployment inventory. <a href="${source}" target="_blank" rel="noopener noreferrer">Read the full relationship inventory and source evidence.</a></p></details>\n</section>`;
fs.writeFileSync(path.join(out,'whimc-qrf-relationships.embed.html'),fragment);
fs.writeFileSync(path.join(out,'whimc-qrf-relationships.html'),`<!doctype html>\n<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>WHIMC qrf-agent relationship map</title><style>body{margin:0;padding:clamp(8px,2vw,32px);background:#fff}</style></head><body>${fragment}</body></html>\n`);
if(process.argv.includes('--png')) {
  require('sharp')(Buffer.from(svg)).resize(2400).png().toFile(path.join(out,'whimc-qrf-relationships.png')).then(()=>console.log('Built SVG, HTML, embed fragment and 2400px PNG.')).catch(e=>{console.error(e);process.exitCode=1});
} else console.log('Built SVG, HTML and embed fragment.');
