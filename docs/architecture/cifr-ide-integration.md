# CIFR and CIFR IDE integration

**Status:** protocol-v2 Java client and workspace-scoped symbol/reference migration are now implemented on the integration branch; full runtime lifecycle and tool-context propagation remain follow-up work.

## 1. Target ownership

| Responsibility | Owner | Notes |
|---|---|---|
| Generic agent runtime, model/provider routing and cross-domain orchestration | Andalus (optional) | Cifr may run standalone or integrate with Andalus; Andalus does not own the Cifr coding loop. |
| Coding plans, task graph, task context ranking, evidence packets | CIFR | Coding-specific intelligence, not generic IDE state. |
| Patch proposals, validation, mutation authorization, execution policy, verification strategy | CIFR | Preserve existing coding-agent governance and lifecycle. |
| Workspace lifecycle, open documents, text snapshots and filesystem view | CIFR IDE | One authoritative workspace/document state. |
| LSP/DAP lifecycle, syntax, outline, references, rename/refactor, navigation | CIFR IDE | Do not retain a second Muqabalah LSP kernel in the steady state. |
| File/symbol indexing primitives and interactive workspace search | CIFR IDE | CIFR may consume results and build task-oriented evidence on top. |
| Repository dependency/evidence graph, impact reasoning, test discovery and task-context selection | CIFR | Retain these existing CIFR capabilities; delegate low-level facts to IDE services where appropriate. |
| Process execution and isolation | CIFR IDE host + CIFR Sandbox policy | IDE host provides process/build/run primitives; CIFR owns agent authorization and policy. Isolation must be explicit, not inferred from a class named Sandbox. |
| Public API, gRPC/REST, Andalus integration and MCP adapters | CIFR | The Rust host protocol is an internal IDE integration boundary, not the public CIFR API. |

## 2. Existing integration boundary

The Rust host already exposes a headless JSON-RPC 2.0 server using
Content-Length framing over stdio. Its current protocol version is **2**:

1. `initialize({version: 2})` returns server version, protocol, OS, architecture
   and home directory.
2. `commands()` returns the remotely exposed command names.
3. `invoke({window, cmd, args})` dispatches a host command.
4. Server notifications include `event` and `channel`.

Relevant existing host commands include `open_project`, `project_current`,
`doc_open`, `doc_close`, `doc_snapshot`, `doc_outline`,
`lsp_references`, `lsp_workspace_symbols`, `search_start`, and
`vcs_status`. The authoritative list is the runtime `commands` response,
not a copied list in Java.

Coordinates passed to the Rust LSP host use IDE coordinates: zero-based line
and UTF-16 column. The Java adapter must convert CIFR's one-based
`SourceRange` values at the boundary. Workspace/window identity must be
stable for a single host session; never use a magic `default-workspace` ID.

The `cifr-ide-client` module implements the protocol-v2 stdio transport. It
starts the host process directly (without a shell), negotiates the protocol,
frames UTF-8 JSON by byte length, and exposes command invocation. It should not
speak LSP directly, depend on MCP-specific semantics, or create an independent
document/index cache. A later MCP adapter may expose selected IDE operations
to external agents, but MCP is an adapter, not the internal domain model.

## 3. Existing CIFR capabilities to preserve

The source inventory shows substantial functionality outside the IDE:

- `cifr-core`: agent contracts, capability and authorization types, context
  budgeting, execution and lifecycle state, memory, patch/mutation contracts,
  workspace fingerprints and repository graph types.
- `cifr-engine`: Andalus runtime integration, coding loop and strategies,
  planning, retrieval/reranking, tool orchestration, model routing, memory,
  recovery, patch validation, mutation gate and closed-loop verification.
- `cifr-tools`: tool SPI and built-ins for filesystem, search, Git, editor,
  process, format, index and harness operations.
- `cifr-indexing`: file/symbol/reference indexes, Lucene search, dependency
  and relation indexes, evidence graph/packet, impact analysis, summaries and
  test discovery.
- `cifr-security`: command guardrails, secret scrubbing and audit ledger.
- `cifr-api`: REST/gRPC surfaces, sessions, projects, memory, skills and MCP
  management.
- `cifr-skills` and `cifr-toolchains`: coding skills and build/toolchain
  adapters.

These modules are not to be deleted or replaced wholesale by CIFR IDE.
Instead, split primitive IDE facts from coding-specific reasoning and retain
the latter in CIFR.

## 4. Confirmed integration defects to address

The following are source-level findings and migration updates from the current integration branch:

1. The indexing module's Muqabalah dependency is removed after moving its
   remaining reference lookup to the CIFR IDE host. The engine-level Muqabalah
   MCP endpoint/configuration is still a separate migration concern.
2. `LspSymbolResolver` now uses the CIFR IDE workspace index. Reference
   resolution has been migrated to `doc_open` + `lsp_references`, using the
   actual indexed symbol range and preserving transport errors.
3. `DefaultReferenceIndex` now resolves the requested `SymbolInfo`, finds
   its file record in the owning workspace, and delegates to the IDE resolver.
4. `DefaultFileIndex` now keys relative file paths by workspace ID,
   `DefaultSymbolIndex` supports workspace-scoped symbol lookup, and
   `DefaultWorkspaceIndexer` carries the workspace ID into extracted symbols.
5. `CifrToolAdapter` creates a synthetic `workspace-default` ID and uses
   `WorkspaceResolver`'s static ThreadLocal to pass workspace state around a
   Mutiny execution. ThreadLocal state is not a safe async context propagation
   mechanism.
6. `CifrMcpAdapter` contains a Muqabalah-specific environment/property and
   falls back to `http://localhost:8080/mcp/sse`. This coupling should be
   removed only when the replacement CIFR IDE adapter is available.
7. `CifrHarnessBridge` previously configured an allow-all capability scope,
   no policy chain and no approval gate. The follow-up hardening branch changes
   the bridge to an exact, operator-configured tool allowlist (deny by default)
   via `cifr.harness.allowed-tools` or `CIFR_HARNESS_ALLOWED_TOOLS`. This is
   an execution opt-in, not a complete enterprise policy or human-approval
   system: the upstream Andalus executor currently ignores
   `REQUIRE_APPROVAL` capability decisions, and action metadata is currently
   created as read-only. Do not expose high-risk tools through this bridge
   until those upstream governance semantics are fixed and tested.
8. `DefaultExecutionSandbox` starts a local `ProcessBuilder` directly and
   waits for process completion before draining stdout/stderr. It does not
   demonstrate OS/container isolation in that class and can block if child
   output fills a pipe. This needs a separate execution/sandbox hardening
   pass; do not conflate it with the IDE process host.

## 5. Migration sequence

### Phase A — make the headless IDE host buildable (implemented; verification pending)

- [x] Keep the Rust crates under `cifr-ide/crates/*`.
- [x] Remove the absent `src-tauri` directory from workspace membership; do not
  create an empty Tauri shell.
- [x] Provide the declared `cifridea-server --stdio` entry point.
- [x] Keep logs on stderr and preserve JSON-RPC framing on stdout.
- Add smoke tests for initialization, command discovery, workspace open and
  document outline when a Rust build environment is available.

### Phase B — stable Java client and workspace identity (partially complete)

- [x] Add a standalone `cifr-ide-client` Java module using protocol version 2.
- [x] Configure the executable path through `CIFR_IDE_SERVER_COMMAND`,
  with a clear startup error if unavailable.
- [x] Keep one managed IDE host session per workspace root, with deterministic
  window identity and explicit lifecycle/close behavior.
- [x] Negotiate protocol version and query the host command catalogue.
- [x] Propagate request timeouts and server errors; do not translate IDE
  transport failures into empty reference results.
- Keep the client independent of CDI/Quarkus; add a CDI lifecycle adapter in
  the consuming module.

### Phase C — retire Muqabalah and finish IDE integration

- [x] Move symbol resolution to the CIFR IDE host's workspace symbol index.
- [x] Resolve reference requests to a real `SymbolInfo` definition range before
  invoking `lsp_references`.
- [x] Carry workspace identity through file indexing, symbol indexing, and
  reference queries. Tool execution still needs explicit async context propagation.
- [x] Remove the Muqabalah Maven dependency from `cifr-indexing` after moving
  its LSP reference resolver. Keep the engine-level dependency until its
  remaining runtime/configuration consumers are audited.
- Remove the Muqabalah default MCP endpoint after the Rust IDE or a deliberate
  external MCP server adapter replaces it.

### Phase D — unify repository intelligence without losing CIFR semantics

- Let CIFR IDE provide file/symbol facts, document state, LSP results and
  interactive search.
- Keep CIFR's evidence graph, task context packet, ranking, impact reasoning,
  dependency reasoning and test-selection policy.
- Define invalidation/version semantics so a patch applied by CIFR cannot
  leave the IDE and CIFR indexes disagreeing.
- Route IDE build/run/debug operations through CIFR's authorization and
  sandbox policy before an agent can invoke them.

## 6. Build and repository baseline

The inspected tree contains 34 Rust crate directories, 133 Rust test-related
files/paths, and the Java/Maven modules listed above. The Rust workspace uses
edition 2024 and declares Rust 1.96. The repository's Maven parent points to a
relative parent POM outside this checkout
(`../../../../../Families/Andalus/Community/andalus/pom.xml`), so Maven
validation depends on the surrounding Andalus monorepo layout.

The repository tree currently has no GitHub Actions workflow files. It also
contains tracked `.aljabr/data/postgres` runtime database files and
`cifr-engine/.aljabr/audit/audit_trail.jsonl`; this PR deliberately does not
delete or rewrite those data files. Review whether they belong in source
control separately.

No Cargo or Maven build/test has been run as part of this GitHub API-based
change. CI/build results must be reported from an actual runner, not inferred
from static inspection.


## 7. Cifr as an Andalus domain extension (optional integration)

Cifr is a complete coding-agent product and domain execution system. Andalus is the neutral agent runtime/orchestration foundation. Cifr may integrate with Andalus, but it must remain independently usable and must not transfer ownership of its coding workflow or state to Andalus.

### Integration surfaces

Cifr can expose one or more surfaces depending on the host application:

1. **Tool extension:** expose selected, narrowly scoped coding capabilities (for example repository search, symbol lookup or a validated patch operation) through Cifr-owned adapters.
2. **Specialist coding agent:** register/invoke Cifr as a domain agent for a bounded engineering task. Cifr owns its internal plan/act/verify loop and returns progress, events, artifacts and outcome.
3. **Complete coding application:** Cifr UI/API/IDE remains a first-class product. Andalus may coordinate with it, but does not replace its user experience or canonical workspace/task state.

These surfaces can coexist. A tool call is not a substitute for the specialist agent or the full Cifr application.

### Deployment options

Use the same Cifr domain contracts across deployment modes:

| Mode | Integration | Appropriate use |
|---|---|---|
| Embedded | In-process Java API/SPI adapter | Desktop, local and self-hosted deployments that want a single process and low overhead |
| Local sidecar | Separate local process using IPC, stdio or local gRPC/HTTP | IDE/Rust host isolation, independent lifecycle or native dependencies |
| Remote service | Versioned HTTP/gRPC contract with streaming and cancellation | Shared/team/enterprise deployments and independently scaled coding workers |
| Interoperability adapter | MCP and/or A2A at the boundary | Connecting external tool clients or agent ecosystems; not the internal Cifr domain model |

The deployment mode is a host decision, not a change to Cifr's domain model. Do not require a distributed control plane, message broker or Kubernetes deployment for local/community usage.

### Delegation contract and ownership

A delegated request should carry trusted principal/tenant context, task and workspace references, correlation ID, capability allowlist, resource budget, deadline and cancellation semantics. Cifr validates that context and remains authoritative for repository permissions, file mutations, process execution, secret access, approvals and verification.

Andalus owns generic delegation/task orchestration and cross-agent coordination. Cifr owns coding task state, repository/workspace state, patch history and verification evidence. Andalus may retain references and summarized outcomes, but must not become the canonical store for Cifr artifacts.

Cifr should emit structured lifecycle/progress events and return stable artifact references and a typed terminal outcome. Remote adapters should define idempotency, retry, timeout, cancellation, event ordering and recovery behavior. Do not expose internal IDE JSON-RPC or MCP wire types as Cifr's public domain API.

### Compatibility and security gates

- Keep the Andalus dependency optional and behind a Cifr-owned adapter.
- Validate JDK, dependency/BOM, API-version and runtime-lifecycle compatibility before enabling embedded integration.
- Authenticate the remote caller and derive tenant/actor context from trusted server-side identity.
- Enforce least-privilege capabilities and deny by default; a tool descriptor is not authorization.
- Apply Cifr domain policy before every sensitive operation, regardless of whether invocation originated locally, from Andalus, MCP or A2A.
- Bound CPU, memory, wall time, process count, output size and workspace access for coding workers.
- Treat code, repository content, tool output and agent-produced plans as untrusted data.

### Delivery sequence

1. Stabilize Cifr's standalone coding loop, Cifr IDE integration and domain contracts.
2. Define a minimal versioned Cifr domain-agent/tool contract and typed execution context.
3. Implement an embedded adapter without making Andalus mandatory.
4. Add a local sidecar adapter where process isolation or native runtime needs justify it.
5. Add remote gRPC/HTTP execution, streaming, cancellation, idempotency and recovery.
6. Add MCP/A2A interoperability adapters as needed.
7. Add multi-tenant worker pools and enterprise isolation only when operational demand justifies them.

Do not mark any mode implemented until its real lifecycle, security boundary and failure behavior are tested.
