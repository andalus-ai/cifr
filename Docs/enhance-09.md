Absolutely. **Next milestone: `DurableTaskState + AgentLoop + CheckpointStore + ResumeEngine`.**

This is the piece that makes Aljabr a **real runtime** rather than a stateless LLM wrapper.

The design goal is:

> **Any meaningful state transition should be resumable without repeating expensive reasoning, repository scans, tool calls, patches, or tests.**

That fits your local-inference use case especially well because Gemma inference and context rebuilding are expensive.

---

# Milestone — Durable Agent Runtime

The runtime becomes:

```text
                         Task
                          │
                          ▼
                  ┌───────────────┐
                  │ DurableState  │
                  └───────┬───────┘
                          │
                          ▼
                    ┌──────────┐
                    │ AgentLoop│
                    └────┬─────┘
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
         Reason       Execute       Verify
            │            │            │
            └────────────┼────────────┘
                         ▼
                     Checkpoint
                         │
                         ▼
                    Event Journal
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
           Continue                Crash
                                    │
                                    ▼
                              ResumeEngine
                                    │
                                    ▼
                              last checkpoint
```

---

# 1. Don't make `AgentLoop` the source of truth

A common implementation is:

```java
while (!done) {
    state = agent.next(state);
}
```

The problem:

```text
process dies
↓
state disappears
↓
everything starts again
```

Instead:

```text
AgentLoop
    ↓
DurableTaskState
    ↓
CheckpointStore
```

The loop is disposable.

**State is not.**

---

# 2. `TaskId`

Everything starts with an immutable task identity.

```java
public record TaskId(String value) {}
```

Example:

```text
task:
aljabr-01J...
```

Every artifact references this:

```text
TaskId
 ├── checkpoints
 ├── events
 ├── executions
 ├── changes
 ├── verification
 ├── context
 └── cache
```

---

# 3. `TaskState`

Don't put everything into one giant mutable object.

Start with:

```java
public record TaskState(
    TaskId taskId,
    TaskStatus status,
    TaskPhase phase,
    long revision,
    TaskGoal goal,
    TaskProgress progress
) {}
```

Then keep the detailed state in specialized components.

---

# 4. `TaskPhase`

```java
public enum TaskPhase {

    CREATED,

    UNDERSTANDING,
    INVESTIGATING,
    PLANNING,

    PATCHING,
    VALIDATING,

    VERIFYING,
    REVIEWING,

    REPAIRING,

    COMPLETED,
    BLOCKED,
    FAILED,
    CANCELLED
}
```

This is much better than:

```text
isRunning = true
```

because recovery knows exactly where the task was.

---

# 5. Task status vs phase

Keep them separate.

```java
public enum TaskStatus {

    RUNNING,
    WAITING,
    COMPLETED,
    BLOCKED,
    FAILED,
    CANCELLED
}
```

For example:

```text
status = WAITING
phase  = VERIFYING
```

could mean:

```text
waiting for test execution
```

Another:

```text
status = WAITING
phase  = REVIEWING
```

could mean:

```text
waiting for user approval
```

---

# 6. State revision

Every state mutation increments:

```text
revision:
41
42
43
44
```

So:

```java
public record StateVersion(
    TaskId task,
    long revision
) {}
```

This provides optimistic concurrency protection.

---

# 7. Checkpoint

The fundamental persistence unit:

```java
public record Checkpoint(
    CheckpointId id,
    TaskId taskId,
    long revision,
    TaskPhase phase,
    Instant createdAt,
    StateSnapshot snapshot
) {}
```

The snapshot contains enough state to resume.

---

# 8. Don't checkpoint after every token

That's too expensive.

Checkpoint at **semantic boundaries**.

For example:

```text
Task created          → checkpoint
Investigation done    → checkpoint
Plan created          → checkpoint
Patch applied         → checkpoint
Validation done       → checkpoint
Tests finished        → checkpoint
Repair planned        → checkpoint
Task completed        → checkpoint
```

Not:

```text
every model token
```

---

# 9. `CheckpointPolicy`

```java
public interface CheckpointPolicy {

    boolean shouldCheckpoint(
        AgentEvent event,
        TaskState state
    );
}
```

Default policy:

```text
state transition → YES
tool execution → YES
mutation → YES
verification completion → YES
ordinary reasoning token → NO
```

---

# 10. Event journal

Checkpointing alone isn't enough.

Keep an append-only event stream:

```java
public interface TaskEventStore {

    Uni<Void> append(TaskEvent event);

    Multi<TaskEvent> stream(TaskId taskId);
}
```

Events:

```text
TASK_CREATED
PHASE_CHANGED
CONTEXT_ACQUIRED
PLAN_CREATED
TOOL_STARTED
TOOL_COMPLETED
PATCH_APPLIED
PATCH_ROLLED_BACK
TEST_STARTED
TEST_COMPLETED
CHECKPOINT_CREATED
TASK_COMPLETED
```

---

# 11. Why both events and checkpoints?

Because they serve different purposes.

### Checkpoint

Fast recovery:

```text
load revision 42
```

### Event log

Detailed history:

```text
what happened between revision 41 and 42?
```

So:

```text
Checkpoint
+
EventLog
```

gives you both speed and auditability.

---

# 12. Event sourcing doesn't need to be extreme

I wouldn't make Aljabr a pure event-sourced system initially.

Use:

```text
durable snapshot
+
append-only events
```

rather than reconstructing the entire agent state from 100,000 events.

Periodically:

```text
events 1...100
       ↓
checkpoint
       ↓
events 101...130
```

---

# 13. `StateSnapshot`

Don't serialize live Java objects blindly.

Use a versioned DTO:

```java
public record StateSnapshot(
    int schemaVersion,
    TaskState state,
    ReasoningState reasoning,
    ContextState context,
    WorkspaceState workspace,
    ChangeState changes,
    VerificationState verification,
    BudgetState budgets
) {}
```

This matters later when Aljabr evolves.

---

# 14. Schema versioning

Suppose:

```text
Aljabr v0.4
schema = 3
```

Then v0.5 introduces:

```text
schema = 4
```

The resume engine can migrate:

```text
schema 3
 ↓
StateMigrator
 ↓
schema 4
```

Don't let persistence format become an architectural trap.

---

# 15. `StateMigrator`

```java
public interface StateMigrator {

    boolean supports(
        int fromVersion,
        int toVersion
    );

    StateSnapshot migrate(
        StateSnapshot snapshot
    );
}
```

---

# 16. Agent loop

Now implement:

```java
public interface AgentLoop {

    Uni<TaskResult> run(TaskId taskId);
}
```

Internally:

```text
load state
↓
recover
↓
select next action
↓
execute
↓
persist
↓
repeat
```

---

# 17. `Step`

Don't make one giant `run()` method.

Represent agent work as steps:

```java
public record AgentStep(
    StepId id,
    TaskId taskId,
    StepType type,
    StepInput input
) {}
```

```java
public enum StepType {

    ANALYZE,
    INVESTIGATE,
    PLAN,
    PATCH,
    VALIDATE,
    EXECUTE_TESTS,
    VERIFY,
    REVIEW,
    REPAIR,
    COMPLETE
}
```

---

# 18. `StepExecutor`

```java
public interface StepExecutor {

    Uni<StepResult> execute(
        AgentStep step,
        AgentContext context
    );
}
```

Then:

```text
AnalyzeStepExecutor
PatchStepExecutor
VerificationStepExecutor
...
```

This keeps the runtime modular.

---

# 19. Critical rule: steps must be idempotent where possible

Suppose Aljabr crashes after:

```text
PATCH_APPLIED
```

but before:

```text
CHECKPOINT_CREATED
```

On restart, you don't want:

```text
apply patch again
```

Instead:

```text
step id
+
transaction id
+
workspace revision
```

allow detection of:

```text
already applied
```

---

# 20. `StepExecution`

Persist this:

```java
public record StepExecution(
    StepId stepId,
    StepStatus status,
    Instant startedAt,
    Instant completedAt,
    String inputHash,
    String outputHash
) {}
```

Then:

```text
step S42
status:
COMPLETED
```

means:

```text
don't run again
```

unless explicitly invalidated.

---

# 21. Tool execution must also be durable

This connects directly to your earlier caching idea.

Instead of:

```text
Gemma
 ↓
search files
 ↓
crash
 ↓
repeat search
```

store:

```text
ToolInvocation
ToolInputHash
ToolOutput
ToolVersion
WorkspaceRevision
```

---

# 22. `ToolExecutionRecord`

```java
public record ToolExecutionRecord(
    ToolExecutionId id,
    TaskId taskId,
    String toolName,
    String inputHash,
    String outputHash,
    long workspaceRevision,
    Instant executedAt,
    ToolExecutionStatus status
) {}
```

---

# 23. Deterministic tool cache

Now you can ask:

```text
Have I already performed this exact operation
against this exact workspace state?
```

Cache key:

```text
tool
+
normalized input
+
workspace revision/hash
+
tool version
```

Example:

```text
search_symbol
TokenValidator.validateExpiration
workspace=abc123
tool=v4
```

→ cached.

---

# 24. But don't blindly reuse stale results

This is crucial.

A search result from:

```text
workspace hash A
```

cannot necessarily be reused after:

```text
workspace hash B
```

So cache validity must be explicit.

```java
public interface CacheValidityPolicy {

    boolean valid(
        CacheEntry entry,
        ExecutionContext current
    );
}
```

---

# 25. Different cache classes

Don't have one generic "cache."

Use:

```text
ObservationCache
ReasoningCache
ExecutionCache
ArtifactCache
ContextCache
```

### ObservationCache

```text
file scan
symbol lookup
dependency graph
```

### ExecutionCache

```text
test result
compile result
lint result
```

### ReasoningCache

```text
hypothesis
analysis
classification
```

### ArtifactCache

```text
generated patch
AST
index
```

---

# 26. Reasoning cache needs more caution

This is where your earlier memory discussion matters.

Don't cache:

```text
"Gemma thinks this is the correct fix."
```

forever.

Instead store:

```java
public record ReasoningArtifact(
    String reasoningId,
    String taskScope,
    String inputContextHash,
    String modelId,
    String promptPolicyVersion,
    String outputHash,
    Instant createdAt,
    ReasoningValidity validity
) {}
```

Then invalidate when relevant evidence changes.

---

# 27. Context cache

Likewise:

```text
"these 8 files are relevant"
```

can be reused only if:

```text
workspace hasn't materially changed
```

or if the changed files are outside the dependency region.

That means your `ImpactGraph` can help cache invalidation.

Very powerful.

---

# 28. Workspace fingerprint

We need a compact identity:

```java
public record WorkspaceFingerprint(
    String repositoryId,
    String revision,
    String indexVersion,
    String dependencyFingerprint
) {}
```

For Git:

```text
repository + commit
```

For an uncommitted workspace:

```text
content Merkle hash
```

or an incremental workspace fingerprint.

---

# 29. Dirty workspace

Don't assume Git commit is enough.

The user may have:

```text
modified file A
untracked file B
```

So:

```text
workspace fingerprint
```

must represent the actual state Aljabr reads.

---

# 30. Incremental fingerprinting

Don't hash the entire repository every time.

Use:

```text
file hash
 ↓
directory hash
 ↓
workspace Merkle root
```

Then modifying:

```text
src/auth/Token.java
```

only requires recalculating its ancestors.

This is especially useful for local machines.

---

# 31. Resume algorithm

Now we can implement the core:

```text
resume(taskId)
```

Algorithm:

```text
1. load latest checkpoint
2. load events after checkpoint
3. reconstruct current state
4. inspect unfinished step
5. inspect workspace
6. reconcile external side effects
7. determine next safe action
8. continue
```

---

# 32. Reconciliation is the hard part

Example:

```text
state says:
PATCH_STARTED

workspace says:
patch exists

event log says:
no PATCH_COMPLETED
```

Don't simply rerun.

Run:

```text
MutationReconciler
```

---

# 33. `MutationReconciler`

```java
public interface MutationReconciler {

    Uni<RecoveryDecision> reconcile(
        TaskState state,
        WorkspaceState workspace,
        List<TaskEvent> recentEvents
    );
}
```

Possible:

```java
public enum RecoveryDecision {

    CONTINUE,
    MARK_COMPLETED,
    ROLLBACK,
    REPAIR,
    BLOCK
}
```

---

# 34. Example recovery

```text
PATCH_STARTED
      │
      ▼
workspace inspection
      │
      ├── patch absent
      │      ↓
      │   retry
      │
      ├── patch complete
      │      ↓
      │   validate
      │
      └── partial patch
             ↓
          rollback
```

This is far safer than naive retry.

---

# 35. External execution reconciliation

Tests are similar.

Suppose:

```text
TEST_STARTED
```

then Aljabr crashes.

Did the test finish?

Maybe.

Don't immediately run it again.

Look for:

```text
execution process
result artifact
process ID
runner journal
```

If result is recoverable:

```text
mark completed
```

Otherwise:

```text
retry
```

---

# 36. `ExecutionReconciler`

```java
public interface ExecutionReconciler {

    Uni<RecoveryDecision> reconcile(
        ExecutionRecord execution
    );
}
```

---

# 37. Model call reconciliation

This is also useful.

Suppose:

```text
Gemma request sent
```

but connection drops before response arrives.

You don't know whether the model generated output.

For local inference:

```text
request ID
```

can be persisted.

If the inference engine supports deterministic request replay or result persistence:

```text
recover response
```

Otherwise:

```text
retry with same inference context
```

but mark:

```text
POSSIBLE_DUPLICATE_INFERENCE
```

---

# 38. Don't persist chain-of-thought

Important architectural distinction.

You don't need to persist hidden/internal reasoning.

Persist **structured reasoning artifacts**:

```text
Hypothesis
Evidence
Decision
Uncertainty
Action
```

For example:

```json
{
  "hypothesis": "expiration uses issuedAt incorrectly",
  "evidence": ["E17", "E21"],
  "confidence": 0.91,
  "decision": "modify validateExpiration"
}
```

This is much more useful and compact.

---

# 39. `ReasoningState`

```java
public record ReasoningState(
    List<Hypothesis> hypotheses,
    List<Decision> decisions,
    List<Uncertainty> uncertainties,
    Optional<HypothesisId> activeHypothesis
) {}
```

This is the durable semantic memory of the task.

---

# 40. Context state

Likewise:

```java
public record ContextState(
    ContextSetId activeContext,
    Set<EvidenceId> evidence,
    TokenBudget budget,
    ContextSummary summary
) {}
```

Don't serialize the entire giant prompt as the primary state.

Store:

```text
context references
+
compressed summary
+
retrievable evidence IDs
```

Then reconstruct the prompt when needed.

---

# 41. This is especially good for Gemma 12B

Instead of restoring:

```text
30k token prompt
```

restore:

```text
Task state
+
8 evidence references
+
1 summary
+
current decision
```

Then `ContextEngine` reconstructs the minimal prompt.

---

# 42. Durable state after context compaction

Suppose the conversation has become huge.

ContextEngine can:

```text
old context
 ↓
extract durable facts
 ↓
store Evidence/Hypothesis/Decision
 ↓
discard raw context
```

Then:

```text
new compact context
```

No loss of the important task state.

---

# 43. This creates a very clean relationship

```text
Raw observation
      ↓
Evidence
      ↓
Reasoning artifact
      ↓
Decision
      ↓
Action
      ↓
Result
      ↓
Verification
```

Persist those semantic artifacts.

Do not persist everything indiscriminately.

---

# 44. Event example

A task might generate:

```json
{
  "event": "E42",
  "type": "HYPOTHESIS_CREATED",
  "task": "T1",
  "payload": {
    "hypothesis": "expiration uses issuedAt"
  }
}
```

Then:

```json
{
  "event": "E43",
  "type": "CHANGE_PLANNED",
  "payload": {
    "target": "TokenValidator.validateExpiration"
  }
}
```

Then:

```json
{
  "event": "E44",
  "type": "PATCH_APPLIED",
  "payload": {
    "changeSet": "CS7"
  }
}
```

Then:

```json
{
  "event": "E45",
  "type": "VERIFICATION_COMPLETED",
  "payload": {
    "result": "PASS"
  }
}
```

This becomes a complete audit trail.

---

# 45. `TaskTimeline`

Build a human-readable view:

```text
12:41  Task started
12:42  Repository indexed
12:43  Root cause identified
12:44  Patch planned
12:44  Patch applied
12:44  12 tests passed
12:45  Requirement verified
12:45  Task completed
```

This is useful both for users and debugging.

---

# 46. Checkpoint frequency policy for local hardware

For your M4 16 GB environment, I'd bias toward **more durable semantic checkpoints** because the cost is tiny compared with repeating inference.

Something like:

```text
after investigation      ✓
after plan               ✓
after model decision     ✓
after patch              ✓
after test batch         ✓
after verification       ✓
```

But not:

```text
every tool output
```

unless the output is expensive to reproduce.

---

# 47. Expensive-operation classification

Add:

```java
public enum ReproductionCost {

    TRIVIAL,
    CHEAP,
    MODERATE,
    EXPENSIVE,
    VERY_EXPENSIVE
}
```

Example:

```text
read file:
CHEAP

ripgrep:
CHEAP

AST index:
MODERATE

Gradle build:
EXPENSIVE

Gemma inference:
EXPENSIVE

large repository analysis:
VERY_EXPENSIVE
```

Checkpoint/cache decisions can use this.

---

# 48. `PersistencePolicy`

Now combine:

```java
public interface PersistencePolicy {

    PersistenceDecision decide(
        Artifact artifact,
        ReproductionCost cost,
        TaskState state
    );
}
```

So Aljabr automatically knows:

```text
cheap + deterministic
→ don't persist aggressively

expensive + deterministic
→ cache

expensive + semantic
→ persist structured artifact
```

---

# 49. This is much better than "cache everything"

Because indiscriminate caching causes:

```text
stale knowledge
storage growth
bad invalidation
confusing recovery
```

The runtime should ask:

> **What would it cost to reproduce this, and under what conditions would it remain valid?**

---

# 50. State machine

At this point I would formalize Aljabr's state machine.

```text
CREATED
   │
   ▼
UNDERSTANDING
   │
   ▼
INVESTIGATING
   │
   ├── need context ──→ INVESTIGATING
   │
   ▼
PLANNING
   │
   ▼
PATCHING
   │
   ├── conflict ──→ INVESTIGATING
   │
   ▼
VALIDATING
   │
   ├── invalid ──→ REPAIRING
   │
   ▼
VERIFYING
   │
   ├── failed ──→ REPAIRING
   │
   ▼
REVIEWING
   │
   ├── concern ──→ REPAIRING
   │
   ▼
COMPLETED
```

This should be deterministic.

Gemma shouldn't control arbitrary state transitions.

---

# 51. `TransitionPolicy`

```java
public interface TransitionPolicy {

    TransitionDecision evaluate(
        TaskState current,
        AgentSignal signal
    );
}
```

Example:

```text
VERIFYING + tests_failed
→ REPAIRING

VERIFYING + all_requirements_verified
→ REVIEWING

REVIEWING + accepted
→ COMPLETED
```

---

# 52. Why this matters

Without a state machine, coding agents tend to develop chaotic behavior:

```text
search
edit
search
edit
run test
edit
search
run test
```

With explicit state:

```text
INVESTIGATING
```

has a defined purpose.

```text
PATCHING
```

has a defined purpose.

```text
VERIFYING
```

has a defined purpose.

That makes Aljabr easier to reason about and debug.

---

# 53. Model selection can happen per state

This also connects to your cloud/local strategy.

For example:

```text
INVESTIGATING
→ Gemma 12B

PATCHING
→ Gemma 12B + deterministic tools

REVIEWING complex architecture
→ larger cloud model if available
```

Same task state.

Different model capability.

---

# 54. Context policy also becomes state-aware

For example:

### Investigating

```text
small targeted evidence
```

### Planning

```text
symbols
call graph
requirements
```

### Patch

```text
exact source region
```

### Review

```text
diff
requirements
verification evidence
```

This prevents the giant "send everything" prompt.

---

# 55. The local-first architecture is now very coherent

```text
              ALJABR RUNTIME
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
 Capability      Context       Durable
 Detector        Engine         State
       │            │            │
       └────────────┼────────────┘
                    ▼
                AgentLoop
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
       Reason      Tools     Memory
          │         │         │
          └─────────┼─────────┘
                    ▼
               Change/Patch
                    │
                    ▼
               Verification
                    │
                    ▼
               Completion
```

Gemma is effectively:

```text
a replaceable reasoning engine
```

rather than the runtime itself.

---

# 56. The next major milestone

After this, I would **not immediately add more agent intelligence**.

The next milestone should be:

## `Aljabr Tool Protocol + Capability-Aware Tool Registry`

That gives you a clean contract for:

```text
read_file
search
symbol_lookup
find_references
AST_query
build
test
lint
format
patch
git
browser/web
memory
context
```

with:

```text
permissions
cost
risk
cacheability
idempotency
input/output schema
model visibility
local/cloud availability
```

And that is where we can make the whole system **small-model optimized at the protocol level**, rather than merely optimizing prompts.

The key idea will be:

```text
Model should not ask:
"How do I accomplish this?"

Model should ask:
"Which capability do I need next?"
```

Wayang then determines the cheapest, safest, most appropriate tool and returns a **compact structured result**.

That will be the foundation for making Aljabr genuinely practical on your M4 16 GB rather than just theoretically capable.


Yes. **Next milestone: `ToolProtocol + CapabilityAwareToolRegistry`.**

This is where the previous pieces become practical. The model should not need to understand Wayang's internal machinery. It should see a **small, stable capability surface**.

The key design principle:

> **Tools are capabilities, not functions exposed directly from the runtime.**

That distinction lets Wayang decide which implementation, permission, cache, context policy, and execution strategy should be used.

---

# 1. The target architecture

Instead of:

```text
Gemma
 ├── read_file()
 ├── write_file()
 ├── shell()
 ├── grep()
 ├── git()
 └── test()
```

build:

```text
Gemma
   │
   ▼
ToolRequest
   │
   ▼
ToolRouter
   │
   ▼
CapabilityRegistry
   │
   ├── FileCapability
   ├── SearchCapability
   ├── SymbolCapability
   ├── BuildCapability
   ├── TestCapability
   ├── PatchCapability
   ├── GitCapability
   └── WebCapability
   │
   ▼
Policy / Cache / Budget
   │
   ▼
ToolExecutor
   │
   ▼
Compact ToolResult
```

The model never needs to know whether a capability is implemented by:

```text
ripgrep
tree-sitter
JDT
Gradle
Xcode
Git
Python
remote service
```

or something else.

---

# 2. `ToolCapability`

Start with:

```java
public interface ToolCapability {

    CapabilityId id();

    ToolDescriptor descriptor();

    Uni<ToolResult> execute(
        ToolRequest request,
        ToolContext context
    );
}
```

But I would actually make the descriptor more important than the implementation.

---

# 3. `ToolDescriptor`

```java
public record ToolDescriptor(
    CapabilityId id,
    String name,
    String description,
    ToolInputSchema inputSchema,
    ToolOutputSchema outputSchema,
    ToolMetadata metadata
) {}
```

Metadata:

```java
public record ToolMetadata(
    ToolCost cost,
    ToolRisk risk,
    boolean deterministic,
    boolean cacheable,
    boolean idempotent,
    boolean mutatesWorkspace,
    boolean requiresNetwork
) {}
```

This is extremely valuable.

Wayang can reason about the tool **without invoking it**.

---

# 4. Example

For `find_symbol`:

```json
{
  "id": "workspace.find_symbol",
  "description": "Find a symbol by name and return compact symbol metadata.",
  "cost": "LOW",
  "risk": "READ_ONLY",
  "deterministic": true,
  "cacheable": true,
  "mutatesWorkspace": false
}
```

Gemma doesn't need:

```text
implementation details
```

---

# 5. Tool taxonomy

I would organize capabilities into domains.

```text
workspace.*
search.*
code.*
build.*
test.*
patch.*
git.*
web.*
memory.*
context.*
```

For example:

```text
workspace.read
workspace.list

search.text
search.symbol
search.reference

code.symbol
code.references
code.ast

patch.plan
patch.apply

build.compile
test.run
test.discover

git.status
git.diff
git.log

context.retrieve
context.summarize

memory.store
memory.search
```

This becomes a very clean vocabulary.

---

# 6. Don't expose 50 tools to Gemma

This is especially important for your 12B model.

Suppose Wayang internally has:

```text
73 capabilities
```

Do **not** put all 73 into the model context.

Instead:

```text
Task
 ↓
CapabilityRouter
 ↓
candidate capabilities
 ↓
3–6 relevant tools
 ↓
Gemma
```

So the model sees only:

```text
find_symbol
find_references
read_source
run_tests
```

for a coding task.

---

# 7. `CapabilityRouter`

```java
public interface CapabilityRouter {

    Uni<List<ToolDescriptor>> select(
        ToolSelectionRequest request,
        AgentContext context
    );
}
```

Example:

```text
Goal:
fix TokenValidator expiration

Candidate tools:

1. code.symbol
2. code.references
3. workspace.read
4. test.discover
```

No shell.

No Git.

No web.

No memory.

Unless needed.

---

# 8. Tool selection should be partly deterministic

Don't ask Gemma:

> Which of 73 tools should I use?

Wayang already knows:

```text
current phase = INVESTIGATING
target = Java symbol
```

Therefore:

```text
likely capabilities:
code.symbol
code.references
workspace.read
search.text
```

Gemma only chooses among a tiny candidate set.

---

# 9. `ToolRequest`

```java
public record ToolRequest(
    ToolRequestId id,
    CapabilityId capability,
    Map<String, Object> arguments,
    ToolRequestOptions options
) {}
```

Example:

```json
{
  "capability": "code.references",
  "arguments": {
    "symbol": "TokenValidator.validateExpiration"
  }
}
```

Small.

---

# 10. Tool result must be compact

This is one of the most important local-model optimizations.

Do **not** return:

```text
20,000 lines of source
```

from:

```text
find_references
```

Instead:

```json
{
  "symbol": "TokenValidator.validateExpiration",
  "references": [
    "AuthService.validate",
    "RefreshTokenService.refresh"
  ],
  "count": 2
}
```

Then the model can request details if necessary.

---

# 11. `ToolResult`

```java
public record ToolResult(
    ToolExecutionId executionId,
    ToolStatus status,
    ToolPayload payload,
    ToolEvidence evidence,
    ToolMetrics metrics
) {}
```

---

# 12. Separate result from evidence

A tool result says:

```text
what happened
```

Evidence says:

```text
what was observed
```

For example:

```text
ToolResult:
2 references found

Evidence:
symbol = TokenValidator.validateExpiration
workspaceRevision = abc123
source = code.index
```

That evidence can enter the EvidenceGraph.

---

# 13. `ToolEvidence`

```java
public record ToolEvidence(
    EvidenceId id,
    EvidenceType type,
    String contentHash,
    WorkspaceFingerprint workspace,
    Instant observedAt
) {}
```

Now tool outputs become durable task evidence.

---

# 14. This connects everything

We now have:

```text
Tool
 ↓
ToolResult
 ↓
Evidence
 ↓
EvidenceGraph
 ↓
ContextEngine
 ↓
Gemma
```

and:

```text
Gemma
 ↓
ToolRequest
 ↓
ToolRouter
 ↓
Tool
```

So the architecture forms a closed loop.

---

# 15. Tool invocation lifecycle

Every invocation should go through:

```text
REQUESTED
    │
    ▼
AUTHORIZED
    │
    ▼
CACHE_LOOKUP
    │
    ├── HIT ────────────┐
    │                   │
    ▼                   │
EXECUTING               │
    │                   │
    ▼                   │
NORMALIZING              │
    │                   │
    ▼                   │
PERSISTING ◄────────────┘
    │
    ▼
RETURNED
```

This is much better than:

```text
tool.execute()
```

directly.

---

# 16. `ToolMiddleware`

I'd implement tool execution as middleware.

```java
public interface ToolMiddleware {

    Uni<ToolResult> intercept(
        ToolRequest request,
        ToolContext context,
        ToolChain chain
    );
}
```

Then:

```text
ToolRequest
 ↓
AuthorizationMiddleware
 ↓
BudgetMiddleware
 ↓
CacheMiddleware
 ↓
TimeoutMiddleware
 ↓
AuditMiddleware
 ↓
ExecutionMiddleware
```

This keeps cross-cutting behavior out of every individual tool.

---

# 17. Authorization

Example:

```text
workspace.read
    READ

test.run
    EXECUTE

patch.apply
    MUTATE

git.commit
    HIGH_MUTATION

web.search
    NETWORK
```

Represent:

```java
public enum ToolAuthority {

    READ,
    ANALYZE,
    EXECUTE,
    MUTATE,
    NETWORK,
    DESTRUCTIVE
}
```

---

# 18. Tool permission should be task-scoped

For example:

```text
Task:
Fix bug

allowed:
READ
ANALYZE
EXECUTE
MUTATE_WORKSPACE

not allowed:
GIT_COMMIT
NETWORK
DESTRUCTIVE
```

Unless policy explicitly enables them.

---

# 19. This prevents model escalation

If Gemma tries:

```json
{
  "capability": "git.commit"
}
```

the router says:

```text
DENIED

reason:
capability not authorized for current task
```

Gemma never gets raw Git authority.

---

# 20. Tool budgets

You already have task budgets.

Now add tool-level budgets:

```java
public record ToolBudget(
    int maxCalls,
    Duration maxExecutionTime,
    long maxOutputTokens,
    long maxInputBytes
) {}
```

Example:

```text
search.text:
maxCalls = 20

test.run:
maxCalls = 5

web.search:
maxCalls = 3
```

---

# 21. Output token budget is important

For local Gemma:

```text
tool output
```

can be more expensive than model output.

So every tool should have:

```text
maxResultSize
```

Example:

```text
find_references
→ max 30 results

search.text
→ max 20 matches
```

Then:

```text
remaining results available
```

instead of dumping everything.

---

# 22. Pagination

A tool should be able to say:

```json
{
  "items": [...],
  "has_more": true,
  "cursor": "abc123"
}
```

Then Gemma can request:

```text
next page
```

only if necessary.

---

# 23. Better than huge context

Bad:

```text
search → 10,000 lines
```

Good:

```text
search → 10 relevant matches
```

Then:

```text
read file region
```

if necessary.

This is the fundamental **progressive disclosure** strategy.

---

# 24. `ToolOutputPolicy`

The runtime should decide:

```java
public interface ToolOutputPolicy {

    ToolOutputPlan plan(
        ToolDescriptor descriptor,
        ToolRequest request,
        ContextBudget budget
    );
}
```

Example:

```text
search:
summary + top 10 matches

read_source:
requested range only

test:
failure summary + relevant stack frames
```

---

# 25. Source reading should be region-based

Don't make:

```text
read_file(path)
```

the primary API.

Prefer:

```text
read_source(
  path,
  startLine,
  endLine
)
```

or better:

```text
read_symbol(
  TokenValidator.validateExpiration
)
```

The second is much more model-friendly.

---

# 26. `code.read_symbol`

Example output:

```text
Symbol:
TokenValidator.validateExpiration

File:
src/auth/TokenValidator.java

Range:
91–101

Body:
...

Dependencies:
expiresAt
clock
```

That is much more useful than:

```text
read_file → 600 lines
```

---

# 27. Semantic tools should be preferred over primitive tools

Define a priority:

```text
semantic capability
    ↓
structured capability
    ↓
text capability
    ↓
raw shell
```

Example:

```text
find method
```

Prefer:

```text
code.symbol
```

over:

```text
grep "validateExpiration"
```

Prefer:

```text
test.run
```

over:

```text
shell("./gradlew test ...")
```

---

# 28. Shell should be an escape hatch

You still want:

```text
shell.exec
```

because real repositories are messy.

But make it:

```text
low-level
high-risk
less preferred
```

Example metadata:

```text
cost = MEDIUM
risk = HIGH
deterministic = UNKNOWN
cacheable = false
mutatesWorkspace = POSSIBLE
```

Then the router will prefer structured tools.

---

# 29. Shell sandbox

For local Aljabr, create:

```java
public interface ExecutionSandbox {

    Uni<ExecutionResult> execute(
        Command command,
        SandboxPolicy policy
    );
}
```

Policy:

```text
working directory
environment
timeout
memory
network
allowed binaries
write paths
```

This is especially important when the model can generate shell commands.

---

# 30. Command classification

Before executing:

```text
npm test
```

versus:

```text
rm -rf /
```

the runtime should classify.

```java
public enum CommandRisk {

    SAFE,
    MODERATE,
    HIGH,
    BLOCKED
}
```

Don't rely solely on an LLM safety judgment.

Use deterministic rules first.

---

# 31. Capability discovery

The model should not receive the entire registry.

Give it:

```text
available capabilities for current phase
```

Example:

```text
INVESTIGATING

Available:
- code.symbol
- code.references
- search.text
- workspace.read

Use:
- code.symbol to inspect a symbol
- code.references to inspect callers
- search.text for unknown text patterns
```

This is enough.

---

# 32. `CapabilityProfile`

Each tool can declare:

```java
public record CapabilityProfile(
    Set<TaskPhase> supportedPhases,
    Set<ModelCapability> requirements,
    ToolMetadata metadata
) {}
```

Then:

```text
current phase
+
model capability
+
task policy
```

filters the registry.

---

# 33. This is where your `CapabilityDetector` pays off

Suppose local Gemma:

```text
structuredOutput = moderate
context = constrained
reasoning = moderate
```

Wayang can choose:

```text
semantic tools
small result sets
few tool choices
```

For a large cloud model:

```text
structuredOutput = strong
context = huge
```

Wayang may allow:

```text
broader tool descriptions
larger result sets
more parallel exploration
```

Same tools.

Different exposure policy.

---

# 34. `ToolExposurePolicy`

```java
public interface ToolExposurePolicy {

    Uni<List<ToolDescriptor>> expose(
        AgentContext context,
        Collection<ToolCapability> available
    );
}
```

This is the bridge between:

```text
CapabilityDetector
```

and:

```text
ToolRegistry
```

---

# 35. Local policy example

For Gemma 12B:

```text
max visible tools:
6

max description tokens:
600

max schema tokens:
500

max result tokens:
1500
```

The exact numbers should be configurable rather than hardcoded.

---

# 36. Cloud policy

For a large-context model:

```text
max visible tools:
20+

larger schemas
larger result budget
more parallel calls
```

But here's the important part:

> **Large context does not mean you should intentionally become wasteful.**

Your compact semantic tool design should remain the default.

The cloud model simply gets a **higher ceiling**, not a fundamentally worse protocol.

---

# 37. Tool batching

Local inference benefits from batching.

Suppose Gemma needs:

```text
symbol
references
tests
```

Instead of:

```text
call
→ Gemma
→ call
→ Gemma
→ call
→ Gemma
```

Wayang can sometimes execute:

```text
parallel:
symbol
references
test discovery
```

then return one compact result.

---

# 38. `ToolBatch`

```java
public record ToolBatch(
    List<ToolRequest> requests,
    BatchExecutionPolicy policy
) {}
```

Result:

```text
BatchResult
 ├── symbol
 ├── references
 └── tests
```

Then only one model turn is needed.

---

# 39. But don't batch dependent calls

This:

```text
find_symbol
 ↓
find_references(symbolId)
```

is dependent.

So:

```text
call 1
→ derive symbolId
→ call 2
```

But:

```text
find_symbol(A)
find_symbol(B)
find_symbol(C)
```

can be parallel.

The router should understand dependencies.

---

# 40. Tool dependency graph

Represent:

```java
public record ToolDependency(
    ToolRequestId from,
    ToolRequestId to
) {}
```

Then:

```text
A ───→ B
C
D
```

means:

```text
A first
B after A
C/D parallel
```

---

# 41. This becomes a small execution planner

Eventually:

```text
ToolRequest
    ↓
Dependency Analysis
    ↓
Batch
    ↓
Parallel Execution
    ↓
Normalize
    ↓
Compact
    ↓
Evidence
```

Again, the model doesn't need to orchestrate this itself.

---

# 42. Tool result compression

Another very important local optimization.

Suppose a test produces:

```text
50KB stack trace
```

Don't send 50KB to Gemma.

Normalizer:

```text
raw output
 ↓
parser
 ↓
failure classifier
 ↓
relevant frames
 ↓
compact result
```

Output:

```text
TEST FAILED

test:
RefreshTokenTest.expired

failure:
expected false
actual true

location:
RefreshTokenTest.java:84

relevant stack:
TokenValidator.validateExpiration:97
```

Maybe 100 tokens.

---

# 43. `ToolResultNormalizer`

```java
public interface ToolResultNormalizer {

    Uni<NormalizedToolResult> normalize(
        ToolResult raw,
        ToolContext context
    );
}
```

Every expensive tool should have one.

---

# 44. Raw output should still be persisted

Don't throw away the original.

Store:

```text
raw artifact
+
normalized result
```

Gemma receives:

```text
normalized result
```

Wayang can later inspect raw output if needed.

This is exactly the right balance for local context efficiency.

---

# 45. Error results should be structured

Don't return:

```text
"command failed"
```

Return:

```json
{
  "status": "FAILED",
  "category": "COMPILATION_ERROR",
  "exitCode": 1,
  "summary": "cannot find symbol Clock",
  "location": "TokenValidator.java:14"
}
```

Now Aljabr can route the failure intelligently.

---

# 46. `ToolFailureClassifier`

```java
public enum ToolFailureCategory {

    INVALID_INPUT,
    NOT_FOUND,
    PERMISSION,
    TIMEOUT,
    NETWORK,
    COMPILATION,
    TEST_FAILURE,
    ENVIRONMENT,
    RESOURCE_LIMIT,
    UNKNOWN
}
```

Then:

```text
COMPILATION
→ inspect source

TIMEOUT
→ retry / reduce scope

PERMISSION
→ ask user

NETWORK
→ offline fallback
```

No model reasoning required.

---

# 47. Tool retry policy

Don't blindly retry everything.

```java
public interface ToolRetryPolicy {

    RetryDecision evaluate(
        ToolFailure failure,
        RetryContext context
    );
}
```

Examples:

```text
timeout:
maybe retry

invalid input:
don't retry unchanged

test failure:
don't retry immediately

network:
retry with backoff

permission:
stop
```

---

# 48. Tool observability

Every call should record:

```text
tool
duration
input size
output size
cache hit
cache miss
failure
workspace revision
task phase
```

Then you can discover:

```text
Gemma spends 40% of task time waiting for tests
```

or:

```text
search output consumes 30% of context
```

This lets you optimize Aljabr empirically.

---

# 49. `ToolMetrics`

```java
public record ToolMetrics(
    Duration duration,
    long inputBytes,
    long outputBytes,
    boolean cacheHit,
    int retries,
    ToolFailureCategory failure
) {}
```

This should feed your runtime telemetry.

---

# 50. Tool registry implementation

A simple first implementation:

```java
public final class DefaultCapabilityRegistry
        implements CapabilityRegistry {

    private final Map<CapabilityId, ToolCapability> capabilities;

    @Override
    public Optional<ToolCapability> find(CapabilityId id) {
        return Optional.ofNullable(capabilities.get(id));
    }
}
```

Keep it boring.

Don't over-engineer plugin discovery yet.

---

# 51. Registry composition

Later:

```text
CoreCapabilities
LanguageCapabilities
BuildCapabilities
GitCapabilities
WebCapabilities
PluginCapabilities
```

compose into:

```text
CapabilityRegistry
```

Example:

```text
Core:
workspace.read
workspace.list

Java:
code.symbol
code.references
code.ast

Gradle:
build.compile
test.run

Git:
git.status
git.diff
```

---

# 52. Language plugins

This architecture also handles different languages naturally.

```text
JavaCapabilityProvider
PythonCapabilityProvider
TypeScriptCapabilityProvider
RustCapabilityProvider
GoCapabilityProvider
```

The runtime doesn't care.

It asks:

```text
what capabilities exist for this workspace?
```

---

# 53. Workspace capability detection

Suppose repository contains:

```text
pom.xml
package.json
```

Wayang detects:

```text
Java
Node
Maven
```

Registry activates:

```text
java.*
node.*
maven.*
```

This keeps the model-facing tool set small.

---

# 54. Capability discovery should be cached

Repository analysis can be expensive.

Store:

```text
WorkspaceProfile
```

containing:

```text
languages
frameworks
build systems
test systems
package managers
repository type
available tools
```

Invalidate when relevant configuration changes.

---

# 55. Tool availability is dynamic

For example:

```text
Gradle available:
YES

Docker:
NO

Node:
YES

Internet:
NO
```

Then tool exposure automatically changes.

Gemma doesn't need to discover:

```text
"gradle not installed"
```

through trial and error.

---

# 56. This is a major local-model optimization

Bad:

```text
Gemma:
run docker

Wayang:
docker unavailable

Gemma:
try podman

Wayang:
podman unavailable

Gemma:
try...
```

Good:

```text
CapabilityDetector:

Docker = unavailable
Podman = unavailable
Gradle = available

expose:
Gradle
```

One inference instead of three.

---

# 57. Capability negotiation

At task startup:

```text
Task
 ↓
WorkspaceProfile
 ↓
ModelCapabilityProfile
 ↓
RuntimePolicy
 ↓
AvailableCapabilities
```

So Aljabr knows:

```text
what the environment can do
```

before reasoning deeply.

---

# 58. The resulting protocol is small

For Gemma, the conceptual protocol can be:

```text
THINK
OBSERVE
REQUEST_CAPABILITY
PROPOSE_CHANGE
VERIFY
FINISH
```

That's almost all it needs.

Wayang translates these into concrete runtime operations.

---

# 59. Model-facing tool schema

I would keep it intentionally minimal:

```json
{
  "capability": "code.references",
  "arguments": {
    "symbol": "TokenValidator.validateExpiration"
  }
}
```

and:

```json
{
  "capability": "workspace.read",
  "arguments": {
    "path": "src/auth/TokenValidator.java",
    "range": "91:101"
  }
}
```

Not a giant tool schema containing internal options.

---

# 60. Internal runtime can be much richer

Internally:

```text
ToolRequest
 ├── taskId
 ├── stepId
 ├── authorization
 ├── budget
 ├── cache policy
 ├── workspace fingerprint
 ├── timeout
 ├── tracing
 └── model-visible arguments
```

Model sees:

```text
capability + arguments
```

Runtime sees everything.

---

# 61. This gives you a clean security boundary

```text
MODEL SPACE
────────────────
intent
tool request
semantic answer

RUNTIME SPACE
────────────────
filesystem
processes
network
Git
credentials
workspace
cache
state
```

Never blur the two.

---

# 62. The entire Aljabr architecture is now becoming coherent

```text
                        ┌───────────────┐
                        │ Model Adapter │
                        │ Gemma / Cloud │
                        └───────┬───────┘
                                │
                         compact protocol
                                │
                                ▼
                        ┌───────────────┐
                        │   AgentLoop   │
                        └───────┬───────┘
                                │
             ┌──────────────────┼──────────────────┐
             ▼                  ▼                  ▼
       ContextEngine       ToolRouter         TaskState
             │                  │                  │
             ▼                  ▼                  ▼
       EvidenceGraph       ToolRegistry      Checkpoints
             │                  │             EventLog
             │                  ▼
             │             ToolMiddleware
             │                  │
             │         ┌────────┼────────┐
             │         ▼        ▼        ▼
             │       Cache    Policy   Executor
             │
             ▼
       ChangePlanner
             │
             ▼
        PatchEngine
             │
             ▼
      VerificationEngine
             │
             ▼
       CompletionPolicy
```

---

# 63. One very important architectural conclusion

At this stage, I would **stop adding model-specific logic**.

Don't build:

```text
GemmaAgent
GemmaCoder
GemmaDebugger
GemmaReviewer
```

as separate runtime architectures.

Build:

```text
ModelAdapter
```

and let all of these be **policies / skills / task strategies**.

Then:

```text
Gemma 12B
Qwen
Llama
cloud model
future model
```

can all drive the same Aljabr runtime.

---

# 64. Next milestone: `Skill Runtime`

Now we have:

```text
Capability
Tool
Context
Task
Patch
Verification
Persistence
```

The next missing layer is the **skill system**.

This is where your existing Wayang `Skill.md` concept becomes much more powerful.

Instead of a Skill merely being:

```text
prompt + tools
```

make it a declarative **behavioral package**:

```text
Skill
 ├── identity
 ├── objective
 ├── triggers
 ├── required capabilities
 ├── allowed phases
 ├── context strategy
 ├── reasoning strategy
 ├── tool policy
 ├── verification policy
 ├── memory policy
 ├── failure strategy
 └── output contract
```

Then Aljabr can have:

```text
coding
debugging
refactoring
code-review
test-generation
migration
dependency-upgrade
research
documentation
```

without creating a separate agent architecture for each.

And crucially, for your **local-first design**, Skill Runtime can decide:

> **What is the smallest amount of context, reasoning, and tool exposure needed to accomplish this particular coding task?**

That is the next foundational layer I'd tackle.
