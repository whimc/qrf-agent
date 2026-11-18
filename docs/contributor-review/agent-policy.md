# WHIMC agent guidance policy draft

**For review — not active instructions.** Proposed shared policy for coding agents and their human contributors. The [review summary](README.md) provides the short version; the [relationship map](relationship-map.md) supplies project context. This draft supplements the existing human review process.

## A1 Distribute guidance explicitly

Maintain the common policy in a central WHIMC repository, with an identifiable policy revision. After approval, put a concise copy of its core rules into each participating repository's root `AGENTS.md`, followed by repository-specific instructions. Keep longer architecture and workflow details in linked local documentation. Do not install this draft as an active instruction file until reviewed.

Codex discovers global and repository guidance through its configured/local hierarchy; the closest applicable guidance can specialize broader instructions. A separate organization repository is not a shared ancestor of every checkout and is not automatically loaded as organization policy. See [Codex AGENTS.md behavior](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

GitHub's public organization `.github` repository can supply supported contribution documents and templates as fallbacks. Those files are not copied into clones, and repository-local files can override defaults. `AGENTS.md` is not one of those inherited community-health files. See [GitHub default files](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/creating-a-default-community-health-file).

Proposed maintenance: a maintainer updates the common policy revision and opens synchronization PRs for participating repositories. Review the diff and preserve local build commands and exceptions. Record intentional exceptions with a reason and owner. A remote link alone is insufficient for core agent rules: tools may be offline or may not follow links automatically. Review adaptations for other coding tools individually.

## A2 Check integration boundaries

Before implementing a change:

1. Read the repository's local instructions and build/setup documentation.
2. Read its relationship entry and identify what supplies and consumes the changed behavior.
3. Identify affected SQL schemas, public Java methods/events, reflection targets, runtime plugin names, commands, permissions, config keys, filesystem formats, or external request/response fields.
4. Record the affected repositories and interfaces in the issue or PR. If unknown, record the uncertainty and inspect the referenced source before claiming compatibility.

Shared MySQL tables count as interfaces even when plugins have no Java dependency on each other. Compilation alone cannot verify a database consumer, a reflected method, or a dispatched command.

Changes to an interface must describe compatibility with existing consumers, the transition/migration approach, and any deployment ordering. Prefer additive changes where feasible. Coordinate breaking changes with affected maintainers through linked work items; do not quietly update unrelated repositories as part of a narrow fix.

## A3 Implement and validate within the contribution scope

Work from a tracked issue and connected branch using [W1–W3](contribution-workflow.md). Keep the change focused on the reported behavior. Explain scope changes in the issue or PR. New contributors may investigate first; they are not expected to know the whole plugin network before reporting a bug.

Use the supported JDK, build tooling, and dependency versions documented in that repository. For qrf-agent, the inspected POM targets Java 21 and CI runs `mvn -B -ntp package`; the POM also references local JARs under `lib/`. These are source observations, not a claim of a verified clean build on every machine. Report a missing dependency explicitly rather than substituting an unrelated binary.

Run checks appropriate to the change and at least one affected integration scenario where the change crosses a plugin boundary. Use a development server, synthetic learners, and a development database. Do not copy research records, credentials, or live player identifiers into issue examples, prompts, or fixtures. If required infrastructure is unavailable, describe the unverified behavior and the follow-up check needed.

Update the relationship documentation when changing an integration. Distinguish active implementation, declared-only dependency, legacy/commented code, and proposed functionality.

## A4 Treat configuration and external data as interfaces

Preserve existing config keys or document migrations. Check default and disabled-feature behavior. A dependency listed as `softdepend` is not proof that every code path safely tolerates its absence.

For changes to learner context, research logging, reference files, or external providers, describe the fields read, summarized, stored, and sent, including peer context and location data when applicable. Use environment-based secrets or the project's established secret mechanism. Review context expansion with the responsible maintainer before enabling it in a shared environment; implementation review and deployment decisions remain distinct.

For SQL migrations, state the producer/owner, readers, schema version, expected old/new behavior, and recovery strategy. Do not imply that reverting a plugin JAR reverses a database migration. Rebuilding/block replay checks belong in a disposable test area.

## A5 Report evidence and preserve human review

The PR must state the problem, observable result, issue link, affected interfaces, and validation evidence. Distinguish checks performed from checks proposed. List exact failures or environmental blockers succinctly. Do not claim tests or runtime compatibility that were not checked.

Agents may prepare code, documentation and review material within the task's authorized scope. Merging, publishing releases, changing organization policy, and deploying to shared servers follow the established maintainer process and the user's authorization. Policy text is guidance; use [W4's GitHub checks](contribution-workflow.md#w4-roll-out-enforcement-in-stages) for mechanical merge requirements.

## A6 Proposed local instruction content

After approval, each repository's `AGENTS.md` should contain:

- The shared policy revision and local links to the full policy/workflow.
- The issue/branch/PR rule and how to record a maintainer exception.
- A short purpose statement, runtime plugin name, canonical repository, and owners.
- Affected producers/consumers, key SQL/API/event/command contracts, and map link.
- Supported build commands, dependency setup, test commands and integration checks.
- Any local exceptions and their owners.

This draft deliberately uses a normal documentation filename so it does not alter the behavior of coding agents while awaiting review.
