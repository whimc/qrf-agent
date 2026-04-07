# Overworld-Agent

Overworld-Agent is a Minecraft plugin to create and define agent behavior. To teleport to the agent room for the guide agent for exploration use `/destination teleport AIchoice`. 
To select the agent right click on the desired agent and to start a conversation with your agent also right click on them. 
To select a dialogue option must click enter then left click on the desired option.

Alternatively you can spawn a guide agent with **`/agents spawn`** or **`/agent spawn`** (same implementation; permission is always **`whimc-agent.agents.spawn`** because the handler is registered under the `agents` permission namespace).

**Spawn syntax**

- **Player agent:** `/agents spawn player <skin> <name…>` — first tab-completion token is `player`, second is a skin key from `skins.<agent_type>` in `config.yml`, then the display name (spaces allowed in the name).
- **Animal agent:** `/agents spawn <animal> <name…>` — `<animal>` is one of the **fixed** mob IDs allowed by `AgentEntityTypes` (see that class / tab-complete: e.g. `axolotl`, `ocelot`, `turtle`, `sheep`, `strider`, `sniffer`, `nautilus`, `happy_ghast`, `bee`, `parrot`; types not present on your game version are omitted at runtime). No skin argument.
- **Legacy:** `/agents spawn <skin> <name…>` — if the first token is not a valid entity type, it is treated as a **player** skin key (same as omitting `player`).

Tab-complete the first argument to see every allowed value on your server version.

To spawn a builder agent use **`/agents rebuilderspawn`** and interact with it like a guide agent.

_**Requires Java 21+**_

---

## Building
Build the source with Maven:
```
$ mvn install
```

---

## Configuration
The base config file can be found under `/Overworld-Agent/src/main/resources/config.yml`.
An example config file can be found under `/Overworld-Agent/example-config.yml`.

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
| **Decision** | `Dialogue#doResponse()` compares confidence to an internal threshold (**0.5**). Above threshold → use that label’s row from **`prompts`** in `config.yml`; otherwise → **unknown** prompt (`label: -2`). |
| **Reply text** | Each prompt row supplies **`feedback`** strings. Placeholders such as `{NAME}`, `{PLANET}`, `{AGENT}` are filled from Bukkit/Citizens (`Dialogue#fillIn()`). |

So answers like “what’s your name?” work because the model maps the sentence to a label (e.g. **agent** / `label: 0`) whose feedback template includes something like `My name is {AGENT}.` That is **intent routing + templates**, not open-ended generation.

Replacing or retraining the classifier means supplying a new **`model.pmml`** (and keeping **`prompts` labels** aligned with the model’s output classes). The PMML file is large and is treated as an **opaque artifact** in this repo.

### LLM chatbot (optional)

Player dialogue can be answered by an **internet-hosted** model (**OpenAI** or **Google Gemini**) or by a **local** engine that exposes an **OpenAI-compatible** HTTP API (e.g. [Ollama](https://ollama.com/), LM Studio, vLLM). On startup, `OverworldAgent` reads `llm.provider` and related keys, creates an `LlmProvider`, and stores it for `Dialogue` to call on a worker thread when `llm.use-for-reply` is true.

#### Providers (`llm.provider`)

| Value | Use case | Credentials | Default model if `llm.model` empty |
|-------|------------|-------------|-------------------------------------|
| `none` | Disable built-in HTTP LLM | — | — |
| `openai` | [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat) | **Required:** API key | `gpt-4o-mini` |
| `gemini` | [Google AI Gemini](https://ai.google.dev/) generateContent | **Required:** API key | `gemini-1.5-flash` |
| `openai_compatible` | Local or self-hosted `/v1/chat/completions` | Optional API key (many local servers use none) | `llama3.2` |

For **local** inference, set `provider: openai_compatible` and `base-url` to your server’s OpenAI-compatible root (must end up posting to `…/v1/chat/completions` — the plugin normalizes a base such as `http://127.0.0.1:11434/v1`). Example: **Ollama** default `http://127.0.0.1:11434/v1`.

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
| `llm.context-directory` | Subfolder name under the plugin **data folder** (default `llm-context`). Created on enable when possible. Full path: `plugins/WHIMC-OverworldAgent/llm-context` (artifact id may differ). |
| `llm.rag.enabled` | When `true`, scans that directory and appends bounded excerpts to the system prompt before each completion. |
| `llm.rag.max-total-chars` / `max-file-chars` | Cap total and per-file bytes so prompts stay reasonable. |
| `llm.rag.max-directory-depth` | How deep to walk subfolders. |
| `llm.rag.include-extensions` | File extensions to read (default `txt`, `md`). |

Put glossaries, world lore, or lesson snippets as `.md`/`.txt` files there. This is **not** a vector database or hybrid search—only a simple file concat for small corpora; you can replace the flow later with a custom `LlmProvider` that does real retrieval.

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

- **`boolean isConfigured()`** — Built-in providers return `true` only when required fields (e.g. API key + model) are set.
- **`String complete(String systemPrompt, String userMessage)`** — Plain-text reply; runs off the main thread.

You can still **override** the auto-selected provider after load:

```java
OverworldAgent oa = (OverworldAgent) Bukkit.getPluginManager().getPlugin("WHIMC-OverworldAgent");
if (oa != null) {
    oa.setLlmProvider(new YourLlmProvider(/* ... */));
}
```

Use `depend` / `softdepend` / load order so your code runs after `WHIMC-OverworldAgent` enables.

#### Behavior summary

| `llm.use-for-reply` | Provider configured | Result |
|---------------------|---------------------|--------|
| `false` | any | PMML intent + template feedback only. |
| `true` | no / `isConfigured()` false | Template feedback (no HTTP call). |
| `true` | yes | Async `complete(...)`; on success the **shown** reply is the LLM output; failures are logged and template feedback is kept. |

---
## Commands

Permissions follow **`whimc-agent.<base>.<subcommand>`** (each `/agents …` subcommand registers its own node). The shared **guide spawn** handler is registered as **`whimc-agent.agents.spawn`** even when invoked as **`/agent spawn`**.

### Root commands (`plugin.yml`)

| Command | Typical use                                                                                      |
|---------|--------------------------------------------------------------------------------------------------|
| **`/agents`** | Admin / spawn / builder — requires a subcommand (see below).                                     |
| **`/agent`** | Player **`chat`** or **`spawn`** (same spawn behavior as `/agents spawn`).                       |
| **`/admintags`** | Manage dialogue tags (`TagAdminCommand`).                                                        |
| **`/assess-habitat`** | Habitat assessment command. Only works with ML-API and routing pythong script on server running. |
| **`/oacallback`** | **Internal** — clickable chat UI callbacks; not for players to run manually.                     |

### `/agents` subcommands (current code)

| Subcommand | Permission node | Description |
|------------|-----------------|-------------|
| **`spawn`** | `whimc-agent.agents.spawn` | Spawn or replace your **guide** agent (`player` + skin + name, or `Animals` mob type + name). |
| **`despawn`** | `whimc-agent.agents.despawn` | Despawn agent(s) for a player or **`all`**. |
| **`destroy`** | `whimc-agent.agents.destroy` | Destroy agent NPC(s) for a player or **`all`**. |
| **`reactivate`** | `whimc-agent.agents.reactivate` | Respawn agent(s) for a player or **`all`**. |
| **`rebuilderspawn`** | `whimc-agent.agents.rebuilderspawn` | Spawn a **builder** NPC (player model, fixed “Builder” setup) at your location. |
| **`skin_type`** | `whimc-agent.agents.skin_type` | Set global skin pack: argument must be a **top-level key** under `skins:` in `config.yml` (bundled: **`scientist_casual`**, **`scientist_stereotype`**). |
| **`chat_type`** | `whimc-agent.agents.chat_type` | Set disembodied dialogue mode: **`Guide`** or **`Builder`** (matches `DialogueType` enum; case-insensitive). |

### `/agent` subcommands

| Subcommand | Permission node | Description |
|------------|-----------------|-------------|
| **`chat`** | `whimc-agent.agent.chat` | Open the disembodied menu (`Guide` or `Builder` per `chat_type`). |
| **`spawn`** | `whimc-agent.agents.spawn` | Same as **`/agents spawn`** (uses the shared `ExpertSpawnCommand`). |

### Guide agent entity types (reference)

The spawn command accepts:

1. **`player`** — then a **skin key** under `skins.<agent_type>` (see `agent_type` in `config.yml`, usually **`scientist_casual`** or **`scientist_stereotype`**).
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
| Guidance (“something cool”) | If **Journey** is present: shows a **random subset** (3–5 when available) of **server public** waypoints (world-scoped when Journey domain mapping works, otherwise all public). Each choice runs **`/<journey-command-root> server waypoint <name_id>`** as the player (default root `journey` from `config.yml`; must match how Journey registers its command, e.g. `jo`). Requires the player to **have permission** for that command. **Console:** each dispatch is logged at `INFO`; if `dispatchCommand` returns `false`, a **`WARNING`** explains common causes. Set **`journey.debug-log: true`** for extra logs when opening the menu (domain id, waypoint counts, fallback). If there are no public waypoints, falls back to **chat** entry for a destination. |
| Free discussion | **Chat input** (not a sign): type what you want to say; routed through **`doResponse()`** (PMML intent today; **`llm.use-for-reply`** when an `LlmProvider` is registered). |
| Scores | Runs **`/progress`** (e.g. **WHIMC-StudentFeedback**); session is ensured when possible. |
| Tagging | Pattern-matched **chat** feedback when tags are configured for the world and under the tag limit. |
| Edit | **Embodied** agents only: change **name**, **entity type** (`player` vs Animals list), and **skin** when the NPC is a **player** model (up to configured edit limits). |

### Builder
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


