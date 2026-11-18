# WHIMC contribution workflow draft

**For review — not enforced.** This extends the current review process with issue tracking and integration context. See the [summary](README.md), [relationship map](relationship-map.md), [agent policy](agent-policy.md), and [example forms](templates.md).

## W1 Start with an issue

Proposed default: every contribution has a tracked issue, including documentation. Search existing issues first. For bugs, capture the observed and expected behavior, minimal reproduction, relevant versions, and sanitized errors. For features, describe the intended user behavior and acceptance criteria. Record suspected affected plugins and interfaces, or “unknown — needs triage.” Lack of architectural knowledge should not prevent a student from reporting an error.

Use an issue in the repository most likely to own the behavior. If ownership is unclear, ask a maintainer to triage rather than creating duplicates in every repository. Maintain one coordination issue for work spanning several repositories, with linked repository-specific implementation issues when needed. Each PR must identify its own completion criteria.

Before coding, claim or request assignment through the existing team process and note the proposed scope. Maintainers identify the likely owner and related plugins using the relationship map. Do not require a second approval gate if the work is already assigned and sufficiently defined.

Suggested labels: `bug`, `enhancement`, `documentation`, `needs-triage`, `good-first-issue`, and `cross-plugin`. An organization Project can provide one view across repositories; labels, assignees and ownership conventions still need local setup and named maintainers.

## W2 Connect the branch and pull request

Create a branch from the issue's Development section where supported and permitted, or link an existing branch using GitHub's issue tools. Contributors using forks should reference the issue explicitly in the PR and have a maintainer establish the link when needed. See [GitHub issue branches](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-a-branch-for-an-issue).

Proposed branch name: `<issue-number>-<short-purpose>`, for example `123-fix-observation-expiry`. This is a readable naming convention, not an issue-linking mechanism. Use the correct base branch for the repository; do not assume it is `main`.

Open a draft PR once there is a reviewable change or concrete design. Include the problem/result, issue, affected plugins, interface changes and validation. For a fix that completes a same-repository issue, use `Closes #123`. For another repository, use `Closes whimc/REPOSITORY#123`. GitHub interprets closing keywords when targeting the repository's default branch; development-branch PRs require an explicit tracking/reference plan until integration to default. See [GitHub PR linking](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue).

For a multi-repository change, use ordinary issue references in partial PRs and reserve a closing keyword for the PR that actually completes the issue. Do not close the coordination issue after the first partial merge. Link related PRs and state rollout order.

## W3 Provide evidence and use existing review

Before requesting final review:

1. Demonstrate the acceptance criteria and summarize the observed result.
2. Identify changed interfaces and consumers; “none” is acceptable with a brief basis.
3. Provide build/test results and targeted integration evidence using synthetic data. Record environment limitations and checks still needed.
4. Include config/schema changes, compatibility implications and deployment ordering where relevant.
5. Update affected documentation and the relationship map.

Preserve the current review requirements. Proposed ownership addition: each repository and shared interface has a named primary and backup maintainer; request affected maintainers when crossing boundaries. `CODEOWNERS` can route path-based reviews, but it does not discover downstream plugins or replace the relationship map. Do not invent reviewer accounts or change approval counts in this draft.

Reviewers confirm the result, evidence, dependency implications and issue linkage. Merge/release/deploy through the existing process. Verify linked issues close at the right milestone and leave unresolved coordination work open.

## W4 Roll out enforcement in stages

### Stage 1 Guidance

After policy approval, introduce central `CONTRIBUTING.md` and issue/PR templates in the public organization `.github` repository, and reviewed local `AGENTS.md` files. Existing local contribution files override organization fallbacks; audit them before assuming defaults apply. GitHub defaults do not distribute workflows, `CODEOWNERS`, or active agent instructions into every checkout. See [default file behavior](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/creating-a-default-community-health-file).

### Stage 2 Advisory checks

Trial an issue-link check on selected repositories without blocking merges. The proposed check should:

- Verify a real, accessible issue and a real PR relationship where GitHub exposes it; a regex matching `#123` is insufficient.
- Accept the approved explicit tracking convention for PRs targeting development branches, where closing-keyword relationships are not created in the same way.
- Reevaluate when a PR opens, its body/base changes, or its head changes. Provide a recheck mechanism for links updated through the sidebar.
- Support fork contributions and cross-repository references without requiring student secrets or elevated workflow permissions.
- Report a clear next step when issue visibility/permissions prevent verification; do not label an inaccessible private issue “nonexistent.”
- Permit the documented maintainer exception described below, with an auditable reason.

The implementation must be reviewed before rollout. Do not run contributed code under a privileged metadata-check job. Begin with repository-local workflow callers/checks; sharing a reusable workflow alone does not make it execute in every repository.

### Stage 3 Merge requirements

Once the advisory checks work for ordinary, fork, cross-repository and development-branch PRs, configure protected branches or rulesets to require PR review, existing build/security checks as appropriate, and the issue-link check. Verify check names and branch coverage before requiring them. Repository protection availability depends on visibility and plan; organization rulesets spanning repositories require GitHub Team or Enterprise. See [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches) and [organization rulesets](https://docs.github.com/en/organizations/managing-organization-settings/creating-rulesets-for-repositories-in-your-organization).

The local qrf-agent [CI](../../.github/workflows/ci.yml) currently builds PRs to `main` and pushes to `animal-ai`/`llm-agent`. Confirm intended targets and live protections first; the workflow file alone does not prove any check is currently required. Keep the current review process during adoption.

### Exceptions

Proposed limited exceptions: urgent incident fixes and routine automated dependency/release maintenance. A maintainer records the reason in the PR and uses a restricted, auditable exception mechanism. If an urgent fix cannot wait for issue creation, create/link the follow-up issue promptly afterward. Students and agents cannot grant themselves an exception by editing the PR text or adding a branch prefix. Confirm the authorized maintainers before implementing a bypass.

## W5 Keep the policy useful

Assign ownership of policy updates and shared schema documentation. Update the map in the same contribution that changes an integration; refresh declared-only and legacy entries when they are investigated. Review friction after the first few student contributions and simplify fields that do not improve triage or review.

Evidence still needed before enabling enforcement: organization plan, current GitHub protections, actual repository base branches, reviewer teams, fork permissions, private issue accessibility, and required check identities. This draft does not claim those live settings were inspected.
