# QRF-Agent

QRF-Agent is a Minecraft plugin to create and define agent behavior (forked from [Overworld-Agent](https://github.com/whimc/Overworld-Agent) `animal-ai`). To teleport to the agent room for the guide agent for exploration use `/destination teleport AIchoice`. 
To select the agent right click on the desired agent and to start a conversation with your agent also right click on them. 
To select a dialogue option must click enter then left click on the desired option.

Alternatively you can spawn a guide agent with **`/agents spawn`** or **`/agent spawn`** (same implementation; permission is always **`whimc-agent.agents.spawn`** because the handler is registered under the `agents` permission namespace).

**Spawn syntax**

- **Player agent:** `/agents spawn player <skin> <nameâ€¦>` â€” first tab-completion token is `player`, second is a skin key from `skins.<agent_type>` in `config.yml`, then the display name (spaces allowed in the name).
- **Animal agent:** `/agents spawn <animal> <nameâ€¦>` â€” `<animal>` is one of the **fixed** mob IDs allowed by `AgentEntityTypes` (see that class / tab-complete: e.g. `axolotl`, `ocelot`, `turtle`, `sheep`, `pig`, `strider`, `sniffer`, `nautilus`, `happy_ghast`, `bee`, `parrot`; types not present on your game version are omitted at runtime). No skin argument.
- **Legacy:** `/agents spawn <skin> <nameâ€¦>` â€” if the first token is not a valid entity type, it is treated as a **player** skin key (same as omitting `player`).

Tab-complete the first argument to see every allowed value on your server version.

Builder functions (build templates, demo builds, base feedback) no longer require a separate mode: they live in **every agent's dialogue menu** under **"I want to build something!"**. A dedicated builder NPC can still be spawned with **`/agents rebuilderspawn`** and interacted with like a guide agent.

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

| Key | Description |
|-----|-------------|
| `llm.context-directory` | Subfolder name under the plugin **data folder** (default `llm-context`). Created on enable when possible. Full path: `plugins/WHIMC-QRF-Agent/llm-context`. |
| `llm.rag.enabled` | When `true`, scans that directory and appends bounded excerpts to the system prompt before each completion. |
| `llm.rag.max-total-chars` / `max-file-chars` | Cap total and per-file bytes so prompts stay reasonable. |
| `llm.rag.max-directory-depth` | How deep to walk subfolders. |
| `llm.rag.include-extensions` | File extensions to read (default `txt`, `md`). |

Put glossaries, world lore, or lesson snippets as `.md`/`.txt` files there. This is **not** a vector database or hybrid searchâ€”only a simple file concat for small corpora; you can replace the flow later with a custom `LlmProvider` that does real retrieval.

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
2. Each chat line is intercepted (public chat is cancelled; the player sees a private `You: â€¦` echo).
3. The plugin builds a system prompt from `llm.system-prompt`, optional **RAG** (`llm.rag`), and optional **nearby NPC context** (`llm.npc-context`).
4. Up to **10** prior user/assistant lines in the session are prepended to the user message for short-term memory.
5. The LLM runs **async**; the player sees `Thinkingâ€¦` then the assistant reply.
6. Type **`exit`**, **`quit`**, **`stop`**, or run `/agent chat end` to leave the mode.

**Requirements:** MySQL configured and reachable (schema migration **8** creates chat research tables). Provider must be configured (`llm.provider` + key/model or `base-url` for local).

**Research logging (MySQL)**

Each turn is stored for analysis (conversation id, turn index, provider/model, latency, status, errors). Related tables:

| Table | Purpose |
|-------|---------|
| `whimc_agent_chat_conversations` | One row per interactive session. |
| `whimc_agent_chat_turns` | User message, assistant response, provider metadata, timing. |
| `whimc_agent_chat_context_items` | Nearby NPC context rows attached to a turn. |
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
```

Then in-game: `/agent chat test` â†’ type messages in chat â†’ `/agent chat end` when finished.

---
## Commands

Permissions follow **`whimc-agent.<base>.<subcommand>`** (each `/agents â€¦` subcommand registers its own node). The shared **guide spawn** handler is registered as **`whimc-agent.agents.spawn`** even when invoked as **`/agent spawn`**.

### Root commands (`plugin.yml`)

| Command | Typical use                                                                                      |
|---------|--------------------------------------------------------------------------------------------------|
| **`/agents`** | Admin / spawn / builder â€” requires a subcommand (see below).                                     |
| **`/agent`** | Player **`chat`** or **`spawn`** (same spawn behavior as `/agents spawn`).                       |
| **`/assess-habitat`** | Habitat assessment command. Only works with ML-API and routing pythong script on server running. |
| **`/oacallback`** | **Internal** â€” clickable chat UI callbacks; not for players to run manually.                     |

### `/agents` subcommands (current code)

| Subcommand | Permission node | Description |
|------------|-----------------|-------------|
| **`spawn`** | `whimc-agent.agents.spawn` | Spawn or replace your **guide** agent (`player` + skin + name, or `Animals` mob type + name). |
| **`despawn`** | `whimc-agent.agents.despawn` | Despawn agent(s) for a player or **`all`**. |
| **`destroy`** | `whimc-agent.agents.destroy` | Destroy agent NPC(s) for a player or **`all`**. |
| **`reactivate`** | `whimc-agent.agents.reactivate` | Respawn agent(s) for a player or **`all`**. |
| **`rebuilderspawn`** | `whimc-agent.agents.rebuilderspawn` | Spawn a **builder** NPC (player model, fixed â€œBuilderâ€ setup) at your location. |
| **`skin_type`** | `whimc-agent.agents.skin_type` | Set global skin pack: argument must be a **top-level key** under `skins:` in `config.yml` (bundled: **`scientist_casual`**, **`scientist_stereotype`**). |

*(The old `chat_type` subcommand was removed: guide and builder menus are merged into one â€” builder options live under "I want to build something!".)*

### `/agent` subcommands

| Subcommand | Permission node | Description |
|------------|-----------------|-------------|
| **`chat`** | `whimc-agent.agent.chat` | Disembodied dialogue menu (guide + builder options merged), or **`chat test`** / **`chat end`** for interactive LLM chat (see above). |
| **`spawn`** | `whimc-agent.agents.spawn` | Same as **`/agents spawn`** (uses the shared `ExpertSpawnCommand`). |

### Guide agent entity types (reference)

The spawn command accepts:

1. **`player`** â€” then a **skin key** under `skins.<agent_type>` (see `agent_type` in `config.yml`, usually **`scientist_casual`** or **`scientist_stereotype`**).
2. Any other token that is in the **configured whitelist** in `AgentEntityTypes` (`player` + fixed mob enum names). Other `EntityType` IDs are rejected even if they are valid mobs on the server.

Use **tab completion** on the first argument of `/agents spawn` / `/agent spawn` for the list (`player` plus allowed mobs in whitelist order). On older servers, mobs whose `EntityType` constant does not exist yet (e.g. `HAPPY_GHAST`) are skipped automatically.

**In-game entity type change:** embodied players can switch the agent between **`player`** and the same allowed mob list (`AgentEntityTypes.selectableAgentTypes()`).

### Skin keys (under each `skins` section)

Use these as **`<skin>`** after **`player`**; names are **lowercase** and must match `config.yml`. They are grouped under **`scientist_casual`** and **`scientist_stereotype`** (switch pack with **`/agents skin_type <pack>`**).

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
### Guide
| Dialogue option | Description |
|-----------------|-------------|
| Guidance ("something cool") | If **Journey** is present: shows a **random subset** (3–5 when available) of **server public** waypoints and **`poi-*` regions** from **portal-linked worlds** (same name prefix, e.g. `ColderCold` / `ColderHot` / `ColderStrip` share `Colder`; override with `journey.linked-world-prefix`). POI regions come from WorldGuard and/or `rg_region` in MySQL (`journey.poi-source`: `worldguard`, `database`, or `both`). Each choice runs **`/<journey-command-root> server waypoint <name_id>`** as the player. Set **`journey.debug-log: true`** for linked-world and source counts in console. Falls back to all public waypoints, then **chat** entry, if nothing matches. |
| Free discussion | **Ongoing AI chat mode**: clicking the option toggles chat mode on (the player is notified) and every chat message they send is routed to the agent through **`doResponse()`** (PMML intent today; **`llm.use-for-reply`** when an `LlmProvider` is registered, with short-term conversation history). Type **`stop`** or **`exit`** in chat to end the session. |
| Scores | Runs **`/progress`** (e.g. **WHIMC-StudentFeedback**); session is ensured when possible. |
| Build ("I want to build something!") | Opens the **builder menu** (templates, demo builds, base feedback â€” see Builder table below); no mode switch needed. |
| Edit | **Embodied** agents only: change **name**, **entity type** (`player` vs Animals list), and **skin** when the NPC is a **player** model (up to configured edit limits). |

Every menu and submenu ends with a **Go back** entry (or "That's all for now" at the top level) so players can always navigate backwards. *(Planet tagging was removed from the plugin.)*

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


