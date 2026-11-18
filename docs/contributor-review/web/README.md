# Website relationship map assets

These assets visualize the [source inventory](../relationship-map.md). Their date marks the inspected source snapshot, not a live server inventory. The source-evidence link is pinned to documentation commit `a91628e`. The underlying policies remain review drafts; the images have no Draft labels.

## Full blue network diagram

`whimc-full-relationships.png` and `whimc-full-relationships.svg` provide the full node-and-arrow network in blue, including the shared data flow, runtime capabilities and external assessment service. `whimc-full-relationships.html` provides a standalone preview. The original Mermaid diagrams remain in the relationship documentation. These blue exports are a separate rendering of the network, rather than the card-based overview below.

For WordPress, use the blue PNG in an Image block and link to the media file. The existing Custom HTML snippet also works: replace both image URLs and change the intrinsic dimensions to those of the blue image (3200 × 1738). Rebuild the blue version with `node build-full-map.cjs --png`; it requires `@viz-js/viz` and, for PNG, `sharp`.

## Choose a format

**For WordPress:** upload `whimc-qrf-relationships.png` to the Media Library and copy its File URL. Paste `wordpress-embed.html` into a **Custom HTML** block, replacing both `REPLACE_WITH_WORDPRESS_IMAGE_URL` placeholders with that URL. The snippet includes a responsive image, a full-size image link, a caption linking to source evidence, and an expandable text description. It needs no scripts, inline SVG, iframe, plugin or external stylesheet. Preview the published page with your theme. Alternatively, use a normal WordPress Image block and set its link destination to the media file. PNG works with the standard image upload flow; SVG acceptance depends on the site's configuration.

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
