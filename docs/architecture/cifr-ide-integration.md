# CIFR and CIFR IDE integration

**Status:** protocol-v2 Java client and workspace-scoped symbol/reference migration are now implemented on the integration branch; full runtime lifecycle and tool-context propagation remain follow-up work.

## 1. Target ownership

| Responsibility | Owner | Notes |
|---|---|---|
| Agent runtime and model/provider routing | CIFR / Andalus | Keep CIFR usable as a coding capability in the general Andalus runtime. |
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

The following are source-level findings from the current `main` tree:

1. The indexing module's Muqabalah dependency is removed after moving its
   remaining reference lookup to the CIFR IDE host. The engine-level Muqabalah
   MCP endpoint/configuration is still a separate migration concern.
2. `LspSymbolResolver` now uses the CIFR IDE workspace index. Reference
   resolution has been migrated to `doc_open` + `lsp_references`, using the
   actual indexed symbol range and preserving transport errors.
3. `DefaultReferenceIndex` now resolves the requested `SymbolInfo`, finds
   its file record in the owning workspace, and delegates to the IDE resolver.
4. `DefaultFileIndex` now keys relative file paths by workspace ID, and
   `DefaultSymbolIndex` supports workspace-scoped symbol lookup. The workspace
   indexer must pass the workspace ID when registering extracted symbols.
5. `CifrToolAdapter` creates a synthetic `workspace-default` ID and uses
   `WorkspaceResolver`'s static ThreadLocal to pass workspace state around a
   Mutiny execution. ThreadLocal state is not a safe async context propagation
   mechanism.
6. `CifrMcpAdapter` contains a Muqabalah-specific environment/property and
   falls back to `http://localhost:8080/mcp/sse`. This coupling should be
   removed only when the replacement CIFR IDE adapter is available.
7. `CifrHarnessBridge` currently configures an allow-all capability scope,
   no policy chain and no approval gate. The comments describe future
   tightening; the bridge must not be treated as enforcing a complete
   enterprise policy until that is implemented.
8. `DefaultExecutionSandbox` starts a local `ProcessBuilder` directly and
   waits for process completion before draining stdout/stderr. It does not
   demonstrate OS/container isolation in that class and can block if child
   output fills a pipe. This needs a separate execution/sandbox hardening
   pass; do not conflate it with the IDE process host.

## 5. Migration sequence

### Phase A — make the headless IDE host buildable

- Keep the Rust crates under `cifr-ide/crates/*`.
- Remove the absent `src-tauri` directory from workspace membership; do not
  create an empty Tauri shell.
- Provide the declared `cifridea-server --stdio` entry point.
- Keep logs on stderr and preserve JSON-RPC framing on stdout.
- Add smoke tests for initialization, command discovery, workspace open and
  document outline when a Rust build environment is available.

### Phase B — stable Java client and workspace identity (partially complete)

- [x] Add a standalone `cifr-ide-client` Java module using protocol version 2.
- Configure the executable path explicitly (system property/environment),
  with a clear startup error if unavailable.
- [x] Keep one managed IDE host session per workspace root, with deterministic
  window identity and explicit lifecycle/close behavior.
- Negotiate protocol version and check required commands before use.
- Propagate timeouts, cancellation and structured server errors. Never
  recover infrastructure errors as empty result lists.
- Keep the client independent of CDI/Quarkus; add a CDI lifecycle adapter in
  the consuming module.

### Phase C — replace the Muqabalah integration safely

- Migrate symbol resolution to `doc_open` + `doc_outline`.
- Correct CIFR's symbol cache and make reference requests resolve a real
  `SymbolInfo` location before invoking `lsp_references`.
- Carry workspace root and workspace/session identity explicitly through
  indexing and tool execution; do not infer it from a process-global setting.
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
