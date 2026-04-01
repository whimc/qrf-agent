# Overworld-Agent

Overworld-Agent is a Minecraft plugin to create and define agent behavior. To teleport to the agent room for the guide agent for exploration use `/destination teleport AIchoice`. 
To select the agent right click on the desired agent and to start a conversation with your agent also right click on them. 
To select a dialogue option must click enter then left click on the desired option.

Alternatively you can use `/agents spawn <skin name> <agent name>` to spawn an agent (described below) or use the `/agent chat` command to talk to a disembodied agent (described below).

To spawn a builder agent use `/agents rebuilderspawn` and interact with it using the same method as described previously with the guide agent.

_**Requires Java 21+**_

---

## Building
Build the source with Maven:
```
$ mvn install
```

---

## Configuration
The config file can be found under `/Overworld-Agent/src/main/resources/config.yml`.

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
---
## Commands
### Admin Commands
| Command                                                      | Description                                                                                                                                      |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `/agents spawn <skin name> <agent name>`                     | Spawn an agent with the specified skin and name that follows the student that spawned the agent and when right clicked prompts for a discussion. |
| `/agents reactivate <player name or all>`                    | Respawns an agent for the specified player or all players on the server when argument is all.                                                    |
| `/agents despawn <player name or all>`                       | Despawns an agent for the specified player or all players on the server when argument is all.                                                    |
| `/agents destroy <player name or all>`                       | Destroys an agent for the specified player or all players on the server when argument is all.                                                    |
| `/agents skin_type <scientist_casual or scientist_stereotyp` | Changes the type of skin agents have (lab coats or formal wear).                                                                                 |
| `/agents chat_type <Builder or Guide>`                       | Changes the agent chat type for when students are using the disembodied agent.                                                                   |
| `/agents rebuilderspawn`                                     | Spawn a builder agent at the player's location.                                                                                                  |
| `/assess-habitat`                                            | Issues a base evaluation to all participants.                                                                                                    |
### Player Commands
| Command       | Description                                                                                       |
|---------------|---------------------------------------------------------------------------------------------------|
| `/agent chat` | Displays chat options depending on the chat type set by the admin in the disembodied agent group (includes base assessment by kids). |

### Skin Types
Input for `<skin name>` make sure spelled correctly and all lowercase
| Name | Description |
|---|---|
| `astronaut` | Ambiguous gender and ethnicity agent in astronaut suit. |
| `wmscientist` | White male scientist. |
| `wfscientist` | White female scientist. |
| `bmscientist` | Black male scientist. |
| `bfscientist` | Black female scientist. |
| `amscientist` | Asian male scientist. |
| `afscientist` | Asian female scientist. |
| `hmscientist` | Hispanic male scientist. |
| `hfscientist` | Hispanic female scientist. |

## Player Dialogue Options
### Guide
| Dialogue Option | Description                                                                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Guidance        | Uses the Journey plugin to open its destination selector (`/jt`) so players can navigate to places of interest (waypoints, warps, etc.).        |
| Tagging         | Gives feedback to students using pattern matching if they use their tag to make note of something scientifically important in the world.        |
| Edit            | Only available to emboodied agents and not agents using the chat function. Allows students to make changes to their skin or name up to 5 times. |

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


