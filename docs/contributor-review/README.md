# WHIMC contributor guidance draft

**For review — November 18, 2026.** This proposal centers on qrf-agent and the plugins and data sources it uses. The students' analysis dashboard and WHIMC-Explorer are outside this draft's scope. These files do not activate agent instructions, GitHub checks, or organization settings.

## Review the relationships first

Open the [relationship map](relationship-map.md) for the diagrams, integration inventory, source evidence, and suggested checks. The main architectural finding is that **shared database schemas are plugin interfaces too**: qrf-agent can consume science measurements, observations, movement history, and progress without calling those plugins' Java APIs.

For website use, [download or embed the HTML, SVG or PNG map](web/README.md). The HTML includes an expandable text description and needs no external libraries.

The map distinguishes implemented code paths, declared dependencies, and legacy code. It describes the local source snapshot, not the plugins or configuration currently running on a server.

## Proposed contributor rules in one minute

| Summary | Detailed draft |
| --- | --- |
| Start with an issue: describe the error, reproduction, expected behavior, and likely affected plugins. “Unsure which plugin” is a valid starting point. | [W1: issues](contribution-workflow.md#w1-start-with-an-issue) |
| Work on a branch connected to that issue; open a draft PR early. A number in a branch name alone is not a GitHub issue link. | [W2: branches and PRs](contribution-workflow.md#w2-connect-the-branch-and-pull-request) |
| Before changing a shared table, event, command, config key, or external request, identify its producers and consumers. | [A2: integration boundaries](agent-policy.md#a2-check-integration-boundaries) |
| Report what was tested, including affected integrations and missing dependencies. Preserve the established review process. | [W3: evidence and review](contribution-workflow.md#w3-provide-evidence-and-use-existing-review) |
| Keep a common policy centrally; place a short, versioned local copy plus project details in each repository's `AGENTS.md`. | [A1: policy distribution](agent-policy.md#a1-distribute-guidance-explicitly) |
| Begin with advisory issue checks; require them only after validating the workflow and existing branch targets. | [W4: enforcement](contribution-workflow.md#w4-roll-out-enforcement-in-stages) |

## Review decisions

1. **Scope:** start with qrf-agent, QRF-Journey, Science-Tools, Observations, Position-Tracker, student-feedback, and Habbitat-Assesser? External plugins remain documented dependencies rather than repositories we control.
2. **Issue requirement:** proposed for all changes, including documentation; allow a documented maintainer exception for urgent fixes or maintenance automation.
3. **Review ownership:** name a primary and backup maintainer for each repository and for shared database contracts. Preserve current approval requirements until those owners are confirmed.
4. **Branch targets:** confirm each repository's default, development, and release branches before adding required checks. qrf-agent's checked-in CI currently handles PRs to `main`, but push builds target `animal-ai` and `llm-agent`.
5. **Data boundaries:** confirm which learner fields, peer observations, and location summaries may be sent to configured external model providers; the draft does not change current settings.

## Proposed rollout

Review and correct the map first. Then select owners and repositories, approve the policies, and introduce the [example contribution forms](templates.md). Trial the process on one ordinary fix touching a data producer and qrf-agent. Only then distribute active instructions and enable checks. The existing review process remains the basis for approval.

The policy proposal separates three mechanisms: human guidance in `CONTRIBUTING.md`, agent guidance in repository `AGENTS.md`, and merge requirements in GitHub. Default organization contribution files are fallbacks and are not included in repository clones; a local policy copy avoids relying on an agent following a remote link. See [GitHub defaults](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/creating-a-default-community-health-file) and [Codex guidance](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
