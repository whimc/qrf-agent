# LLM reference material (RAG)

This document describes how WHIMC QRF Agent loads files from `llm-context` (and per-world folders) and attaches them to LLM calls.

## What “RAG” means here

In this plugin, **RAG is not vector search or semantic retrieval**. The server:

1. Reads files from disk on the server.
2. Truncates them to configured size limits.
3. Pastes excerpts into the **system prompt** before each LLM call.

The model is instructed to use that block only when relevant. There is **no embedding index**, **no similarity search**, and **no per-question chunk selection**. Every eligible file in the configured folder is considered, in sorted path order, until character limits are reached.

Implementation: `LlmRagContextBuilder` and `RagDocumentReader` in the plugin source.

## Directory layout

Typical layout under the plugin data folder (`plugins/WHIMC-QRF-Agent/`):

```
plugins/WHIMC-QRF-Agent/
├── world-prompts/          # Per-world system prompts (+ optional rag-directory)
│   ├── default.yml
│   └── colder.yml
└── llm-context/            # Global RAG folder (when llm.rag.enabled)
    └── colder/             # Example per-world subfolder
        ├── notes.txt
        └── lesson.docx
```

On each LLM call (dialogue **Discuss something** with `llm.use-for-reply: true`, or `/agent chat test`):

1. Resolve the **base system prompt** for the player’s world.
2. Optionally **append file contents** under a `## Reference material` header.
3. Send the combined string as the **system** message to the provider.
4. Send the student’s message as the **user** message.

## Two ways reference files get attached

These paths are **independent**:

| Path | When it runs | Configuration |
|------|----------------|---------------|
| **Per-world RAG** | Player is in a world matched by `world-prompts/*.yml` **and** that file sets `rag-directory` | e.g. `rag-directory: llm-context/colder` in `world-prompts/colder.yml` |
| **Global RAG** | No world prompt with `rag-directory`, and fallback uses `llm.system-prompt` **and** `llm.rag.enabled: true` | `llm.context-directory: llm-context` in `config.yml` |

**Important:** Per-world `rag-directory` works **without** `llm.rag.enabled: true`. Global `llm.rag` only applies on the fallback `llm.system-prompt` path.

### Prompt resolution order

`WorldLlmPromptRegistry.buildSystemPrompt` resolves in this order:

1. World-specific YAML (e.g. `ColderStrip` → `colder.yml`)
2. Else `world-prompts/default.yml`
3. Else `llm.system-prompt` in `config.yml`

If the chosen world prompt defines `rag-directory`, files from that folder are appended. Otherwise, if global `llm.rag.enabled` is on, files from `llm.context-directory` are appended.

## How files are scanned and parsed

### 1. Directory scan

- **Root:** `plugins/WHIMC-QRF-Agent/<rag-directory>` (relative to the plugin data folder), or an absolute path if set in YAML.
- **Recursion:** Subfolders up to `llm.rag.max-directory-depth` (default **6**).
- **Extensions:** Only types listed in `llm.rag.include-extensions` (default `txt`, `md`, `doc`, `docx`, `yml`, `yaml`).
- **Order:** Files are processed in **sorted path order** (stable and predictable).

### 2. Text extraction

| Extension | Method |
|-----------|--------|
| `.txt`, `.md` | UTF-8 plain text |
| `.docx` | Apache POI `XWPFWordExtractor` |
| `.doc` | Apache POI `WordExtractor` |
| `.yml`, `.yaml` | Bukkit YAML parse. **Citizens NPC saves** (`npc:` map) are summarized to name, world/coords, hologram labels, and dialogue only (skins/UUIDs/equipment stripped). Other YAML is flattened with noisy keys skipped. |

Unsupported extensions are skipped.

### 3. Truncation limits

From `config.yml` → `llm.rag`:

| Setting | Default | Effect |
|---------|---------|--------|
| `max-file-chars` | 4000 | Each file is cut off with `...[truncated]` if longer |
| `max-total-chars` | 12000 | Stops adding files once total RAG size reaches this |
| `max-directory-depth` | 6 | Maximum subdirectory depth when walking the tree |

### 4. Prompt assembly

Each file becomes a block like:

```text
--- file: colder/notes.txt ---
(contents here)

```

All blocks are appended after this header:

```text
## Reference material (from server context files — use only if relevant)
```

The LLM therefore sees: **mentor instructions + pasted reference docs + (optionally) Journey destinations + (in `/agent chat test` only) nearby NPC summaries**.

## What does *not* happen

- **No query-time retrieval** — the student’s question does not select which files or chunks to load.
- **No chunking or embeddings** — whole files (truncated) are included; the `whimc_agent_chat_retrieved_chunks` table is reserved for future use and is **not populated** today.
- **No separate RAG reload** — files are read when the system prompt is built for that LLM call (reload world prompts with `/agent reload_llm_prompt`; file contents are re-read on each call).
- **PMML-only dialogue** — if `llm.use-for-reply: false`, discussion does not call the LLM, so reference files are not used.

## Configuration reference

**Global** (`config.yml`):

```yaml
llm:
  context-directory: llm-context
  rag:
    enabled: false
    max-total-chars: 12000
    max-file-chars: 4000
    max-directory-depth: 6
    include-extensions:
      - txt
      - md
      - doc
      - docx
      - yml
      - yaml
  debug-log: false   # log RAG char counts and file lists to console
```

**Per-world** (`world-prompts/colder.yml` example):

```yaml
worlds:
  - ColderStrip
  - ColderHot
  - ColderCold
prompt: |
  You are a WHIMC science mentor...
rag-directory: llm-context/colder
```

## Verifying that RAG is working

1. Add files under e.g. `plugins/WHIMC-QRF-Agent/llm-context/colder/`.
2. Set `rag-directory: llm-context/colder` in the matching world-prompt YAML.
3. Run `/agent reload_llm_prompt ColderStrip` (or your world name).
4. Check the server console for `[OverworldAgent][LLM]` — file list and `(RAG appended)` in reload output when material was included.
5. Set `llm.debug-log: true` for per-call prompt size and RAG diagnostics.

## Practical tips

- **File order matters** — earlier paths (alphabetically) are included first if you hit `max-total-chars`.
- **Prefer several smaller files** over one huge document so more topics fit under the cap.
- **Use `.txt` / `.md` when possible** — Word support is for convenience; complex formatting may be lost.
- **Per-world folders** keep scenario-specific lore separate (e.g. Colder vs other worlds).

## Related features (not RAG)

These also extend the system prompt but are separate from `llm-context`:

| Feature | Config | Purpose |
|---------|--------|---------|
| Journey actions | `llm.journey-actions` | Appends destination catalog for navigation from chat |
| NPC context | `llm.npc-context` | Nearby Citizens NPC summaries (`/agent chat test` only) |
| Learner activity | `llm.activity-context` | Live MySQL: observations, science tools, position summary, progress |

### Learner activity context

When `llm.activity-context.enabled` is true, each LLM turn (dialogue discuss + `/agent chat test`) queries the same MySQL database as `mysql:` for:

- `whimc_progress` — latest component scores
- `whimc_player_positions` — recent trail summarized to biomes / hotspots (not every 2s row)
- `whimc_sciencetools` — recent measurements for this player
- `whimc_observations` — this player's recent active observations, plus nearby peers' public observations

The prompt also includes the player's **live** coordinates. Results are appended under `## Learner activity context` with instructions to ground directions and notice strong peer observations.

| Key | Default |
|-----|---------|
| `enabled` | `false` |
| `max-own-observations` | `10` |
| `max-peer-observations` | `10` |
| `nearby-observation-radius` | `80` |
| `include-peer-observations` | `true` |
| `max-science-tools` | `12` |
| `position-lookback-ms` | `900000` |
| `position-row-limit` | `200` |
| `include-progress` | `true` |

Free discussion (menu) and `/agent chat test` both show **Thinking…** and block overlapping turns while a reply is in flight.

See the main [README](../README.md) for LLM provider setup, world prompts, and dialogue behavior.
