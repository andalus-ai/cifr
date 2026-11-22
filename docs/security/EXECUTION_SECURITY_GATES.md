# Cifr execution security gates

**Status: fail-closed verification boundary; no production OS sandbox is claimed.**

This document records the security contract that must hold until a real isolation backend is implemented and tested. An interface or declared capability is not proof that the operating system enforces the control.

## Current enforced boundary

- The strict verification entry point is IsolatedVerificationService.
- It accepts only a backend whose declared isolation profile satisfies every strict verification requirement.
- It refuses execution when no eligible backend exists or backend selection is ambiguous.
- The ordinary process-backed verification provider is not an eligible isolated backend and must never be silently substituted.
- Verification evidence must be bound to the exact task, workspace, workspace fingerprint, check, verifier and command; required checks must pass before task completion.

These guarantees apply only when callers use the strict service. They do not make every process or tool execution in Cifr isolated.

## Required controls for an isolated backend

A backend must enforce all of the following at the OS/runtime boundary:

1. A dedicated execution identity with no ambient host credentials.
2. A read-only base filesystem and an explicitly mounted writable workspace.
3. Filesystem namespace confinement, including symlink and mount-boundary protections.
4. Network disabled by default, with narrowly scoped explicit exceptions.
5. Hard CPU, memory, process-count, wall-clock, disk and output limits.
6. Process-group or container-wide termination on timeout and cancellation.
7. An environment-variable allowlist; secrets are never inherited implicitly.
8. Bounded stdout/stderr capture while continuing to drain both streams.
9. Image/runtime provenance and a pinned, maintained execution image.
10. Cleanup of temporary resources even after crashes or forced termination.

A configuration field, Java-side preflight check, Docker CLI invocation, or backend self-reported profile is not independently sufficient evidence that these controls are effective. The implementation must validate runtime configuration and fail closed when a control cannot be established.

## Authorization requirements

Tool allowlisting is a coarse capability gate, not a complete authorization system. Before any sensitive operation, Cifr must authorize the concrete operation using trusted principal, workspace, resource and action metadata.

- Read-only and mutating actions must be classified by the implementation, not by model-provided metadata.
- A mutation must not execute when policy returns REQUIRE_APPROVAL, DENY, or an unknown decision.
- Approval must be bound to the principal, workspace, exact action and canonicalized arguments; it must expire and be single-use.
- Approval must be checked immediately before the side effect to avoid time-of-check/time-of-use gaps.
- Missing identity, policy, approval service or audit sink must fail closed for sensitive actions.
- Every decision and side effect must produce correlated audit records without storing secret values.

Until these semantics are implemented and tested end-to-end, high-risk tools must remain disabled in the Andalus harness bridge. Do not rely on upstream runtime behavior that does not explicitly implement approval semantics.

## Required adversarial tests

A production backend is not eligible for strict verification until tests prove:

- Workspace traversal, absolute paths, symlink escapes and writable mount escapes are blocked.
- The process cannot read host home directories, SSH keys, cloud credentials, container sockets or unrelated workspaces.
- Network access is unavailable by default.
- Memory, CPU, process-count, disk and output limits are enforced.
- Timeout/cancellation terminates descendants and removes the execution environment.
- Environment variables and secrets are not inherited.
- Malformed requests, backend errors, missing controls and ambiguous backend selection fail closed.
- Forged, stale, cross-task and cross-workspace evidence is rejected.
- REQUIRE_APPROVAL cannot fall through to execution, including through retries, alternate adapters or direct tool invocation.

## Release gate

Do not enable repository-controlled build scripts, tests, package lifecycle scripts or arbitrary shell commands in shared/remote execution until isolation and authorization are implemented, exercised in CI, and reviewed. The local process runner remains a convenience adapter, not a security boundary.
