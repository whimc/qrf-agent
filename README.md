# QRF-Agent

QRF-Agent is a Minecraft plugin to create and define agent behavior (forked from [Overworld-Agent](https://github.com/whimc/Overworld-Agent) `animal-ai`). To teleport to the agent room for the guide agent for exploration use `/destination teleport AIchoice`. 
To select the agent right click on the desired agent and to start a conversation with your agent also right click on them. 
To select a dialogue option must click enter then left click on the desired option.

Alternatively you can spawn a guide agent with **`/agent spawn`** (`/agents` is an alias).

**Spawn syntax**

- **Player agent (config skin):** `/agent spawn player <skin> <name…>` — skin key from `skins.<agent_type>` in `config.yml`.
- **Player agent (custom URL):** `/agent spawn player --url <https://…> <name…>` — direct **https** link to a **.png** skin (same Mineskin flow as Citizens `/npc skin --url`). Optional `--slim` for slim arms. Set `agent-spawn.allow-url-skins: false` to disable.
- **Animal agent:** `/agent spawn <animal> <name…>` — mob ID from `AgentEntityTypes` (tab-complete). No skin argument.
- **Legacy:** `/agent spawn <skin> <name…>` — first token treated as a **player** skin key if it is not a valid entity type.

Tab-complete the first argument to see every allowed value on your server version.

Builder functions (build templates, demo builds, base feedback) no longer require a separate mode: they live in **every agent's dialogue menu** under **"I want to build something!"**. A dedicated builder NPC can still be spawned with **`/agent rebuilderspawn`** and interacted with like a guide agent.

_**Requires Java 21+**_

---

## Building
Build the source with Maven:
```
$ mvn install
```

---

## Configuration
The base config file can be found under [`src/main/resources/config.yml`](src/main/resources/config.yml).
An example config file can be found under [`example-config.yml`](example-config.yml).

### Skins
| Key | Type | Description |
|---|---|---|
|`skins.<skin name>`|`string`|Unique name for the skin can be made up by the researcher|
|`skins.<skin name>.signature`|`string`|The texture signature of the custom skin from https://mineskin.org|
|`skins.<skin name>.data`|`string`|The texture value of the custom skin from https://mineskin.org|

#### Example
```yaml
skins:
  <skin name>:
    signature: <texture signature of custom skin>
    data: <texture value of custom skin>
```


### Local dialogue classifier (bundled PMML)

Free-text player lines (e.g. **Discuss something** / chat input) are **not** handled by the optional HTTP LLM unless `llm.use-for-reply` is on and a provider is configured. By default the plugin uses a **local** classifier:

| Piece | Role |
|--------|------|
| **`src/main/resources/model.pmml`** | Shipped inside the plugin JAR. A **PMML** model evaluated at runtime with **PMML4s** (`Chatbot#classifyDialogueIntent()`). |
| **Output** | An integer **label** (class index) plus a **confidence** score in \([0, 1]\). |
| **Decision** | `Dialogue#doResponse()` compares confidence to an internal threshold (**0.5**). Above threshold â†’ use that labelâ€™s row from **`prompts`** in `config.yml`; otherwise â†’ **unknown** prompt (`label: -2`). |
| **Reply text** | Each prompt row supplies **`feedback`** strings. Placeholders such as `{NAME}`, `{PLANET}`, `{AGENT}` are filled from Bukkit/Citizens (`Dialogue#fillIn()`). |

So answers like â€œwhatâ€™s your name?â€ work because the model maps the sentence to a label (e.g. **agent** / `label: 0`) whose feedback template includes something like `My name is {AGENT}.` That is **intent routing + templates**, not open-ended generation.

Replacing or retraining the classifier means supplying a new **`model.pmml`** (and keeping **`prompts` labels** aligned with the modelâ€™s output classes). The PMML file is large and is treated as an **opaque artifact** in this repo.

### LLM chatbot (optional)

Player dialogue can be answered by an **internet-hosted** model (**OpenAI** or **Google Gemini**) or by a **local** engine that exposes an **OpenAI-compatible** HTTP API (e.g. [Ollama](https://ollama.com/), LM Studio, vLLM). On startup, `OverworldAgent` reads `llm.provider` and related keys, creates an `LlmProvider`, and stores it for `Dialogue` to call on a worker thread when `llm.use-for-reply` is true.

#### Providers (`llm.provider`)

| Value | Use case | Credentials | Default model if `llm.model` empty |
|-------|------------|-------------|-------------------------------------|
| `none` | Disable built-in HTTP LLM | â€” | â€” |
| `openai` | [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat) | **Required:** API key | `gpt-4o-mini` |
| `gemini` | [Google AI Gemini](https://ai.google.dev/) generateContent | **Required:** API key | `gemini-1.5-flash` |
| `openai_compatible` | Local or self-hosted `/v1/chat/completions` | Optional API key (many local servers use none) | `llama3.2` |

For **local** inference, set `provider: openai_compatible` and `base-url` to your serverâ€™s OpenAI-compatible root (must end up posting to `â€¦/v1/chat/completions` â€” the plugin normalizes a base such as `http://127.0.0.1:11434/v1`). Example: **Ollama** default `http://127.0.0.1:11434/v1`.

**Networking:** Cloud providers need outbound **HTTPS** from the Paper host. Local providers only need **localhost** (or your LAN URL) reachable from the JVM running the server.

#### API keys

| Key | Description |
|-----|-------------|
| `llm.api-key` | Inline key (avoid in production repos). |
| `llm.api-key-env` | If set, the key is read from **`System.getenv(<name>)`** instead of `llm.api-key` (recommended on real servers). |

Do **not** commit keys. Rotate keys if exposed.

Other useful keys: `llm.model`, `llm.base-url` (for `openai_compatible` only), `llm.request-timeout-seconds`, `llm.system-prompt`.

#### RAG context directory

RAG (retrieval-augmented generation) here means: **optional** inclusion of plain-text files from a designated folder into the **system** prompt so the model can ground answers in your own notes.

**Full guide:** [How LLM reference material (RAG) works](docs/llm-rag.md) — directory layout, per-world vs global paths, file parsing, limits, and what this plugin does *not* do (no vector DB or query-time retrieval).

| Key | Description |
|-----|-------------|
| `llm.context-directory` | Subfolder name under the plugin **data folder** (default `llm-context`). Created on enable when possible. Full path: `plugins/WHIMC-QRF-Agent/llm-context`. |
| `llm.rag.enabled` | When `true`, scans that directory and appends bounded excerpts to the system prompt before each completion. |
| `llm.rag.max-total-chars` / `max-file-chars` | Cap total and per-file bytes so prompts stay reasonable. |
| `llm.rag.max-directory-depth` | How deep to walk subfolders. |
| `llm.rag.include-extensions` | File extensions to read (default `txt`, `md`, `doc`, `docx`, `yml`, `yaml`). Word files use Apache POI; Citizens NPC YAML is summarized to dialogue/labels/locations. |

Put glossaries, world lore, or lesson snippets as `.md`, `.txt`, `.doc`, `.docx`, or `.yml`/`.yaml` files there. See [docs/llm-rag.md](docs/llm-rag.md) for behavior details.

#### Per-world prompts (`world-prompts/`)

Long or world-specific system prompts live in **`plugins/WHIMC-QRF-Agent/world-prompts/*.yml`** instead of the main `config.yml`. Each file can set:

| Key | Description |
|-----|-------------|
| `world` | Single Bukkit world name this prompt applies to. |
| `worlds` | List of world names sharing one prompt (e.g. `ColderStrip`, `ColderHot`, `ColderCold`). |
| `prompt` | Multi-line system prompt text for those worlds. |
| `rag-directory` | Optional folder under the plugin data directory for world-specific RAG (see [docs/llm-rag.md](docs/llm-rag.md)). |

If no file matches the player's world, **`world-prompts/default.yml`** is used; if that is missing, the plugin falls back to **`llm.system-prompt`** in `config.yml`.

**Per-world `rag-directory`** is documented in [docs/llm-rag.md](docs/llm-rag.md#two-ways-reference-files-get-attached). In short: it does **not** require `llm.rag.enabled: true` in the main config.

Reload without restart: **`/agent reload_llm_prompt`** (optional world name). In-game output shows prompt size; **`(RAG appended)`** means files from `rag-directory` were included. For a per-file list, check the server console **`[OverworldAgent][LLM]`** block on startup/reload (or set `llm.debug-log: true`).

On **startup** and after **`reload_llm_prompt`**, the console logs an **`[OverworldAgent][LLM]`** summary: provider, model, `use-for-reply`, global RAG directory and document list, each loaded world-prompt file with bound worlds, and per-world RAG directories with their document filenames.

#### LLM Journey actions (`llm.journey-actions`)

When **Journey** is installed and the player uses **free discussion** with `llm.use-for-reply: true`, the plugin can start navigation from chat:

| Key | Description |
|-----|-------------|
| `llm.journey-actions.enabled` | When `true`, appends the Journey destination catalog to the system prompt and may run `/journey server waypoint …` after the LLM reply. |
| `llm.journey-actions.max-destinations` | Max destinations listed in the prompt (default `60`). |

The model may end its reply with a line `JOURNEY:<name_id>` (stripped before the player sees it). The plugin also fuzzy-matches the player’s message against known `name_id` / labels.

#### Journey guidance dispatch (`journey.*`)

Guidance menu clicks and PMML **guidance** intents dispatch navigation as the player. Prefer **`dispatch-command: auto`** (default): runs **`/journey server waypoint <name_id>`**, which avoids `/jt` merging all Journey scopes.

| Key | Default | Description |
|-----|---------|-------------|
| `journey.journey-command-root` | `journey` | Root for `/journey server waypoint …` |
| `journey.journeyto-command-root` | `jt` | Root for `/jt` / journeyto (used only if you set `dispatch-command` to `journeyto_*`) |
| `journey.dispatch-command` | `auto` | `auto` → `server waypoint`; also `server_waypoint`, `journeyto_scoped`, `journeyto_plain`, `server_waypoint_display` |

**Do not use `journeyto_*` on servers where Journey throws `Duplicate key …` when merging scopes** (e.g. two destinations with the same display name like “Mission Control”). Fix duplicate names in `journey_waypoints` / NPC data, or stay on `auto` / `server_waypoint`.

Other Journey keys: `debug-log`, `linked-world-prefix`, `include-poi-regions`, `poi-source`, etc. — see [`config.yml`](src/main/resources/config.yml).

#### Nearby NPC context (`llm.npc-context`)

Used by **interactive LLM chat** (`/agent chat test`) to ground replies in Citizens NPCs near the player. The plugin scans spawned NPCs in the same world, ranks by distance, and appends a short summary to the system prompt (name, trait type, assigned player, coordinates).

| Key | Default | Description |
|-----|---------|-------------|
| `llm.npc-context.enabled` | `true` | Include nearby NPC summaries in interactive chat turns. |
| `llm.npc-context.radius` | `25.0` | Search radius in blocks around the player. |
| `llm.npc-context.max-items` | `3` | Maximum NPCs to include per turn. |

Example:

```yaml
llm:
  npc-context:
    enabled: true
    radius: 25.0
    max-items: 3
```

#### Learner activity context (`llm.activity-context`)

When enabled, each LLM turn (dialogue **Discuss something** with `llm.use-for-reply`, and `/agent chat test`) queries the same MySQL DB as `mysql:` and appends a short snapshot for grounding directions. Live player location is included; position history is **summarized** (not every 2-second sample).

| Table | What is injected |
|-------|------------------|
| `whimc_progress` | Latest component scores (observation, tools, exploration, quest, POI, overall) |
| `whimc_player_positions` | Recent path summarized to biomes / hotspots |
| `whimc_sciencetools` | Recent measurements for this player |
| `whimc_observations` | This player's recent active observations + nearby peers' public ones |

| Key | Default | Description |
|-----|---------|-------------|
| `llm.activity-context.enabled` | `false` | Turn on live activity injection |
| `llm.activity-context.max-own-observations` | `10` | Cap for this player's recent observations |
| `llm.activity-context.max-peer-observations` | `10` | Cap for nearby peer observations |
| `llm.activity-context.nearby-observation-radius` | `80` | Peer observation search radius (blocks) |
| `llm.activity-context.include-peer-observations` | `true` | Include other students' nearby observations |
| `llm.activity-context.max-science-tools` | `12` | Cap for recent science-tool readings |
| `llm.activity-context.position-lookback-ms` | `900000` | How far back to read positions (15 min) |
| `llm.activity-context.position-row-limit` | `200` | Max position rows fetched before summarizing |
| `llm.activity-context.include-progress` | `true` | Include latest `whimc_progress` scores |

Example:

```yaml
llm:
  activity-context:
    enabled: true
    include-peer-observations: true
    nearby-observation-radius: 80
```

See also [docs/llm-rag.md](docs/llm-rag.md#learner-activity-context) (related live context, not file RAG).

#### Example configs

**OpenAI (env key):**

```yaml
llm:
  use-for-reply: true
  provider: openai
  api-key-env: OPENAI_API_KEY
  model: gpt-4o-mini
  system-prompt: "You are a concise in-game science tutor for students."
  context-directory: llm-context
  rag:
    enabled: true
```

**Gemini:**

```yaml
llm:
  use-for-reply: true
  provider: gemini
  api-key-env: GEMINI_API_KEY
  model: gemini-1.5-flash
```

**Local Ollama:**

```yaml
llm:
  use-for-reply: true
  provider: openai_compatible
  base-url: http://127.0.0.1:11434/v1
  model: llama3.2
```

#### `LlmProvider` interface

- **`boolean isConfigured()`** â€” Built-in providers return `true` only when required fields (e.g. API key + model) are set.
- **`String complete(String systemPrompt, String userMessage)`** â€” Plain-text reply; runs off the main thread.

You can still **override** the auto-selected provider after load:

```java
OverworldAgent oa = (OverworldAgent) Bukkit.getPluginManager().getPlugin("WHIMC-QRF-Agent");
if (oa != null) {
    oa.setLlmProvider(new YourLlmProvider(/* ... */));
}
```

Use `depend` / `softdepend` / load order so your code runs after `WHIMC-QRF-Agent` enables.

#### Behavior summary

| `llm.use-for-reply` | Provider configured | Result |
|---------------------|---------------------|--------|
| `false` | any | PMML intent + template feedback only. |
| `true` | no / `isConfigured()` false | Template feedback (no HTTP call). |
| `true` | yes | Async `complete(...)`; on success the **shown** reply is the LLM output; failures are logged and template feedback is kept. |

### Interactive LLM chat (`/agent chat test`)

Separate from embodied right-click dialogue and from `llm.use-for-reply` on the **Discuss something** flow. This mode starts a **multi-turn chat session** that listens to the playerâ€™s **public chat** (`T`), calls the configured `LlmProvider`, and logs research data to MySQL.

| Command | Permission | Description |
|---------|------------|-------------|
| `/agent chat test` | `whimc-agent.agent.chat` | Start interactive LLM chat (requires a configured provider; independent of `llm.use-for-reply`). |
| `/agent chat end` | `whimc-agent.agent.chat` | End the session. |
| `/agent chat` | `whimc-agent.agent.chat` | Opens the **disembodied dialogue menu** (guidance, scores, discussion, build, edit; PMML + optional `use-for-reply`). |

**In-session behavior**

1. Player runs `/agent chat test`.
2. Each chat line is intercepted (public chat is cancelled; the player sees a private `You: …` echo).
3. The plugin builds a system prompt from world/`llm.system-prompt`, optional **RAG** (`llm.rag`), optional **nearby NPC context** (`llm.npc-context`), and optional **learner activity** (`llm.activity-context`).
4. Up to **10** prior user/assistant lines in the session are prepended to the user message for short-term memory.
5. The LLM runs **async**; the player sees `Thinking…` then the assistant reply.
6. Type **`exit`**, **`quit`**, **`stop`**, or run `/agent chat end` to leave the mode. Quitting the server also ends the session.

**Requirements:** MySQL configured and reachable (schema migration **8** creates chat research tables). Provider must be configured (`llm.provider` + key/model or `base-url` for local).

**Research logging (MySQL)**

Each turn from **`/agent chat test`** and from **dialogue free discussion** (Discuss something → chat input, `command = dialogue_discussion`) is stored for analysis (conversation id, turn index, provider/model, latency, status, errors). PMML-only dialogue turns log with `provider = pmml`; LLM dialogue turns include full request/response payloads. Related tables:

| Table | Purpose |
|-------|---------|
| `whimc_agent_chat_conversations` | One row per interactive session. |
| `whimc_agent_chat_turns` | User message, assistant response, provider metadata, timing. |
| `whimc_agent_chat_context_items` | Nearby NPC / learner-activity context rows attached to a turn (interactive chat). |
| `whimc_agent_chat_events` | Stage/trace events (LLM call, RAG, failures). |
| `whimc_agent_chat_retrieved_chunks` | Reserved for RAG chunk metadata when populated. |

Console logs are prefixed with `[OverworldAgent][LLM chat]` for debugging.

**Minimal interactive setup**

```yaml
llm:
  provider: openai
  api-key-env: OPENAI_API_KEY
  model: gpt-4o-mini
  system-prompt: "You are a friendly in-game science tutor."
  rag:
    enabled: false
  npc-context:
    enabled: true
    radius: 25.0
    max-items: 3
  activity-context:
    enabled: true
```

Then in-game: `/agent chat test` â†’ type messages in chat â†’ `/agent chat end` when finished.

---
## Commands

Permissions follow **`whimc-agent.<base>.<subcommand>`** (each subcommand registers its own node under the unified **`agent`** command).

### `/agent` (alias: `/agents`)

Running **`/agent`** with no arguments opens the **disembodied dialogue menu** (same as **`/agent chat`**). Subcommands:

| Subcommand | Permission node | Description |
|------------|-----------------|-------------|
| **`chat`** | `whimc-agent.agent.chat` | Dialogue menu, or **`chat test`** / **`chat end`** for interactive LLM chat (see above). |
| **`spawn`** | `whimc-agent.agent.spawn` | Spawn or replace your **guide** agent (`player` + skin + name, or mob type + name). |
| **`despawn`** | `whimc-agent.agent.despawn` | Despawn agent(s) for a player or **`all`**. |
| **`destroy`** | `whimc-agent.agent.destroy` | Destroy agent NPC(s) for a player or **`all`**. |
| **`reactivate`** | `whimc-agent.agent.reactivate` | Respawn agent(s) for a player or **`all`**. |
| **`rebuilderspawn`** | `whimc-agent.agent.rebuilderspawn` | Spawn a **builder** NPC at your location. |
| **`skin_type`** | `whimc-agent.agent.skin_type` | Set global skin pack (`scientist_casual`, `scientist_stereotype`, etc.). |
| **`about`** | `whimc-agent.agent.about` | List subcommands and permissions. |
| **`reload_llm_prompt`** | `whimc-agent.agent.reload_llm_prompt` | Reload `world-prompts/` from disk; re-logs LLM config to console. |

*(The old separate `/agents` root command was merged into `/agent`. Permission nodes changed from `whimc-agent.agents.*` to `whimc-agent.agent.*` — update LuckPerms or similar grants.)*

### Other commands (`plugin.yml`)

| Command | Typical use |
|---------|---------------|
| **`/assess-habitat`** | Habitat assessment (requires ML-API and routing script on server). |
| **`/oacallback`** | **Internal** — clickable chat UI callbacks; not for players. |

### Guide agent entity types (reference)

The spawn command accepts:

1. **`player`** â€” then a **skin key** under `skins.<agent_type>` (see `agent_type` in `config.yml`, usually **`scientist_casual`** or **`scientist_stereotype`**).
2. Any other token that is in the **configured whitelist** in `AgentEntityTypes` (`player` + fixed mob enum names). Other `EntityType` IDs are rejected even if they are valid mobs on the server.

Use **tab completion** on the first argument of `/agent spawn` for the list (`player` plus allowed mobs in whitelist order). On older servers, mobs whose `EntityType` constant does not exist yet (e.g. `HAPPY_GHAST`) are skipped automatically.

**In-game entity type change:** embodied players can switch the agent between **`player`** and the same allowed mob list (`AgentEntityTypes.selectableAgentTypes()`).

### Skin keys (under each `skins` section)

Use these as **`<skin>`** after **`player`**; names are **lowercase** and must match `config.yml`. They are grouped under **`scientist_casual`** and **`scientist_stereotype`** (switch pack with **`/agent skin_type <pack>`**).

| Skin key | Typical label |
|----------|----------------|
| `astronaut` | Astronaut |
| `wmscientist` | White male scientist |
| `wfscientist` | White female scientist |
| `bmscientist` | Black male scientist |
| `bfscientist` | Black female scientist |
| `amscientist` | Asian male scientist |
| `afscientist` | Asian female scientist |
| `hmscientist` | Hispanic male scientist |
| `hfscientist` | Hispanic female scientist |

## Player dialogue options

The main menu lists **free discussion first** ("I want to discuss something"), then guidance, scores, build, edit, and close.

### Guide
| Dialogue option | Description |
|-----------------|-------------|
| Free discussion | **First option** in the menu. **Ongoing AI chat mode**: clicking toggles chat mode on and every chat message is routed to the agent through **`doResponse()`** (PMML intent by default; **`llm.use-for-reply`** when an `LlmProvider` is configured, with short-term conversation history). Type **`stop`** or **`exit`** in chat to end the session. |
| Guidance ("something cool") | If **Journey** is present: shows a **random subset** (3–5 when available) of **server public** waypoints and **`poi-*` regions** from **portal-linked worlds** (same name prefix, e.g. `ColderCold` / `ColderHot` / `ColderStrip` share `Colder`; override with `journey.linked-world-prefix`). POI regions come from WorldGuard and/or `rg_region` in MySQL (`journey.poi-source`: `worldguard`, `database`, or `both`). Each choice runs **`/<journey-command-root> server waypoint <name_id>`** as the player. Set **`journey.debug-log: true`** for linked-world and source counts in console. Falls back to all public waypoints, then **chat** entry, if nothing matches. |
| Scores | Runs **`/progress`** (e.g. **WHIMC-StudentFeedback**); session is ensured when possible. |
| Build ("I want to build something!") | Opens the **builder menu** (templates, demo builds, base feedback — see Builder table below); no mode switch needed. |
| Edit | **Embodied** agents only: change **name**, **entity type** (`player` vs Animals list), and **skin** when the NPC is a **player** model (up to configured edit limits). |

Every menu and submenu ends with a **Go back** entry; the top-level menu ends with **"That's all for now"** as the last option.

### Builder ("I want to build something!")

Opened from the main dialogue menu (or by right-clicking a `rebuilderspawn` NPC). State for an in-progress template is kept while navigating menus.

| Dialogue Option | Description                                                                                                                                                                                                    |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Demo            | Only available to admins. Enables admin agents to demo build using the rowid of the template.                                                                                                                  |
| Reset Templates | Only available to admins. Enables admin agents to reset build templates for all students or single players using their username.                                                                               |
| Start Template  | Starts a template for the player that will record all the blocks placed after so that the agent can rebuild it later.                                                                                          |
| Cancel Template | Will cancel the build template currently working on.                                                                                                                                                           |
| Finish Template | Will save the build template they have been working on so that the agent can rebuild it when commanded.                                                                                                        |
| Build           | Will open another menu for students to select which template they want the builder to build and then the agent will start building the template at their current location.                                     |
| Feedback        | Gives feedback to player using AI about their team's base. Teams are designated by defining members of the world guard region students are working on. Socket server and API must be running for this to work. |
| Stay            | Only available to embodied builders and not agents using the chat function. Makes agent wait in place until commanded to follow again.                                                                         |
| Follow          | Only available to embodied builders and not agents using the chat function. Makes agent follow the player until commanded to stay.                                                                             |
| Go back         | Returns to the main dialogue menu.                                                                                                                                                                              |

## Agent movement & following

Agents follow their assigned player using Citizens **`FollowTrait`** + navigator pathfinding, tuned per entity type by `AgentFollowTuning`:

- **Player-shaped agents** use normal gravity and **A\* pathfinding, so they WALK** after the player (straight-line steering is disabled â€” it made them glide over terrain instead of walking). They re-attach follow on respawn, world change, and player rejoin.
- **Animal/mob agents** hover at a configurable height above the ground (no gravity) and steer more directly so they keep up while floating.

| Config key | Default | Description |
|------------|---------|-------------|
| `agent-player-follow-path-range` | `48` | Max pathfinding range (blocks) for player-shaped agents. Too low makes Citizens give up on paths. |
| `agent-player-follow-margin` | `2.5` | Distance at which the follower counts as "close enough". |
| `agent-player-nav-destination-teleport-margin` | `-1` | When `>= 0`, allows snap-teleporting near the final waypoint; `-1` disables (prefer walking). |
| `agent-player-nav-stationary-ticks` | `1200` | Ticks standing still before navigation cancels as stuck. |
| `agent-follow-catch-up-distance` | `16.0` | Catch-up teleport only when horizontal distance to the owner exceeds this (blocks). |
| `agent-follow-catch-up-offset` | `1.5` | How far beside the player catch-up teleports land (blocks). |
| `agent-non-player-hover-height` | `2.0` | Blocks above ground that mob agents hover; `0` disables vertical tracking. |
| `agent-non-player-navigator-speed-modifier` | `1.65` | Speed multiplier for hovering mob agents. |
| `agent-mob-follow-path-range` / `agent-mob-follow-margin` | `5` / `1.25` | Tighter follow tuning for mob agents. |


