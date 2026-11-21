# AGENTS.md — Cifr Repository Instructions

**Scope:** Applies to the entire Cifr repository unless a more specific AGENTS.md overrides it.

## Read first

1. docs/architecture/cifr-ide-integration.md — coding-domain ownership, IDE boundary, migration and Andalus integration.
2. Root and module POMs, Rust workspace manifests under cifr-ide, relevant module READMEs.
3. Existing source, tests, dependency versions, branch state and CI before changing architecture or behavior.

## Product and ownership invariant

**Cifr is the coding-agent product and domain system. Andalus is a neutral, reusable agent runtime/orchestration foundation that Cifr may integrate with.**

Cifr must remain independently usable as a coding system. Andalus integration is optional and must not become a prerequisite for core coding workflows, repository intelligence, IDE operations or domain state.

## Cifr owns

- Coding-specific agent execution loop, engineering task lifecycle and coding plans.
- Repository/workspace intelligence, evidence packets, dependency and impact reasoning, retrieval/ranking and test discovery.
- Patch planning, patch validation, mutation authorization, coding-specific policy and verification strategy.
- Coding tools, skills, toolchains, build/test workflows and coding artifacts.
- Cifr IDE integration and the coding product's public API/UI surfaces.
- Canonical coding task, repository, workspace and verification state.

## Cifr IDE owns

- IDE-host lifecycle and workspace/document primitives.
- LSP/DAP lifecycle and language intelligence exposed by the IDE host.
- Document snapshots, navigation, symbols/references, rename/refactor and low-level interactive search.
- IDE-level process/build/run/debug primitives, subject to Cifr authorization and isolation controls.

Do not duplicate IDE-owned document or language-server state inside Cifr. Do not move Cifr's coding-specific reasoning into the IDE host.

## Andalus integration rules

- Integrate through explicit, versioned contracts and adapters; keep transport details out of Cifr domain types.
- Support standalone Cifr operation. Andalus is an optional host/orchestrator, not a mandatory runtime dependency.
- Expose Cifr through one or more deliberate surfaces: selected tools, a specialist coding-agent invocation, and/or the complete Cifr application. These are complementary, not mutually exclusive.
- When invoked by Andalus, Cifr owns its internal coding loop. Andalus delegates a bounded task and receives lifecycle events, progress, artifacts and a final outcome; it must not micromanage every edit, shell command, test or IDE call.
- Preserve trusted actor/tenant, policy, capability allowlist, correlation ID, budget, deadline and cancellation context across the boundary. Never trust identity or permissions supplied only by prompts or untrusted tool arguments.
- Keep Cifr authorization authoritative for repository access, file mutation, process execution, secrets and verification actions. Generic Andalus approval or tool authorization does not replace Cifr domain checks.
- Support deployment choices without changing Cifr domain semantics: embedded/in-process adapter, local sidecar/IPC, or remote service over a versioned protocol. Add MCP/A2A adapters only for interoperability; do not make either protocol the internal domain model.
- Do not introduce a hard dependency on a particular Andalus runtime release until API, JDK, dependency graph, lifecycle and security compatibility are demonstrated.

## IDE and legacy migration rules

- Use cifr-ide as the intended IDE engine; retire Muqabalah as the primary implementation path.
- Preserve only narrowly required Muqabalah code when justified by a documented gap and license/provenance review.
- Keep the Java client version-aware and isolate the Rust host protocol behind the Cifr IDE adapter.
- Propagate real workspace/session identity. Never use synthetic global workspace IDs or ThreadLocal as asynchronous context propagation.
- Distinguish infrastructure errors from valid empty search/index results.
- Do not claim process isolation merely because a component is named Sandbox. Enforce and test OS/container isolation, resource limits, output draining, timeouts and cancellation.

## Engineering workflow

1. Inspect current branch, module structure, relevant implementations, tests and CI.
2. State the coding user journey and acceptance criteria.
3. Preserve existing Cifr capabilities unless an explicit migration proves replacement parity.
4. Implement coherent vertical slices with typed contracts and explicit failure semantics.
5. Test positive and negative paths, including denied access, workspace isolation, cancellation, stale indexes and failed IDE-host startup.
6. Verify real adapters and compatibility; mocks alone do not prove integration.
7. Update architecture docs and report exact commits, commands, test results and known gaps.

## Must not do

- Do not turn Cifr into a thin wrapper around Andalus or Cifr IDE.
- Do not place coding-specific modules or business logic under Andalus-owned namespaces solely to make integration convenient.
- Do not replace Cifr's evidence graph, coding planner, mutation gate or verification workflow with generic IDE primitives.
- Do not expose unrestricted filesystem, shell, Git mutation or process tools to an agent.
- Do not add an implicit localhost service fallback or silently swallow remote/IDE failures.
- Do not claim builds, tests, security isolation or production readiness without observed evidence.
- Do not open a PR automatically; wait for explicit user approval.

## Completion gate

A feature is complete only when the intended Cifr user journey works with real adapters, domain authorization is demonstrated, tests pass, contracts and docs match the implementation, and remaining limitations are explicit. Otherwise label it foundation, partial or unverified.
