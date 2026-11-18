# CI and releases

Pull requests to `main`, `animal-ai` and `llm-agent`, and pushes to the two development branches, use the CI workflow. A small `changes` job checks the diff and validates the change classifier. Changes limited to `docs/`, root Markdown files, or the license skip the Java build, dependency audit, TruffleHog and CodeQL jobs. Skipped jobs still report a successful/skipped check so documentation PRs do not hang on required checks. Mixed documentation/code changes run the code checks. Markdown under `src/` is packaged content and is treated as code.

Build, workflow and other non-documentation changes still run the code checks. The main ruleset requires the actual GitHub check names: `changes`, `build`, `dependency-audit`, `trufflehog` and `analyze`. The existing human approval rule remains in place.

Only changes merged/pushed to `main` can publish releases. Release inputs are `src/`, `lib/`, Maven wrappers and `pom.xml`. A change limited to the project's own POM version is a reconciliation change, not a new release. Dependency or build configuration changes in the POM do qualify. Documentation and workflow-only changes do not publish a JAR or increment a release version.

The release job increments the POM's patch version in its temporary build checkout, skipping versions already published as GitHub Releases. It builds and publishes the versioned JAR, with the source commit as the release tag target. **Published release versions are authoritative; the POM in Git need not advance for every artifact.** A normal local Maven build uses the checked-in POM version. No version-only commit or automatic bump PR is created. A deliberate major/minor change can set a new POM baseline in the same PR as the corresponding code changes.

This prevents duplicate releases from development and main branches and prevents documentation edits from producing releases. Release concurrency is serialized on `main`. Minecraft server installation remains manual.

Classifier validation: `node --test .github/scripts/change-scope.test.cjs`.
