# WHIMC contribution forms draft

**Review examples only.** These are proposed template text, not installed GitHub templates. See [W1–W3](contribution-workflow.md) and [A2–A5](agent-policy.md) for their policy basis.

## Issue example

```text
Title: <plugin or behavior>: <short problem>

What happened?
What should happen?
How can someone reproduce it?
Which versions/environment were involved?
What sanitized error or screenshot helps explain it?

Likely plugin(s): <names, or unknown — needs triage>
Related interface: <table/event/command/config/API, or unknown>
Related issues/PRs:
Acceptance criteria:
```

For a feature, replace reproduction/errors with the desired behavior and a concrete example. Do not require students to diagnose the root cause before submitting.

## Pull request example

```text
Problem and resulting behavior:
Issue: <link; use Closes only when this PR completes the issue>
Related PRs and coordination issue:

Affected plugins/data producers/consumers:
Changed interfaces: <SQL schema, event, command, reflection, config, service>
Compatibility/migration/deployment order: <or none>

Validation performed:
- Build/check and result:
- Integration scenario, versions/config and observed result:
- Unverified behavior and follow-up needed:

Documentation/map update: <link, or why unnecessary>
Reviewer ownership: <affected maintainer/team, if known>
```

Do not put student names, UUIDs, research transcripts, connection strings, API keys or private server configuration in public examples. Use synthetic values.

## Example cross-plugin contribution

**Issue:** “Expired observations still appear in agent context.” Reproduce using a synthetic observation with a known expiration. Initial owners: Observations for the stored expiration meaning and qrf-agent for the reader's filtering. Student Feedback is a potential additional consumer to inspect.

**Investigation:** compare writer and reader timestamp units, active status and null/zero expiration semantics. Determine which repository needs the fix before changing either. The map's observation row identifies both source files.

**PR evidence:** create active, expired and inactive synthetic observations; show which appear in the generated context. If changing the shared schema or semantics, add a linked compatibility task for every affected consumer. This is a proposed teaching scenario, not a claim that this bug currently exists.
