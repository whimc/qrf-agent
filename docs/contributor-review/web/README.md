# Website relationship map assets

These assets visualize the [reviewed source inventory](../relationship-map.md). They remain marked as a draft source snapshot, not a live server inventory. The source-evidence link is pinned to documentation commit `a91628e`.

## Choose a format

- **`whimc-qrf-relationships.svg`** — scalable image; recommended for a website image block. Intrinsic size: 1600 × 1480.
- **`whimc-qrf-relationships.png`** — 2400 × 2220 raster image for a CMS that does not accept SVG.
- **`whimc-qrf-relationships.embed.html`** — paste the complete fragment into a custom HTML block. Includes the SVG, scoped styles, keyboard-accessible horizontal scrolling on narrow screens, and an expandable text description.
- **`whimc-qrf-relationships.html`** — standalone preview/page with the same embedded content.

The HTML files contain the image directly and require no JavaScript, external libraries, fonts, or adjacent image files. Your CMS must permit inline SVG and styles for the HTML version. If it strips either, use the SVG or PNG as a normal image instead. On small screens the HTML keeps diagram labels readable through horizontal scrolling rather than shrinking all text.

## Normal image embed

Upload the SVG to your site's image/assets directory, then adjust this example path:

```html
<figure>
  <a href="/assets/whimc-qrf-relationships.svg">
    <img
      src="/assets/whimc-qrf-relationships.svg"
      alt="WHIMC qrf-agent relationships: activity plugins share MySQL data; Citizens, Journey, WorldGuard, CoreProtect and ProtocolLib support agent features; optional services supply model replies and habitat assessment."
      width="1600" height="1480"
      loading="lazy" decoding="async"
      style="display:block;max-width:100%;height:auto"
    >
  </a>
  <figcaption>
    Draft source snapshot, November 18, 2026.
    <a href="https://github.com/whimc/qrf-agent/blob/a91628e/docs/contributor-review/relationship-map.md">
      Full relationship inventory and source evidence
    </a>
  </figcaption>
</figure>
```

For PNG, change the filename and set `width="2400" height="2220"`. Linking to the full image allows visitors to zoom into the details. Image embeds do not expose the SVG's internal link; keep the caption link when using an image block.

## Update the assets

Edit `build-map.cjs`, then run `node build-map.cjs` from this directory to rebuild the SVG and both HTML versions. Use `node build-map.cjs --png` with the Node `sharp` package available to regenerate the PNG as well. Generated files are committed so website use needs no build step. Change the source snapshot/date and evidence URL when the underlying inventory changes.

The HTML fragment uses the root ID `whimc-map`; include it once per page. For multiple copies, change that ID and the corresponding CSS selectors in each additional instance.
