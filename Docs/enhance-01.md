Summary

Aljabr should be a focused coding runtime built on Wayang's primitives. The repository already contains substantial implementations closing the gap between design and code. Below is a concise status and a short next-step checklist.

Implemented (core items)

- Patch/mutation pipeline: Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/patch/* (PatchOperation.java, PatchEdit.java, PatchValidator.java, PatchApplier.java)
- Workspace tool: Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/tool/WorkspacePatchTool.java
- Repository change model: Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/repo/ (ChangeRecord.java, ChangeSet.java, GitRepositoryService)
- Coding loop primitives: Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/loop/ (CodingTask.java, CodingLoop.java, DefaultCodingLoop.java)

Next steps (short)

1. Add WorkspaceGuard for path-granular conflict checks.
2. Make mutation idempotent via mutation/request id tracking in the repo service.
3. Extend PatchTool result to include before/after hashes for stronger evidence.

### My architectural view

I would make the relationship:

```text
                         WAYANG
              ┌───────────────────────────┐
              │ Agent Model                │
              │ AgentDefinition            │
              │ Runtime / Execution       │
              │ Memory                     │
              │ Skills                     │
              │ Tools / MCP                │
              │ RAG / Context              │
              │ Harness / Policies        │
              │ Sessions / Projects       │
              │ Workflow / Gamelan        │
              └─────────────┬─────────────┘
                            │
                     specialized runtime
                            │
                     ┌──────▼──────┐
                     │   ALJABR     │
                     │ Coding Agent │
                     └──────┬──────┘
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
   Repository          Code Intelligence      Execution
   understanding       Muqabala/LSP          sandbox
   semantic search     symbols                terminal
   file graph          refs/defs              tests
   change tracking     diagnostics             builds
        │                   │                    │
        └───────────────────┼────────────────────┘
                            ▼
                       Coding loop
```

That is much cleaner than making Aljabr independently implement memory, orchestration, tools, sessions, etc.

---

# What is already good

You already have several important pieces.

**1. Aljabr has become a real Wayang runtime**

`AljabrWayangRuntime` explicitly declares that it supports agents labelled `type=coder`, and delegates execution to `CoderOrchestrator`. 

That's the correct direction.

I would actually strengthen this concept:

```text
Wayang Runtime
    ├── Default assistant runtime
    ├── Research runtime
    ├── Workflow runtime
    └── Aljabr coding runtime
```

rather than:

```text
Wayang
   └── random coding features
```

---

**2. Workspace tools are already separated**

You have:

* `semantic_search`
* `read_file`
* `list_files`
* `search_files`

through `AljabrWorkspaceTools`. 

That's a good base because Aljabr needs a **repository perception layer**, not just a shell.

---

**3. Muqabala integration is the right move**

This is probably one of the most important architectural decisions.

The current prompt explicitly establishes Muqabala as the IDE/LSP boundary and exposes things such as definition, references, document symbols and workspace symbols through Aljabr skills.

So:

```text
RAG
  = "what text looks relevant?"

Muqabala
  = "what does this code actually mean?"

Git
  = "what changed?"

Build/Test
  = "does it work?"
```

That combination is much stronger than relying on semantic search alone.

---

# The biggest thing I would change

Right now Aljabr still looks somewhat like:

```text
Prompt
  ↓
CoderOrchestrator
  ↓
LLM
  ↓
Tools
```

I would evolve it into:

```text
User Intent
     ↓
Task Classifier
     ↓
Coding Task
     ↓
Repository Context
     ↓
Plan
     ↓
Change
     ↓
Verify
     ↓
Repair
     ↓
Review
     ↓
Result
```

In other words:

## Aljabr should be a **coding control loop**, not merely a coding prompt.

The prompt already says things like inspect first, make minimal reversible edits, test changes, and provide verification.

But those rules currently live largely in the prompt.

That's the next architectural weakness.

**Critical coding behavior should move from prompt → runtime policy.**

---

# 1. Create `CodingTask`

This should become the central Aljabr concept.

Something like:

```java
public record CodingTask(
    String id,
    String sessionId,
    Path workspace,
    String request,
    CodingIntent intent,
    CodingScope scope,
    CodingPolicy policy
) {}
```

Where:

```java
enum CodingIntent {
    EXPLAIN,
    EXPLORE,
    IMPLEMENT,
    FIX,
    REFACTOR,
    TEST,
    REVIEW,
    DEBUG,
    MIGRATE
}
```

Then Aljabr knows:

> "This isn't just a prompt. This is a FIX task."

That enables completely different behavior.

For example:

```text
EXPLAIN
  inspect → analyze → answer

IMPLEMENT
  inspect → plan → edit → test → review

BUGFIX
  reproduce → inspect → diagnose → patch → regression test

REFACTOR
  analyze → impact → plan → change → compile → test

REVIEW
  inspect → static analysis → git diff → findings
```

This is much more powerful than asking the LLM to infer the entire workflow every time.

---

# 2. Build a real `CodingLoop`

This is the heart of Aljabr.

I would introduce something conceptually like:

```java
public interface CodingLoop {

    Uni<CodingResult> execute(CodingTask task);

}
```

Internally:

```text
CodingLoop
    │
    ├── Understand
    ├── Plan
    ├── Execute
    ├── Verify
    ├── Repair
    └── Finalize
```

And the state:

```java
public record CodingState(
    CodingTask task,
    RepositorySnapshot repository,
    CodingPlan plan,
    List<Change> changes,
    VerificationState verification,
    int iteration
) {}
```

This is where Wayang becomes extremely useful.

Wayang provides the general execution infrastructure.

Aljabr provides the **coding state machine**.

---

# 3. RepositorySnapshot is extremely important

This is one feature I'd prioritize.

Before the agent starts making changes:

```text
RepositorySnapshot
├── root
├── branch
├── commit
├── dirty state
├── changed files
├── project type
├── languages
├── build system
├── test framework
├── dependency manifest
└── relevant modules
```

For example:

```java
public record RepositorySnapshot(
    String commit,
    String branch,
    boolean dirty,
    List<String> changedFiles,
    List<ProjectModule> modules,
    List<Language> languages
) {}
```

Then Aljabr can reason:

> I started at commit X with three existing user modifications.

And later:

> I changed only A.java and ATest.java.

This becomes **much safer**.

---

# 4. Add a proper ChangeSet

This is another major improvement.

Don't let the LLM think of edits as arbitrary tool calls.

Represent them.

```text
ChangeSet
 ├── Change #1
 │    ├── file
 │    ├── original hash
 │    ├── new hash
 │    └── patch
 │
 ├── Change #2
 └── Change #3
```

Then:

```java
public record Change(
    Path file,
    String beforeHash,
    String afterHash,
    String patch
) {}
```

Now Aljabr can implement:

```text
preview
apply
rollback
diff
approve
commit
```

without asking the model to manage this itself.

This is especially important because your GUI already has concepts for approvals, plans, tool calls, statuses, retries and jobs. 

---

# 5. Separate "tool execution" from "permission"

This should become a Wayang-level capability eventually.

For Aljabr:

```text
Tool
 │
 ▼
Policy Engine
 │
 ├── allowed
 ├── requires approval
 ├── denied
 └── sandboxed
```

Example:

| Operation       | Default           |
| --------------- | ----------------- |
| read file       | allow             |
| semantic search | allow             |
| LSP query       | allow             |
| git diff        | allow             |
| compile         | allow             |
| unit test       | allow             |
| modify source   | policy-dependent  |
| delete file     | approval          |
| git reset       | approval          |
| git commit      | approval          |
| git push        | explicit approval |
| network         | policy-dependent  |

Your GUI already has `ApprovalRequest` and suspended session states, so this fits the existing architecture rather than requiring a new UX concept. 

---

# 6. Make verification a first-class subsystem

This is where Aljabr can become substantially better than a generic coding agent.

Instead of:

```text
LLM: "I think the code works."
```

make:

```text
VerificationEngine

    compile
       ↓
    static analysis
       ↓
    unit tests
       ↓
    integration tests
       ↓
    diagnostics
       ↓
    diff review
```

Something like:

```java
interface VerificationEngine {

    Uni<VerificationReport> verify(
        CodingTask task,
        ChangeSet changes
    );
}
```

And:

```java
record VerificationReport(
    boolean passed,
    List<CheckResult> checks,
    List<Diagnostic> diagnostics
) {}
```

This lets the agent enter:

```text
IMPLEMENT
   ↓
PATCH
   ↓
VERIFY
   ↓
FAIL
   ↓
DIAGNOSE
   ↓
REPAIR
   ↓
VERIFY
```

rather than simply stopping after writing code.

---

# 7. Add a RepairLoop

This should probably be one of Aljabr's signature capabilities.

```text
                    ┌──────────────┐
                    │   implement  │
                    └──────┬───────┘
                           ↓
                       verify
                           ↓
                     ┌─────┴─────┐
                     │           │
                   pass        fail
                     │           │
                     ↓           ↓
                  review      diagnose
                                 ↓
                              repair
                                 ↓
                              verify
```

With a hard budget:

```java
maxRepairIterations = 3;
```

And each iteration records:

```text
attempt #1
failure
diagnosis
change
verification

attempt #2
failure
diagnosis
change
verification

attempt #3
success
```

That state should be persisted.

This connects directly with the Wayang execution/checkpoint direction we've discussed previously.

---

# 8. Your memory approach needs specialization

I would **not** let Aljabr indiscriminately store coding information into generic long-term memory.

Your current prompt says memory should be queried at the beginning of every session and important project facts should be stored. 

That's useful, but coding agents need multiple memory layers.

I would use:

```text
Wayang Memory
│
├── User memory
│
├── Project memory
│
├── Repository memory
│
├── Session memory
│
└── Task memory
```

For example:

### Project memory

```text
"Wayang uses Maven."
"Muqabala owns LSP."
"Never put LSP implementation inside Aljabr."
```

### Repository memory

```text
"API module uses REST."
"Engine uses CDI."
"Tests are under ..."
```

### Session memory

```text
"We discovered CoderOrchestrator does X."
```

### Task memory

```text
"Attempt #2 failed because ..."
```

The last one should probably be checkpoint state rather than long-term memory.

---

# 9. Research/cache should also be part of Aljabr

This is particularly important given our previous Wayang discussion.

If Aljabr does:

```text
search web
 ↓
read documentation
 ↓
summarize
 ↓
inspect source
```

don't just throw that context into the LLM and lose it.

Create:

```text
ResearchArtifact
```

such as:

```java
record ResearchArtifact(
    String id,
    String query,
    String source,
    String contentHash,
    String summary,
    Instant createdAt
) {}
```

Then the task can reference:

```text
TaskContext
 ├── repository observations
 ├── research artifacts
 ├── diagnostics
 ├── plans
 └── changes
```

This gives you exactly the resilience you were talking about earlier: if the connection/model dies, Aljabr resumes instead of rediscovering everything.

---

# 10. Don't overbuild `AljabrPromptComposer`

This is one place where I would **reduce**, not add.

The current prompt is already becoming quite large. It contains memory rules, internal tools, workspace rules, LSP rules, coding behavior, response rules, etc.

Eventually I'd split it:

```text
AljabrPolicy
AljabrToolPolicy
AljabrMemoryPolicy
AljabrEditPolicy
AljabrVerificationPolicy
AljabrResponsePolicy
```

Then compose the model prompt from the current runtime state.

For example:

```text
System
+
Coding identity
+
Current task
+
Relevant repository context
+
Current plan
+
Current verification failure
+
Applicable policy
```

rather than one enormous permanent system prompt.

---

# 11. MCP should be capability discovery, not a special Aljabr subsystem

Your `AljabrMcpAdapter` is already discovering workspace MCP configuration and Muqabala. 

I'd move toward:

```text
Wayang Tool Registry
       │
       ├── built-in tools
       ├── skills
       ├── MCP
       ├── RAG
       └── runtime tools
             │
             ▼
       Aljabr capability selection
```

Aljabr shouldn't care whether:

```text
git_diff
```

came from:

* Wayang built-in
* MCP
* native implementation
* external plugin

It should care that:

```text
Capability.GIT_DIFF
```

exists.

That's a much more extensible architecture.

---

# 12. Your existing skills should become capability providers

The current LSP skills are a good example:

```text
LspQuerySkill
LspDocumentSymbolSkill
LspWorkspaceSymbolSkill
LspHoverSkill
...
```

But eventually I'd organize them conceptually as:

```text
Code Intelligence
├── symbol_search
├── definition
├── references
├── hover
├── diagnostics
├── completion
├── rename
├── code_actions
└── formatting
```

Then Aljabr can select capabilities according to task.

---

# 13. Add Git intelligence

I think this is currently one of the biggest missing coding-agent primitives.

At minimum:

```text
git_status
git_diff
git_diff_file
git_log
git_show
git_blame
git_branch
git_changed_files
```

And eventually:

```text
create_checkpoint
restore_checkpoint
create_patch
apply_patch
```

A coding agent without strong Git awareness is fundamentally less safe.

---

# 14. Add project intelligence

Instead of asking the model to discover everything manually:

```text
ProjectDetector
```

could detect:

```text
Java
 ├── Maven
 ├── Gradle

Dart
 ├── Flutter
 └── pub

Node
 ├── npm
 ├── pnpm
 └── yarn

Python
 ├── uv
 ├── poetry
 └── pip
```

Then:

```text
BuildStrategy
TestStrategy
LintStrategy
RunStrategy
```

can be selected automatically.

So Aljabr understands:

```text
"test this"

→ Maven project
→ ./mvnw test

rather than

→ model guessing command
```

---

# 15. Sandbox execution should become a Wayang primitive

This is very important.

I wouldn't make Aljabr own all process execution.

Instead:

```text
Wayang Execution Sandbox
```

with:

```text
workspace
filesystem
network
process
environment
timeout
CPU
memory
permissions
```

Then Aljabr requests:

```java
sandbox.execute(command, policy)
```

instead of:

```java
Runtime.exec(...)
```

This allows the same mechanism to be used by:

```text
Aljabr
Research Agent
Browser Agent
Data Agent
Workflow Agent
```

---

# 16. GUI: you're already building the right event model

The GUI has a useful event-oriented model:

```text
TEXT
TOOL_CALL
TOOL_RESULT
DONE
```

and chat entries already represent:

```text
pending
processing
suspended
completed
failed
cancelled
```



I would take that much further and make **Wayang's agent event stream the canonical protocol**.

Something like:

```text
RunStarted
PlanCreated
ContextDiscovered
ToolRequested
ApprovalRequested
ToolStarted
ToolCompleted
FileChanged
VerificationStarted
DiagnosticFound
RepairStarted
CheckpointCreated
RunCompleted
RunFailed
```

Then the GUI doesn't need to understand Aljabr internals.

It simply renders Wayang events.

That is a major architectural win.

---

# 17. Your current API is too thin

Currently the API essentially exposes:

```text
POST /api/v1/coder/run
```

which directly delegates to `CoderOrchestrator`. 

For a real coding agent I'd eventually want:

```text
POST   /projects
GET    /projects

POST   /projects/{id}/sessions
GET    /projects/{id}/sessions

POST   /sessions/{id}/runs
GET    /runs/{id}
POST   /runs/{id}/cancel
POST   /runs/{id}/pause
POST   /runs/{id}/resume

GET    /runs/{id}/events
GET    /runs/{id}/changes
GET    /runs/{id}/diff

POST   /runs/{id}/approvals/{approvalId}

POST   /runs/{id}/checkpoints
POST   /runs/{id}/rollback
```

But I'd actually prefer these to become **Wayang execution APIs**, with Aljabr merely providing the coder runtime.

---

# 18. The most important architectural distinction

I'd define three layers:

### Wayang

**Generic agent operating system**

```text
Agent
Execution
Memory
Tools
Skills
MCP
Context
Policies
Events
Sessions
Projects
Checkpoints
```

### Aljabr

**Coding intelligence**

```text
Repository
Code intelligence
Coding task
Planning
Editing
Testing
Repair
Git
Build
Diagnostics
```

### Muqabala

**Language/IDE intelligence**

```text
LSP
AST
symbols
definitions
references
diagnostics
completion
refactoring
```

And then:

```text
                 Wayang
                    │
              ┌─────┴─────┐
              │   Aljabr  │
              │           │
              └─────┬─────┘
                    │
               Muqabala
```

That boundary is excellent.

---

# What I would NOT do

I would avoid turning Aljabr into another giant framework.

Specifically, don't duplicate:

* memory engine
* session engine
* generic tool registry
* generic MCP runtime
* generic workflow engine
* generic execution engine
* generic agent definition
* generic model provider abstraction

Those belong in Wayang.

Aljabr should be opinionated and comparatively small.

---

# My priority order

If I were continuing the implementation from today's state, I'd do it in this order:

### Phase 1 — Make Aljabr a real coding runtime

```text
CodingTask
CodingState
CodingLoop
RepositorySnapshot
ChangeSet
VerificationReport
```

This is the biggest architectural improvement.

### Phase 2 — Safe execution

```text
Git tools
Sandbox
Approval policy
Checkpoint/rollback
```

### Phase 3 — Coding intelligence

```text
Muqabala capability registry
Project detector
Build/test strategy
Diagnostics → repair
```

### Phase 4 — Resilient execution

```text
checkpoint
event journal
research artifacts
task cache
resume after disconnect
resume after model failure
```

### Phase 5 — GUI protocol

Make the GUI a pure consumer of Wayang execution events instead of having Aljabr-specific assumptions.

### Phase 6 — Prompt reduction

Move behavioral guarantees out of `AljabrPromptComposer` and into actual runtime policies.

---

# The end state I'd aim for

The beautiful version of Aljabr is actually quite simple from the outside:

```text
User:
"Fix the authentication bug."

                ↓

              Wayang
        execution + memory +
        tools + policy + events

                ↓

             Aljabr
       ┌─────────────────┐
       │ classify task   │
       │ inspect repo    │
       │ understand code │
       │ plan            │
       │ modify          │
       │ verify          │
       │ repair          │
       │ review          │
       └─────────────────┘

          ↓           ↓
     Muqabala     Sandbox
       code          build
     intelligence    tests

                ↓

          ChangeSet
          + evidence
          + verification
          + diff

                ↓

             User
```

**That would make Aljabr genuinely different from "Wayang + a coding prompt."**

And I think your current code is at the point where **the next move should be architectural consolidation rather than adding more individual skills**. You already have workspace/RAG tools, MCP, skills, Muqabala/LSP integration, memory policy, runtime registration, API exposure, and a GUI event model.

The missing center is essentially:

> **`CodingTask → CodingState → ChangeSet → Verification → Repair → Checkpoint`**

I'd make that the **Aljabr core**, while aggressively pushing generic infrastructure back down into Wayang. That would also align very well with the Wayang architecture we've been shaping.



Yes. Now that I can see the current Wayang runtime pieces alongside the current Aljabr implementation, I would make this **much more concrete**.

The important realization is that **you don't need to invent the execution infrastructure**. Wayang already has several of the primitives Aljabr needs: `AgentStrategy`, policy-aware `AgentToolExecutor`, `CheckpointStore`, ReAct execution, context compilation/planning, and listener/event infrastructure.

So I would implement Aljabr as a **thin coding state machine over those primitives**.

---

# 1. First: reshape the Aljabr package

I would move toward this:

```text
aljabr-engine/
└── src/main/java/tech/kayys/aljabr/
    │
    ├── api/
    │
    ├── runtime/
    │   ├── AljabrWayangRuntime.java
    │   └── AljabrRuntimeConfig.java
    │
    ├── task/
    │   ├── CodingTask.java
    │   ├── CodingIntent.java
    │   ├── CodingScope.java
    │   └── CodingPolicy.java
    │
    ├── state/
    │   ├── CodingState.java
    │   ├── CodingPhase.java
    │   └── CodingStateStore.java
    │
    ├── repository/
    │   ├── RepositorySnapshot.java
    │   ├── RepositoryInspector.java
    │   ├── ProjectDetector.java
    │   └── ChangeSet.java
    │
    ├── planning/
    │   ├── CodingPlanner.java
    │   ├── CodingPlan.java
    │   └── PlanStep.java
    │
    ├── execution/
    │   ├── CodingLoop.java
    │   ├── CodingExecutor.java
    │   └── RepairLoop.java
    │
    ├── verification/
    │   ├── VerificationEngine.java
    │   ├── VerificationReport.java
    │   ├── VerificationCheck.java
    │   └── VerificationStrategy.java
    │
    ├── change/
    │   ├── ChangeManager.java
    │   ├── ChangeApplier.java
    │   ├── ChangeRollback.java
    │   └── DiffService.java
    │
    ├── policy/
    │   ├── CodingPolicyEngine.java
    │   └── CodingApprovalService.java
    │
    ├── checkpoint/
    │   └── AljabrCheckpointAdapter.java
    │
    ├── event/
    │   └── AljabrEventPublisher.java
    │
    ├── skill/
    │
    └── engine/
        ├── AljabrWorkspaceTools.java
        ├── AljabrMcpAdapter.java
        └── AljabrSkillAdapter.java
```

Don't implement all of this at once.

The first vertical slice should only be:

```text
task
state
repository snapshot
change set
verification
coding loop
```

---

# 2. `CodingTask` should be the first real class

Right now `AgentRequest` is doing too much of the conceptual work.

Add:

```java
package tech.kayys.aljabr.task;

import java.nio.file.Path;
import java.util.Map;

public record CodingTask(
        String id,
        String sessionId,
        Path workspace,
        String request,
        CodingIntent intent,
        CodingScope scope,
        CodingPolicy policy,
        Map<String, Object> metadata
) {
    public CodingTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }

        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("request is required");
        }

        workspace = workspace.toAbsolutePath().normalize();

        intent = intent == null
                ? CodingIntent.IMPLEMENT
                : intent;

        scope = scope == null
                ? CodingScope.workspace()
                : scope;

        policy = policy == null
                ? CodingPolicy.defaults()
                : policy;

        metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }
}
```

Then:

```java
public enum CodingIntent {
    EXPLORE,
    EXPLAIN,
    IMPLEMENT,
    FIX,
    DEBUG,
    TEST,
    REFACTOR,
    REVIEW,
    MIGRATE
}
```

This is important because later the execution strategy can do:

```java
switch (task.intent()) {
    case EXPLORE -> ...
    case FIX -> ...
    case IMPLEMENT -> ...
    case TEST -> ...
    case REVIEW -> ...
}
```

instead of relying on the model to invent the workflow.

---

# 3. Add a very small `CodingPhase`

```java
public enum CodingPhase {
    CREATED,
    INSPECTING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    REPAIRING,
    REVIEWING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

Then the state:

```java
public record CodingState(
        String executionId,
        CodingTask task,
        CodingPhase phase,
        RepositorySnapshot repository,
        CodingPlan plan,
        ChangeSet changes,
        VerificationReport verification,
        int iteration,
        String lastError
) {

    public CodingState next(CodingPhase phase) {
        return new CodingState(
                executionId,
                task,
                phase,
                repository,
                plan,
                changes,
                verification,
                iteration,
                lastError);
    }
}
```

This is the **center of Aljabr**.

Not the prompt.

---

# 4. Repository snapshot

Start simple.

```java
public record RepositorySnapshot(
        Path workspace,
        String branch,
        String commit,
        boolean dirty,
        List<Path> changedFiles,
        String projectType,
        List<String> languages
) {}
```

Then:

```java
@ApplicationScoped
public class RepositoryInspector {

    public Uni<RepositorySnapshot> inspect(Path workspace) {
        return Uni.createFrom().item(() -> {
            String branch = git(workspace, "branch", "--show-current");
            String commit = git(workspace, "rev-parse", "HEAD");

            String status = git(workspace, "status", "--porcelain");

            List<Path> changed = status.lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> workspace.resolve(
                            line.substring(3).trim()))
                    .toList();

            return new RepositorySnapshot(
                    workspace,
                    branch,
                    commit,
                    !changed.isEmpty(),
                    changed,
                    detectProjectType(workspace),
                    detectLanguages(workspace));
        });
    }

    private String git(Path workspace, String... args) {
        // delegate to Wayang execution/sandbox rather than Runtime.exec()
        return "";
    }
}
```

**Important:** don't actually implement `Runtime.exec()` here. Your current Aljabr already has sandbox execution infrastructure, including selectable `SandboxProvider`s and command execution with timeout. 

So the final design should be:

```text
RepositoryInspector
       ↓
ExecutionService
       ↓
Wayang Sandbox
```

not direct OS process management.

---

# 5. `ChangeSet` is critical

I'd make this immutable.

```java
public record ChangeSet(
        String id,
        List<FileChange> files
) {

    public ChangeSet {
        files = files == null
                ? List.of()
                : List.copyOf(files);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }
}
```

And:

```java
public record FileChange(
        Path path,
        String beforeHash,
        String afterHash,
        ChangeType type,
        String patch
) {}
```

```java
public enum ChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    RENAMED
}
```

Now Aljabr has a real object representing:

> "What did I change?"

instead of depending on chat history.

---

# 6. Add `ChangeManager`

This becomes the safety boundary.

```java
public interface ChangeManager {

    Uni<ChangeSet> preview(CodingTask task);

    Uni<ChangeSet> apply(CodingTask task, ChangeSet changes);

    Uni<Void> rollback(String changeSetId);

    Uni<String> diff(String changeSetId);
}
```

Implementation:

```java
@ApplicationScoped
public class DefaultChangeManager implements ChangeManager {

    @Inject
    GitService git;

    @Override
    public Uni<ChangeSet> preview(CodingTask task) {
        return git.diff(task.workspace());
    }

    @Override
    public Uni<ChangeSet> apply(
            CodingTask task,
            ChangeSet changes) {

        return Uni.createFrom().item(() -> {
            // Validate original hashes first.
            validateBaseState(changes);

            // Apply patch through controlled mechanism.
            applyPatch(changes);

            return changes;
        });
    }
}
```

The **hash validation** matters:

```text
Agent inspected A.java
      ↓
user manually modifies A.java
      ↓
agent tries to apply old patch
      ↓
HASH MISMATCH
      ↓
re-inspect
```

This prevents Aljabr from blindly overwriting user work.

---

# 7. Add `CodingPlan`

Don't make planning just text.

```java
public record CodingPlan(
        String id,
        String objective,
        List<PlanStep> steps
) {}
```

```java
public record PlanStep(
        String id,
        String description,
        StepType type,
        List<Path> files,
        boolean completed
) {}
```

```java
public enum StepType {
    INSPECT,
    MODIFY,
    TEST,
    VERIFY,
    REVIEW
}
```

Example:

```text
Plan
1. Inspect authentication service
2. Inspect token validation
3. Modify TokenValidator.java
4. Add regression test
5. Run authentication tests
6. Review diff
```

Now the GUI can render an actual plan rather than parsing LLM prose.

---

# 8. `VerificationEngine`

This should be one of the first serious abstractions.

```java
public interface VerificationEngine {

    Uni<VerificationReport> verify(
            CodingTask task,
            CodingState state);
}
```

Result:

```java
public record VerificationReport(
        boolean passed,
        List<VerificationCheck> checks
) {

    public boolean hasFailures() {
        return checks.stream()
                .anyMatch(c -> !c.passed());
    }
}
```

```java
public record VerificationCheck(
        String id,
        String name,
        CheckType type,
        boolean passed,
        int exitCode,
        String output,
        List<Diagnostic> diagnostics
) {}
```

```java
public enum CheckType {
    COMPILE,
    UNIT_TEST,
    INTEGRATION_TEST,
    LINT,
    STATIC_ANALYSIS,
    LSP_DIAGNOSTIC,
    CUSTOM
}
```

---

# 9. Make verification strategy project-aware

This is where `ProjectDetector` comes in.

```java
public interface VerificationStrategy {

    boolean supports(ProjectInfo project);

    List<VerificationCommand> commands(
            CodingTask task,
            ChangeSet changes);
}
```

For Maven:

```java
@ApplicationScoped
public class MavenVerificationStrategy
        implements VerificationStrategy {

    @Override
    public boolean supports(ProjectInfo project) {
        return project.hasFile("pom.xml");
    }

    @Override
    public List<VerificationCommand> commands(
            CodingTask task,
            ChangeSet changes) {

        return List.of(
            new VerificationCommand(
                "compile",
                "./mvnw",
                List.of("test-compile")),

            new VerificationCommand(
                "test",
                "./mvnw",
                List.of("test"))
        );
    }
}
```

For Gradle:

```text
./gradlew test
```

For Dart:

```text
flutter analyze
flutter test
```

For Node:

```text
npm test
```

The model shouldn't have to discover this every time.

---

# 10. Now the actual `CodingLoop`

This is the part I'd implement next.

```java
@ApplicationScoped
public class CodingLoop {

    @Inject
    RepositoryInspector repositoryInspector;

    @Inject
    CodingPlanner planner;

    @Inject
    CodingExecutor executor;

    @Inject
    VerificationEngine verifier;

    @Inject
    RepairLoop repairLoop;

    public Uni<CodingResult> execute(CodingTask task) {

        return repositoryInspector
                .inspect(task.workspace())

                .flatMap(repo ->
                    plan(task, repo))

                .flatMap(state ->
                    executePlan(state))

                .flatMap(state ->
                    verify(state))

                .flatMap(this::repairIfNeeded)

                .map(this::complete);
    }
}
```

Conceptually:

```text
inspect
   ↓
plan
   ↓
execute
   ↓
verify
   ↓
repair?
   ├── yes → repair → verify
   └── no
   ↓
review
   ↓
complete
```

---

# 11. But don't make `CodingLoop` own the LLM

This is where Wayang comes in.

Your existing `BaseReActAgent` already supports:

* provider
* memory
* model
* system prompt
* tools
* context compiler
* context planner
* token budget
* policy-aware tool execution
* checkpoint bridge
* listeners. 

So Aljabr's `CodingExecutor` should essentially configure Wayang:

```java
@ApplicationScoped
public class CodingExecutor {

    @Inject
    AgentFactory agentFactory;

    public Uni<CodingState> execute(CodingState state) {

        Agent coder = agentFactory.create(
                "aljabr-coder");

        coder.setTools(...);
        coder.setSystemPrompt(...);

        return coder.execute(...)
                .map(result ->
                    applyResult(state, result));
    }
}
```

The **LLM is the reasoning engine**.

The **CodingLoop is the control engine**.

That distinction is extremely important.

---

# 12. Don't make the model control the phase machine

Avoid this:

```text
LLM:
"I'll inspect the repo."

LLM:
"I'll now plan."

LLM:
"I'll now verify."

LLM:
"Actually let's edit first."
```

Instead:

```text
CodingLoop
   ↓
INSPECTING
   ↓
Wayang agent gets inspection task
   ↓
PLANNING
   ↓
Wayang agent gets planning task
   ↓
EXECUTING
   ↓
Wayang agent gets implementation task
   ↓
VERIFYING
   ↓
VerificationEngine
```

This dramatically improves determinism.

And it fits Wayang's existing `AgentStrategy` abstraction, which already exists specifically to decide the next action from semantic state and execution status. 

---

# 13. Actually use `AgentStrategy` for Aljabr

I'd create:

```java
@ApplicationScoped
public class AljabrStrategy implements AgentStrategy {

    @Override
    public AgentDecision decide(
            AgentContext context,
            ExecutionStatus status) {

        CodingState state = codingState(context);

        return switch (state.phase()) {

            case CREATED ->
                new AgentDecision.StartPhase("INSPECTING");

            case INSPECTING ->
                new AgentDecision.StartPhase("PLANNING");

            case PLANNING ->
                new AgentDecision.StartPhase("EXECUTING");

            case EXECUTING ->
                new AgentDecision.StartPhase("VERIFYING");

            case VERIFYING ->
                decideAfterVerification(state);

            case REPAIRING ->
                new AgentDecision.StartPhase("VERIFYING");

            case REVIEWING ->
                new AgentDecision.Complete();

            default ->
                new AgentDecision.Wait();
        };
    }
}
```

The exact `AgentDecision` constructors depend on your current SPI, but conceptually **this is where Aljabr should plug into Wayang rather than creating its own orchestration protocol**.

---

# 14. Repair loop

Very small implementation first:

```java
@ApplicationScoped
public class RepairLoop {

    private static final int MAX_REPAIRS = 3;

    @Inject
    CodingExecutor executor;

    @Inject
    VerificationEngine verifier;

    public Uni<CodingState> repair(CodingState state) {

        if (state.iteration() >= MAX_REPAIRS) {
            return Uni.createFrom().item(
                    state.next(CodingPhase.FAILED));
        }

        CodingState repairing =
                new CodingState(
                        state.executionId(),
                        state.task(),
                        CodingPhase.REPAIRING,
                        state.repository(),
                        state.plan(),
                        state.changes(),
                        state.verification(),
                        state.iteration() + 1,
                        null);

        return executor.repair(repairing)
                .flatMap(verifier::verify)
                .map(report ->
                    updateVerification(repairing, report));
    }
}
```

Then:

```java
if (report.passed()) {
    return review(state);
}

if (state.iteration() < 3) {
    return repairLoop.repair(state);
}

return fail(state);
```

This gives Aljabr a genuine:

**implement → test → diagnose → repair → test**

loop.

---

# 15. Use Wayang's checkpoint infrastructure instead of inventing another one

This is particularly important.

Wayang already has a `CheckpointStore`, described as a pluggable mechanism for persisting and recovering execution checkpoints. 

And `BaseReActAgent` already has a checkpoint bridge that can save after each step. 

So Aljabr should implement:

```java
@ApplicationScoped
public class AljabrCheckpointAdapter {

    @Inject
    CheckpointStore checkpointStore;

    public void save(CodingState state) {

        checkpointStore.save(
                state.executionId(),
                toAgentContext(state));
    }

    public Optional<CodingState> restore(
            String executionId) {

        return checkpointStore
                .load(executionId)
                .map(this::fromAgentContext);
    }
}
```

Don't create:

```text
AljabrCheckpointDatabase
AljabrCheckpointRepository
AljabrCheckpointManager
```

if Wayang already owns that concern.

---

# 16. Add an event publisher immediately

This will make your GUI dramatically easier.

```java
public sealed interface AljabrEvent
        permits RunStarted,
                PhaseChanged,
                ToolStarted,
                ToolCompleted,
                FileChanged,
                VerificationStarted,
                VerificationCompleted,
                ApprovalRequested,
                CheckpointCreated,
                RunCompleted,
                RunFailed {}
```

Example:

```java
public record PhaseChanged(
        String executionId,
        CodingPhase from,
        CodingPhase to
) implements AljabrEvent {}
```

Then:

```java
eventPublisher.publish(
    new PhaseChanged(
        executionId,
        CodingPhase.EXECUTING,
        CodingPhase.VERIFYING));
```

Your Flutter UI already understands streamed text/tool events and session states, so this gives you a much more reliable source of truth than trying to infer state from messages. 

---

# 17. Approval should happen below the coding loop

Don't do:

```java
if (command.equals("git push")) {
    askUser();
}
```

Instead:

```text
CodingExecutor
     ↓
Wayang AgentToolExecutor
     ↓
CodingPolicy
     ↓
Approval
```

Wayang's `AgentToolExecutor` already exists as a policy-aware execution pipeline, and explicitly supports returning an approval decision. 

So Aljabr defines the policy:

```java
public record CodingPolicy(
        boolean allowFileWrites,
        boolean allowDelete,
        boolean allowNetwork,
        boolean allowGitCommit,
        boolean allowGitPush,
        boolean allowPackageInstall,
        int maxRepairIterations
) {

    public static CodingPolicy defaults() {
        return new CodingPolicy(
                true,
                false,
                false,
                false,
                false,
                false,
                3);
    }
}
```

Then Wayang handles enforcement.

---

# 18. Add explicit coding tools

I'd prioritize these before adding more fancy agents.

### Repository

```text
repository_status
repository_diff
repository_log
repository_changed_files
```

### Files

```text
read_file
write_file
apply_patch
delete_file
```

### Code intelligence

```text
symbol_search
definition
references
hover
diagnostics
rename
code_actions
```

You already have Muqabala-backed skills for definition/references/workspace symbols. 

### Execution

```text
run_command
run_tests
run_build
run_linter
```

### Debugging

```text
dap_launch
dap_terminate
```

You already have a `DapSessionSkill` backed by Muqabala's debug tool. 

---

# 19. Add `ProjectDetector`

Very pragmatic:

```java
public record ProjectInfo(
        Path root,
        ProjectType type,
        List<String> languages,
        BuildSystem buildSystem,
        TestFramework testFramework
) {}
```

Start with:

```java
public enum ProjectType {
    JAVA,
    DART,
    JAVASCRIPT,
    TYPESCRIPT,
    PYTHON,
    RUST,
    GO,
    UNKNOWN
}
```

Detection:

```java
if (Files.exists(root.resolve("pom.xml"))) {
    return ProjectType.JAVA;
}

if (Files.exists(root.resolve("pubspec.yaml"))) {
    return ProjectType.DART;
}

if (Files.exists(root.resolve("package.json"))) {
    return ProjectType.JAVASCRIPT;
}

if (Files.exists(root.resolve("pyproject.toml"))) {
    return ProjectType.PYTHON;
}
```

This tiny thing will eliminate a surprising amount of unnecessary model reasoning.

---

# 20. Context should become structured

This is where your existing Wayang context system becomes valuable.

You already have a `BudgetedContextCompiler` and `ContextPlanner` in the ReAct layer. 

Don't send the whole repository.

Build:

```java
public record CodingContext(
        RepositorySnapshot repository,
        ProjectInfo project,
        List<RelevantFile> files,
        List<Symbol> symbols,
        List<Diagnostic> diagnostics,
        List<ResearchArtifact> research,
        CodingPlan plan,
        ChangeSet changes
) {}
```

Then the context planner decides what enters the model context.

This is where your existing hybrid RAG infrastructure can help: Wayang already combines dense and sparse retrieval and reranking for memory/context retrieval. 

---

# 21. Research cache

I'd add this after the first loop works.

```java
public record ResearchArtifact(
        String id,
        String query,
        String source,
        String contentHash,
        String summary,
        Instant createdAt
) {}
```

And:

```java
public interface ResearchCache {

    Uni<Optional<ResearchArtifact>> find(
            String query,
            String workspace);

    Uni<ResearchArtifact> store(
            ResearchArtifact artifact);
}
```

Then:

```text
Need documentation?
       ↓
ResearchCache
   ├── hit → reuse
   └── miss
          ↓
       search
          ↓
       summarize
          ↓
       cache
```

This is exactly the sort of reusable execution artifact that should survive a disconnected/restarted run rather than being buried in the model's context.

---

# 22. The first actual implementation milestone

I would **not** implement all of the above immediately.

I'd create this first:

```text
CodingTask
    ↓
CodingLoop
    ↓
RepositoryInspector
    ↓
Wayang ReAct Agent
    ↓
ChangeSet
    ↓
VerificationEngine
    ↓
CodingResult
```

With exactly one supported task:

```text
IMPLEMENT
```

For example:

```text
User:
"Add validation to UserService."
```

Execution:

```text
CREATED

  ↓

INSPECTING
  ├── repository_status
  ├── semantic_search
  ├── read_file

  ↓

PLANNING
  └── Wayang ReAct planner

  ↓

EXECUTING
  ├── read relevant files
  ├── apply_patch
  └── record ChangeSet

  ↓

VERIFYING
  ├── compile
  └── test

  ↓

REVIEWING
  └── diff

  ↓

COMPLETED
```

Then add `FIX`:

```text
FIX
 ↓
inspect
 ↓
reproduce
 ↓
diagnose
 ↓
patch
 ↓
test
 ↓
repair × 3
```

Then `REFACTOR`.

Then `REVIEW`.

---

# 23. One important cleanup in your current code

I would specifically start reducing things that duplicate Wayang.

For example, `AljabrWayangRuntime` currently injects `CoderOrchestrator` and simply delegates `executeAsync()` to it. 

Eventually I want that to become closer to:

```java
@ApplicationScoped
public class AljabrWayangRuntime implements WayangRuntime {

    @Inject
    CodingLoop codingLoop;

    @Override
    public CompletableFuture<AgentResponse> executeAsync(
            AgentDefinition agent,
            AgentRequest request) {

        CodingTask task =
                CodingTaskMapper.from(agent, request);

        return codingLoop.execute(task)
                .map(CodingResponseMapper::toAgentResponse)
                .subscribeAsCompletionStage();
    }

    @Override
    public boolean supports(AgentDefinition agent) {
        return agent != null
                && "coder".equals(
                    agent.metadata()
                         .labels()
                         .get("type"));
    }
}
```

That makes the architecture obvious:

```text
Wayang Runtime
      ↓
Aljabr CodingLoop
      ↓
Wayang Agent
      ↓
Tools / Skills / MCP
```

rather than:

```text
Wayang Runtime
      ↓
CoderOrchestrator
      ↓
??? another orchestration layer
```

---

# 24. And I would change `CoderOrchestrator`

Instead of making it the giant brain, make it a compatibility facade:

```java
@ApplicationScoped
public class CoderOrchestrator {

    @Inject
    CodingLoop codingLoop;

    public Uni<AgentResponse> execute(
            AgentRequest request) {

        CodingTask task =
                CodingTaskMapper.from(request);

        return codingLoop.execute(task)
                .map(CodingResponseMapper::toAgentResponse);
    }
}
```

That means existing API callers don't break.

Your current `/api/v1/coder/run` can remain temporarily unchanged. 

But internally:

```text
POST /coder/run
       ↓
CoderOrchestrator
       ↓
CodingLoop
```

---

# 25. Then your API can evolve naturally

Once that works:

```text
POST /api/v1/coder/runs
```

returns:

```json
{
  "runId": "run-123",
  "sessionId": "session-42",
  "status": "RUNNING"
}
```

Then:

```text
GET /api/v1/coder/runs/run-123
```

```json
{
  "runId": "run-123",
  "phase": "VERIFYING",
  "iteration": 1,
  "changes": 3,
  "verification": {
    "passed": false,
    "failedChecks": 1
  }
}
```

And:

```text
GET /api/v1/coder/runs/run-123/events
```

streams:

```text
RUN_STARTED
PHASE_CHANGED
TOOL_STARTED
TOOL_COMPLETED
FILE_CHANGED
VERIFICATION_STARTED
VERIFICATION_FAILED
REPAIR_STARTED
VERIFICATION_PASSED
RUN_COMPLETED
```

That's exactly what your GUI wants.

---

# The implementation sequence I'd actually follow

I'd make the commits roughly:

```text
01  Add CodingTask + CodingIntent
02  Add CodingPhase + CodingState
03  Add RepositoryInspector
04  Add ProjectDetector
05  Add CodingPlan
06  Add ChangeSet
07  Add VerificationEngine
08  Add CodingLoop
09  Connect CodingLoop to Wayang ReAct
10  Add AljabrStrategy
11  Add checkpoint adapter
12  Add Aljabr events
13  Add coding policy
14  Add Git tools
15  Add repair loop
16  Add project-specific verification
17  Refactor CoderOrchestrator into facade
18  Upgrade coder API
19  Connect GUI to event stream
20  Add research/context cache
```

And importantly, **do not wait until #20 to make it useful**.

After roughly **01–09**, you should already have a real Aljabr that can:

> inspect → plan → modify → compile/test → report.

After **10–17**, it becomes a robust coding agent.

After **18–20**, it becomes a proper product/runtime.

---

## The key implementation principle

I'd keep repeating this while coding Aljabr:

```text
Wayang owns:
    "How do agents execute?"

Aljabr owns:
    "How should a coding task progress?"

Muqabala owns:
    "What does the code mean?"

Sandbox owns:
    "Where can code safely execute?"

Git/ChangeSet owns:
    "What did we change?"

Verification owns:
    "Does it actually work?"
```

That separation prevents Aljabr from becoming another 100k-line framework.

And your current Wayang code already gives us a particularly good foundation for this: the policy-aware tool executor, checkpoint bridge, context compiler/planner, and ReAct agent are already there.

**So my next concrete move would be to implement `CodingTask → CodingState → CodingLoop` first, then wire it into your existing `CoderOrchestrator`.** That gives us the architectural spine before adding more tools.


Absolutely. The next step should be **deeper implementation of the Aljabr execution core**, not more architecture.

I’d continue from the `CodingTask → CodingState → CodingLoop` spine and work downward into the actual classes, interfaces, transitions, persistence, and tool contracts.

# Next: implement the Aljabr execution core

The target is:

```text
                    CodingTask
                        │
                        ▼
                 ┌──────────────┐
                 │  CodingLoop  │
                 └──────┬───────┘
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
      Inspect         Plan         Execute
          │             │             │
          └─────────────┼─────────────┘
                        ▼
                    Verify
                        │
                  ┌─────┴─────┐
                  │           │
                PASS         FAIL
                  │           │
                  ▼           ▼
               Review       Repair
                  │           │
                  │           └──────► Verify
                  ▼
               Complete
```

The key is that **each transition becomes persisted state**.

---

# 1. Don't make `CodingState` a giant mutable object

I'd actually improve the previous proposal slightly.

Use an explicit state machine:

```java
public record CodingState(
    String runId,
    CodingTask task,
    CodingPhase phase,

    RepositorySnapshot repository,
    ProjectInfo project,

    CodingPlan plan,
    ChangeSet changes,
    VerificationReport verification,

    int repairAttempts,

    Instant createdAt,
    Instant updatedAt,

    String failureReason
) {
}
```

And keep transitions outside the record:

```java
@ApplicationScoped
public class CodingStateMachine {

    public CodingState inspectStarted(CodingState state) {
        return transition(state, CodingPhase.INSPECTING);
    }

    public CodingState planningStarted(CodingState state) {
        return transition(state, CodingPhase.PLANNING);
    }

    public CodingState executionStarted(CodingState state) {
        return transition(state, CodingPhase.EXECUTING);
    }

    public CodingState verificationStarted(CodingState state) {
        return transition(state, CodingPhase.VERIFYING);
    }

    public CodingState repairStarted(CodingState state) {
        return new CodingState(
            state.runId(),
            state.task(),
            CodingPhase.REPAIRING,
            state.repository(),
            state.project(),
            state.plan(),
            state.changes(),
            state.verification(),
            state.repairAttempts() + 1,
            state.createdAt(),
            Instant.now(),
            null
        );
    }

    private CodingState transition(
            CodingState state,
            CodingPhase target) {

        validateTransition(state.phase(), target);

        return new CodingState(
            state.runId(),
            state.task(),
            target,
            state.repository(),
            state.project(),
            state.plan(),
            state.changes(),
            state.verification(),
            state.repairAttempts(),
            state.createdAt(),
            Instant.now(),
            state.failureReason()
        );
    }
}
```

This gives you a very important invariant:

> **Aljabr can never arbitrarily jump between phases.**

---

# 2. Define valid transitions

Don't rely on `switch` everywhere.

```java
@ApplicationScoped
public class CodingPhaseGraph {

    private static final Map<CodingPhase, Set<CodingPhase>> TRANSITIONS =
        Map.of(
            CREATED, Set.of(INSPECTING),

            INSPECTING, Set.of(
                PLANNING,
                FAILED
            ),

            PLANNING, Set.of(
                EXECUTING,
                FAILED
            ),

            EXECUTING, Set.of(
                VERIFYING,
                WAITING_APPROVAL,
                FAILED
            ),

            WAITING_APPROVAL, Set.of(
                EXECUTING,
                FAILED,
                CANCELLED
            ),

            VERIFYING, Set.of(
                REVIEWING,
                REPAIRING,
                FAILED
            ),

            REPAIRING, Set.of(
                VERIFYING,
                FAILED
            ),

            REVIEWING, Set.of(
                COMPLETED,
                FAILED
            )
        );

    public boolean canTransition(
            CodingPhase from,
            CodingPhase to) {

        return TRANSITIONS
            .getOrDefault(from, Set.of())
            .contains(to);
    }
}
```

This becomes particularly useful when you later resume a crashed run.

---

# 3. Persist every transition

This is where I would leverage Wayang rather than creating a completely independent persistence system.

Create an Aljabr state store abstraction:

```java
public interface CodingStateStore {

    Uni<Void> save(CodingState state);

    Uni<Optional<CodingState>> get(String runId);

    Uni<Void> delete(String runId);
}
```

Implementation can eventually delegate to your existing Wayang persistence/checkpoint mechanisms.

Then:

```java
@ApplicationScoped
public class PersistentCodingStateMachine {

    @Inject
    CodingStateStore store;

    @Inject
    CodingPhaseGraph graph;

    public Uni<CodingState> transition(
            CodingState state,
            CodingPhase target) {

        if (!graph.canTransition(state.phase(), target)) {
            return Uni.createFrom()
                .failure(new IllegalStateException(
                    "Invalid transition: "
                    + state.phase()
                    + " -> "
                    + target));
        }

        CodingState next = copy(state, target);

        return store.save(next)
            .replaceWith(next);
    }
}
```

Now:

```text
phase transition
       ↓
persist
       ↓
publish event
       ↓
continue
```

not:

```text
phase transition
       ↓
hope JVM doesn't die
```

---

# 4. Add `CodingEvent`

I'd make events append-only.

```java
public sealed interface CodingEvent
    permits CodingPhaseChanged,
            CodingToolCall,
            CodingFileChanged,
            CodingVerification,
            CodingApprovalRequested,
            CodingCheckpoint,
            CodingCompleted,
            CodingFailed {
}
```

For example:

```java
public record CodingPhaseChanged(
    String runId,
    CodingPhase from,
    CodingPhase to,
    Instant timestamp
) implements CodingEvent {}
```

And:

```java
public record CodingFileChanged(
    String runId,
    Path file,
    ChangeType type,
    Instant timestamp
) implements CodingEvent {}
```

Now your event history becomes:

```text
RUN_CREATED
PHASE_CHANGED CREATED → INSPECTING

TOOL_CALL semantic_search
TOOL_RESULT semantic_search

TOOL_CALL read_file
TOOL_RESULT read_file

PHASE_CHANGED INSPECTING → PLANNING

PLAN_CREATED

PHASE_CHANGED PLANNING → EXECUTING

FILE_CHANGED UserService.java

PHASE_CHANGED EXECUTING → VERIFYING

VERIFICATION_STARTED
VERIFICATION_FAILED

PHASE_CHANGED VERIFYING → REPAIRING

...
```

This is much better than trying to reconstruct history from chat messages.

---

# 5. Make `CodingLoop` orchestration explicit

I'd implement this as a coordinator, not as one giant reactive chain.

```java
@ApplicationScoped
public class CodingLoop {

    @Inject
    CodingStateStore states;

    @Inject
    RepositoryInspector repository;

    @Inject
    ProjectDetector projectDetector;

    @Inject
    CodingPlanner planner;

    @Inject
    CodingExecutor executor;

    @Inject
    VerificationEngine verifier;

    @Inject
    RepairLoop repair;

    @Inject
    CodingStateMachine machine;

    public Uni<CodingResult> run(CodingTask task) {

        CodingState initial =
            CodingStateFactory.create(task);

        return states.save(initial)
            .flatMap(x -> inspect(initial))
            .flatMap(this::plan)
            .flatMap(this::execute)
            .flatMap(this::verify)
            .flatMap(this::repairIfNeeded)
            .flatMap(this::review)
            .map(this::complete);
    }
}
```

But don't make each method opaque.

Each should correspond to one meaningful operation.

---

# 6. Inspection should produce structured context

This is important.

Don't let inspection just return:

```java
String text
```

Create:

```java
public record RepositoryContext(
    RepositorySnapshot repository,
    ProjectInfo project,
    List<Path> relevantFiles,
    List<SymbolReference> relevantSymbols,
    List<Diagnostic> diagnostics
) {}
```

Then:

```java
public Uni<CodingState> inspect(CodingState state) {

    return repository.inspect(state.task().workspace())

        .flatMap(repo ->
            projectDetector.detect(repo.workspace())
                .map(project ->
                    state.withRepository(repo, project)))

        .flatMap(enriched ->
            discoverRelevantContext(enriched));
}
```

This is where:

* RAG
* semantic search
* Muqabala
* Git
* file inspection

come together.

---

# 7. Separate discovery from reasoning

This is a subtle but important improvement.

Don't do:

```text
LLM
 └── search repository
 └── decide everything
```

Instead:

```text
Repository Discovery
       ↓
Structured Context
       ↓
LLM Reasoning
```

For example:

```java
public interface RepositoryContextBuilder {

    Uni<RepositoryContext> build(
        CodingTask task,
        RepositorySnapshot repository,
        ProjectInfo project);
}
```

The implementation can use:

```text
Git
RAG
Muqabala
filesystem
project detector
diagnostics
```

Then the model receives:

```json
{
  "project": "wayang",
  "language": "java",
  "build": "maven",
  "relevantFiles": [
    "UserService.java",
    "UserServiceTest.java"
  ],
  "symbols": [
    "UserService.validate()",
    "UserRepository.find()"
  ]
}
```

instead of wasting tokens discovering basic facts repeatedly.

---

# 8. Planning should be machine-readable

This is another place I'd tighten Aljabr.

Don't ask the model to produce:

```text
I will first inspect...
Then I will...
After that...
```

Require:

```json
{
  "objective": "Fix token validation",
  "steps": [
    {
      "id": "1",
      "type": "INSPECT",
      "description": "Inspect token validation"
    },
    {
      "id": "2",
      "type": "MODIFY",
      "description": "Fix expiration validation"
    },
    {
      "id": "3",
      "type": "TEST",
      "description": "Add regression test"
    }
  ]
}
```

Map this directly:

```java
public record CodingPlan(
    String objective,
    List<PlanStep> steps
) {}
```

Then validate it before execution.

---

# 9. Add plan validation

```java
@ApplicationScoped
public class CodingPlanValidator {

    public void validate(CodingPlan plan) {

        if (plan.steps().isEmpty()) {
            throw new IllegalArgumentException(
                "Coding plan contains no steps");
        }

        boolean modifies =
            plan.steps().stream()
                .anyMatch(s ->
                    s.type() == StepType.MODIFY);

        if (!modifies) {
            throw new IllegalArgumentException(
                "Implementation task contains no modification step");
        }
    }
}
```

Eventually:

```text
FIX
 ├── must contain INSPECT
 ├── must contain MODIFY
 └── should contain TEST

REFACTOR
 ├── must contain INSPECT
 ├── must contain MODIFY
 └── must contain VERIFY

REVIEW
 └── must NOT modify files
```

This is exactly the sort of deterministic rule that should not be left to the LLM.

---

# 10. Tool execution should return evidence

This is a big improvement I'd make to your existing tools.

Don't return only:

```java
String output
```

Return:

```java
public record ToolEvidence(
    String toolCallId,
    String tool,
    boolean success,
    String output,
    Duration duration,
    List<Path> affectedFiles,
    List<Diagnostic> diagnostics
) {}
```

For example:

```text
run_tests
    ↓
ToolEvidence
    success = false
    output = "2 tests failed"
    affectedFiles = []
    diagnostics = [...]
```

Then verification can consume it.

---

# 11. Verification should produce evidence, not just boolean

Instead of:

```java
boolean passed
```

make:

```java
public record VerificationReport(
    boolean passed,
    List<VerificationCheck> checks,
    List<Diagnostic> diagnostics,
    Duration duration
) {}
```

Example:

```text
VerificationReport

compile
  PASS

unit-tests
  FAIL
  ├── UserServiceTest.shouldRejectExpiredToken
  └── UserServiceTest.shouldRejectMalformedToken

lsp-diagnostics
  PASS
```

Now the repair agent gets highly focused context.

---

# 12. Repair should NOT resend the entire conversation

This is where your earlier caching/memory architecture becomes useful.

Bad:

```text
Repair attempt
 ↓
send entire previous context again
```

Better:

```text
RepairContext

Original task
+
Relevant plan step
+
Changed files
+
Current diff
+
Verification failure
+
Diagnostics
+
Previous repair attempt
```

Define:

```java
public record RepairContext(
    String originalRequest,
    CodingPlan plan,
    ChangeSet changes,
    VerificationReport failure,
    List<RepairAttempt> previousAttempts
) {}
```

This dramatically reduces context pollution.

---

# 13. `RepairAttempt`

```java
public record RepairAttempt(
    int number,
    String diagnosis,
    String action,
    ChangeSet changes,
    VerificationReport result,
    Instant timestamp
) {}
```

Then your run history becomes:

```text
Attempt #1
──────────
Diagnosis:
  Token expiration comparison uses seconds instead of millis.

Change:
  TokenValidator.java

Result:
  2 tests failed.

Attempt #2
──────────
Diagnosis:
  Regression test uses wrong fixture.

Change:
  TokenValidatorTest.java

Result:
  PASS
```

That's incredibly useful for both the user and future model context.

---

# 14. Add an explicit `ReviewEngine`

Don't finish immediately after tests pass.

```java
public interface ReviewEngine {

    Uni<ReviewReport> review(
        CodingTask task,
        ChangeSet changes,
        VerificationReport verification);
}
```

Review can check:

```text
scope
correctness
unintended changes
style
security
test coverage
API compatibility
```

Initially, make it simple:

```text
git diff
+
Muqabala diagnostics
+
test result
```

Then optionally ask a model for review.

---

# 15. Review should be read-only

Very important policy:

```java
CodingIntent.REVIEW
```

should have:

```java
allowFileWrites = false
```

And the review engine should never mutate the repository.

Similarly:

```text
EXPLAIN
REVIEW
EXPLORE
```

should default to read-only.

---

# 16. Introduce `CodingCapability`

This will clean up the tool explosion.

```java
public enum CodingCapability {

    FILE_READ,
    FILE_WRITE,
    FILE_DELETE,

    SEARCH,
    SYMBOL_SEARCH,

    DEFINITION,
    REFERENCES,
    DIAGNOSTICS,

    GIT_STATUS,
    GIT_DIFF,
    GIT_COMMIT,
    GIT_PUSH,

    BUILD,
    TEST,
    LINT,

    DEBUG
}
```

Then policy:

```java
public interface CodingPolicy {

    boolean allows(CodingCapability capability);
}
```

This is cleaner than hardcoding tool names.

---

# 17. Map capabilities to Wayang tools

For example:

```java
public record ToolCapability(
    String toolName,
    CodingCapability capability
) {}
```

Then:

```text
read_file
    → FILE_READ

write_file
    → FILE_WRITE

delete_file
    → FILE_DELETE

git_diff
    → GIT_DIFF

git_push
    → GIT_PUSH

run_tests
    → TEST
```

Now Wayang's policy layer can make the actual decision.

This fits naturally with the policy-aware `AgentToolExecutor` already present in Wayang. 

---

# 18. Make approvals resumable

Don't block a Java thread.

When:

```text
git_push
```

requires approval:

```text
EXECUTING
    ↓
WAITING_APPROVAL
```

persist:

```java
public record ApprovalRequest(
    String id,
    String runId,
    CodingCapability capability,
    String description,
    Instant createdAt
) {}
```

Then return control to the API/UI.

Later:

```text
POST /runs/{id}/approvals/{approvalId}
```

and:

```text
WAITING_APPROVAL
       ↓
EXECUTING
```

This is where the Wayang execution/checkpoint model becomes especially valuable.

---

# 19. Checkpoint after meaningful mutations

Don't necessarily checkpoint every token.

Checkpoint at:

```text
after inspection
after plan
after file change
after verification
after repair
before approval
```

So:

```java
checkpoint.save(state);
```

after:

```text
INSPECTING
PLANNING
EXECUTING
VERIFYING
REPAIRING
WAITING_APPROVAL
```

Wayang's existing checkpoint bridge already supports checkpointing after agent steps, so Aljabr can layer coding-state persistence over that rather than reinventing the underlying execution mechanism. 

---

# 20. Recovery becomes straightforward

When a run starts:

```java
public Uni<CodingResult> resume(String runId) {

    return stateStore.get(runId)
        .flatMap(optional -> {

            if (optional.isEmpty()) {
                return Uni.createFrom()
                    .failure(new RunNotFoundException(runId));
            }

            CodingState state = optional.get();

            return switch (state.phase()) {

                case VERIFYING ->
                    continueVerification(state);

                case REPAIRING ->
                    continueRepair(state);

                case WAITING_APPROVAL ->
                    awaitApproval(state);

                default ->
                    continueFromPhase(state);
            };
        });
}
```

Now:

> connection lost

doesn't mean:

> start coding again.

It means:

> restore state and continue.

---

# 21. This also gives you cancellation correctly

Don't just kill the LLM future.

Have:

```java
public interface CodingRunControl {

    Uni<Void> pause(String runId);

    Uni<Void> resume(String runId);

    Uni<Void> cancel(String runId);
}
```

State:

```text
EXECUTING
    ↓
PAUSED

PAUSED
    ↓
EXECUTING

EXECUTING
    ↓
CANCELLED
```

And cancellation should be persisted.

---

# 22. Now improve `AljabrWayangRuntime`

After the core exists, this becomes tiny:

```java
@ApplicationScoped
public class AljabrWayangRuntime
        implements WayangRuntime {

    @Inject
    CodingLoop codingLoop;

    @Override
    public CompletableFuture<AgentResponse> executeAsync(
            AgentDefinition agent,
            AgentRequest request) {

        CodingTask task =
            CodingTaskMapper.from(agent, request);

        return codingLoop
            .run(task)
            .map(CodingResponseMapper::map)
            .subscribeAsCompletionStage();
    }

    @Override
    public boolean supports(AgentDefinition agent) {

        return agent != null
            && "coder".equals(
                agent.metadata()
                    .labels()
                    .get("type"));
    }
}
```

This is the point where I'd consider `CoderOrchestrator` mostly finished as a compatibility facade.

---

# 23. Then make `AljabrSkill` thin

Your skills shouldn't contain business logic.

Bad:

```text
LspDefinitionSkill
 ├── parse task
 ├── decide workflow
 ├── invoke model
 ├── manipulate state
 └── call LSP
```

Good:

```text
LspDefinitionSkill
        ↓
MuqabalaClient
        ↓
result
```

Likewise:

```text
RunTestsSkill
       ↓
VerificationExecutor
       ↓
Wayang Sandbox
```

Skills are **capability adapters**, not orchestration.

---

# 24. This gives you a very clean dependency direction

I would enforce this:

```text
aljabr-api
     ↓
aljabr-runtime
     ↓
aljabr-core
     ↓
wayang-spi
```

And:

```text
aljabr-core
     ↓
Muqabala SPI
     ↓
Muqabala
```

Not:

```text
Aljabr → Muqabala implementation internals
```

Likewise:

```text
Aljabr → Wayang public APIs
```

not:

```text
Aljabr → Wayang internal classes
```

---

# 25. The first integration test should be an end-to-end state test

This is probably the most valuable test you can write.

```java
@Test
void implementsAndVerifiesChange() {

    CodingTask task = task(
        "Add null validation to UserService");

    CodingResult result =
        aljabr.run(task)
            .await().indefinitely();

    assertThat(result.status())
        .isEqualTo(COMPLETED);

    assertThat(result.changes())
        .isNotEmpty();

    assertThat(result.verification().passed())
        .isTrue();
}
```

But also test failure/repair:

```java
@Test
void repairsFailedImplementation() {

    CodingResult result =
        aljabr.run(task)
            .await().indefinitely();

    assertThat(result.repairAttempts())
        .isGreaterThan(0);

    assertThat(result.verification().passed())
        .isTrue();
}
```

And safety:

```java
@Test
void refusesGitPushWithoutApproval() {

    CodingResult result =
        aljabr.run(pushTask)
            .await().indefinitely();

    assertThat(result.phase())
        .isEqualTo(WAITING_APPROVAL);
}
```

And recovery:

```java
@Test
void resumesFromVerificationCheckpoint() {

    runUntilVerificationCheckpoint();

    CodingResult result =
        aljabr.resume(runId)
            .await().indefinitely();

    assertThat(result.status())
        .isEqualTo(COMPLETED);
}
```

---

# 26. One thing I'd change from my previous proposal

I would **not immediately create a separate `AljabrStrategy` if the current Wayang `AgentStrategy` is already sufficient for the state machine**.

Your Wayang `AgentStrategy` is intended to decide the next action based on semantic state and execution status. 

So first determine:

```text
Can existing AgentStrategy express:

INSPECT
→ PLAN
→ EXECUTE
→ VERIFY
→ REPAIR
→ REVIEW
```

If yes:

**use it.**

Only introduce a separate Aljabr strategy abstraction if coding-specific behavior genuinely needs it.

That is consistent with our larger goal:

> **clean the garbage, don't create another abstraction just because it sounds architecturally nice.**

---

# The next concrete coding milestone

At this point I would stop designing and implement this exact slice:

```text
┌───────────────────────────────────────┐
│             Aljabr Core               │
│                                       │
│  CodingTask                            │
│      ↓                                │
│  CodingState                           │
│      ↓                                │
│  CodingStateMachine                   │
│      ↓                                │
│  CodingLoop                           │
│      │                                │
│      ├── RepositoryInspector          │
│      ├── ProjectDetector              │
│      ├── CodingPlanner                │
│      ├── CodingExecutor ──→ Wayang   │
│      ├── ChangeManager                │
│      ├── VerificationEngine          │
│      └── RepairLoop                   │
│                                       │
│  CodingStateStore ──────→ Wayang     │
│  CodingEventPublisher ──→ Wayang     │
└───────────────────────────────────────┘
```

And the **first working behavior** should be:

```text
User request
    ↓
CodingTask
    ↓
Repository snapshot
    ↓
Project detection
    ↓
Relevant context
    ↓
Wayang coding agent
    ↓
ChangeSet
    ↓
Compile/test
    ↓
VerificationReport
    ↓
Repair if necessary
    ↓
Review
    ↓
CodingResult
```

After that, the next layer I would tackle is **the actual tool contracts and implementation**: `read_file`, `apply_patch`, `git_diff`, `symbol_search`, `run_tests`, `diagnostics`, `checkpoint`, and how each produces `ToolEvidence` while going through Wayang's policy/sandbox machinery. That is where Aljabr will start becoming a genuinely usable coding agent rather than just a well-structured orchestrator.


Yes. Let's go one level deeper: **the actual Aljabr tool layer**.

This is where I would make the design concrete enough that you can start replacing placeholders with production code.

The principle is:

> **Every coding capability should be a small, typed tool that produces evidence, passes through Wayang policy, and uses the correct underlying subsystem.**

---

# 1. Define one common tool contract

Don't let every tool invent its own return type.

```java
public interface CodingTool<I, O> {

    String name();

    CodingCapability capability();

    Uni<O> execute(
        I input,
        CodingToolContext context
    );
}
```

Context:

```java
public record CodingToolContext(
    String runId,
    Path workspace,
    CodingTask task,
    CodingPolicy policy
) {}
```

But I would actually put **security-sensitive information behind services**, rather than giving arbitrary tools direct access to everything.

---

# 2. Tool result should contain evidence

Use a common wrapper:

```java
public record CodingToolResult<T>(
    String toolCallId,
    String tool,
    boolean success,
    T value,
    ToolEvidence evidence,
    ToolError error
) {}
```

Evidence:

```java
public record ToolEvidence(
    Instant startedAt,
    Instant completedAt,
    Duration duration,
    List<Path> affectedFiles,
    List<Diagnostic> diagnostics,
    Map<String, Object> metadata
) {}
```

Error:

```java
public record ToolError(
    String code,
    String message,
    boolean retryable
) {}
```

Now every tool has a consistent shape.

---

# 3. File reading

Start with the simplest tool.

```java
public record ReadFileInput(
    Path path,
    Integer startLine,
    Integer endLine
) {}
```

Implementation:

```java
@ApplicationScoped
public class ReadFileTool
        implements CodingTool<ReadFileInput, String> {

    @Inject
    WorkspaceGuard workspaceGuard;

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public CodingCapability capability() {
        return CodingCapability.FILE_READ;
    }

    @Override
    public Uni<String> execute(
            ReadFileInput input,
            CodingToolContext context) {

        return Uni.createFrom().item(() -> {

            Path path =
                workspaceGuard.resolve(
                    context.workspace(),
                    input.path());

            String content =
                Files.readString(path);

            return slice(
                content,
                input.startLine(),
                input.endLine());
        });
    }
}
```

Important:

```text
LLM
 ↓
read_file
 ↓
WorkspaceGuard
 ↓
filesystem
```

Not:

```text
LLM
 ↓
arbitrary filesystem path
```

---

# 4. `WorkspaceGuard` is worth having

This is a tiny class with huge value.

```java
@ApplicationScoped
public class WorkspaceGuard {

    public Path resolve(
            Path workspace,
            Path requested) {

        Path resolved =
            workspace
                .resolve(requested)
                .normalize()
                .toAbsolutePath();

        if (!resolved.startsWith(
                workspace.toAbsolutePath())) {

            throw new SecurityException(
                "Path escapes workspace: "
                + requested);
        }

        return resolved;
    }
}
```

This blocks:

```text
../../../../etc/passwd
```

and similar mistakes.

---

# 5. File write should NEVER simply overwrite

Instead:

```java
public record WriteFileInput(
    Path path,
    String content,
    String expectedHash
) {}
```

Then:

```java
@ApplicationScoped
public class WriteFileTool
        implements CodingTool<WriteFileInput, FileChange> {

    @Inject
    WorkspaceGuard workspaceGuard;

    @Inject
    FileHasher hasher;

    @Override
    public Uni<FileChange> execute(
            WriteFileInput input,
            CodingToolContext context) {

        return Uni.createFrom().item(() -> {

            Path path =
                workspaceGuard.resolve(
                    context.workspace(),
                    input.path());

            String currentHash =
                Files.exists(path)
                    ? hasher.hash(path)
                    : null;

            if (!Objects.equals(
                    currentHash,
                    input.expectedHash())) {

                throw new ConcurrentModificationException(
                    "File changed since inspection: "
                    + path);
            }

            String before =
                Files.exists(path)
                    ? Files.readString(path)
                    : null;

            Files.writeString(path, input.content());

            String afterHash =
                hasher.hash(path);

            return FileChange.modified(
                path,
                currentHash,
                afterHash);
        });
    }
}
```

This is one of the most important safety mechanisms in Aljabr.

---

# 6. But `apply_patch` should be the preferred write operation

The model shouldn't normally rewrite an entire file.

Define:

```java
public record ApplyPatchInput(
    Path path,
    String patch,
    String expectedHash
) {}
```

Tool:

```java
public interface PatchEngine {

    Uni<PatchResult> apply(
        Path file,
        String patch,
        String expectedHash);
}
```

Result:

```java
public record PatchResult(
    Path file,
    String beforeHash,
    String afterHash,
    String appliedPatch
) {}
```

Then:

```text
read_file
   ↓
model reasons
   ↓
apply_patch
   ↓
hash validation
   ↓
patch
   ↓
FileChange
```

This is much safer than:

```text
write_file(entire 2,000-line file)
```

---

# 7. `apply_patch` should be atomic

Conceptually:

```java
public Uni<PatchResult> apply(...) {

    Path temp = createTempFile();

    try {
        validateHash();
        createPatchedContent(temp);
        validateSyntaxIfPossible(temp);
        atomicReplace(temp, target);

        return success();

    } catch (Exception e) {
        delete(temp);
        return failure(e);
    }
}
```

So a failed patch doesn't leave:

```text
half-written Java file
```

behind.

---

# 8. Git tools should be read-only first

Start with:

```text
git_status
git_diff
git_log
git_show
```

For example:

```java
public record GitDiff(
    List<FileDiff> files,
    int additions,
    int deletions
) {}
```

Then:

```java
public interface GitService {

    Uni<GitStatus> status(Path workspace);

    Uni<GitDiff> diff(Path workspace);

    Uni<GitLog> log(
        Path workspace,
        int limit);
}
```

Don't initially give the model:

```text
git_commit
git_push
```

Those should come later through explicit capabilities and approval.

---

# 9. `git_diff` becomes the canonical ChangeSet source

This is an important simplification.

Rather than trying to track every mutation independently:

```text
write_file
write_file
apply_patch
delete_file
...
```

you can always reconcile against Git:

```text
working tree
      ↓
git diff
      ↓
ChangeSet
```

So:

```java
public Uni<ChangeSet> collectChanges(
        Path workspace) {

    return git.diff(workspace)
        .map(diffMapper::toChangeSet);
}
```

This gives you a canonical answer to:

> "What actually changed?"

---

# 10. Semantic search tool

Since your existing architecture has Muqabala/LSP capabilities, Aljabr should expose them as simple coding tools rather than reinventing code intelligence.

```java
public record SearchCodeInput(
    String query,
    List<String> paths,
    int limit
) {}
```

```java
@ApplicationScoped
public class SearchCodeTool
        implements CodingTool<SearchCodeInput, List<SearchResult>> {

    @Inject
    CodeSearchService search;

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public CodingCapability capability() {
        return CodingCapability.SEARCH;
    }

    @Override
    public Uni<List<SearchResult>> execute(
            SearchCodeInput input,
            CodingToolContext context) {

        return search.search(
            context.workspace(),
            input.query(),
            input.paths(),
            input.limit());
    }
}
```

This keeps Aljabr independent of whether the backend is:

```text
ripgrep
LSP
RAG
Muqabala
hybrid search
```

---

# 11. Symbol tools

You already have the Muqabala-oriented LSP capabilities, so make the Aljabr surface simple:

```text
definition
references
symbols
diagnostics
hover
```

For example:

```java
public record SymbolLocation(
    Path file,
    int line,
    int column,
    String symbol
) {}
```

And:

```java
public interface CodeIntelligence {

    Uni<List<SymbolLocation>> definition(
        Path workspace,
        SymbolQuery query);

    Uni<List<SymbolLocation>> references(
        Path workspace,
        SymbolQuery query);

    Uni<List<Diagnostic>> diagnostics(
        Path workspace,
        List<Path> files);
}
```

Aljabr doesn't care how Muqabala implements it.

---

# 12. Diagnostics should be first-class

Don't make compiler errors plain text.

Normalize them:

```java
public record Diagnostic(
    DiagnosticSeverity severity,
    Path file,
    int line,
    int column,
    String code,
    String message,
    String source
) {}
```

```java
public enum DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
```

Then Maven, LSP, Dart analyzer, TypeScript, etc. all map into the same structure.

That makes repair dramatically easier.

---

# 13. Build/test execution should use Wayang sandbox

This is where I would be strict.

Don't implement:

```java
Runtime.getRuntime().exec(...)
```

inside Aljabr.

Instead:

```java
public interface CodingCommandExecutor {

    Uni<CommandResult> execute(
        Path workspace,
        List<String> command,
        ExecutionPolicy policy);
}
```

and internally:

```text
Aljabr
 ↓
Wayang execution service
 ↓
SandboxProvider
 ↓
process
```

Your existing runtime already has sandbox concepts and command execution infrastructure. 

---

# 14. Normalize command output

```java
public record CommandResult(
    int exitCode,
    String stdout,
    String stderr,
    Duration duration,
    boolean timedOut
) {
    public boolean success() {
        return exitCode == 0 && !timedOut;
    }
}
```

Then:

```java
public record VerificationCommand(
    String id,
    List<String> command,
    Duration timeout
) {}
```

---

# 15. Don't let the model invent arbitrary shell commands

Instead of giving it:

```text
run_shell(command: String)
```

prefer:

```text
run_build
run_tests
run_lint
run_formatter
```

with project-specific implementations.

For example:

```java
public interface ProjectExecutionStrategy {

    boolean supports(ProjectInfo project);

    List<VerificationCommand> testCommands(
        ProjectInfo project);

    List<VerificationCommand> buildCommands(
        ProjectInfo project);
}
```

Then Maven:

```java
public class MavenExecutionStrategy
        implements ProjectExecutionStrategy {

    public List<VerificationCommand> testCommands(
            ProjectInfo project) {

        return List.of(
            new VerificationCommand(
                "unit-tests",
                List.of("./mvnw", "test"),
                Duration.ofMinutes(5)));
    }
}
```

This reduces command injection and hallucinated build commands.

---

# 16. But allow an escape hatch

You will eventually encounter:

```text
make test
cargo test
mix test
gradle check
bazel test ...
```

So add:

```java
CodingCapability.CUSTOM_COMMAND
```

but make it:

```text
CUSTOM_COMMAND
    ↓
policy
    ↓
approval / sandbox
```

rather than unrestricted.

---

# 17. `run_tests` should become a coding tool

```java
@ApplicationScoped
public class RunTestsTool
        implements CodingTool<RunTestsInput, VerificationReport> {

    @Inject
    VerificationEngine verification;

    @Override
    public String name() {
        return "run_tests";
    }

    @Override
    public CodingCapability capability() {
        return CodingCapability.TEST;
    }

    @Override
    public Uni<VerificationReport> execute(
            RunTestsInput input,
            CodingToolContext context) {

        return verification.runTests(
            context.task(),
            input.scope());
    }
}
```

Now the agent can reason:

```text
apply patch
 ↓
run_tests
 ↓
verification report
 ↓
repair
```

without knowing how tests are executed.

---

# 18. Tool registry

Don't manually inject every tool everywhere.

Create:

```java
public interface CodingToolRegistry {

    Optional<CodingTool<?, ?>> find(String name);

    List<CodingTool<?, ?>> all();

    List<CodingTool<?, ?>> allowed(
        CodingPolicy policy);
}
```

Then:

```java
@ApplicationScoped
public class DefaultCodingToolRegistry
        implements CodingToolRegistry {

    @Inject
    Instance<CodingTool<?, ?>> tools;

    @Override
    public List<CodingTool<?, ?>> all() {
        return tools.stream().toList();
    }

    @Override
    public List<CodingTool<?, ?>> allowed(
            CodingPolicy policy) {

        return all().stream()
            .filter(tool ->
                policy.allows(tool.capability()))
            .toList();
    }
}
```

This makes tool availability dynamic.

---

# 19. Tool definition exposed to the LLM

The internal Java interface and model-facing schema should be separate.

```java
public record ToolDefinition(
    String name,
    String description,
    JsonNode inputSchema
) {}
```

For example:

```json
{
  "name": "read_file",
  "description": "Read source code inside the workspace",
  "inputSchema": {
    "type": "object",
    "properties": {
      "path": {
        "type": "string"
      },
      "startLine": {
        "type": "integer"
      },
      "endLine": {
        "type": "integer"
      }
    },
    "required": ["path"]
  }
}
```

Then Wayang's tool system can consume the definitions.

---

# 20. Critical: don't expose every tool at every phase

This is one of the easiest improvements you can make.

Define:

```java
public interface ToolAvailabilityPolicy {

    boolean available(
        CodingPhase phase,
        CodingCapability capability);
}
```

Example:

```text id="3l4vpp"
INSPECTING
  ✓ read_file
  ✓ search_code
  ✓ definition
  ✓ references
  ✓ diagnostics
  ✗ write_file
  ✗ delete_file
  ✗ git_push

PLANNING
  ✓ read-only tools
  ✗ write tools

EXECUTING
  ✓ read
  ✓ search
  ✓ write
  ✓ patch
  ✓ test

VERIFYING
  ✓ read
  ✓ diff
  ✓ test
  ✗ write

REVIEWING
  ✓ read
  ✓ diff
  ✓ diagnostics
  ✗ write
```

This dramatically narrows the model's action space.

---

# 21. This should integrate with Wayang's policy executor

The final call chain should be:

```text
LLM tool call
     ↓
Wayang AgentToolExecutor
     ↓
Aljabr CodingPolicy
     ↓
phase availability
     ↓
capability permission
     ↓
approval?
     ↓
CodingTool
     ↓
Muqabala / Sandbox / Git / FS
     ↓
ToolEvidence
     ↓
Wayang observation
     ↓
LLM
```

This is exactly where you should **reuse Wayang rather than duplicate execution policy**. Your current Wayang runtime already has the policy-aware `AgentToolExecutor` abstraction. 

---

# 22. Add tool idempotency

This becomes important when recovering after a crash.

Every tool invocation gets:

```java
public record ToolInvocation(
    String invocationId,
    String runId,
    String toolName,
    String inputHash,
    Instant createdAt
) {}
```

Before executing:

```text
same invocationId?
     │
     ├── yes → return previous result
     │
     └── no → execute
```

This prevents:

```text
write file
 ↓
crash before checkpoint
 ↓
resume
 ↓
write same file twice
```

For read-only operations this is less important.

For mutations it is essential.

---

# 23. Tool execution record

Persist:

```java
public record ToolExecution(
    String invocationId,
    String runId,
    String tool,
    String inputHash,
    ToolStatus status,
    ToolEvidence evidence,
    Instant startedAt,
    Instant completedAt
) {}
```

```java
public enum ToolStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    DENIED,
    APPROVAL_REQUIRED,
    CANCELLED
}
```

Now your UI can show:

```text
✓ search_code       82ms
✓ read_file         4ms
✓ apply_patch       31ms
✓ run_tests         2.4s
✗ run_tests         1.8s
```

without scraping agent text.

---

# 24. Add a `ToolExecutionService`

This becomes the single doorway:

```java
@ApplicationScoped
public class ToolExecutionService {

    @Inject
    CodingToolRegistry registry;

    @Inject
    CodingPolicyEngine policy;

    @Inject
    ToolExecutionStore store;

    public Uni<CodingToolResult<?>> execute(
            ToolInvocation invocation,
            CodingToolContext context) {

        return policy.authorize(
                invocation,
                context)

            .flatMap(decision ->
                switch (decision.type()) {

                    case ALLOW ->
                        executeAllowed(
                            invocation,
                            context);

                    case DENY ->
                        denied(invocation);

                    case APPROVAL_REQUIRED ->
                        approval(invocation);
                });
    }
}
```

This is a very important class.

**No Aljabr component should directly invoke a privileged tool.**

---

# 25. Then the coding executor becomes much simpler

```java
@ApplicationScoped
public class CodingExecutor {

    @Inject
    WayangAgentFactory agents;

    public Uni<CodingState> execute(
            CodingState state) {

        Agent agent =
            agents.createCoder(
                buildCoderContext(state));

        return agent.execute(
                buildExecutionRequest(state))
            .map(result ->
                updateState(state, result));
    }
}
```

The agent gets:

```text
task
+
current plan step
+
relevant context
+
allowed tools
+
current changes
+
previous tool evidence
```

Nothing more.

---

# 26. This also fixes context bloat

Instead of:

```text
entire conversation
+
entire repository
+
all previous tool output
+
all test logs
```

build:

```java
public record CodingAgentContext(
    CodingTask task,
    CodingPhase phase,
    PlanStep currentStep,
    RepositoryContext repository,
    ChangeSet changes,
    List<ToolEvidence> recentEvidence,
    Optional<VerificationReport> verification
) {}
```

Then Wayang's existing context compiler/planner can budget this against the model context window. Your ReAct layer already has a budgeted context compiler and context planner. 

---

# 27. Don't store huge stdout in the state

This is another practical issue.

If:

```text
mvn test
```

outputs:

```text
500,000 lines
```

don't put all of it into `CodingState`.

Instead:

```java
public record ArtifactReference(
    String id,
    ArtifactType type,
    long size,
    String hash
) {}
```

Store:

```text
verification.stdout
      ↓
artifact store
      ↓
ArtifactReference
```

and keep only:

```text
last 100 lines
errors
diagnostics
summary
```

in the active context.

---

# 28. Artifact model

I'd introduce:

```java
public enum ArtifactType {
    COMMAND_OUTPUT,
    TEST_REPORT,
    DIFF,
    PATCH,
    LOG,
    RESEARCH,
    SOURCE_SNAPSHOT
}
```

Then:

```java
public record ArtifactReference(
    String id,
    ArtifactType type,
    String contentHash,
    long size,
    String mediaType
) {}
```

This connects nicely to the research/cache architecture you were thinking about earlier.

---

# 29. The actual execution loop becomes elegant

At this point:

```java
private Uni<CodingState> executePlan(
        CodingState state) {

    return executeCurrentStep(state)
        .flatMap(this::advancePlan)
        .flatMap(next -> {

            if (next.plan().completed()) {
                return Uni.createFrom().item(next);
            }

            return executePlan(next);
        });
}
```

But with a safety bound:

```java
if (stepCount > MAX_PLAN_STEPS) {
    return fail("plan exceeded maximum steps");
}
```

Never allow infinite autonomous execution.

---

# 30. Add budgets at three levels

This is important for a real coding agent.

### Token budget

```text
model context / generation
```

Wayang already has token-budget/context infrastructure. 

### Tool budget

```java
maxToolCalls = 100;
```

### Execution budget

```java
maxDuration = 30 minutes;
```

And perhaps:

```java
maxChangedFiles = 50;
maxRepairAttempts = 3;
maxCommandRuntime = 5 minutes;
```

So the policy becomes:

```java
public record CodingBudget(
    int maxToolCalls,
    int maxPlanSteps,
    int maxRepairAttempts,
    int maxChangedFiles,
    Duration maxRunDuration,
    Duration maxCommandDuration
) {}
```

---

# 31. One more important safety mechanism: scope

The user says:

> "Fix the authentication bug."

Aljabr shouldn't automatically modify 300 unrelated files.

Add:

```java
public record CodingScope(
    Set<Path> allowedPaths,
    Set<Path> excludedPaths,
    int maxChangedFiles
) {}
```

Initially:

```java
CodingScope.workspace()
```

Later:

```text
src/main/java/auth/**
src/test/java/auth/**
```

Then:

```java
scope.allows(path)
```

becomes another policy gate.

---

# 32. Now you have the real Aljabr architecture

At this point I'd consider the core shape settled:

```text
                        ┌───────────────┐
                        │   CodingTask  │
                        └───────┬───────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   CodingLoop    │
                       └────────┬────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
        Repository          Planner          Executor
        Context             │                 │
              │              │                 ▼
              │              │          Wayang Agent
              │              │                 │
              │              │                 ▼
              │              │        ToolExecutionService
              │              │                 │
              │              │                 ▼
              │              │       ┌─────────┼─────────┐
              │              │       ▼         ▼         ▼
              │              │      FS       Git      Muqabala
              │              │
              └──────────────┼──────────────────────┐
                             ▼                      │
                       Verification                 │
                             │                      │
                       ┌─────┴─────┐                │
                       │           │                │
                     PASS         FAIL              │
                       │           │                │
                       ▼           ▼                │
                    Review       Repair ────────────┘
                       │
                       ▼
                   Completed
```

And underneath everything:

```text
StateStore
EventStore
CheckpointStore
ArtifactStore
Policy
Budget
```

---

# What I would code next, literally

I would now implement in this order:

```text
01  WorkspaceGuard
02  CodingCapability
03  CodingTool
04  CodingToolResult
05  ToolEvidence
06  CodingToolRegistry
07  ToolExecutionService
08  ReadFileTool
09  ApplyPatchTool
10  GitStatusTool
11  GitDiffTool
12  SearchCodeTool
13  DefinitionTool
14  ReferencesTool
15  DiagnosticsTool
16  RunTestsTool
17  CodingPolicyEngine
18  ToolAvailabilityPolicy
19  ToolExecutionStore
20  Tool idempotency
21  ArtifactStore
22  connect ToolExecutionService → Wayang AgentToolExecutor
```

Then test the vertical slice:

```text
read_file
   ↓
search_code
   ↓
definition
   ↓
apply_patch
   ↓
git_diff
   ↓
run_tests
   ↓
verification
```

Once **that** works end-to-end, we can move to the next major piece: **the Aljabr coding prompt/skill itself**—how to structure the `Skill.md`, system instructions, tool descriptions, planning protocol, repair protocol, and context format so the Wayang agent actually behaves like a disciplined coding agent rather than a generic ReAct agent. That is the point where the runtime and the intelligence layer meet.




Absolutely. Now we should build the **Aljabr intelligence layer**: the skill, coding-agent prompt protocol, context contract, planning behavior, repair behavior, and tool-use discipline.

The goal is **not** to create another generic agent. Aljabr should be a specialized coding behavior running *inside* Wayang.

# 1. Aljabr should have one primary Skill

I'd make the main skill roughly:

```text
skills/
└── aljabr/
    ├── Skill.md
    ├── protocols/
    │   ├── inspection.md
    │   ├── planning.md
    │   ├── implementation.md
    │   ├── verification.md
    │   ├── repair.md
    │   └── review.md
    └── schemas/
        ├── plan.json
        ├── patch.json
        └── review.json
```

But don't make six independent "agents."

The runtime remains:

```text
Aljabr Agent
    │
    ├── inspect
    ├── plan
    ├── implement
    ├── verify
    ├── repair
    └── review
```

The protocols are **behavioral instructions**, not separate agents.

---

# 2. `Skill.md` should be short

Don't put your entire coding philosophy into one 2,000-line prompt.

Something like:

```markdown
# Aljabr

Aljabr is Wayang's software engineering agent.

## Objective

Modify software safely and minimally to satisfy the user's request.

## Core loop

1. Understand the request.
2. Inspect the repository.
3. Build evidence before making assumptions.
4. Create an implementation plan.
5. Apply the smallest appropriate change.
6. Verify the change.
7. Repair failures when possible.
8. Review the final diff.
9. Report what changed and what was verified.

## Rules

- Never modify files before sufficient inspection.
- Prefer existing project patterns over introducing new abstractions.
- Prefer minimal changes.
- Never claim verification that was not performed.
- Do not hide failures.
- Preserve unrelated user changes.
- Never access files outside the permitted workspace.
- Follow tool and approval policies.
- Stop when the task is satisfied.

## Verification

A successful implementation requires evidence appropriate to the task:
- compilation
- tests
- diagnostics
- lint
- or other project-specific verification.

## Completion

Before completing:
- inspect the final diff
- verify the intended behavior
- ensure no unrelated changes were introduced
- summarize changes and verification honestly.
```

That's enough for the global behavior.

The detailed procedures belong in the runtime/context.

---

# 3. The most important prompt rule

I'd make this explicit:

> **Reason from repository evidence, not assumptions.**

This changes the agent dramatically.

Instead of:

```text
User:
Fix authentication.

LLM:
I'll modify AuthService...
```

Aljabr should behave:

```text
User:
Fix authentication.

Aljabr:
What authentication implementation exists?

→ search_code("authentication")
→ inspect relevant symbols
→ inspect tests
→ inspect configuration
→ formulate hypothesis
```

Only then:

```text
plan
→ modify
```

---

# 4. Give the agent a structured `CodingContext`

Don't assemble giant prompt strings.

```java
public record CodingContext(
    CodingTask task,
    CodingPhase phase,

    ProjectInfo project,
    RepositoryContext repository,

    Optional<CodingPlan> plan,
    Optional<PlanStep> currentStep,

    ChangeSet changes,

    List<ToolEvidence> recentEvidence,

    Optional<VerificationReport> verification,

    CodingBudget budget,

    CodingScope scope
) {}
```

Then have a renderer:

```java
public interface CodingContextRenderer {

    ModelContext render(CodingContext context);
}
```

This is much easier to evolve.

---

# 5. Context should be layered

I'd structure the model context as:

```text
SYSTEM
  ↓
ALJABR RULES

TASK
  ↓
USER REQUEST

PROJECT
  ↓
language/build/framework

REPOSITORY EVIDENCE
  ↓
relevant files/symbols/diagnostics

PLAN
  ↓
current plan

EXECUTION
  ↓
changes/tool evidence

VERIFICATION
  ↓
failures/results

AVAILABLE TOOLS
  ↓
phase-specific tools
```

Not:

```text
SYSTEM
everything ever discovered
everything ever output
entire repository
entire conversation
```

---

# 6. Separate facts from hypotheses

This is a surprisingly valuable addition.

Represent repository understanding as:

```java
public record CodingObservation(
    ObservationType type,
    String statement,
    List<EvidenceReference> evidence
) {}
```

Types:

```java
public enum ObservationType {
    FACT,
    HYPOTHESIS,
    CONSTRAINT,
    RISK
}
```

Example:

```text
FACT
UserService validates expiration in validateToken().
evidence: UserService.java:82

FACT
Tests use fixed Clock instances.
evidence: UserServiceTest.java:41

HYPOTHESIS
Expiration comparison may use milliseconds incorrectly.
evidence: UserService.java:87

RISK
Changing Clock behavior may affect existing tests.
```

This helps stop hallucinated reasoning from becoming treated as fact.

---

# 7. Inspection protocol

`inspection.md`:

```markdown
# Inspection Protocol

Before implementation:

1. Identify the project type.
2. Locate relevant source code.
3. Locate relevant tests.
4. Search for related symbols/usages.
5. Inspect configuration when relevant.
6. Inspect existing implementation patterns.
7. Identify constraints and risks.
8. Determine the smallest likely change.

Do not modify files during inspection.

Do not infer implementation details when repository evidence can establish them.

Inspection is complete when:
- relevant implementation is identified
- relevant tests are identified
- dependencies/usage are understood sufficiently
- a concrete implementation hypothesis exists
```

Notice the final criterion:

> **sufficiently**

Don't force the agent to inspect the entire repository.

---

# 8. Prevent endless research

This needs a stopping rule.

Otherwise:

```text
search
→ search
→ search
→ search
→ search
```

Define:

```java
public record InspectionCompletion(
    boolean implementationLocated,
    boolean testsLocated,
    boolean dependenciesUnderstood,
    boolean hypothesisFormed,
    boolean sufficient
) {}
```

Then:

```text
if sufficient:
    PLAN
else:
    continue inspection
```

And enforce:

```java
maxInspectionToolCalls
```

For example:

```text
default = 20
```

---

# 9. Planning protocol

The model should produce structured output.

```markdown
# Planning Protocol

Create a minimal implementation plan based on repository evidence.

Each step must:
- have a unique ID
- identify its purpose
- identify affected files when known
- identify expected verification
- avoid speculative changes

Prefer:
- modifying existing code
- existing project utilities
- existing test patterns

Avoid:
- unnecessary abstractions
- unrelated refactors
- broad rewrites
```

Then:

```java
public record PlanStep(
    String id,
    StepType type,
    String objective,
    List<Path> expectedFiles,
    List<String> verification
) {}
```

---

# 10. Add a plan confidence

Useful:

```java
public record CodingPlan(
    String objective,
    List<PlanStep> steps,
    PlanConfidence confidence,
    List<String> risks
) {}
```

```java
enum PlanConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

But don't let confidence magically authorize risky behavior.

It is diagnostic metadata, not permission.

---

# 11. Implementation protocol

This should be extremely strict:

```markdown
# Implementation Protocol

For each implementation step:

1. Confirm the target file/symbol.
2. Re-read the relevant current content if necessary.
3. Apply the smallest change satisfying the step.
4. Preserve unrelated modifications.
5. Inspect the resulting diff.
6. Continue to the next step.

Do not:
- rewrite unrelated files
- perform opportunistic cleanup
- rename APIs without requirement
- introduce abstractions without evidence
- modify generated files unless required
```

This addresses one of the biggest weaknesses of coding agents:

> **scope creep.**

---

# 12. Make "minimal diff" measurable

Don't just tell the LLM:

> make minimal changes.

Have the runtime calculate:

```java
public record ChangeMetrics(
    int filesChanged,
    int linesAdded,
    int linesRemoved,
    int filesCreated,
    int filesDeleted
) {}
```

Then:

```text
requested:
  1 bug

actual:
  17 files changed
  1,200 lines changed
```

That should trigger:

```text
SCOPE_WARNING
```

Potentially:

```text
REVIEW_REQUIRED
```

---

# 13. Detect suspicious changes

Add:

```java
public interface ChangeRiskAnalyzer {

    Uni<ChangeRiskReport> analyze(
        CodingTask task,
        ChangeSet changes);
}
```

Report:

```java
public record ChangeRiskReport(
    RiskLevel level,
    List<ChangeRisk> risks
) {}
```

Examples:

```text
HIGH
- 14 unrelated files modified

MEDIUM
- public API changed

MEDIUM
- dependency added

LOW
- test fixture updated
```

This is much more reliable than asking the LLM to self-police.

---

# 14. Verification protocol

`verification.md`:

```markdown
# Verification Protocol

Verification must be evidence-based.

Prefer, in order appropriate to the project:

1. compile/build
2. targeted tests
3. broader tests
4. diagnostics
5. lint/static analysis
6. final diff inspection

Do not claim success if verification failed.

If verification cannot be performed:
- state why
- report what was performed
- do not represent the task as fully verified.
```

That last rule is critical.

---

# 15. Verification should be task-aware

Not every change needs the same verification.

For:

```text
rename method
```

maybe:

```text
compile
references
tests
```

For:

```text
README typo
```

maybe:

```text
diff
```

For:

```text
security vulnerability
```

maybe:

```text
targeted tests
static analysis
diff review
```

So:

```java
public interface VerificationStrategy {

    boolean supports(
        CodingTask task,
        ProjectInfo project);

    List<VerificationCheck> checks(...);
}
```

---

# 16. Repair protocol

This is where Aljabr becomes significantly better than a basic coding agent.

```markdown
# Repair Protocol

When verification fails:

1. Classify the failure.
2. Identify the most likely cause.
3. Inspect only evidence relevant to that failure.
4. Apply a targeted repair.
5. Re-run the failed verification.
6. Re-run broader verification when appropriate.

Do not blindly modify code after a failure.

Do not repeat an unsuccessful repair without new evidence.
```

The last sentence is important.

---

# 17. Detect repeated failed reasoning

Store:

```java
public record RepairFingerprint(
    String failureHash,
    String diagnosisHash,
    String changeHash
) {}
```

If:

```text
attempt 1
failure X
diagnosis Y
change Z

attempt 2
failure X
diagnosis Y
change Z
```

then:

```text
STOP
```

Don't let the agent burn 30 iterations doing the same thing.

---

# 18. Repair should classify failures

```java
enum FailureType {

    COMPILATION,
    TEST_FAILURE,
    LINT,
    DIAGNOSTIC,
    RUNTIME,
    ENVIRONMENT,
    TOOL,
    UNKNOWN
}
```

Then:

```java
public record VerificationFailure(
    FailureType type,
    List<Diagnostic> diagnostics,
    ArtifactReference rawOutput
) {}
```

Now the repair strategy can be deterministic:

```text
COMPILATION
    ↓
inspect compiler diagnostics

TEST_FAILURE
    ↓
inspect failing test + implementation

ENVIRONMENT
    ↓
don't modify application code blindly
```

---

# 19. Environment failures must not become code changes

This is a major guardrail.

Suppose:

```text
npm test
→ network unavailable
```

Bad agent:

```text
I'll modify package configuration.
```

Correct:

```text
Verification failed due to environment/network failure.

No code repair attempted.
```

So:

```java
if (failure.type() == ENVIRONMENT) {
    return stopForEnvironmentFailure();
}
```

---

# 20. Review protocol

The final reviewer should answer:

```text
1. Does the change satisfy the request?
2. Is the implementation consistent with the project?
3. Are there unrelated changes?
4. Are tests sufficient?
5. Are there obvious regressions?
6. Did we leave temporary/debug code?
```

And produce:

```java
public record ReviewReport(
    ReviewStatus status,
    List<ReviewFinding> findings,
    ChangeRiskReport changeRisk
) {}
```

---

# 21. Review should compare task ↔ diff

This is important.

Give the reviewer:

```text
USER REQUEST
     ↕
FINAL DIFF
     ↕
VERIFICATION
```

not:

```text
entire agent conversation
```

That makes final review much more focused.

---

# 22. Tool selection instructions

Don't tell the model merely:

> You have tools.

Tell it when to use them.

Example:

```markdown
## Tool selection

Use `search_code` when:
- you don't know where functionality is implemented
- you need usages of a symbol
- you need repository-wide evidence

Use `read_file` when:
- you identified a relevant file
- exact implementation context is required

Use `definition` when:
- a symbol's implementation is unclear

Use `references` when:
- changing a method/API may affect callers

Use `apply_patch` when:
- the intended modification is sufficiently understood

Use `run_tests` when:
- implementation changes behavior
- tests exist
- verification is required
```

This is much better than generic tool descriptions.

---

# 23. Tool calls should be purposeful

I would add this behavioral rule:

> **Before using a tool, know what question the tool call is intended to answer.**

For example:

Bad:

```text
search_code("User")
search_code("Service")
search_code("auth")
search_code("token")
...
```

Better:

```text
Question:
Where is token expiration validated?

→ search_code("token expiration")
```

Then:

```text
Question:
Who calls validateToken()?

→ references(validateToken)
```

This makes the agent's research much more efficient.

---

# 24. Add `ResearchQuestion`

Internally:

```java
public record ResearchQuestion(
    String question,
    ResearchPurpose purpose,
    Optional<String> expectedEvidence
) {}
```

Purpose:

```java
enum ResearchPurpose {
    LOCATE_IMPLEMENTATION,
    UNDERSTAND_USAGE,
    UNDERSTAND_PATTERN,
    VALIDATE_HYPOTHESIS,
    IDENTIFY_TEST,
    IDENTIFY_RISK
}
```

This can be logged with tool calls.

Now your trace becomes:

```text
QUESTION:
Where is expiration validated?

TOOL:
search_code

EVIDENCE:
UserService.validateToken()

QUESTION:
Who calls this method?

TOOL:
references

EVIDENCE:
LoginController
RefreshTokenService
```

That is much better for debugging Aljabr itself.

---

# 25. This is where your earlier research caching becomes powerful

Remember the research-cache idea we discussed.

Don't cache only:

```text
search query → result
```

Cache:

```java
public record ResearchArtifact(
    String questionHash,
    ResearchQuestion question,
    List<EvidenceReference> evidence,
    String summary,
    String repositoryRevision,
    Instant createdAt
) {}
```

The repository revision matters.

If:

```text
HEAD = abc123
```

then:

```text
"Where is authentication validated?"
```

can reuse the answer.

But after:

```text
HEAD = def456
```

the cache should be reconsidered.

---

# 26. Cache invalidation can be scope-based

You don't necessarily invalidate everything after every edit.

For example:

```text
UserService.java changed
```

invalidate:

```text
UserService symbols
UserService references
authentication-related research
```

but preserve:

```text
Project build configuration
unrelated package research
```

This is where Aljabr + Wayang memory/cache infrastructure becomes very powerful.

---

# 27. Memory should distinguish repository knowledge from task knowledge

I would define:

```java
enum CodingMemoryScope {

    RUN,
    TASK,
    WORKSPACE,
    PROJECT
}
```

### RUN

Temporary:

```text
current tool outputs
current hypotheses
current repair attempts
```

### TASK

Useful for the current request:

```text
user requirement
decisions
accepted plan
```

### WORKSPACE

Repository-local:

```text
project conventions
build commands
architecture facts
```

### PROJECT

Longer-lived:

```text
known framework conventions
stable architectural decisions
```

---

# 28. But don't let Aljabr silently persist everything

Important rule:

```text
tool output ≠ memory
```

Only selected artifacts become memory.

For example:

```text
"mvn test output"
```

probably stays an artifact.

But:

```text
"Project uses Maven wrapper ./mvnw"
```

might become workspace knowledge.

This prevents memory pollution.

---

# 29. Memory promotion should be explicit

```java
public interface CodingMemoryPolicy {

    MemoryDecision evaluate(
        CodingObservation observation);
}
```

Result:

```java
enum MemoryDecision {
    DISCARD,
    RUN_ONLY,
    TASK,
    WORKSPACE,
    PROJECT
}
```

Now Aljabr can learn useful project facts without turning every tool output into permanent memory.

---

# 30. Final Aljabr agent behavior

With all of this, the model's effective loop becomes:

```text
┌──────────────────────────────────────────────┐
│                  ALJABR                      │
│                                              │
│ USER REQUEST                                 │
│      ↓                                       │
│ UNDERSTAND                                   │
│      ↓                                       │
│ INSPECT                                      │
│      │                                       │
│      ├── search                              │
│      ├── read                                │
│      ├── symbols                             │
│      ├── references                          │
│      └── diagnostics                         │
│      ↓                                       │
│ OBSERVATIONS                                 │
│      ↓                                       │
│ PLAN                                         │
│      ↓                                       │
│ IMPLEMENT                                    │
│      │                                       │
│      ├── apply_patch                         │
│      └── inspect diff                        │
│      ↓                                       │
│ VERIFY                                       │
│      │                                       │
│      ├── build                               │
│      ├── tests                               │
│      └── diagnostics                         │
│      ↓                                       │
│   ┌──┴───────────────┐                       │
│   │                  │                       │
│ PASS               FAIL                      │
│   │                  │                       │
│   │             CLASSIFY                     │
│   │                  ↓                       │
│   │               REPAIR                     │
│   │                  │                       │
│   │                  └──────→ VERIFY        │
│   ↓                                          │
│ REVIEW                                       │
│      ↓                                       │
│ FINAL DIFF                                   │
│      ↓                                       │
│ COMPLETED                                    │
└──────────────────────────────────────────────┘
```

And the **Wayang runtime owns the mechanics**:

```text
policy
permissions
tools
sandbox
context
memory
checkpoint
events
persistence
model invocation
recovery
budget
```

while **Aljabr owns coding behavior**:

```text
inspect
plan
modify
verify
repair
review
```

That separation is, IMO, the key architectural decision.

---

# The next thing I'd implement

Now we're at the point where the next step should become **very concrete**:

### Build the actual `Skill.md` + tool schema package

Specifically:

```text
aljabr/
├── Skill.md
├── tools/
│   ├── read_file.md
│   ├── search_code.md
│   ├── definition.md
│   ├── references.md
│   ├── apply_patch.md
│   ├── git_diff.md
│   ├── diagnostics.md
│   └── run_tests.md
│
├── protocols/
│   ├── inspection.md
│   ├── planning.md
│   ├── implementation.md
│   ├── verification.md
│   ├── repair.md
│   └── review.md
│
└── schemas/
    ├── coding-plan.json
    ├── repair-plan.json
    └── review-report.json
```

Then we can write the **actual production-grade contents** of those files—not pseudocode—and simultaneously map each tool to your existing Wayang `Skill`, `AgentTool`, `AgentToolExecutor`, Muqabala, sandbox, memory, and checkpoint APIs. That would be the next practical step before touching more architecture.



Great. Now let's make the previous design **actually implementable in the Wayang repository**.

The next layer is the **production `Skill.md` + tool contracts + schemas**. I would deliberately keep this boring and deterministic. Aljabr's intelligence should come from the model, while the Skill constrains *how* that intelligence operates.

# 1. `Skill.md`

I would make the root skill look approximately like this:

```markdown
# Aljabr — Software Engineering Agent

## Purpose

Aljabr is Wayang's coding agent.

Its responsibility is to understand software-engineering tasks, inspect the
repository, make minimal targeted changes, verify those changes, repair
failures when possible, and report the result honestly.

Aljabr operates inside the permissions, sandbox, memory, checkpoint,
budget, and tool policies provided by Wayang.

---

## Operating Principles

### 1. Evidence before modification

Do not modify code until sufficient repository evidence has been collected.

Prefer repository evidence over assumptions.

### 2. Minimal change

Change only what is required to satisfy the task.

Do not perform unrelated refactoring, cleanup, formatting, renaming, or
dependency changes unless required.

### 3. Preserve existing behavior

Prefer existing project conventions, utilities, abstractions, and patterns.

Do not introduce a new abstraction when an existing mechanism is sufficient.

### 4. Verify

Do not claim that an implementation works unless appropriate verification
has actually been performed.

### 5. Repair from evidence

When verification fails, diagnose the failure before modifying code again.

Do not repeatedly apply the same unsuccessful fix.

### 6. Protect user changes

Do not overwrite unrelated changes already present in the workspace.

### 7. Respect boundaries

Never access paths outside the permitted workspace.

Never bypass Wayang policy, approval, sandbox, or execution controls.

---

# Execution Lifecycle

Aljabr follows:

INSPECT
→ PLAN
→ IMPLEMENT
→ VERIFY
→ REPAIR*
→ REVIEW
→ COMPLETE

`REPAIR` is entered only when verification identifies a repairable failure.

---

# INSPECT

Determine:

- project type
- language/framework
- relevant implementation
- relevant tests
- relevant usages
- relevant configuration
- existing project patterns
- constraints
- risks

Use repository tools to answer concrete questions.

Do not modify files during inspection.

Stop inspecting when sufficient evidence exists to produce a reliable plan.

---

# PLAN

Produce a structured implementation plan.

The plan must:

- contain concrete steps
- identify expected files when possible
- avoid speculative changes
- include verification
- minimize scope

Do not implement while producing the plan.

---

# IMPLEMENT

For each plan step:

1. Confirm the target.
2. Inspect current content if necessary.
3. Make the smallest appropriate change.
4. Preserve unrelated changes.
5. Inspect the resulting diff.

Prefer targeted patches over complete-file rewrites.

---

# VERIFY

Select verification appropriate to the task.

Possible checks include:

- compilation
- build
- targeted tests
- broader tests
- diagnostics
- lint
- static analysis
- final diff inspection

Verification results must be represented by tool evidence.

---

# REPAIR

When verification fails:

1. Classify the failure.
2. Inspect the relevant evidence.
3. Determine the likely cause.
4. Make a targeted repair.
5. Re-run verification.

If the failure is caused by the environment rather than the implementation,
do not modify application code merely to make the verification command pass.

Stop when the repair budget is exhausted or progress has stalled.

---

# REVIEW

Before completion:

- inspect the final diff
- compare the diff with the original task
- detect unrelated modifications
- inspect verification results
- check for temporary/debug code
- check for obvious regressions

---

# COMPLETE

Report:

- what changed
- important implementation decisions
- verification performed
- verification failures, if any
- remaining limitations

Never claim verification that was not performed.
```

This is intentionally **not enormous**.

The runtime supplies the current phase, task, evidence, tools, budget, and policy.

---

# 2. Tool descriptions

I would make each tool description extremely explicit.

For example:

## `read_file.md`

```markdown
# read_file

Read source or configuration content inside the permitted workspace.

## Use when

- exact implementation context is required
- a relevant file has already been identified
- a specific line range needs inspection

## Do not use when

- repository-wide discovery is required
- symbol relationships are required
- the requested information is already available in context

## Input

- `path`
- optional `startLine`
- optional `endLine`

## Output

Returns file content and metadata.

## Safety

The path must remain inside the permitted workspace.

This tool is read-only.
```

---

# 3. `search_code.md`

```markdown
# search_code

Search the repository for relevant source code.

## Use when

- locating an implementation
- locating usages
- discovering project patterns
- finding tests related to functionality

## Good queries

Prefer semantic questions or precise identifiers.

Examples:

- `token expiration validation`
- `validateToken`
- `authentication middleware`

Avoid performing many broad searches without a concrete question.

## Output

Returns matching files, locations, snippets, and relevance metadata.

## Safety

This tool is read-only.
```

The phrase **"concrete question"** is valuable because it encourages purposeful research.

---

# 4. `definition.md`

```markdown
# definition

Find the definition of a symbol.

## Use when

- the implementation behind a symbol is unknown
- a symbol found during search needs precise inspection

## Input

- symbol
- optional file
- optional language/project context

## Output

Returns symbol definition and location.

## Safety

Read-only.
```

---

# 5. `references.md`

This deserves special emphasis.

```markdown
# references

Find usages of a symbol.

## Use when

A planned modification could affect:

- callers
- implementations
- overrides
- tests
- public APIs
- dependency relationships

## Output

Returns references grouped by file and symbol context.

## Rule

Before changing a public or widely-used API, inspect references unless the
task explicitly establishes that compatibility is irrelevant.

## Safety

Read-only.
```

That gives Aljabr an explicit **impact-analysis habit**.

---

# 6. `apply_patch.md`

This should be one of the most constrained tools.

```markdown
# apply_patch

Apply a targeted modification to an existing file.

## Use when

- the implementation approach is understood
- the target location is known
- the modification is sufficiently specific

## Prefer

Small, localized patches.

## Avoid

- rewriting entire files
- formatting unrelated code
- opportunistic refactoring
- modifying generated files unnecessarily

## Preconditions

The expected file state must still match the state observed during inspection.

If the file changed, stop and re-inspect it.

## Output

Returns:

- affected file
- before hash
- after hash
- applied patch
- resulting change metadata

## Safety

The patch is restricted to the permitted workspace and passes through
Wayang's policy controls.
```

This ties directly into the `expectedHash` mechanism from the previous step.

---

# 7. `git_diff.md`

```markdown
# git_diff

Inspect current workspace changes.

## Use when

- reviewing implementation changes
- checking scope
- validating that unrelated changes were not introduced
- preparing final review

## Output

Returns structured file-level and line-level changes.

## Safety

Read-only.
```

I'd make this available during **IMPLEMENT**, **VERIFY**, and **REVIEW**, but not necessarily during initial planning unless useful.

---

# 8. `diagnostics.md`

```markdown
# diagnostics

Retrieve normalized compiler, language-server, or static-analysis diagnostics.

## Use when

- implementation produces errors
- symbol resolution is unclear
- verification reports compilation problems
- code intelligence is needed

## Output

Diagnostics contain:

- severity
- file
- line
- column
- code
- message
- source

## Safety

Read-only.
```

---

# 9. `run_tests.md`

```markdown
# run_tests

Execute project tests through the configured Wayang execution environment.

## Use when

- behavior has changed
- relevant tests exist
- regression verification is appropriate

## Do not

Invent arbitrary commands when a project-specific verification strategy
exists.

## Output

Returns a structured verification report including:

- command
- exit status
- duration
- test results
- diagnostics
- relevant output
- artifact references

## Safety

Execution is subject to:

- sandbox policy
- timeout
- resource limits
- workspace scope
- Wayang permissions
```

---

# 10. Add one more tool: `inspect_project`

I actually think this deserves to exist rather than forcing the model to reconstruct project metadata repeatedly.

```markdown
# inspect_project

Identify project structure and development commands.

## Output

- language
- framework
- build system
- package manager
- source roots
- test roots
- build commands
- test commands
- lint commands when detectable
- project configuration

## Safety

Read-only.
```

Then the beginning of almost every task becomes:

```text
inspect_project
      ↓
search_code
```

instead of repeatedly rediscovering Maven/npm/Cargo/etc.

---

# 11. Another valuable tool: `list_files`

This sounds trivial, but it is useful for controlled repository navigation.

```markdown
# list_files

List files and directories inside the permitted workspace.

## Use when

- understanding project structure
- locating source/test roots
- confirming paths

## Rules

Prefer bounded listings.

Do not recursively dump the entire repository into model context.

## Safety

Read-only.
```

The runtime should enforce output limits.

---

# 12. Don't expose `shell` initially

I would strongly resist:

```text
run_shell(command)
```

as a default Aljabr tool.

Instead expose:

```text
inspect_project
list_files
search_code
read_file
definition
references
diagnostics
apply_patch
git_diff
run_tests
```

Then later:

```text
run_build
run_lint
run_formatter
```

Only introduce:

```text
custom_command
```

when there is a concrete requirement.

This will prevent a lot of future security and reliability garbage.

---

# 13. Define the plan schema

Now make the model output machine-readable.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AljabrCodingPlan",
  "type": "object",
  "required": [
    "objective",
    "steps",
    "verification"
  ],
  "properties": {
    "objective": {
      "type": "string"
    },
    "steps": {
      "type": "array",
      "minItems": 1,
      "items": {
        "$ref": "#/$defs/step"
      }
    },
    "verification": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "risks": {
      "type": "array",
      "items": {
        "type": "string"
      }
    }
  },
  "$defs": {
    "step": {
      "type": "object",
      "required": [
        "id",
        "type",
        "objective"
      ],
      "properties": {
        "id": {
          "type": "string"
        },
        "type": {
          "enum": [
            "INSPECT",
            "MODIFY",
            "TEST",
            "CONFIGURE",
            "REVIEW"
          ]
        },
        "objective": {
          "type": "string"
        },
        "files": {
          "type": "array",
          "items": {
            "type": "string"
          }
        }
      }
    }
  }
}
```

Then your Java code can deserialize and validate it.

---

# 14. Repair schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AljabrRepairPlan",
  "type": "object",
  "required": [
    "failureType",
    "diagnosis",
    "action"
  ],
  "properties": {
    "failureType": {
      "enum": [
        "COMPILATION",
        "TEST_FAILURE",
        "LINT",
        "DIAGNOSTIC",
        "RUNTIME",
        "ENVIRONMENT",
        "TOOL",
        "UNKNOWN"
      ]
    },
    "diagnosis": {
      "type": "string"
    },
    "action": {
      "type": "string"
    },
    "files": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "expectedOutcome": {
      "type": "string"
    }
  }
}
```

Again:

**the model proposes; the runtime validates.**

---

# 15. Review schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AljabrReviewReport",
  "type": "object",
  "required": [
    "status",
    "summary",
    "findings"
  ],
  "properties": {
    "status": {
      "enum": [
        "PASS",
        "PASS_WITH_WARNINGS",
        "FAIL"
      ]
    },
    "summary": {
      "type": "string"
    },
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "severity",
          "message"
        ],
        "properties": {
          "severity": {
            "enum": [
              "INFO",
              "WARNING",
              "ERROR"
            ]
          },
          "message": {
            "type": "string"
          },
          "file": {
            "type": "string"
          },
          "line": {
            "type": "integer"
          }
        }
      }
    }
  }
}
```

---

# 16. Now define phase-specific tool availability

This should become actual code.

```java
@ApplicationScoped
public class DefaultToolAvailabilityPolicy
        implements ToolAvailabilityPolicy {

    private static final Map<CodingPhase,
        Set<CodingCapability>> ALLOWED = Map.of(

        INSPECTING, Set.of(
            FILE_READ,
            SEARCH,
            SYMBOL_SEARCH,
            DEFINITION,
            REFERENCES,
            DIAGNOSTICS
        ),

        PLANNING, Set.of(
            FILE_READ,
            SEARCH,
            SYMBOL_SEARCH,
            DEFINITION,
            REFERENCES,
            DIAGNOSTICS
        ),

        EXECUTING, Set.of(
            FILE_READ,
            SEARCH,
            SYMBOL_SEARCH,
            DEFINITION,
            REFERENCES,
            DIAGNOSTICS,
            FILE_WRITE,
            GIT_DIFF,
            TEST
        ),

        VERIFYING, Set.of(
            FILE_READ,
            DIAGNOSTICS,
            GIT_DIFF,
            TEST
        ),

        REPAIRING, Set.of(
            FILE_READ,
            SEARCH,
            SYMBOL_SEARCH,
            DEFINITION,
            REFERENCES,
            DIAGNOSTICS,
            FILE_WRITE,
            GIT_DIFF,
            TEST
        ),

        REVIEWING, Set.of(
            FILE_READ,
            DIAGNOSTICS,
            GIT_DIFF
        )
    );

    @Override
    public boolean available(
            CodingPhase phase,
            CodingCapability capability) {

        return ALLOWED
            .getOrDefault(phase, Set.of())
            .contains(capability);
    }
}
```

This should be **runtime enforcement**, not merely prompt text.

---

# 17. Add a "read-only mode"

This is very useful for:

```text
"Explain this code."
"Review this PR."
"Find the bug but don't change anything."
"Why is this test failing?"
```

Represent it explicitly:

```java
public enum CodingMode {
    IMPLEMENT,
    REVIEW,
    DEBUG,
    EXPLAIN,
    EXPLORE
}
```

Then:

```java
public record CodingTask(
    String request,
    CodingMode mode,
    CodingScope scope,
    CodingBudget budget
) {}
```

For:

```text
EXPLAIN
```

the runtime simply cannot expose:

```text
FILE_WRITE
FILE_DELETE
```

No prompt trick required.

---

# 18. This is where I would remove a lot of generic-agent baggage

For Aljabr, don't expose generic tools such as:

```text
remember
think
summarize
browse
ask_user
```

unless they have a concrete coding-specific role.

For example, "think" is unnecessary because reasoning is internal.

"Summarize" is unnecessary because context management should handle it.

"Remember" should be handled by Wayang memory policy.

This keeps Aljabr clean.

---

# 19. User interaction should be explicit

There *is* one interaction capability I would keep:

```java
CodingInteraction
```

for cases where the agent genuinely cannot safely continue.

For example:

```text
The repository contains two authentication implementations.
Changing either could affect production behavior.

Which one should be modified?
```

But don't ask the user:

```text
Should I inspect UserService?
```

when Aljabr can inspect it autonomously.

A good rule:

> **Ask only when ambiguity materially affects the implementation.**

---

# 20. Add an ambiguity detector

```java
public record CodingAmbiguity(
    String question,
    List<String> options,
    String impact
) {}
```

Only transition:

```text
EXECUTING
   ↓
WAITING_USER
```

when the ambiguity crosses a threshold.

Otherwise:

```text
choose evidence-supported interpretation
```

This prevents annoying agent behavior.

---

# 21. Final output should be structured too

Don't let the final answer be arbitrary model prose.

Use:

```java
public record CodingResult(
    CodingStatus status,
    String summary,
    ChangeSet changes,
    VerificationReport verification,
    ReviewReport review,
    List<ArtifactReference> artifacts,
    int repairAttempts
) {}
```

Then the UI can render:

```text
Completed

Changed:
  UserService.java
  UserServiceTest.java

Verification:
  ✓ compile
  ✓ 8 tests

Review:
  ✓ no unrelated changes

Repair attempts:
  1
```

The model can still produce a human-friendly explanation, but the runtime owns the authoritative facts.

---

# 22. This gives you an important separation

There are now **three kinds of information**:

### Model-generated reasoning

```text
hypothesis
diagnosis
plan explanation
```

### Runtime-generated facts

```text
file changed
test passed
exit code
diff
permissions
tool execution
```

### User-provided facts

```text
original task
clarifications
approval
```

Never mix these casually.

Especially:

> **Model saying "tests pass" is not evidence that tests passed.**

Only `run_tests` can establish that.

---

# 23. The resulting Aljabr package

I'd now expect something like:

```text
aljabr/
├── api/
│   ├── CodingTask.java
│   ├── CodingResult.java
│   ├── CodingMode.java
│   └── CodingPhase.java
│
├── core/
│   ├── CodingLoop.java
│   ├── CodingStateMachine.java
│   ├── CodingPlanner.java
│   ├── CodingExecutor.java
│   ├── VerificationEngine.java
│   ├── RepairLoop.java
│   └── ReviewEngine.java
│
├── tools/
│   ├── CodingTool.java
│   ├── CodingToolRegistry.java
│   ├── ToolExecutionService.java
│   ├── ReadFileTool.java
│   ├── SearchCodeTool.java
│   ├── DefinitionTool.java
│   ├── ReferencesTool.java
│   ├── ApplyPatchTool.java
│   ├── GitDiffTool.java
│   ├── DiagnosticsTool.java
│   └── RunTestsTool.java
│
├── policy/
│   ├── CodingPolicy.java
│   ├── ToolAvailabilityPolicy.java
│   ├── CodingBudget.java
│   └── CodingScope.java
│
├── context/
│   ├── CodingContext.java
│   ├── CodingContextRenderer.java
│   ├── CodingObservation.java
│   └── ResearchQuestion.java
│
├── memory/
│   ├── CodingMemoryPolicy.java
│   └── ResearchArtifact.java
│
└── skill/
    └── aljabr/
        ├── Skill.md
        ├── protocols/
        ├── tools/
        └── schemas/
```

The important part is that **the skill folder isn't the runtime**.

---

# 24. The next major piece: `CodingLoop` implementation

We've now defined enough contracts that I would stop adding abstractions.

The next step should be the actual **orchestrator implementation**, roughly:

```java
public Uni<CodingResult> run(CodingTask task) {

    return createRun(task)

        .flatMap(this::inspect)

        .flatMap(this::createPlan)

        .flatMap(this::executePlan)

        .flatMap(this::verify)

        .flatMap(this::repairIfRequired)

        .flatMap(this::review)

        .flatMap(this::complete)

        .onFailure()
        .recoverWithUni(this::failSafely);
}
```

But I would make the real implementation more interesting than that:

```text
                    ┌──────────────┐
                    │   INSPECT    │
                    └──────┬───────┘
                           ↓
                    ┌──────────────┐
                    │     PLAN     │
                    └──────┬───────┘
                           ↓
                  ┌──────────────────┐
                  │ EXECUTE PLAN STEP│
                  └────────┬─────────┘
                           ↓
                    ┌──────────────┐
                    │ STEP COMPLETE│
                    └──────┬───────┘
                           │
                     more steps?
                       /       \
                     yes        no
                     │           │
                     └───┐       ↓
                         │    VERIFY
                         │       ↓
                         │   ┌───┴────┐
                         │ PASS      FAIL
                         │   │         │
                         │   ↓         ↓
                         │ REVIEW    REPAIR
                         │             │
                         │             └──→ VERIFY
                         ↓
                    next step
```

And **every arrow persists state before continuing**.

That is the point where Aljabr becomes a real durable coding workflow rather than a prompt wrapped around an LLM.

The next implementation pass should therefore be the **actual `CodingLoop`, `PlanExecutor`, `VerificationEngine`, and `RepairLoop` Java code**, including the failure/recovery paths and how they call Wayang's existing execution/policy/checkpoint infrastructure.



Yes. The next layer should be the **actual orchestration code**. This is where Aljabr stops being a collection of prompts/tools and becomes a reliable coding state machine.

I would build it around one principle:

> **Every meaningful transition is durable, observable, resumable, and policy-checked.**

## 1. Core state machine

Don't implement Aljabr as one giant `while` loop.

Define explicit phases:

```java
public enum CodingPhase {
    CREATED,
    INSPECTING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    REPAIRING,
    REVIEWING,
    WAITING_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

And state:

```java
public record CodingRun(
    String runId,
    CodingTask task,

    CodingPhase phase,

    CodingContext context,

    Optional<CodingPlan> plan,

    int currentStep,

    int repairAttempts,

    ChangeSet changes,

    List<VerificationReport> verifications,

    Optional<ReviewReport> review,

    CodingBudget budget
) {}
```

The important thing is that **this is the source of truth**, not the LLM conversation.

---

# 2. The state machine

I'd make transitions explicit:

```java
public interface CodingStateMachine {

    Uni<CodingRun> advance(CodingRun run);
}
```

Implementation:

```java
@ApplicationScoped
public class DefaultCodingStateMachine
        implements CodingStateMachine {

    @Override
    public Uni<CodingRun> advance(CodingRun run) {

        return switch (run.phase()) {

            case CREATED ->
                transitionToInspection(run);

            case INSPECTING ->
                inspect(run);

            case PLANNING ->
                plan(run);

            case EXECUTING ->
                executeStep(run);

            case VERIFYING ->
                verify(run);

            case REPAIRING ->
                repair(run);

            case REVIEWING ->
                review(run);

            case WAITING_USER ->
                Uni.createFrom().item(run);

            case COMPLETED,
                 FAILED,
                 CANCELLED ->
                Uni.createFrom().item(run);
        };
    }
}
```

This gives you a very important property:

```text
crash
 ↓
load CodingRun
 ↓
inspect phase
 ↓
resume
```

No need to reconstruct what happened from chat history.

---

# 3. Persist before executing

This is one of the most important implementation details.

Never do:

```java
executeTool();
persistState();
```

because:

```text
executeTool()
   ↓
process crashes
   ↓
state says tool never happened
```

Instead record an execution intent:

```text
STATE
  ↓
TOOL_CALL_STARTED
  ↓
execute
  ↓
TOOL_CALL_COMPLETED
  ↓
STATE
```

So your event model becomes:

```java
public sealed interface CodingEvent
        permits
            RunCreated,
            PhaseChanged,
            ToolCallStarted,
            ToolCallCompleted,
            PlanCreated,
            StepCompleted,
            VerificationCompleted,
            RepairAttempted,
            ReviewCompleted {}
```

---

# 4. `CodingEventStore`

```java
public interface CodingEventStore {

    Uni<Void> append(
        String runId,
        CodingEvent event
    );

    Multi<CodingEvent> events(
        String runId
    );
}
```

Then state can be rebuilt:

```java
public interface CodingRunRepository {

    Uni<CodingRun> load(String runId);

    Uni<Void> save(CodingRun run);
}
```

Initially you can implement this with ordinary persistence.

Later, event sourcing can become more sophisticated.

Don't over-engineer it yet.

---

# 5. The main `CodingLoop`

Now the real orchestrator:

```java
@ApplicationScoped
public class CodingLoop {

    private final CodingStateMachine stateMachine;
    private final CodingRunRepository runs;

    public Uni<CodingResult> run(String runId) {

        return Multi.createFrom()
            .ticks()
            .every(Duration.ofMillis(1))

            .onItem()
            .transformToUniAndMerge(ignore ->
                runs.load(runId)
                    .flatMap(stateMachine::advance)
                    .flatMap(runs::save)
            )

            .select()
            .where(run -> isTerminal(run.phase()))

            .select()
            .first()

            .map(this::toResult);
    }

    private boolean isTerminal(CodingPhase phase) {
        return phase == CodingPhase.COMPLETED
            || phase == CodingPhase.FAILED
            || phase == CodingPhase.CANCELLED;
    }
}
```

However, in production I'd avoid the timer-driven version and use an explicit recursive/continuation loop:

```java
public Uni<CodingResult> run(String runId) {
    return advanceUntilTerminal(runId);
}

private Uni<CodingResult> advanceUntilTerminal(
        String runId) {

    return runs.load(runId)
        .flatMap(stateMachine::advance)
        .flatMap(runs::save)
        .flatMap(run ->
            isTerminal(run.phase())
                ? Uni.createFrom().item(toResult(run))
                : advanceUntilTerminal(runId)
        );
}
```

With appropriate asynchronous execution safeguards.

---

# 6. Inspection implementation

Now implement actual inspection.

```java
private Uni<CodingRun> inspect(CodingRun run) {

    return codingTools.execute(
        run,
        ToolRequest.inspectProject()
    )
    .flatMap(projectEvidence ->
        context.addEvidence(
            run,
            projectEvidence
        )
    )
    .flatMap(updated ->
        codingTools.execute(
            updated,
            ToolRequest.searchCode(
                updated.task().request()
            )
        )
    )
    .flatMap(evidence ->
        context.addEvidence(run, evidence)
    )
    .flatMap(this::evaluateInspectionCompletion);
}
```

But don't hardcode "search once."

Instead create an inspection controller.

---

# 7. `InspectionController`

```java
public interface InspectionController {

    Uni<InspectionDecision> evaluate(
        CodingContext context
    );
}
```

```java
public record InspectionDecision(
    boolean complete,
    Optional<ResearchQuestion> nextQuestion,
    List<String> reasons
) {}
```

Example:

```text
INSPECTION

Project:
  Kotlin + Gradle

Question:
  Where is token expiration checked?

Evidence:
  TokenValidator.kt:84

Question:
  Who calls TokenValidator?

Evidence:
  AuthService.kt
  RefreshService.kt

Question:
  Are there tests?

Evidence:
  TokenValidatorTest.kt

→ sufficient
```

Now Aljabr doesn't need a fixed inspection sequence.

---

# 8. Research question loop

```java
private Uni<CodingRun> inspect(CodingRun run) {

    return inspectionController
        .evaluate(run.context())

        .flatMap(decision -> {

            if (decision.complete()) {
                return transition(
                    run,
                    CodingPhase.PLANNING
                );
            }

            return codingTools
                .execute(
                    run,
                    createResearchRequest(
                        decision.nextQuestion().orElseThrow()
                    )
                )
                .flatMap(evidence ->
                    addEvidence(run, evidence)
                );
        });
}
```

This is where your earlier **research cache** plugs directly in.

---

# 9. Check research cache first

Before invoking a tool:

```java
private Uni<ToolEvidence> research(
        CodingRun run,
        ResearchQuestion question) {

    return researchCache.find(
        question,
        run.context().repository()
    )
    .onItem()
    .ifNotNull()
    .continueWith(cached ->
        cached.toEvidence()
    )
    .onItem()
    .ifNull()
    .switchTo(() ->
        codingTools.execute(
            run,
            question.toToolRequest()
        )
    );
}
```

But cache validity must consider repository state.

```java
public record ResearchCacheKey(
    String repositoryRevision,
    String questionHash
) {}
```

So:

```text
repo abc123
+
question "where is auth validation?"
=
cached evidence
```

A changed repository produces a different key.

---

# 10. Planning

Once inspection completes:

```java
private Uni<CodingRun> plan(CodingRun run) {

    return model.generateStructured(
        buildPlanningContext(run),
        CodingPlan.class
    )
    .flatMap(plan ->
        planValidator.validate(plan)
            .replaceWith(plan)
    )
    .flatMap(plan ->
        eventStore.append(
            run.runId(),
            new PlanCreated(plan)
        )
        .replaceWith(
            run.withPlan(plan)
                 .withPhase(CodingPhase.EXECUTING)
        )
    );
}
```

Notice:

```text
LLM
 ↓
structured output
 ↓
schema validation
 ↓
runtime accepts/rejects
```

Never blindly trust model JSON.

---

# 11. Plan validation

```java
public interface PlanValidator {

    Uni<Void> validate(
        CodingPlan plan,
        CodingContext context
    );
}
```

Validate things such as:

```text
✓ steps exist
✓ IDs unique
✓ no forbidden paths
✓ files inside workspace
✓ no unsupported operation
✓ plan doesn't violate task scope
✓ verification exists
```

Example:

```java
if (plan.steps().stream()
        .anyMatch(step -> step.files().stream()
            .anyMatch(scope::isForbidden))) {

    return Uni.createFrom()
        .failure(
            new PlanRejectedException(
                "Plan accesses forbidden path"
            )
        );
}
```

---

# 12. Execute exactly one step at a time

Don't send the entire plan to an unconstrained model and say:

> Go implement everything.

Instead:

```java
private Uni<CodingRun> executeStep(CodingRun run) {

    PlanStep step =
        run.plan()
           .orElseThrow()
           .steps()
           .get(run.currentStep());

    return stepExecutor.execute(run, step)
        .flatMap(result ->
            recordStepCompletion(run, step, result)
        );
}
```

This gives you:

```text
Step 1
 ↓
persist
 ↓
Step 2
 ↓
persist
 ↓
Step 3
```

If the process crashes:

```text
currentStep = 2
```

Resume there.

---

# 13. `PlanStepExecutor`

```java
@ApplicationScoped
public class PlanStepExecutor {

    public Uni<StepExecutionResult> execute(
            CodingRun run,
            PlanStep step) {

        return model.generateStructured(
            buildStepContext(run, step),
            ImplementationDecision.class
        )
        .flatMap(decision ->
            validateDecision(run, decision)
        )
        .flatMap(decision ->
            applyDecision(run, decision)
        );
    }
}
```

The model doesn't directly get an unrestricted file-writing capability.

It proposes:

```java
public record ImplementationDecision(
    String objective,
    List<PatchOperation> patches,
    List<String> reasoning,
    List<String> verification
) {}
```

The runtime then validates and applies those patches.

---

# 14. `PatchOperation`

```java
public record PatchOperation(
    Path path,
    String expectedHash,
    List<PatchHunk> hunks
) {}
```

This gives you optimistic concurrency.

Suppose Aljabr read:

```text
UserService.java
hash = abc123
```

Then another process/user changes it:

```text
hash = xyz789
```

Aljabr tries:

```text
expectedHash = abc123
```

Runtime rejects:

```text
FILE_CHANGED_SINCE_INSPECTION
```

Then Aljabr must re-inspect.

This is far safer than blindly overwriting.

---

# 15. Verification engine

Now:

```java
@ApplicationScoped
public class VerificationEngine {

    public Uni<VerificationReport> verify(
            CodingRun run) {

        return strategyResolver
            .resolve(run.task(), run.context())
            .flatMap(strategy ->
                executeChecks(run, strategy)
            );
    }
}
```

A verification report:

```java
public record VerificationReport(
    VerificationStatus status,
    List<VerificationCheckResult> checks,
    List<VerificationFailure> failures
) {}
```

Example:

```text
Verification

✓ compilation
✓ TokenValidatorTest
✓ AuthServiceTest

Result: PASS
```

---

# 16. Verification strategy

```java
public interface VerificationStrategy {

    boolean supports(
        CodingTask task,
        ProjectInfo project
    );

    List<VerificationCheck> checks(
        CodingTask task,
        CodingContext context
    );
}
```

For example:

```java
public final class JavaBugFixVerification
        implements VerificationStrategy {

    @Override
    public List<VerificationCheck> checks(...) {
        return List.of(
            compile(),
            targetedTests(),
            diffInspection()
        );
    }
}
```

Later you can have:

```text
Java
Kotlin
Python
Rust
TypeScript
Go
documentation
configuration
```

without changing Aljabr's core loop.

---

# 17. Verification routing

The state transition becomes:

```java
private CodingRun afterVerification(
        CodingRun run,
        VerificationReport report) {

    if (report.status() == PASS) {
        return run.withPhase(CodingPhase.REVIEWING);
    }

    if (report.isEnvironmentFailure()) {
        return run.withPhase(CodingPhase.FAILED);
    }

    if (run.repairAttempts() >=
            run.budget().maxRepairAttempts()) {

        return run.withPhase(CodingPhase.FAILED);
    }

    return run
        .withVerification(report)
        .withPhase(CodingPhase.REPAIRING);
}
```

That gives you deterministic routing.

---

# 18. Repair loop

```java
private Uni<CodingRun> repair(CodingRun run) {

    VerificationFailure failure =
        run.latestVerification()
           .primaryFailure();

    return model.generateStructured(
        buildRepairContext(run, failure),
        RepairPlan.class
    )
    .flatMap(plan ->
        repairValidator.validate(run, plan)
            .replaceWith(plan)
    )
    .flatMap(plan ->
        applyRepair(run, plan)
    )
    .flatMap(updated ->
        updated
            .incrementRepairAttempts()
            .withPhase(CodingPhase.VERIFYING)
            .persist()
    );
}
```

---

# 19. Add repair fingerprints

This is where we stop infinite loops.

```java
public record RepairFingerprint(
    FailureFingerprint failure,
    String diagnosisHash,
    String patchHash
) {}
```

Before applying a repair:

```java
if (repairHistory.contains(fingerprint)) {
    return fail(
        run,
        "Repeated unsuccessful repair strategy"
    );
}
```

You can also detect:

```text
same failure
different patch
same resulting failure
```

which means the agent is likely stuck.

---

# 20. Add progress detection

A better version:

```java
public record RepairProgress(
    boolean failureChanged,
    boolean diagnosticsImproved,
    boolean testsImproved,
    boolean scopeImproved
) {}
```

Example:

```text
Attempt 1
10 failing tests

Attempt 2
10 failing tests

Attempt 3
10 failing tests
```

That's clearly no progress.

Whereas:

```text
Attempt 1
10 failures

Attempt 2
4 failures

Attempt 3
1 failure
```

is productive.

So allow continued repair when evidence shows progress.

---

# 21. Review engine

```java
@ApplicationScoped
public class ReviewEngine {

    public Uni<ReviewReport> review(
            CodingRun run) {

        return diffService.currentDiff()
            .flatMap(diff ->
                model.generateStructured(
                    buildReviewContext(run, diff),
                    ReviewReport.class
                )
            )
            .flatMap(report ->
                reviewValidator.validate(run, report)
                    .replaceWith(report)
            );
    }
}
```

But don't let the model determine everything.

The runtime should independently calculate:

```text
files changed
lines added
lines removed
forbidden paths
uncommitted baseline
```

---

# 22. Baseline is essential

At the beginning:

```java
public record WorkspaceBaseline(
    String revision,
    ChangeSet preExistingChanges
) {}
```

Suppose the user already had:

```text
UserService.java modified
README.md modified
```

Aljabr modifies:

```text
UserService.java
```

Final diff must distinguish:

```text
USER PRE-EXISTING CHANGE
+
ALJABR CHANGE
```

Otherwise Aljabr's final review may incorrectly claim:

> I changed README.md.

This is a major detail for a serious coding agent.

---

# 23. `ChangeSet` should therefore have provenance

```java
public record FileChange(
    Path path,
    ChangeOrigin origin,
    Diff diff
) {}
```

```java
public enum ChangeOrigin {
    USER_PREEXISTING,
    ALJABR,
    EXTERNAL
}
```

Now your final result can accurately say:

```text
Aljabr changed:
  UserService.java
  UserServiceTest.java

Existing user changes preserved:
  README.md
```

---

# 24. Completion should be deterministic

Don't let the LLM decide:

```text
"I'm done."
```

Instead:

```java
private boolean canComplete(CodingRun run) {

    return run.phase() == REVIEWING
        && run.review()
            .map(r -> r.status() != FAIL)
            .orElse(false)
        && run.latestVerification()
            .map(v -> v.isAcceptable())
            .orElse(false);
}
```

Then:

```text
REVIEW
 ↓
runtime checks
 ↓
COMPLETED
```

The model contributes evidence; the runtime decides terminal state.

---

# 25. Failure handling

Every layer needs controlled failure.

```java
private Uni<CodingRun> failSafely(
        CodingRun run,
        Throwable error) {

    return eventStore.append(
        run.runId(),
        new RunFailed(error)
    )
    .replaceWith(
        run.withPhase(CodingPhase.FAILED)
           .withFailure(error)
    );
}
```

And distinguish:

```text
MODEL_FAILURE
TOOL_FAILURE
POLICY_FAILURE
ENVIRONMENT_FAILURE
TIMEOUT
BUDGET_EXCEEDED
USER_CANCELLED
IMPLEMENTATION_FAILURE
```

Don't collapse everything into:

```text
Exception
```

---

# 26. Budget should be first-class

```java
public record CodingBudget(
    int maxModelCalls,
    int maxToolCalls,
    int maxInspectionCalls,
    int maxRepairAttempts,
    Duration maxRuntime,
    long maxOutputTokens
) {}
```

Runtime tracks consumption:

```java
public record BudgetUsage(
    int modelCalls,
    int toolCalls,
    int inspectionCalls,
    int repairAttempts,
    Duration runtime
) {}
```

Before every operation:

```java
budgetGuard.check(
    run,
    OperationType.TOOL_CALL
);
```

This is another thing that should **never depend on prompt compliance**.

---

# 27. Cancellation

The runtime should be able to do:

```java
codingRun.cancel(runId);
```

which transitions:

```text
EXECUTING
   ↓
CANCEL_REQUESTED
   ↓
safe tool boundary
   ↓
CANCELLED
```

Don't kill arbitrary subprocesses without cleanup.

For command execution:

```text
Wayang
 ↓
execution handle
 ↓
cancellation
 ↓
terminate process
 ↓
cleanup sandbox
 ↓
persist cancellation
```

---

# 28. The resulting execution architecture

At this point:

```text
                     USER
                       │
                       ▼
                ┌─────────────┐
                │ CodingTask  │
                └──────┬──────┘
                       │
                       ▼
                ┌─────────────┐
                │ CodingLoop  │
                └──────┬──────┘
                       │
                       ▼
              ┌──────────────────┐
              │ State Machine     │
              └────────┬─────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
    Inspector       Planner        Executor
        │              │              │
        └──────────────┼──────────────┘
                       │
                       ▼
                Verification
                       │
                 ┌─────┴─────┐
                 ▼           ▼
                PASS        FAIL
                 │           │
                 │        Repair
                 │           │
                 │           └─────→ Verify
                 │
                 ▼
                Review
                 │
                 ▼
              Complete
```

And beneath all of it:

```text
Wayang Runtime
──────────────────────────────────────
Policy
Permissions
Sandbox
Tool Registry
Tool Executor
Model Gateway
Context
Memory
Research Cache
Checkpoint
Event Store
Budget
Cancellation
Observability
```

That is the architecture I would aim for.

---

# 29. One important refinement: don't make Aljabr own Wayang's tool execution

Aljabr should say:

```java
toolExecutor.execute(
    ToolRequest.searchCode(...)
);
```

It should **not** implement:

```java
Runtime.exec(...)
Files.readString(...)
ProcessBuilder(...)
```

Those belong underneath Wayang.

So:

```text
Aljabr
  ↓
CodingTool interface
  ↓
Wayang Tool Registry
  ↓
Policy
  ↓
Sandbox
  ↓
actual execution
```

This makes Aljabr portable and keeps security centralized.

---

# 30. The next thing I'd build after this

At this point I would **not add more Aljabr features yet**.

The next practical task is to connect this architecture to the actual Wayang runtime you already have.

Specifically, map:

```text
CodingLoop
    ↓
existing Wayang Agent execution

CodingTool
    ↓
existing Tool abstraction

Model invocation
    ↓
existing model gateway

CodingContext
    ↓
existing context/memory system

ResearchArtifact
    ↓
existing cache/memory

CodingEvent
    ↓
existing checkpoint/event mechanism

CodingBudget
    ↓
existing runtime budget

CodingScope
    ↓
existing sandbox/permission system
```

That integration is important because **we don't want Aljabr to accidentally become a second runtime inside Wayang**.

The ideal end state is:

```text
                    WAYANG
 ┌─────────────────────────────────────────┐
 │                                         │
 │  Runtime / Policy / Memory / Sandbox   │
 │  Context / Tools / Checkpoint / Model  │
 │                                         │
 │               ┌─────────┐               │
 │               │ ALJABR  │               │
 │               │         │               │
 │               │ inspect │               │
 │               │ plan    │               │
 │               │ execute │               │
 │               │ verify  │               │
 │               │ repair  │               │
 │               │ review  │               │
 │               └─────────┘               │
 │                                         │
 └─────────────────────────────────────────┘
```

**Aljabr should be a specialized intelligence/workflow package, not a competing framework.**

And given your earlier Wayang direction, I think the *next* step should be even more concrete: take the actual interfaces/classes in your current `wayang-framework` and `wayang-runtime` and produce a **1:1 mapping of the above Aljabr components onto them**, including which existing abstractions should be reused, which should be extended, and which are now redundant garbage that should be removed.


Yes. Now that I can see the **current Wayang + Aljabr code**, I would change direction slightly from the generic architecture I described before.

You already have much of the runtime infrastructure. The biggest opportunity is **not to build a second `CodingLoop` beside Wayang**. Instead, make Aljabr a specialized execution strategy/state machine on top of the existing runtime.

The current code already has `AgentExecutionService`, `DefaultAgentExecution`, `AgentDecision`, `AgentToolExecutor`, `CheckpointStore`, tool governance, execution cache, and strategy abstractions.  

So I'd now refactor toward this.

---

# 1. First: identify the current architectural duplication

Right now Aljabr has:

```text
CoderOrchestrator
    ↓
ExecutionStrategy
    ├── ReActExecutionStrategy
    └── PlanAndSolveExecutionStrategy
```

but those strategies are currently stubs—they return `"ReAct strategy executed"` / `"PlanAndSolve strategy executed"` rather than executing the actual coding workflow.  

Meanwhile Wayang already has:

```text
AgentExecutionService
    ↓
DefaultAgentExecution
    ↓
Agent / ReActAgent
    ↓
AgentToolExecutor
    ↓
Tool
```

and `AgentExecutionService` already creates and resumes executions through `CheckpointStore`. 

So:

> **Do not create another independent execution runtime inside `CoderOrchestrator`.**

That's the first cleanup.

---

# 2. Target architecture

I'd make this:

```text
                 Wayang Runtime
┌─────────────────────────────────────────────┐
│                                             │
│ AgentExecutionService                      │
│          │                                  │
│          ▼                                  │
│ DefaultAgentExecution                      │
│          │                                  │
│          ▼                                  │
│ AgentStrategy                               │
│          │                                  │
│          ▼                                  │
│      AljabrStrategy                         │
│          │                                  │
│    ┌─────┼─────────┐                        │
│    ▼     ▼         ▼                        │
│ Inspect Plan    Execute/Verify             │
│                    │                        │
│                    ▼                        │
│                  Repair                     │
│                    │                        │
│                    ▼                        │
│                  Review                     │
│                                             │
│ ToolExecutor / Policy / Cache / Checkpoint │
└─────────────────────────────────────────────┘
```

This fits the existing `AgentStrategy` abstraction, whose job is explicitly to decide the next action based on semantic agent state and physical execution state. 

---

# 3. Aljabr should become a strategy, not another Agent runtime

Create:

```java
@ApplicationScoped
public final class AljabrStrategy
        implements AgentStrategy {

    @Override
    public AgentDecision decide(
            AgentContext context,
            ExecutionStatus status) {

        // determine coding phase
        // inspect current state
        // decide next model/tool/approval/finish action
    }
}
```

But there is one important limitation:

The current `AgentStrategy` interface is too small for a serious coding workflow:

```java
AgentDecision decide(
    AgentContext context,
    ExecutionStatus status
);
```

It doesn't expose:

* tool execution history
* coding phase
* verification state
* repair attempts
* plan
* workspace baseline
* repository revision

So **don't overload `AgentStrategy` with Aljabr-specific state**.

Instead introduce:

```java
public interface CodingStrategy
        extends AgentStrategy {

    CodingDecision decide(
        CodingContext context,
        ExecutionStatus status
    );
}
```

Keep generic Wayang untouched.

---

# 4. `CodingContext` should wrap existing `AgentContext`

Don't replace `AgentContext`.

Use composition:

```java
public record CodingContext(
    AgentContext agent,

    CodingPhase phase,

    Optional<CodingPlan> plan,

    int currentStep,

    int repairAttempts,

    WorkspaceSnapshot workspace,

    List<CodingObservation> observations,

    List<VerificationReport> verifications
) {}
```

This means:

```text
AgentContext
    +
coding-specific state
```

rather than creating another competing context system.

---

# 5. Most importantly: use the existing `AgentDecision`

You already have:

```text
CallModel
ExecuteTool
WaitForApproval
Finish
Fail
Retry
```

in `AgentDecision`. 

Don't create:

```java
CodingDecision
ToolDecision
RepairDecision
AljabrDecision
```

unless there's a genuine missing semantic.

Instead extend `AgentDecision` if required.

For example, if you need phase transitions:

```java
public record TransitionPhase(
    CodingPhase phase
) implements AgentDecision {}
```

But I would initially avoid even that.

The existing:

```text
CallModel
ExecuteTool
WaitForApproval
Finish
Retry
Fail
```

is probably sufficient.

---

# 6. The real Aljabr loop becomes

Conceptually:

```java
public AgentDecision decide(
        CodingContext context,
        ExecutionStatus status) {

    return switch (context.phase()) {

        case INSPECTING ->
            decideInspection(context);

        case PLANNING ->
            decidePlanning(context);

        case EXECUTING ->
            decideImplementation(context);

        case VERIFYING ->
            decideVerification(context);

        case REPAIRING ->
            decideRepair(context);

        case REVIEWING ->
            decideReview(context);

        case WAITING_USER ->
            AgentDecision.waitForApproval(...);

        case COMPLETED ->
            AgentDecision.finish(...);

        default ->
            AgentDecision.fail(...);
    };
}
```

The existing runtime remains responsible for actually executing the returned decision.

That is the cleanest integration.

---

# 7. Don't make the model directly control the phase

This is a subtle but important distinction.

Bad:

```text
LLM:
"Now I'm in verification."
```

Better:

```text
Runtime:
verification tool completed successfully

Aljabr state:
VERIFYING → REVIEWING
```

The LLM can **recommend** a transition, but runtime state determines it.

---

# 8. Use `AgentDecision.CallModel` for reasoning/planning

For example:

```java
private AgentDecision decideInspection(
        CodingContext context) {

    if (inspectionComplete(context)) {
        return callModel(
            buildPlanningPrompt(context)
        );
    }

    return callModel(
        buildInspectionPrompt(context)
    );
}
```

The model then chooses a tool.

Wayang executes it through the existing `AgentToolExecutor`.

This is important because the existing executor already has a serious governance pipeline:

```text
cache
 ↓
tool existence
 ↓
schema validation
 ↓
budget
 ↓
policy
 ↓
approval
 ↓
guardrail
 ↓
circuit breaker
 ↓
retry
 ↓
timeout
 ↓
tool execution
```

The current implementation explicitly contains those stages.  

**Aljabr should not duplicate any of that.**

---

# 9. Your existing tool cache is already useful

This is especially relevant to our earlier discussion.

`DefaultAgentToolExecutor` already calculates a tool input hash and looks up the execution cache before executing the tool. 

So don't immediately build:

```text
AljabrResearchCache
```

on top of it.

Instead distinguish two caches:

### Tool execution cache

Already exists:

```text
tool + arguments
        ↓
cached ToolResult
```

Useful for:

```text
read file
search
deterministic commands
```

### Research knowledge cache

Still worth adding:

```text
research question
+ repository revision
        ↓
ResearchArtifact
```

Those are different things.

---

# 10. Extend the existing execution cache rather than creating another cache API

You already have:

```text
ExecutionCache
ExecutionCacheEntry
CacheNamespace
```

and the tool executor uses `CacheNamespace.TOOL`. 

Add a namespace:

```java
public enum CacheNamespace {
    TOOL,
    RESEARCH,
    PROJECT,
    CONTEXT
}
```

Then:

```java
ResearchCache
```

doesn't need a whole new storage implementation.

It becomes:

```java
public final class ExecutionResearchCache {

    private final ExecutionCache cache;

    public Optional<ResearchArtifact> get(
            ResearchCacheKey key) {

        return cache.lookup(
            CacheNamespace.RESEARCH,
            key.tenantId(),
            key.userId(),
            key.hash()
        )
        ...
    }
}
```

That is much cleaner.

---

# 11. The same applies to checkpointing

You already have `CheckpointStore`.

`AgentExecutionService` already saves and restores `AgentContext`. 

Don't create:

```text
CodingCheckpointStore
AljabrCheckpointStore
CodingRunRepository
```

as a second persistence system.

Instead evolve the checkpoint payload.

For example:

```java
public record CodingCheckpoint(
    CodingPhase phase,
    CodingPlan plan,
    int currentStep,
    int repairAttempts,
    WorkspaceSnapshot workspace,
    List<VerificationReport> verification
) {}
```

Then attach it to the existing execution context.

---

# 12. This is one area where your current `AgentContext` probably needs improvement

I would inspect how much arbitrary state `AgentContext` can carry.

If it already supports metadata/state, use it.

If not, add something like:

```java
public interface AgentContextState {

    <T> Optional<T> get(
        String key,
        Class<T> type
    );

    void put(
        String key,
        Object value
    );

    void remove(String key);
}
```

Then:

```text
AgentContext
 ├── request
 ├── conversation
 ├── execution metadata
 └── state
      └── coding
           ├── phase
           ├── plan
           ├── step
           └── verification
```

I would **not** create a second root context hierarchy.

---

# 13. Your existing `BaseReActAgent` is already designed for this bridge

This is another useful discovery.

`BaseReActAgent` already has:

```text
Provider
Memory
Tools
workspace
BudgetedContextCompiler
ContextPlanner
ToolExecutorBridge
CheckpointBridge
executionId
listeners
```

and explicitly has a policy-aware tool executor and per-step checkpoint bridge. 

That means the architecture we were proposing isn't foreign to Wayang.

I'd actually leverage this rather than build another implementation.

---

# 14. But there is one thing I would clean

This:

```java
public interface ToolExecutorBridge {
    ToolResult executeViaPolicy(
        ToolInvocation invocation,
        Supplier<ToolResult> directFallback
    ) throws Exception;
}
```

The existence of:

```text
directFallback
```

is dangerous for Aljabr.

It creates the possibility of:

```text
policy executor
     ↓
failure
     ↓
direct tool execution
     ↓
policy bypass
```

For a coding agent, I would remove that concept or make the fallback impossible in production.

Better:

```java
public interface ToolExecutorBridge {

    ToolResult execute(
        ToolInvocation invocation
    ) throws ToolExecutionException;
}
```

**One execution path.**

---

# 15. Same issue with `autoApproveTools`

`BaseReActAgent` currently exposes:

```text
autoApproveTools
```

but your runtime already has proper policy evaluation and approval handling.

For Aljabr, don't use:

```java
autoApproveTools = true;
```

as a shortcut.

Instead:

```text
Aljabr
 ↓
AgentToolExecutor
 ↓
ToolPolicyEvaluator
 ↓
ALLOW / DENY / REQUIRE_APPROVAL
```

The current executor already implements this policy decision flow. 

---

# 16. Now fix `CoderOrchestrator`

Current:

```java
List<Tool> tools =
    availableTools.stream().collect(Collectors.toList());

return strategy.execute(
    request,
    toolExecutor,
    tools
);
```

This is too generic.

The orchestrator should become a **thin adapter**.

Something like:

```java
@ApplicationScoped
public class CoderOrchestrator {

    @Inject
    AgentExecutionService executionService;

    @Inject
    AljabrAgentDefinitionFactory definitionFactory;

    public Uni<AgentResponse> execute(
            AgentRequest request) {

        AgentDefinition definition =
            definitionFactory.create(request);

        AgentExecution execution =
            executionService.create(
                definition,
                request,
                budgetFor(request)
            );

        return Uni.createFrom()
            .completionStage(execution.execute());
    }
}
```

Now the runtime does what it was designed to do.

---

# 17. `AljabrAgentDefinitionFactory`

This becomes the bridge between:

```text
Aljabr Skill
```

and:

```text
Wayang AgentDefinition
```

```java
@ApplicationScoped
public class AljabrAgentDefinitionFactory {

    public AgentDefinition create(
            AgentRequest request) {

        return AgentDefinition.builder()
            .metadata(...)
            .goal(ALJABR_GOAL)
            .build();
    }
}
```

But don't put the entire Aljabr prompt inline.

Load:

```text
skills/aljabr/Skill.md
```

through the existing Skill infrastructure.

Your runtime already has `SkillExecutor`, `SkillManifestCatalog`, `SkillValidator`, and `SkillRuntime` for `SKILL.md`-based skills. 

So the Skill becomes declarative configuration/behavior.

---

# 18. Aljabr tool set should be capability-based

Don't give Aljabr every CDI `Tool`.

The current `CoderOrchestrator` does:

```java
availableTools.stream().collect(...)
```

which effectively exposes everything available to it. 

Instead:

```java
public interface CodingToolCatalog {

    List<Tool> toolsFor(
        CodingMode mode,
        ProjectContext project
    );
}
```

For example:

```text
EXPLAIN
  read
  search
  definition
  references

IMPLEMENT
  read
  search
  definition
  references
  patch
  diff
  test

REVIEW
  read
  search
  diff
  diagnostics
```

The runtime policy still has final authority.

---

# 19. Existing framework-aware tool selection can help

Your runtime already has a tool-selection component that detects frameworks/build tools such as Maven, Gradle, Tailwind, Redux, etc., and builds a `ToolChain`. 

Don't duplicate that in Aljabr.

Instead:

```text
Aljabr
  ↓
"What capabilities do I need?"
  ↓
CodingToolCatalog
  ↓
existing framework/tool selection
  ↓
selected tools
```

But I would simplify the existing `ToolChain` later if it overlaps with the normal Tool registry.

---

# 20. One big cleanup: ReAct vs Plan-and-Solve

I would **not keep both as competing coding engines**.

Your current:

```text
react
plan_and_solve
```

strategies are mostly scaffolding. 

For Aljabr, define one:

```text
aljabr
```

with internal phases:

```text
INSPECT
PLAN
EXECUTE
VERIFY
REPAIR
REVIEW
```

The model can still use ReAct-style tool calls *inside* those phases.

So:

```text
Aljabr
  └── internal reasoning style = ReAct
```

rather than:

```text
Aljabr
 ├── ReAct agent
 └── PlanAndSolve agent
```

This is much cleaner.

---

# 21. The actual model interaction becomes

For inspection:

```text
SYSTEM
  Aljabr Skill

TASK
  Fix token expiration bug

STATE
  phase=INSPECTING

EVIDENCE
  ...

AVAILABLE CAPABILITIES
  search_code
  read_file
  references
  definition
```

Model:

```text
tool_call(search_code, ...)
```

Runtime:

```text
AgentToolExecutor
 ↓
policy
 ↓
cache
 ↓
tool
```

Observation comes back:

```text
ToolResult
```

Then:

```text
AgentContext updated
 ↓
CheckpointStore
 ↓
next model decision
```

That is the architecture you already have most of the pieces for.

---

# 22. Where Aljabr should add new code

I would keep the new module surprisingly small:

```text
aljabr/
├── strategy/
│   └── AljabrStrategy.java
│
├── coding/
│   ├── CodingContext.java
│   ├── CodingPhase.java
│   ├── CodingPlan.java
│   ├── CodingObservation.java
│   └── VerificationReport.java
│
├── inspection/
│   └── InspectionController.java
│
├── verification/
│   ├── VerificationStrategy.java
│   └── VerificationEngine.java
│
├── repair/
│   └── RepairController.java
│
├── review/
│   └── ReviewController.java
│
├── tools/
│   └── CodingToolCatalog.java
│
└── skill/
    └── aljabr/
        └── Skill.md
```

Not:

```text
CodingRuntime
CodingExecutionService
CodingCheckpointStore
CodingToolExecutor
CodingModelGateway
CodingMemoryManager
```

because **Wayang already has those responsibilities**.

---

# 23. What I would remove from current Aljabr

Based strictly on the current source, these are the first candidates.

### Remove/replace the stub strategy implementations

They're currently returning placeholder responses. 

### Simplify `CoderOrchestrator`

It currently owns strategy discovery and raw tool collection. 

Move execution to `AgentExecutionService`.

### Don't duplicate tool execution

Wayang already has a robust `DefaultAgentToolExecutor`. 

### Don't duplicate checkpoint persistence

Wayang already has `CheckpointStore` and execution resume. 

### Don't duplicate generic caching

Wayang already has execution caching in the tool executor. 

### Don't create another generic agent runtime

`DefaultAgentExecution` is already the runtime bridge.

---

# 24. One thing I would improve in Wayang itself

The current `AgentExecutionService` creates:

```java
new DefaultAgentExecution(...)
```

and `resume()` reconstructs it from the checkpoint. 

For Aljabr, the checkpoint needs to preserve **strategy identity**.

Add something like:

```java
public record ExecutionCheckpoint(
    String executionId,
    String agentType,
    String strategyId,
    AgentContext context,
    ExecutionStatus status
) {}
```

Then:

```text
executionId
strategyId = "aljabr"
```

On resume:

```java
strategyRegistry.resolve("aljabr")
```

This avoids losing the coding strategy after a restart.

---

# 25. Also fix the runner

The current `AljabrRunner` creates:

```text
aljabr-standalone-agent
goal = "You are a helpful AI assistant..."
```

which is obviously too generic for a coding agent. 

Change it to:

```java
AgentDefinition definition =
    aljabrAgentDefinitionFactory.create(
        request,
        projectContext
    );
```

Then:

```text
aljabr-runner
   ↓
Aljabr definition
   ↓
AgentExecutionService
   ↓
Aljabr strategy
```

That makes the CLI genuinely Aljabr.

---

# 26. The final architecture I'd target now

```text
                         USER
                           │
                           ▼
                    Aljabr Runner/UI
                           │
                           ▼
                AljabrAgentDefinition
                           │
                           ▼
                AgentExecutionService
                           │
                           ▼
                 DefaultAgentExecution
                           │
                           ▼
                    AljabrStrategy
                           │
          ┌────────────────┼─────────────────┐
          ▼                ▼                 ▼
      INSPECT            PLAN             EXECUTE
          │                │                 │
          └────────────────┼─────────────────┘
                           ▼
                       VERIFY
                           │
                    ┌──────┴──────┐
                    ▼             ▼
                  PASS          FAIL
                    │             │
                    │          REPAIR
                    │             │
                    │             └──────┐
                    │                    │
                    └────────────────────┘
                           │
                           ▼
                         REVIEW
                           │
                           ▼
                        FINISH

──────────────── WAYANG SERVICES ────────────────

Model Provider
Tool Executor
Tool Policy
Approval
Guardrails
Tool Cache
Research Cache
Checkpoint
Memory
Context Compiler
Budget
Resilience
Event Ledger
Sandbox
```

This is substantially cleaner than the earlier "Aljabr has its own runtime" design.

---

# 27. And this reveals the real next task

I would now move from architecture to **surgical refactoring**.

### Phase A — cleanup

```text
1. Remove stub ReActExecutionStrategy
2. Remove stub PlanAndSolveExecutionStrategy
3. Stop CoderOrchestrator from executing strategies directly
4. Stop Aljabr from owning tool execution
5. Stop Aljabr from owning checkpoints
```

### Phase B — integration

```text
6. Add AljabrStrategy
7. Add CodingContext
8. Add CodingPhase
9. Add CodingPlan
10. Bind strategy to AgentExecution
11. Persist strategyId
```

### Phase C — coding intelligence

```text
12. InspectionController
13. Planning prompt/schema
14. VerificationEngine
15. RepairController
16. ReviewController
```

### Phase D — optimization

```text
17. Research cache
18. repository revision/fingerprint
19. workspace baseline
20. change provenance
21. repair-progress detection
```

And importantly, **don't implement all 21 at once**.

The first actual code change I'd make is **`AljabrStrategy + CodingContext + CodingPhase` and wire it into the existing `DefaultAgentExecution`**, because that establishes the correct spine. Everything else can then attach to it without creating another runtime.

The current Wayang code already gives us the right foundation: `AgentExecution`, `AgentStrategy`, `AgentDecision`, `AgentToolExecutor`, `CheckpointStore`, and the governed tool pipeline are already present.  



Absolutely. The next step is to go **one level deeper: implement the actual `AljabrStrategy` spine**, including the state transitions, structured decisions, checkpoint-safe state, and how it talks to Wayang's existing execution machinery.

The goal here is to get from:

```text
"Aljabr architecture"
```

to:

```text
request → running coding agent → inspect → plan → edit → test → repair → review → done
```

without creating another runtime.

---

# 1. Start with the smallest real Aljabr state

I would make the coding state immutable.

```java
public record CodingState(
    CodingPhase phase,

    Optional<CodingPlan> plan,

    int currentStep,

    int repairAttempts,

    Optional<VerificationReport> lastVerification,

    Optional<WorkspaceSnapshot> workspace,

    List<ResearchArtifact> research,

    List<ChangeRecord> changes
) {

    public static CodingState initial(
            WorkspaceSnapshot workspace) {

        return new CodingState(
            CodingPhase.INSPECTING,
            Optional.empty(),
            0,
            0,
            Optional.empty(),
            Optional.of(workspace),
            List.of(),
            List.of()
        );
    }

    public CodingState nextPhase(CodingPhase phase) {
        return new CodingState(
            phase,
            plan,
            currentStep,
            repairAttempts,
            lastVerification,
            workspace,
            research,
            changes
        );
    }
}
```

Don't put mutable workflow state inside the strategy object itself.

Bad:

```java
@ApplicationScoped
class AljabrStrategy {

    private CodingPhase phase;
    private int repairAttempts;
}
```

Because the strategy is shared infrastructure.

Instead:

```text
strategy = stateless
state = execution-specific
```

That is essential for concurrency.

---

# 2. `CodingPhase`

Keep it intentionally small:

```java
public enum CodingPhase {

    INSPECTING,

    PLANNING,

    EXECUTING,

    VERIFYING,

    REPAIRING,

    REVIEWING,

    WAITING_USER,

    COMPLETED,

    FAILED,

    CANCELLED
}
```

Don't add things like:

```text
READING_FILE
SEARCHING
RUNNING_TEST
COMPILING
PATCHING
```

Those are **operations**, not phases.

Otherwise your state machine becomes enormous.

---

# 3. `CodingContext`

Now bridge it into Wayang.

```java
public record CodingContext(
    AgentContext agent,
    CodingState coding
) {

    public CodingPhase phase() {
        return coding.phase();
    }

    public CodingContext withState(
            CodingState state) {

        return new CodingContext(
            agent,
            state
        );
    }
}
```

This is the critical composition:

```text
AgentContext
       +
CodingState
       =
CodingContext
```

Don't fork Wayang's context architecture.

---

# 4. Store coding state inside the existing execution state

Ideally:

```java
public final class CodingStateCodec {

    private static final String KEY =
        "wayang.aljabr.coding";

    public static void put(
            AgentContext context,
            CodingState state) {

        context.state().put(KEY, state);
    }

    public static CodingState get(
            AgentContext context) {

        return context.state()
            .get(KEY, CodingState.class)
            .orElseThrow(...);
    }
}
```

Then the existing checkpoint mechanism naturally persists it.

This is much better than:

```text
Wayang checkpoint
        +
Aljabr checkpoint
```

You want exactly **one execution checkpoint**.

---

# 5. `AljabrStrategy`

Now the important part.

```java
@ApplicationScoped
public final class AljabrStrategy
        implements CodingStrategy {

    @Override
    public AgentDecision decide(
            CodingContext context,
            ExecutionStatus status) {

        return switch (context.phase()) {

            case INSPECTING ->
                inspect(context);

            case PLANNING ->
                plan(context);

            case EXECUTING ->
                execute(context);

            case VERIFYING ->
                verify(context);

            case REPAIRING ->
                repair(context);

            case REVIEWING ->
                review(context);

            case WAITING_USER ->
                waitForUser(context);

            case COMPLETED ->
                finish(context);

            case FAILED ->
                fail(context);

            case CANCELLED ->
                cancel(context);
        };
    }
}
```

This becomes the heart of Aljabr.

---

# 6. But `decide()` should remain cheap

Don't do this:

```java
decide()
   ↓
search files
   ↓
run tests
   ↓
call model
```

`decide()` should primarily determine:

> **What should happen next?**

For example:

```java
private AgentDecision inspect(
        CodingContext context) {

    return AgentDecision.callModel(
        buildInspectionPrompt(context)
    );
}
```

Then the normal Wayang execution machinery handles the model call.

---

# 7. Let the model produce structured intent

This is important.

Don't have the model emit:

```text
I think I should inspect UserService.java.
```

Have it emit:

```json
{
  "action": "SEARCH",
  "query": "TokenValidator expiration",
  "reason": "Need to locate expiration logic"
}
```

Represent it:

```java
public record CodingIntent(
    CodingAction action,
    String target,
    String reason
) {}
```

```java
public enum CodingAction {

    SEARCH,
    READ,
    REFERENCES,
    DEFINITION,

    CREATE_PLAN,

    PATCH,
    RUN_TEST,
    RUN_BUILD,

    VERIFY,
    REVIEW,

    FINISH
}
```

Then the runtime converts that into an actual `AgentDecision`.

---

# 8. Why this separation matters

You get:

```text
LLM
 ↓
CodingIntent
 ↓
validator
 ↓
AgentDecision
 ↓
Wayang executor
 ↓
Tool
```

instead of:

```text
LLM
 ↓
arbitrary tool call
```

The first is much safer.

---

# 9. Intent validation

Create:

```java
public interface CodingIntentValidator {

    ValidationResult validate(
        CodingIntent intent,
        CodingContext context
    );
}
```

Example:

```java
public ValidationResult validate(
        CodingIntent intent,
        CodingContext context) {

    if (intent.action() == PATCH
            && context.phase() != EXECUTING
            && context.phase() != REPAIRING) {

        return ValidationResult.reject(
            "PATCH is not allowed in current phase"
        );
    }

    if (intent.action() == FINISH
            && !verificationPassed(context)) {

        return ValidationResult.reject(
            "Cannot finish before verification"
        );
    }

    return ValidationResult.accept();
}
```

This gives you runtime-level guarantees.

---

# 10. Phase-specific capabilities

I'd formalize that.

```java
public interface PhasePolicy {

    Set<CodingAction> allowedActions(
        CodingPhase phase
    );
}
```

Example:

```java
public Set<CodingAction> allowedActions(
        CodingPhase phase) {

    return switch (phase) {

        case INSPECTING ->
            Set.of(
                SEARCH,
                READ,
                REFERENCES,
                DEFINITION
            );

        case PLANNING ->
            Set.of(
                CREATE_PLAN,
                SEARCH,
                READ
            );

        case EXECUTING ->
            Set.of(
                READ,
                SEARCH,
                PATCH,
                RUN_TEST,
                RUN_BUILD
            );

        case VERIFYING ->
            Set.of(
                RUN_TEST,
                RUN_BUILD,
                VERIFY
            );

        case REPAIRING ->
            Set.of(
                READ,
                SEARCH,
                PATCH,
                RUN_TEST,
                RUN_BUILD
            );

        case REVIEWING ->
            Set.of(
                READ,
                SEARCH,
                REVIEW,
                VERIFY
            );

        default ->
            Set.of();
    };
}
```

This is excellent protection against accidental phase drift.

---

# 11. Inspection becomes an actual loop

Now implement:

```java
private AgentDecision inspect(
        CodingContext context) {

    if (inspectionComplete(context)) {
        transition(context, PLANNING);

        return AgentDecision.callModel(
            planningPrompt(context)
        );
    }

    return AgentDecision.callModel(
        inspectionPrompt(context)
    );
}
```

The model might return:

```text
SEARCH
```

Then Wayang executes the search tool.

Result returns:

```text
ToolResult
```

and the context is updated.

Then:

```text
INSPECTING
 ↓
model
 ↓
search
 ↓
observation
 ↓
checkpoint
 ↓
model
 ↓
read
 ↓
observation
 ↓
checkpoint
 ↓
...
```

This is exactly the resumable behavior we wanted.

---

# 12. Don't decide inspection completion by token output

Create:

```java
public interface InspectionCompleteness {

    boolean isComplete(
        CodingContext context
    );
}
```

A first implementation can be simple:

```java
public boolean isComplete(
        CodingContext context) {

    return hasRepositoryStructure(context)
        && hasRelevantFiles(context)
        && hasRelevantTests(context)
        && hasEnoughEvidence(context);
}
```

Later, this can become model-assisted.

But runtime owns the final decision.

---

# 13. Planning state

When inspection completes:

```java
CodingState planning =
    state.nextPhase(PLANNING);
```

Then request:

```java
public record CodingPlan(
    String objective,
    List<PlanStep> steps
) {}
```

with:

```java
public record PlanStep(
    String id,
    String objective,
    List<Path> expectedFiles,
    List<CodingAction> allowedActions,
    List<String> verificationRequirements
) {}
```

Example:

```json
{
  "objective": "Fix token expiration validation",
  "steps": [
    {
      "id": "step-1",
      "objective": "Fix expiration comparison",
      "expectedFiles": [
        "src/auth/TokenValidator.java"
      ]
    },
    {
      "id": "step-2",
      "objective": "Add regression test",
      "expectedFiles": [
        "src/test/auth/TokenValidatorTest.java"
      ]
    }
  ]
}
```

---

# 14. Validate the plan before executing

```java
public Uni<CodingPlan> validatePlan(
        CodingPlan plan,
        CodingContext context) {

    return Uni.createFrom().item(() -> {

        if (plan.steps().isEmpty()) {
            throw new InvalidPlanException(
                "Plan contains no steps"
            );
        }

        for (PlanStep step : plan.steps()) {

            scopeGuard.assertAllowed(
                step.expectedFiles()
            );
        }

        return plan;
    });
}
```

This is where the model stops being authoritative.

---

# 15. Step execution

Then:

```java
private AgentDecision execute(
        CodingContext context) {

    CodingPlan plan =
        context.coding()
            .plan()
            .orElseThrow();

    if (context.coding().currentStep()
            >= plan.steps().size()) {

        transition(context, VERIFYING);

        return AgentDecision.callModel(
            verificationPrompt(context)
        );
    }

    PlanStep step =
        plan.steps()
            .get(context.coding().currentStep());

    return AgentDecision.callModel(
        implementationPrompt(
            context,
            step
        )
    );
}
```

The key point:

**one plan step at a time.**

---

# 16. After a successful step

Don't simply increment:

```java
currentStep++;
```

First record:

```java
public record StepCompletion(
    String stepId,
    ChangeSet changes,
    VerificationSummary verification
) {}
```

Then:

```text
step
 ↓
patch
 ↓
targeted verification
 ↓
StepCompletion
 ↓
checkpoint
 ↓
next step
```

This makes the execution much more robust.

---

# 17. Verification should happen at two levels

This is a major improvement I'd make.

### Step verification

After each meaningful change:

```text
compile affected module
run targeted test
```

### Final verification

After all steps:

```text
full relevant test suite
build
static analysis
diff validation
```

So:

```text
PLAN
 ↓
STEP 1
 ↓
targeted verify
 ↓
STEP 2
 ↓
targeted verify
 ↓
STEP 3
 ↓
final verify
```

This dramatically reduces repair complexity.

---

# 18. Verification result

```java
public record VerificationReport(
    VerificationStatus status,
    List<VerificationCheck> checks,
    List<VerificationFailure> failures
) {

    public boolean passed() {
        return status == VerificationStatus.PASS;
    }
}
```

Don't allow:

```text
"tests seem okay"
```

as a terminal state.

---

# 19. Repair controller

Now the state transition:

```java
private AgentDecision repair(
        CodingContext context) {

    VerificationReport report =
        context.coding()
            .lastVerification()
            .orElseThrow();

    if (context.coding().repairAttempts()
            >= MAX_REPAIR_ATTEMPTS) {

        return AgentDecision.fail(
            "Maximum repair attempts exceeded"
        );
    }

    return AgentDecision.callModel(
        repairPrompt(
            context,
            report
        )
    );
}
```

After the repair model proposes changes:

```text
repair
 ↓
validate
 ↓
apply
 ↓
verify
```

Never:

```text
repair
 ↓
assume fixed
```

---

# 20. Add the repair fingerprint now

This is cheap and extremely valuable.

```java
public record FailureFingerprint(
    String type,
    String normalizedMessage,
    List<String> locations
) {}
```

Then:

```java
public record RepairAttempt(
    FailureFingerprint failure,
    String patchHash
) {}
```

Before another repair:

```java
if (previousAttempts.contains(
        new RepairAttempt(
            fingerprint,
            patchHash
        ))) {

    return AgentDecision.fail(
        "Repeated repair attempt detected"
    );
}
```

This protects against:

```text
fix
 ↓
test
 ↓
same failure
 ↓
same fix
 ↓
test
 ↓
same failure
```

---

# 21. Review should inspect the actual diff

Not the model's memory.

```java
private AgentDecision review(
        CodingContext context) {

    Diff diff =
        workspace.diff(
            context.coding()
                .workspace()
                .orElseThrow()
        );

    return AgentDecision.callModel(
        reviewPrompt(
            context,
            diff
        )
    );
}
```

The runtime independently checks:

```text
✓ only intended files changed
✓ no forbidden files
✓ no accidental deletions
✓ no user changes overwritten
✓ tests included where appropriate
✓ diff is consistent with plan
```

---

# 22. Final completion gate

This should be extremely strict.

```java
public boolean canFinish(
        CodingContext context) {

    return
        planCompleted(context)
        && verificationPassed(context)
        && reviewPassed(context)
        && noPolicyViolations(context)
        && noOutstandingApproval(context)
        && !budgetExceeded(context);
}
```

Only then:

```java
return AgentDecision.finish(
    buildFinalResponse(context)
);
```

The model doesn't get to say:

> "Done."

The runtime says:

> **Done.**

---

# 23. Now introduce `CodingEvent`

There is still one thing I would add despite reusing Wayang checkpointing.

Checkpointing answers:

> "Where am I now?"

An event ledger answers:

> "What happened?"

So:

```java
public sealed interface CodingEvent
    permits
        PhaseChanged,
        ObservationRecorded,
        PlanCreated,
        StepStarted,
        StepCompleted,
        VerificationCompleted,
        RepairStarted,
        ReviewCompleted,
        CodingCompleted {}
```

Then:

```text
checkpoint
=
current state

event ledger
=
history
```

You want both.

---

# 24. Why this matters for Aljabr

Suppose the user asks:

> Why did you modify this file?

Aljabr can reconstruct:

```text
INSPECT
  search → TokenValidator.java

READ
  line 84

PLAN
  modify expiration comparison

EXECUTE
  patch TokenValidator.java

VERIFY
  TokenValidatorTest failed

REPAIR
  adjust boundary condition

VERIFY
  pass
```

That's much more useful than a giant conversation transcript.

---

# 25. And this plugs directly into your existing memory architecture

This is where your earlier Wayang memory work becomes useful.

Don't put all coding events into long-term memory.

Use scopes:

```text
Execution memory
    ↓
all coding events

Task memory
    ↓
important decisions

Project memory
    ↓
stable project facts

User memory
    ↓
user coding preferences
```

For example:

```text
"Project uses Gradle"
```

might eventually become project knowledge.

But:

```text
"Step 2 executed grep for TokenValidator"
```

should stay execution-local.

---

# 26. Research artifacts should also have lifecycle

Use:

```java
public record ResearchArtifact(
    String id,
    ResearchQuestion question,
    String repositoryRevision,
    List<Evidence> evidence,
    Instant createdAt,
    ResearchValidity validity
) {}
```

Validity:

```java
public enum ResearchValidity {
    VALID,
    STALE,
    INVALIDATED
}
```

Then a code modification can invalidate relevant research.

Example:

```text
repo abc123
 ↓
research says:
TokenValidator line 84 checks expiry
 ↓
Aljabr changes TokenValidator
 ↓
research artifact → STALE
```

This prevents later reasoning from accidentally relying on obsolete evidence.

---

# 27. Repository revision should become a first-class concept

At inspection start:

```java
WorkspaceSnapshot snapshot =
    workspace.snapshot();
```

Something like:

```java
public record WorkspaceSnapshot(
    String repositoryRevision,
    String workingTreeHash,
    ChangeSet existingChanges
) {}
```

Now every important artifact can say:

```text
derivedFromRevision = abc123
```

This is enormously useful for a coding agent.

---

# 28. Then implement stale-state detection

Before applying a patch:

```java
if (!workspace.matches(
        expectedSnapshot
)) {

    return AgentDecision.callModel(
        staleWorkspacePrompt(...)
    );
}
```

Meaning:

```text
Aljabr inspected repository
       ↓
user modifies file
       ↓
Aljabr tries patch
       ↓
runtime detects divergence
       ↓
re-inspect
```

Never blindly patch stale state.

---

# 29. This should be a hard runtime invariant

Something like:

```java
public interface WorkspaceGuard {

    void assertUnchanged(
        WorkspaceSnapshot snapshot
    ) throws WorkspaceChangedException;
}
```

Called before mutation:

```java
workspaceGuard.assertUnchanged(
    coding.workspace()
);
```

This belongs in the runtime/tool layer, not the prompt.

---

# 30. At this point Aljabr's intelligence is actually quite small

And that's a good sign.

The architecture becomes:

```text
                 ALJABR
                    │
        ┌───────────┴───────────┐
        │                       │
  Coding Strategy        Coding State
        │                       │
        └───────────┬───────────┘
                    │
                    ▼
              WAYANG RUNTIME
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
     Model         Tools       Memory
       │            │            │
       ▼            ▼            ▼
    Provider      Policy       Cache
                  Sandbox     Checkpoint
```

That's exactly where I think the project should converge.

---

# 31. The concrete implementation order

I would now implement in this exact order:

### Commit 1 — Aljabr state

```text
CodingPhase
CodingState
CodingContext
CodingPlan
PlanStep
```

### Commit 2 — strategy

```text
CodingStrategy
AljabrStrategy
phase routing
```

### Commit 3 — Wayang integration

```text
AljabrAgentDefinition
AgentExecutionService
strategy registry
strategyId in checkpoint
```

### Commit 4 — inspection

```text
InspectionCompleteness
ResearchArtifact
repository revision
```

### Commit 5 — planning

```text
structured CodingPlan
PlanValidator
```

### Commit 6 — implementation

```text
StepExecutor
PatchOperation
WorkspaceGuard
ChangeSet
```

### Commit 7 — verification

```text
VerificationEngine
VerificationStrategy
VerificationReport
```

### Commit 8 — repair

```text
RepairController
FailureFingerprint
RepairAttempt
progress detection
```

### Commit 9 — review

```text
ReviewController
diff validation
completion gate
```

### Commit 10 — history

```text
CodingEvent
event ledger
research invalidation
```

---

## One architectural rule I would lock in now

**Aljabr owns the coding intelligence. Wayang owns execution reality.**

So if you ever find yourself writing:

```java
ProcessBuilder
Files.write
ToolPolicy
CheckpointStore
ModelRetry
CircuitBreaker
MemoryStore
```

inside `aljabr/`, stop and ask:

> "Does Wayang already own this?"

If yes, Aljabr should call the abstraction instead.

That one rule will prevent Aljabr from turning into the second framework you're currently trying to avoid.


Exactly. Then I would make the **next move implementation-first**, not add more architecture.

The key now is to establish the boundary:

> **Wayang = execution operating system.
> Aljabr = coding intelligence running inside it.**

From here, I'd tackle the first concrete slice: **make one complete Aljabr execution path work end-to-end before adding advanced repair/research machinery.**

### Next slice

```text
User request
    ↓
AljabrAgentDefinition
    ↓
AgentExecutionService
    ↓
DefaultAgentExecution
    ↓
AljabrStrategy
    ↓
INSPECTING
    ↓
structured model decision
    ↓
existing AgentToolExecutor
    ↓
observation
    ↓
checkpoint
    ↓
PLANNING
    ↓
structured CodingPlan
    ↓
EXECUTING
    ↓
existing governed tools
    ↓
VERIFYING
    ↓
REVIEWING
    ↓
FINISH
```

I would implement only these classes first:

```text
aljabr/
├── strategy/
│   ├── AljabrStrategy.java
│   └── CodingStrategy.java
│
├── state/
│   ├── CodingContext.java
│   ├── CodingState.java
│   └── CodingPhase.java
│
├── plan/
│   ├── CodingPlan.java
│   └── PlanStep.java
│
└── AljabrAgentDefinitionFactory.java
```

Then wire those into the existing Wayang execution path.

**Do not implement yet:**

* custom Aljabr tool executor
* custom Aljabr checkpoint store
* custom Aljabr model gateway
* custom Aljabr memory
* repair engine
* sophisticated research cache
* event-sourcing infrastructure

Those come after the first vertical slice proves the architecture.

The next thing I'd do is therefore **write the actual Java implementation for these classes and the exact modifications to Wayang's existing `AgentExecution` / `AgentStrategy` / checkpoint interfaces**, including what to delete from the current Aljabr code. That will turn the design above into a concrete refactoring patch plan.


Great. Let's make the next step **the actual refactoring contract**: what to add, what to modify, and what to delete so the first Aljabr vertical slice becomes real.

I’d do this in roughly **three commits**, keeping each one buildable.

---

# 1. Commit A — establish the Aljabr state model

Start with this.

### `CodingPhase.java`

```java
public enum CodingPhase {
    INSPECTING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    REPAIRING,
    REVIEWING,
    WAITING_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

Keep the phase count small.

Don't model every tool operation as a phase.

---

### `CodingState.java`

```java
public record CodingState(
    CodingPhase phase,
    CodingPlan plan,
    int currentStep,
    int repairAttempts,
    VerificationReport lastVerification
) {

    public static CodingState initial() {
        return new CodingState(
            CodingPhase.INSPECTING,
            null,
            0,
            0,
            null
        );
    }

    public CodingState phase(CodingPhase next) {
        return new CodingState(
            next,
            plan,
            currentStep,
            repairAttempts,
            lastVerification
        );
    }

    public CodingState plan(CodingPlan next) {
        return new CodingState(
            phase,
            next,
            currentStep,
            repairAttempts,
            lastVerification
        );
    }

    public CodingState stepCompleted() {
        return new CodingState(
            phase,
            plan,
            currentStep + 1,
            repairAttempts,
            lastVerification
        );
    }

    public CodingState repairAttempt() {
        return new CodingState(
            phase,
            plan,
            currentStep,
            repairAttempts + 1,
            lastVerification
        );
    }

    public CodingState verification(
            VerificationReport report) {

        return new CodingState(
            phase,
            plan,
            currentStep,
            repairAttempts,
            report
        );
    }
}
```

The important property is:

> **No mutable fields.**

Every transition creates a new state.

That makes checkpoint/resume dramatically easier.

---

# 2. Don't put `CodingState` directly inside the strategy

The strategy must remain stateless.

Bad:

```java
@ApplicationScoped
public class AljabrStrategy {

    private CodingState state;
}
```

This will eventually break when multiple users execute Aljabr concurrently.

Instead:

```text
Execution A
    AgentContext A
       └── CodingState A

Execution B
    AgentContext B
       └── CodingState B
```

while:

```text
AljabrStrategy
    ↑
shared / stateless
```

---

# 3. Add a small state accessor

If Wayang's `AgentContext` already has metadata/state storage, use that.

Conceptually:

```java
public final class CodingStateStore {

    private static final String KEY =
        "aljabr.coding.state";

    public CodingState get(AgentContext context) {
        return context.state()
            .get(KEY, CodingState.class)
            .orElseGet(CodingState::initial);
    }

    public void put(
            AgentContext context,
            CodingState state) {

        context.state().put(KEY, state);
    }
}
```

If the current `AgentContext` doesn't provide typed state storage, **that's the Wayang change I'd make**, rather than introducing an Aljabr persistence mechanism.

---

# 4. `CodingPlan`

Keep the first version deliberately boring.

```java
public record CodingPlan(
    String objective,
    List<PlanStep> steps
) {

    public CodingPlan {
        steps = List.copyOf(steps);
    }

    public boolean completed(int currentStep) {
        return currentStep >= steps.size();
    }

    public PlanStep currentStep(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IllegalArgumentException(
                "Invalid plan step: " + index
            );
        }

        return steps.get(index);
    }
}
```

And:

```java
public record PlanStep(
    String id,
    String objective,
    List<String> expectedFiles,
    List<String> verificationRequirements
) {

    public PlanStep {
        expectedFiles = List.copyOf(expectedFiles);
        verificationRequirements =
            List.copyOf(verificationRequirements);
    }
}
```

Don't put tool names into the plan yet.

The plan says **what needs to happen**, not exactly how.

---

# 5. Then define the actual Aljabr strategy

This is the first important implementation.

```java
@ApplicationScoped
public final class AljabrStrategy
        implements CodingStrategy {

    private final CodingStateStore stateStore;

    @Inject
    public AljabrStrategy(
            CodingStateStore stateStore) {

        this.stateStore = stateStore;
    }

    @Override
    public AgentDecision decide(
            AgentContext context,
            ExecutionStatus status) {

        CodingState state =
            stateStore.get(context);

        return switch (state.phase()) {

            case INSPECTING ->
                inspect(context, state);

            case PLANNING ->
                plan(context, state);

            case EXECUTING ->
                execute(context, state);

            case VERIFYING ->
                verify(context, state);

            case REPAIRING ->
                repair(context, state);

            case REVIEWING ->
                review(context, state);

            case WAITING_USER ->
                waitForUser(context, state);

            case COMPLETED ->
                finish(context, state);

            case FAILED ->
                fail(context, state);

            case CANCELLED ->
                cancel(context, state);
        };
    }
}
```

Now Aljabr has an actual brain.

---

# 6. But there is an important problem

The existing `AgentStrategy.decide(...)` only receives:

```java
AgentContext
ExecutionStatus
```

If the current Wayang implementation doesn't provide a mechanism for the strategy to update context/state before returning a decision, don't make the strategy mutate random context fields.

Instead, I recommend making the decision itself capable of carrying state transition metadata.

For example:

```java
public sealed interface AgentDecision
    permits
        CallModel,
        ExecuteTool,
        WaitForApproval,
        Finish,
        Fail,
        Retry,
        Transition {
}
```

Then:

```java
public record Transition(
    CodingPhase phase
) implements AgentDecision {}
```

But there's an even better option.

---

# 7. Better: make state transition part of execution result

If you control the Wayang interfaces, I'd prefer:

```java
public record AgentDecisionResult(
    AgentDecision decision,
    AgentStateUpdate stateUpdate
) {}
```

Where:

```java
public record AgentStateUpdate(
    Map<String, Object> values
) {}
```

Then:

```text
Strategy
   ↓
Decision + StateUpdate
   ↓
Runtime
   ├── persist state
   └── execute decision
```

This makes the runtime authoritative.

However, **don't modify this yet if it would cause a large Wayang API break**.

For the first vertical slice, use whatever existing state/context mechanism is already available.

---

# 8. Inspection should produce model work, not perform inspection itself

For example:

```java
private AgentDecision inspect(
        AgentContext context,
        CodingState state) {

    return AgentDecision.callModel(
        ModelRequest.builder()
            .purpose("aljabr-inspection")
            .systemPrompt(ALJABR_INSPECTION_PROMPT)
            .context(buildInspectionContext(context))
            .build()
    );
}
```

The model then decides:

```json
{
  "action": "SEARCH",
  "query": "TokenValidator expiration"
}
```

The runtime converts that into the existing tool decision.

---

# 9. This means we need one small adapter

```java
public interface CodingIntentMapper {

    AgentDecision map(
        CodingIntent intent,
        CodingContext context
    );
}
```

Implementation:

```java
@ApplicationScoped
public class DefaultCodingIntentMapper
        implements CodingIntentMapper {

    @Override
    public AgentDecision map(
            CodingIntent intent,
            CodingContext context) {

        return switch (intent.action()) {

            case SEARCH ->
                executeTool(
                    "search_code",
                    intent.arguments()
                );

            case READ ->
                executeTool(
                    "read_file",
                    intent.arguments()
                );

            case REFERENCES ->
                executeTool(
                    "find_references",
                    intent.arguments()
                );

            case PATCH ->
                executeTool(
                    "apply_patch",
                    intent.arguments()
                );

            case RUN_TEST ->
                executeTool(
                    "run_tests",
                    intent.arguments()
                );

            default ->
                throw new InvalidCodingIntentException(
                    intent.action()
                );
        };
    }
}
```

This adapter is where Aljabr semantics meet Wayang tools.

---

# 10. But don't let it bypass `AgentToolExecutor`

The mapper should produce:

```text
AgentDecision.ExecuteTool
```

not:

```java
tool.execute(...)
```

So the actual path stays:

```text
Aljabr
 ↓
CodingIntentMapper
 ↓
AgentDecision.ExecuteTool
 ↓
DefaultAgentExecution
 ↓
AgentToolExecutor
 ↓
policy
 ↓
approval
 ↓
cache
 ↓
guardrail
 ↓
sandbox
 ↓
tool
```

That's the architecture we want.

---

# 11. Add a strict intent schema

I'd make:

```java
public record CodingIntent(
    CodingAction action,
    Map<String, Object> arguments,
    String reason
) {
    public CodingIntent {
        arguments = Map.copyOf(arguments);
    }
}
```

And:

```java
public enum CodingAction {
    SEARCH,
    READ,
    REFERENCES,
    DEFINITION,
    PATCH,
    RUN_TEST,
    RUN_BUILD,
    FINISH
}
```

Don't expose arbitrary Java classes to the model.

The model sees a small JSON schema.

---

# 12. The model must not be allowed to change phase arbitrarily

For example, don't let it emit:

```json
{
  "phase": "COMPLETED"
}
```

Instead the runtime determines:

```text
PATCH
 ↓
VERIFYING
```

or:

```text
RUN_TEST
 ↓
passed
 ↓
next step
```

The model controls **intent**.

The runtime controls **state**.

That's one of the most important Aljabr invariants.

---

# 13. Now the first complete loop

Once these pieces exist, the first working execution should look like:

```text
INSPECTING
    │
    ▼
CallModel
    │
    ▼
CodingIntent(SEARCH)
    │
    ▼
ExecuteTool(search)
    │
    ▼
ToolResult
    │
    ▼
checkpoint
    │
    ▼
CallModel
    │
    ▼
CodingIntent(READ)
    │
    ▼
ExecuteTool(read)
    │
    ▼
ToolResult
    │
    ▼
inspection complete
    │
    ▼
PLANNING
```

Then:

```text
PLANNING
    │
    ▼
CallModel
    │
    ▼
CodingPlan
    │
    ▼
validate plan
    │
    ▼
EXECUTING
```

That's already a real coding agent.

---

# 14. The most important test

Before implementing tests/build/repair, make this test pass:

```java
@Test
void aljabrCanInspectRepository() {
    AgentExecution execution =
        createExecution(
            "Find where authentication
             token expiration is checked"
        );

    AgentResponse response =
        execution.execute().toCompletableFuture().join();

    assertThat(response).isNotNull();

    CodingState state =
        stateStore.get(execution.context());

    assertThat(state.phase())
        .isEqualTo(CodingPhase.PLANNING);
}
```

Then test checkpoint:

```java
@Test
void inspectionResumesAfterCheckpoint() {
    AgentExecution execution =
        createExecution(...);

    runUntilCheckpoint(execution);

    AgentExecution resumed =
        executionService.resume(
            execution.id()
        );

    resumed.execute();

    assertThat(...)
        .isEqualTo(CodingPhase.PLANNING);
}
```

**This test is more valuable right now than building ten additional tools.**

---

# 15. Then make one tiny coding task work

Don't start with:

> "Refactor the entire authentication system."

Start with:

> "Find `TokenValidator` and explain where expiration is checked."

Then:

> "Change the expiration comparison from `>` to `>=` and add a regression test."

That second task exercises:

```text
inspect
→ plan
→ patch
→ test
→ review
→ finish
```

which is exactly the first vertical slice we need.

---

# 16. What I'd delete immediately

From the existing Aljabr side, I would now start removing the scaffolding that competes with this architecture:

```text
CoderOrchestrator
    ↓
ExecutionStrategy
    ↓
ReActExecutionStrategy
PlanAndSolveExecutionStrategy
```

Especially the placeholder implementations.

The orchestrator should eventually become almost embarrassingly thin:

```java
public Uni<AgentResponse> execute(
        AgentRequest request) {

    AgentDefinition definition =
        definitionFactory.create(request);

    return executionService
        .create(definition, request)
        .execute();
}
```

That's it.

---

# 17. One more thing: `DefaultWayangAgent`

I agree with the direction from our earlier discussion: **don't keep a fake/default agent just because the framework expects one.**

If it has genuine value, turn it into a minimal generic agent:

```text
DefaultWayangAgent
    =
generic tool-using agent
```

with no coding-specific behavior.

Then:

```text
Wayang
 ├── DefaultWayangAgent
 │      generic
 │
 └── Aljabr
        coding-specialized
```

But if `DefaultWayangAgent` exists only as legacy/demo glue, remove it.

**No compatibility abstractions without a real consumer.**

---

# 18. The next boundary after this

Once the first slice works, the next major implementation should be:

```text
        CodingIntent
             │
             ▼
      CodingIntentValidator
             │
      ┌──────┴──────┐
      │             │
    valid         invalid
      │             │
      ▼             ▼
AgentDecision    model feedback
      │
      ▼
Wayang execution
```

Then add:

```text
WorkspaceGuard
PlanValidator
VerificationEngine
RepairController
```

in that order.

That gives Aljabr **deterministic guardrails around probabilistic reasoning**, which I think should become one of the defining architectural principles of the project.


Yes. The next layer should be **the execution contract around coding intents**. This is where Aljabr stops being merely an LLM-driven state machine and becomes a reliable coding agent.

The key idea:

> **The model proposes. Aljabr validates. Wayang executes.**

---

# 1. Build the `CodingIntent` pipeline

We currently have:

```text
AljabrStrategy
      ↓
model
      ↓
CodingIntent
```

Now make the complete pipeline:

```text
                     MODEL
                       │
                       ▼
                CodingIntent
                       │
                       ▼
             ┌─────────────────┐
             │ IntentValidator │
             └────────┬────────┘
                      │
             ┌────────┴────────┐
             │                 │
           VALID            INVALID
             │                 │
             ▼                 ▼
      IntentMapper        Feedback to model
             │
             ▼
       AgentDecision
             │
             ▼
     Wayang Execution
```

This becomes one of Aljabr's most important boundaries.

---

# 2. Make validation composable

Don't create one enormous validator.

Use rules:

```java
public interface CodingIntentRule {

    ValidationResult validate(
        CodingIntent intent,
        CodingContext context
    );
}
```

Then:

```java
@ApplicationScoped
public class CodingIntentValidator {

    private final List<CodingIntentRule> rules;

    public ValidationResult validate(
            CodingIntent intent,
            CodingContext context) {

        for (CodingIntentRule rule : rules) {

            ValidationResult result =
                rule.validate(intent, context);

            if (!result.valid()) {
                return result;
            }
        }

        return ValidationResult.accept();
    }
}
```

Now rules can be independently tested.

---

# 3. First rules

I'd initially implement only five.

```text
PhaseRule
ScopeRule
ArgumentRule
SafetyRule
CompletionRule
```

---

# 4. `PhaseRule`

```java
@ApplicationScoped
public final class PhaseRule
        implements CodingIntentRule {

    @Override
    public ValidationResult validate(
            CodingIntent intent,
            CodingContext context) {

        CodingPhase phase =
            context.state().phase();

        if (!allowed(phase, intent.action())) {
            return ValidationResult.reject(
                "Action " + intent.action()
                + " is not allowed during "
                + phase
            );
        }

        return ValidationResult.accept();
    }
}
```

The policy becomes:

```text
INSPECTING
  READ
  SEARCH
  REFERENCES
  DEFINITION

PLANNING
  READ
  SEARCH

EXECUTING
  READ
  SEARCH
  PATCH
  RUN_TEST
  RUN_BUILD

VERIFYING
  RUN_TEST
  RUN_BUILD

REPAIRING
  READ
  SEARCH
  PATCH
  RUN_TEST
  RUN_BUILD

REVIEWING
  READ
  SEARCH
  REVIEW
```

This prevents the model from doing:

```text
INSPECTING → PATCH
```

just because it decided it wanted to.

---

# 5. `ScopeRule`

This one is much more important for coding agents.

Suppose the user says:

```text
Fix src/auth/*
```

The model tries:

```text
PATCH /etc/passwd
```

The intent must never reach the tool layer.

Create:

```java
public interface CodingScope {

    boolean canRead(Path path);

    boolean canWrite(Path path);

    boolean canExecute(Command command);
}
```

Then:

```java
public final class ScopeRule
        implements CodingIntentRule {

    @Override
    public ValidationResult validate(
            CodingIntent intent,
            CodingContext context) {

        if (intent.action() != PATCH) {
            return ValidationResult.accept();
        }

        Path target =
            Path.of(
                String.valueOf(
                    intent.arguments().get("path")
                )
            );

        if (!context.scope().canWrite(target)) {
            return ValidationResult.reject(
                "Path is outside allowed workspace"
            );
        }

        return ValidationResult.accept();
    }
}
```

This should ultimately align with Wayang's existing sandbox/policy mechanisms.

**Don't replace Wayang's security boundary with this.**

This is an Aljabr semantic check *before* the generic runtime check.

---

# 6. `ArgumentRule`

The model shouldn't be able to invent arbitrary tool arguments.

For example:

```json
{
  "action": "PATCH",
  "arguments": {
    "path": "../../secret",
    "content": "..."
  }
}
```

Validate the intent schema before mapping it to a tool.

```java
public interface CodingActionSchema {

    void validate(
        Map<String, Object> arguments
    );
}
```

Registry:

```java
Map<CodingAction, CodingActionSchema>
```

Example:

```java
PATCH → PatchIntentSchema
READ  → ReadIntentSchema
SEARCH → SearchIntentSchema
```

This keeps model output constrained.

---

# 7. `CompletionRule`

This is where we enforce:

> The model cannot declare victory.

```java
public final class CompletionRule
        implements CodingIntentRule {

    @Override
    public ValidationResult validate(
            CodingIntent intent,
            CodingContext context) {

        if (intent.action() != FINISH) {
            return ValidationResult.accept();
        }

        CodingState state =
            context.state();

        if (state.lastVerification() == null) {
            return ValidationResult.reject(
                "Cannot finish without verification"
            );
        }

        if (!state.lastVerification().passed()) {
            return ValidationResult.reject(
                "Cannot finish while verification fails"
            );
        }

        return ValidationResult.accept();
    }
}
```

This single rule changes the quality of the agent considerably.

---

# 8. Invalid intent should become a useful model observation

Don't simply throw:

```java
throw new InvalidIntentException();
```

Instead:

```java
public record IntentRejection(
    CodingIntent intent,
    String reason,
    List<String> constraints
) {}
```

Then feed it back:

```text
Previous action was rejected.

Reason:
PATCH is not permitted during INSPECTING.

Allowed actions:
SEARCH
READ
REFERENCES
DEFINITION

Choose another action.
```

Now the model can recover.

---

# 9. But cap repeated invalid intents

Otherwise you get:

```text
model
 ↓
invalid
 ↓
model
 ↓
invalid
 ↓
model
 ↓
invalid
```

Add:

```java
public record IntentFailureState(
    int consecutiveFailures,
    Optional<CodingIntent> lastIntent
) {}
```

Then:

```java
if (failures >= 3) {
    return AgentDecision.fail(
        "Repeated invalid coding decisions"
    );
}
```

This belongs to the execution state and therefore survives checkpoint/resume.

---

# 10. Now the `CodingIntentMapper`

Once validated:

```java
public interface CodingIntentMapper {

    AgentDecision map(
        CodingIntent intent,
        CodingContext context
    );
}
```

Example:

```java
@Override
public AgentDecision map(
        CodingIntent intent,
        CodingContext context) {

    return switch (intent.action()) {

        case SEARCH ->
            tool(
                "code.search",
                intent.arguments()
            );

        case READ ->
            tool(
                "file.read",
                intent.arguments()
            );

        case REFERENCES ->
            tool(
                "code.references",
                intent.arguments()
            );

        case PATCH ->
            tool(
                "workspace.patch",
                intent.arguments()
            );

        case RUN_TEST ->
            tool(
                "build.test",
                intent.arguments()
            );

        case RUN_BUILD ->
            tool(
                "build.run",
                intent.arguments()
            );

        case FINISH ->
            finish();

        default ->
            throw new IllegalStateException(
                "Unsupported action"
            );
    };
}
```

Again:

**this does not execute anything.**

It creates the normal Wayang `AgentDecision`.

---

# 11. The complete strategy now becomes very clean

```java
@Override
public AgentDecision decide(
        AgentContext context,
        ExecutionStatus status) {

    CodingContext coding =
        contextFactory.create(context);

    CodingState state =
        stateStore.get(context);

    return switch (state.phase()) {

        case INSPECTING ->
            requestInspection(coding);

        case PLANNING ->
            requestPlanning(coding);

        case EXECUTING ->
            requestImplementation(coding);

        case VERIFYING ->
            requestVerification(coding);

        case REPAIRING ->
            requestRepair(coding);

        case REVIEWING ->
            requestReview(coding);

        case WAITING_USER ->
            waitForUser(coding);

        case COMPLETED ->
            finish(coding);

        case FAILED ->
            fail(coding);

        case CANCELLED ->
            cancel(coding);
    };
}
```

The strategy isn't doing file operations.

It isn't running commands.

It isn't calling Maven.

It isn't touching Git.

It isn't managing checkpoints.

That's exactly what we want.

---

# 12. Now introduce the first real Aljabr-specific service: `WorkspaceGuard`

This one deserves special treatment.

The coding agent has a workspace:

```text
repository
├── src/
├── tests/
├── build.gradle
└── ...
```

At execution start capture:

```java
public record WorkspaceSnapshot(
    String revision,
    String workingTreeFingerprint,
    ChangeSet preExistingChanges
) {}
```

Then before a mutation:

```java
public interface WorkspaceGuard {

    WorkspaceCheck check(
        WorkspaceSnapshot expected
    );
}
```

Result:

```java
public record WorkspaceCheck(
    boolean unchanged,
    List<WorkspaceConflict> conflicts
) {}
```

---

# 13. Why pre-existing changes matter

Suppose:

```text
before Aljabr starts:

User changed:
src/Foo.java
src/Bar.java
```

Aljabr changes:

```text
src/Baz.java
```

Fine.

But if Aljabr's patch mechanism blindly operates on the working tree, it might accidentally overwrite the user's `Foo.java`.

So Aljabr needs to know:

```text
USER CHANGE
ALJABR CHANGE
```

as separate provenance.

---

# 14. Introduce `ChangeRecord`

```java
public record ChangeRecord(
    String id,
    String path,
    ChangeType type,
    String beforeHash,
    String afterHash,
    String originatingStep,
    ChangeOrigin origin
) {}
```

```java
public enum ChangeOrigin {
    USER,
    ALJABR,
    EXTERNAL
}
```

This becomes incredibly useful later for review.

---

# 15. Don't build a Git abstraction prematurely

For now:

```text
WorkspaceSnapshot
ChangeSet
ChangeRecord
```

are enough.

Wayang/tool implementations can use Git where appropriate.

Aljabr should reason in terms of:

```text
workspace state
changes
revision
```

not:

```text
git checkout
git reset
git diff
```

That keeps Aljabr independent of the underlying source-control system.

---

# 16. Now we can define `PatchOperation`

This is the semantic coding operation:

```java
public record PatchOperation(
    String path,
    List<PatchEdit> edits,
    String reason
) {}
```

Not:

```java
public record ShellCommand(
    String command
) {}
```

The model should ideally produce a structured patch.

Example:

```json
{
  "path": "src/auth/TokenValidator.java",
  "edits": [
    {
      "oldText": "expiresAt > now",
      "newText": "expiresAt >= now"
    }
  ],
  "reason": "Token should expire at the exact boundary"
}
```

Then Aljabr validates it.

---

# 17. Patch validation

Before the patch reaches the tool:

```java
public interface PatchValidator {

    PatchValidationResult validate(
        PatchOperation patch,
        CodingContext context
    );
}
```

Check:

```text
✓ path in scope
✓ expected file exists
✓ old content exists
✓ old content occurs exactly as expected
✓ patch belongs to current plan step
✓ file wasn't changed unexpectedly
✓ patch size is reasonable
```

Only then:

```text
PatchOperation
      ↓
AgentDecision.ExecuteTool
      ↓
Wayang
```

---

# 18. This solves a classic coding-agent failure

Without validation:

```text
model:
replace:

foo()

with:

bar()
```

But `foo()` appears in 15 places.

Tool blindly replaces all 15.

With structured patch validation:

```text
expected match count = 1
actual match count = 15
```

Reject.

Then ask the model to produce a more precise patch.

This is much safer than relying on prompts.

---

# 19. Then verification becomes deterministic

For the first implementation, create:

```java
public interface VerificationEngine {

    Uni<VerificationReport> verify(
        VerificationRequest request
    );
}
```

Request:

```java
public record VerificationRequest(
    List<String> checks,
    WorkspaceSnapshot baseline
) {}
```

Example checks:

```text
compile
unit tests
targeted tests
lint
static analysis
```

The engine itself should use Wayang tools.

It should **not** directly spawn processes.

---

# 20. Verification policy

The plan determines the minimum verification:

```java
public record VerificationRequirements(
    boolean compile,
    boolean targetedTests,
    boolean fullTests,
    boolean lint
) {}
```

Then:

```text
small documentation change
    → maybe no compile

Java source change
    → compile

authentication logic
    → targeted tests + compile

large refactor
    → compile + relevant tests
```

Later we can make this framework-aware.

---

# 21. Don't let the LLM choose whether tests passed

This is crucial.

Bad:

```text
LLM:
"The tests probably pass."
```

Correct:

```text
VerificationEngine
    ↓
actual tool result
    ↓
exit code / structured result
    ↓
VerificationReport
```

The model interprets failures.

The runtime determines truth.

---

# 22. Then the repair loop becomes simple

```text
VERIFY
  │
  ├── PASS ───────→ REVIEW
  │
  └── FAIL
        │
        ▼
      REPAIR
        │
        ▼
      PATCH
        │
        ▼
      VERIFY
```

And add three termination conditions:

```text
MAX_ATTEMPTS
NO_PROGRESS
BUDGET_EXCEEDED
```

---

# 23. `NO_PROGRESS` is particularly important

Calculate:

```java
public record VerificationFingerprint(
    String normalizedFailureHash
) {}
```

If:

```text
attempt 1 → failure hash A
attempt 2 → failure hash A
attempt 3 → failure hash A
```

and the patch doesn't materially change the relevant state:

```text
STOP
```

Likewise:

```text
attempt 1 → failure A
attempt 2 → failure B
attempt 3 → failure A
```

could indicate oscillation.

That should also trigger a stop/replan condition.

---

# 24. At that point introduce `Replan`

Don't force the same plan forever.

Add:

```java
public enum RecoveryAction {
    RETRY,
    REPAIR,
    REPLAN,
    ASK_USER,
    FAIL
}
```

For example:

```text
targeted test fails once
    → REPAIR

same failure after repair
    → REPLAN

plan assumptions invalid
    → REPLAN

workspace conflict
    → ASK_USER

budget exhausted
    → FAIL
```

This is much more intelligent than simply:

```text
while (!testPassed)
    askLLMToFix()
```

---

# 25. This is where Aljabr becomes genuinely different from a basic coding agent

A simplistic coding agent is:

```text
prompt
 ↓
LLM
 ↓
tool
 ↓
LLM
 ↓
tool
```

Aljabr should be:

```text
                     ┌───────────────┐
                     │     PLAN      │
                     └───────┬───────┘
                             │
                             ▼
┌────────┐             ┌───────────┐
│INSPECT │────────────▶│ EXECUTE   │
└────────┘             └─────┬─────┘
                             │
                             ▼
                       ┌──────────┐
                       │ VERIFY   │
                       └────┬─────┘
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
              PASS                   FAIL
                 │                     │
                 ▼                     ▼
              REVIEW                REPAIR
                 │                     │
                 │              ┌──────┴──────┐
                 │              ▼             ▼
                 │           progress       no progress
                 │              │             │
                 │              ▼             ▼
                 │           VERIFY        REPLAN
                 │
                 ▼
               DONE
```

The LLM is inside this control system, not above it.

---

# 26. One final Wayang integration I'd do now

I'd introduce a registry:

```java
public interface AgentStrategyRegistry {

    AgentStrategy resolve(
        String strategyId
    );
}
```

Implementation:

```java
@ApplicationScoped
public class DefaultAgentStrategyRegistry
        implements AgentStrategyRegistry {

    private final Map<String, AgentStrategy> strategies;

    @Inject
    public DefaultAgentStrategyRegistry(
            Instance<AgentStrategy> beans) {

        this.strategies =
            StreamSupport.stream(
                beans.spliterator(),
                false
            )
            .collect(Collectors.toUnmodifiableMap(
                AgentStrategy::id,
                Function.identity()
            ));
    }

    @Override
    public AgentStrategy resolve(
            String id) {

        AgentStrategy strategy =
            strategies.get(id);

        if (strategy == null) {
            throw new UnknownStrategyException(id);
        }

        return strategy;
    }
}
```

Then:

```java
AljabrStrategy.id()
    → "aljabr"
```

and generic:

```java
DefaultWayangStrategy.id()
    → "default"
```

Now checkpoints can contain:

```json
{
  "strategy": "aljabr"
}
```

and resume can reconstruct the correct behavior.

---

# 27. The execution lifecycle becomes explicit

I would now make this invariant:

```text
CREATED
   ↓
RUNNING
   ↓
CHECKPOINTED
   ↓
RUNNING
   ↓
...
   ↓
COMPLETED
```

with failures:

```text
RUNNING
   ↓
FAILED
```

and cancellation:

```text
RUNNING
   ↓
CANCELLED
```

The coding phase is **inside** execution status.

Don't mix:

```text
ExecutionStatus
CodingPhase
```

into one enum.

They represent different dimensions.

---

# 28. That gives us two axes

This distinction is subtle but very important.

### Execution lifecycle

```text
CREATED
RUNNING
PAUSED
FAILED
COMPLETED
CANCELLED
```

### Coding lifecycle

```text
INSPECTING
PLANNING
EXECUTING
VERIFYING
REPAIRING
REVIEWING
```

So:

```text
ExecutionStatus = RUNNING
CodingPhase     = VERIFYING
```

or:

```text
ExecutionStatus = PAUSED
CodingPhase     = REPAIRING
```

This is exactly the sort of separation that will make resume, approval, cancellation, and scheduling sane later.

---

# 29. The next milestone

After this layer, we will have enough foundation to implement the **first genuine coding operation**:

```text
User:
"Change X"

Aljabr:
  INSPECT
  ↓
  SEARCH
  ↓
  READ
  ↓
  PLAN
  ↓
  PATCH
  ↓
  TARGETED TEST
  ↓
  REVIEW
  ↓
  FINISH
```

And at that point we can stop discussing architecture abstractly and build the actual **`PatchOperation → WorkspaceGuard → Wayang ToolExecutor → VerificationEngine` implementation**, including the exact Java interfaces, error handling, idempotency, diff tracking, and checkpoint updates.

That is the next layer I'd tackle.
