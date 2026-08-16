
The **next milestone after this should be the Coding Agent Loop itself**: how Aljabr repeatedly goes

```text
OBSERVE → PLAN → ACT → VERIFY → REVIEW
```

while deciding when to continue, when to replan, when to stop, and—most importantly—**when it has enough evidence to confidently declare the coding task complete**.

That is where we can define the actual `AljabrAgent` implementation without turning it into a giant `while(true) { askLLM(); }` class.




This is the point where we should finally define the actual `AljabrAgent` behavior. The important thing is **not** to make it a giant autonomous loop.

Instead, make the loop a **state machine driven by the task graph, evidence, tool results, and explicit completion gates**.

---

# 1. The wrong implementation

Avoid this:

```java
while (!done) {
    var response = llm.chat(context);

    if (response.toolCall()) {
        executeTool(response.toolCall());
    }
}
```

It looks simple, but eventually becomes:

```text
LLM
 ├── decides task
 ├── decides state
 ├── decides retry
 ├── decides completion
 ├── decides memory
 ├── decides recovery
 ├── decides whether verification passed
 └── decides whether to stop
```

That's too much authority.

---

# 2. The Aljabr loop should be controlled

Instead:

```text
                 ┌──────────────┐
                 │   OBJECTIVE  │
                 └──────┬───────┘
                        ▼
                    OBSERVE
                        │
                        ▼
                     PLAN
                        │
                        ▼
                    EXECUTE
                        │
                        ▼
                    VERIFY
                        │
                        ▼
                    REVIEW
                        │
              ┌─────────┼─────────┐
              ▼         ▼         ▼
           DONE      REPLAN     RECOVER
                        │         │
                        └────┬────┘
                             ▼
                          OBSERVE
```

The runtime owns the transitions.

The model supplies **proposals and decisions within controlled boundaries**.

---

# 3. `AljabrAgent`

I'd make the public interface tiny:

```java
public interface AljabrAgent {

    Uni<AljabrResult> run(
        CodingRequest request
    );

    Uni<AljabrState> state(
        String executionId
    );

    Uni<Void> cancel(
        String executionId
    );
}
```

That's enough.

Don't expose dozens of orchestration methods publicly.

---

# 4. Agent state

```java
public record AljabrState(
    String executionId,
    AgentPhase phase,
    CodingPlan plan,
    CodingProgress progress,
    WorkspaceRevision workspaceRevision,
    AgentOutcome outcome
) {}
```

Phases:

```java
public enum AgentPhase {

    INITIALIZING,

    OBSERVING,

    PLANNING,

    EXECUTING,

    VERIFYING,

    REVIEWING,

    REPLANNING,

    RECOVERING,

    WAITING_FOR_USER,

    COMPLETED,

    FAILED,

    CANCELLED
}
```

---

# 5. The state machine

Make transitions explicit.

```java
public interface AgentStateMachine {

    TransitionResult transition(
        AljabrState state,
        AgentEvent event
    );
}
```

Example:

```text
INITIALIZING
    ↓
OBSERVING
    ↓
PLANNING
    ↓
EXECUTING
    ↓
VERIFYING
    ↓
REVIEWING
    ↓
COMPLETED
```

Failure:

```text
EXECUTING
    ↓
RECOVERING
    ↓
REPLANNING
    ↓
EXECUTING
```

---

# 6. `AgentEvent`

```java
public sealed interface AgentEvent {

    record ObservationCompleted(...) implements AgentEvent {}

    record PlanCreated(...) implements AgentEvent {}

    record TaskSucceeded(...) implements AgentEvent {}

    record TaskFailed(...) implements AgentEvent {}

    record VerificationCompleted(...) implements AgentEvent {}

    record ReviewCompleted(...) implements AgentEvent {}

    record UserApproved(...) implements AgentEvent {}

    record UserDenied(...) implements AgentEvent {}

    record BudgetExceeded(...) implements AgentEvent {}

    record CancellationRequested(...) implements AgentEvent {}
}
```

The state machine responds to events rather than random method calls.

---

# 7. Why this matters

Suppose Aljabr crashes after:

```text
T3 MODIFY ✓
```

Before continuing, it loads:

```text
AljabrState
TaskGraph
WorkspaceRevision
Evidence
```

and determines:

```text
T1 ✓
T2 ✓
T3 ✓
T4 READY
T5 BLOCKED
```

Then resumes.

No LLM memory reconstruction is required.

---

# 8. Observation phase

The first stage should not immediately modify anything.

```java
public interface Observer {

    Uni<ObservationResult> observe(
        CodingRequest request,
        ObservationContext context
    );
}
```

The observer should answer:

```text
What do we already know?
What repository is this?
What changed?
What relevant evidence exists?
What is the likely scope?
What is unknown?
```

---

# 9. Initial observation should be cheap

Don't immediately scan the whole repository.

Start with:

```text
workspace status
repository metadata
build system
relevant project structure
existing task/memory
user objective
```

Then progressively deepen research.

This keeps Aljabr fast for simple tasks.

---

# 10. Observation result

```java
public record ObservationResult(
    RepositorySnapshot repository,
    List<Observation> observations,
    List<String> evidenceIds,
    List<Unknown> unknowns
) {}
```

The `unknowns` are important.

```java
public record Unknown(
    String question,
    UnknownImportance importance
) {}
```

Example:

```text
Unknown:
"Which component owns token expiration?"

importance:
HIGH
```

---

# 11. Unknowns become research targets

This gives a natural transition:

```text
OBSERVE
   ↓
unknowns
   ↓
RESEARCH TASKS
   ↓
PLAN
```

Rather than asking the LLM to magically know what it doesn't know.

---

# 12. Planning phase

The planner receives:

```text
objective
observations
unknowns
relevant knowledge
repository state
constraints
```

and produces:

```java
PlanProposal
```

Then:

```text
PlanProposal
    ↓
PlanValidator
    ↓
PlanReconciler
    ↓
CodingPlan
```

The LLM is only one participant in this process.

---

# 13. Planning should be iterative

Don't assume:

```text
one plan = entire task
```

Instead:

```text
Plan v1
    ↓
execute
    ↓
new evidence
    ↓
Plan v2
    ↓
execute
```

This is critical for real repositories.

---

# 14. Execution phase

The executor shouldn't ask the LLM:

> "What should I do next?"

every single time.

The scheduler already knows:

```text
which tasks are READY
which dependencies are satisfied
which tasks are blocked
```

So:

```java
List<CodingTask> ready =
    scheduler.readyTasks(plan, state);
```

Then execute those.

---

# 15. When does the LLM participate?

The LLM participates when judgment is needed.

For example:

```text
research interpretation
plan generation
ambiguous failure analysis
patch proposal
review
replanning
```

Not:

```text
"Is T3's dependency T2 satisfied?"
```

The runtime knows that.

---

# 16. This distinction is crucial

Use deterministic code for:

```text
graph
state
permissions
budgets
workspace
hashes
dependency checks
task scheduling
tool validation
verification status
```

Use the model for:

```text
interpretation
inference
planning proposals
code reasoning
failure diagnosis
alternative generation
review
```

This dramatically improves reliability.

---

# 17. Tool-call loop inside a task

A task can still involve multiple model/tool turns.

For example:

```text
TASK: fix TokenValidator

LLM
 ↓
inspect_symbol
 ↓
result
 ↓
LLM
 ↓
read_file
 ↓
result
 ↓
LLM
 ↓
apply_patch
 ↓
result
 ↓
LLM
 ↓
get_diff
 ↓
result
 ↓
TASK COMPLETE
```

But that loop belongs to:

```text
TaskAgent
```

not the global `AljabrAgent`.

---

# 18. `TaskAgent`

```java
public interface TaskAgent {

    Uni<TaskExecutionResult> execute(
        CodingTask task,
        TaskExecutionContext context
    );
}
```

Then:

```text
AljabrAgent
   │
   ▼
TaskScheduler
   │
   ▼
TaskAgent
   │
   ├── LLM
   ├── tools
   └── evidence
```

This gives you a very clean hierarchy.

---

# 19. Task loop

A task agent can use:

```java
public interface TaskLoop {

    Uni<TaskExecutionResult> run(
        CodingTask task,
        TaskExecutionContext context
    );
}
```

Internally:

```text
LOAD TASK
   ↓
BUILD CONTEXT
   ↓
MODEL TURN
   ↓
TOOL CALL?
 ┌─┴───────┐
 │         │
yes        no
 │         │
 ▼         ▼
tool     decision
 │         │
 ▼         ▼
evidence  evaluate
 │         │
 └────┬────┘
      ▼
 continue?
```

---

# 20. The task loop needs a stop policy

Never:

```java
while (true)
```

Use:

```java
public record TaskLoopBudget(
    int maxModelTurns,
    int maxToolCalls,
    Duration maxDuration,
    long maxTokens
) {}
```

Then every task has a finite execution envelope.

---

# 21. Task completion must be explicit

The model should not simply say:

```text
"I'm done."
```

Instead it produces:

```java
public record TaskCompletionProposal(
    TaskStatus proposedStatus,
    String summary,
    List<String> evidenceIds,
    CompletionClaim claim
) {}
```

Then the runtime validates the claim.

---

# 22. Completion claims

```java
public enum CompletionClaim {

    INFORMATION_FOUND,

    CHANGE_APPLIED,

    TEST_ADDED,

    VERIFICATION_PASSED,

    REVIEW_COMPLETED
}
```

Example:

```text
MODIFY task
claim:
CHANGE_APPLIED
```

The runtime checks:

```text
Did workspace actually change?
Is the patch valid?
Are expected files modified?
```

---

# 23. Completion gate

This should be deterministic whenever possible.

```java
public interface CompletionGate {

    Uni<CompletionDecision> evaluate(
        CodingTask task,
        TaskExecutionResult result,
        CodingExecutionContext context
    );
}
```

Example:

```text
Task:
VERIFY

Claim:
VERIFICATION_PASSED

Gate:
test result exists
AND
status == PASSED
AND
workspace revision matches
```

Only then:

```text
SUCCEEDED
```

---

# 24. This prevents hallucinated completion

Without this:

```text
LLM:
"Tests should pass."
```

Agent:

```text
SUCCESS
```

Bad.

With completion gates:

```text
LLM:
"Tests should pass."

Runtime:
No test result found.

→ NOT COMPLETE
```

This is one of the most important Aljabr mechanisms.

---

# 25. Global completion

Now we need a separate:

```java
public interface CompletionPolicy {

    Uni<CompletionDecision> evaluate(
        CodingPlan plan,
        CodingState state,
        EvidenceContext evidence
    );
}
```

The agent can only finish when required conditions are met.

---

# 26. Example completion policy

For a code modification:

```text
required:
    intended files changed
    tests added/updated if required
    affected tests pass
    build passes if applicable
    final diff reviewed
```

Not every task needs every condition.

---

# 27. Completion policy should depend on risk

For a trivial formatting task:

```text
diff review
```

might be enough.

For authentication changes:

```text
tests
build
static analysis
security-sensitive review
```

may be required.

So:

```java
public interface CompletionPolicyResolver {
    CompletionPolicy resolve(
        CodingRequest request,
        CodingPlan plan
    );
}
```

---

# 28. Risk classification

Add:

```java
public enum ChangeRisk {

    TRIVIAL,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}
```

Examples:

```text
README edit
    TRIVIAL

business logic
    MODERATE

authentication
    HIGH

deployment configuration
    HIGH

security infrastructure
    CRITICAL
```

This influences verification depth.

---

# 29. Verification escalation

For example:

```text
LOW
 ↓
targeted tests

MODERATE
 ↓
targeted tests
+
module tests

HIGH
 ↓
targeted tests
+
module tests
+
build
+
static analysis

CRITICAL
 ↓
all above
+
explicit review/approval
```

Don't hardcode these forever, but establish the policy mechanism now.

---

# 30. Review phase

After verification:

```java
public interface CodeReviewer {

    Uni<ReviewResult> review(
        ReviewContext context
    );
}
```

Review should inspect:

```text
diff
objective
evidence
tests
scope
unintended changes
```

Not reread the whole repository.

---

# 31. Review result

```java
public record ReviewResult(
    ReviewStatus status,
    List<ReviewFinding> findings,
    List<String> evidenceIds
) {}
```

```java
public enum ReviewStatus {
    APPROVED,
    CHANGES_REQUIRED,
    BLOCKED
}
```

---

# 32. Review findings

```java
public record ReviewFinding(
    Severity severity,
    String description,
    Optional<String> path,
    Optional<Integer> line
) {}
```

Then:

```text
BLOCKER
    → recovery/replan

WARNING
    → maybe continue

INFO
    → record only
```

---

# 33. Recovery controller

Now we can finally make recovery concrete.

```java
public interface RecoveryController {

    Uni<RecoveryDecision> recover(
        FailureContext failure
    );
}
```

```java
public enum RecoveryAction {

    RETRY,

    RESEARCH,

    REPAIR,

    REPLAN,

    ROLLBACK,

    ASK_USER,

    ABORT
}
```

---

# 34. Failure classification

The model can help diagnose failures, but the runtime first classifies deterministic failures.

Example:

```text
TEST_FAILED
PATCH_CONFLICT
BUILD_FAILED
TOOL_TIMEOUT
PERMISSION_DENIED
BUDGET_EXCEEDED
ENVIRONMENT_FAILURE
```

Then recovery policy decides what to do.

---

# 35. Example

```text
apply_patch
 ↓
PATCH_CONFLICT
 ↓
RecoveryController
 ↓
RESEARCH
 ↓
inspect current file
 ↓
new evidence
 ↓
REPLAN PATCH
```

Not:

```text
retry same patch 10 times
```

---

# 36. Failure loop protection

You need a failure fingerprint.

```java
public record FailureFingerprint(
    String taskId,
    String failureType,
    String relevantStateHash
) {}
```

Store:

```text
F1
F2
F3
```

If the same failure repeats:

```text
same fingerprint
+
same attempted action
```

then:

```text
do not retry blindly
```

Escalate.

---

# 37. Recovery escalation

```text
1st failure
    ↓
retry / local repair

2nd similar failure
    ↓
research

3rd similar failure
    ↓
replan

4th same failure
    ↓
ask user / abort
```

Exact thresholds should be policy-driven.

---

# 38. The agent loop becomes surprisingly small

The orchestration logic can look approximately like:

```java
while (!state.isTerminal()) {

    switch (state.phase()) {

        case OBSERVING ->
            observe();

        case PLANNING ->
            plan();

        case EXECUTING ->
            executeReadyTasks();

        case VERIFYING ->
            verify();

        case REVIEWING ->
            review();

        case RECOVERING ->
            recover();

        case REPLANNING ->
            replan();

        case WAITING_FOR_USER ->
            awaitUser();

        default ->
            ...
    }
}
```

But this is acceptable **only because each phase delegates to dedicated components**.

The state machine controls it.

The loop itself should contain almost no business logic.

---

# 39. Better still: event-driven

Eventually:

```text
AgentEvent
    ↓
StateMachine
    ↓
PhaseHandler
    ↓
Action
    ↓
new AgentEvent
```

For example:

```text
TaskSucceeded
    ↓
Scheduler
    ↓
TaskReady
    ↓
TaskStarted
    ↓
TaskSucceeded
```

This fits Wayang very naturally.

---

# 40. Persist every meaningful transition

```java
public record AgentTransition(
    String executionId,
    AgentPhase from,
    AgentPhase to,
    AgentEvent event,
    Instant timestamp
) {}
```

Now you have a complete execution history.

---

# 41. This gives you "time travel"

Given:

```text
execution E42
```

you can reconstruct:

```text
what was known
what was planned
what changed
what failed
why it replanned
what verification ran
why it completed
```

This is an enormous advantage for debugging Aljabr itself.

---

# 42. Observability

Expose metrics such as:

```text
task success rate
tool failure rate
average turns/task
average tools/task
replan frequency
verification failure rate
completion confidence
budget utilization
```

But importantly:

> Measure the runtime, not just the model.

---

# 43. Example trace

A real execution could look like:

```text
E42
│
├── OBSERVE
│   ├── get_status
│   ├── search_code
│   └── inspect_symbol
│
├── PLAN v1
│   ├── T1 research
│   ├── T2 inspect
│   ├── T3 modify
│   ├── T4 test
│   └── T5 verify
│
├── T1 ✓
├── T2 ✓
├── T3 ✓
├── T4 ✓
│
├── T5
│   └── tests FAILED
│
├── RECOVER
│   └── classify failure
│
├── REPLAN v2
│   └── add T6 inspect failing case
│
├── T6 ✓
├── T7 repair ✓
├── T8 verify ✓
│
└── REVIEW
    └── APPROVED
```

That is a proper coding-agent execution.

---

# 44. The actual `AljabrAgent` architecture now

```text
                         AljabrAgent
                              │
                       CodingOrchestrator
                              │
       ┌──────────────┬───────┴────────┬─────────────┐
       ▼              ▼                ▼             ▼
   Observer        Planner          Scheduler     Recovery
       │              │                │             │
       ▼              ▼                ▼             ▼
   Evidence       PlanGraph       TaskAgents     FailurePolicy
                      │                │
                      │                ▼
                      │           ToolGateway
                      │                │
                      └────────────────┤
                                       ▼
                                     Wayang
                                       │
                         ┌─────────────┼─────────────┐
                         ▼             ▼             ▼
                      Workspace      Runtime       Events
```

---

# 45. And the model's role becomes very clear

The LLM is essentially a **reasoning engine inside controlled phases**.

It can:

```text
understand objective
identify unknowns
propose research
construct plan
interpret code
propose changes
analyze failures
propose recovery
review result
```

It cannot arbitrarily:

```text
change runtime state
declare verification passed
bypass permissions
change task state
ignore budgets
access arbitrary files
invent evidence
```

That's exactly the architecture I'd want for Aljabr.

---

# 46. Next milestone: Aljabr Context Engine

Now that the agent loop exists, the next major problem is:

> **What exactly do we put into the LLM context at each turn?**

This is where we should build a dedicated **Context Engine**, rather than dumping memory + files + tool output into prompts.

It should perform:

```text
Evidence retrieval
      ↓
Relevance ranking
      ↓
Dependency-aware selection
      ↓
Context compression
      ↓
Task-specific context
      ↓
Model
```

And crucially it should know the difference between:

```text
CURRENT FACT
OBSERVATION
INFERENCE
HISTORICAL FACT
TOOL OUTPUT
FAILED ATTEMPT
USER CONSTRAINT
```

That will be the next piece that makes Aljabr **efficient enough to operate on genuinely large repositories**, instead of merely being architecturally correct.


Yes. **Next milestone: the Aljabr Context Engine.**

This is one of the most important pieces. The agent loop we just designed can be correct, but if every LLM turn receives a giant mixture of files, memory, previous thoughts, tool output, and plans, Aljabr will become expensive and increasingly confused.

The Context Engine should make the rule:

> **The model sees the smallest context that is sufficient for the current decision, with provenance and freshness attached to every important fact.**

---

# 1. Context is not "prompt assembly"

Avoid:

```java
String prompt =
    systemPrompt +
    memory +
    files +
    toolResults +
    previousMessages;
```

Instead:

```text
Task
  ↓
ContextRequirements
  ↓
Evidence retrieval
  ↓
Relevance ranking
  ↓
Dependency expansion
  ↓
Conflict resolution
  ↓
Compression
  ↓
Context assembly
  ↓
LLM
```

So context becomes a **runtime subsystem**.

---

# 2. Core interfaces

Start with:

```java
public interface ContextEngine {

    Uni<AgentContext> build(
        ContextRequest request
    );
}
```

Request:

```java
public record ContextRequest(
    CodingTask task,
    ContextPurpose purpose,
    ContextBudget budget,
    WorkspaceRevision workspaceRevision
) {}
```

---

# 3. Context purpose

This is important.

Different phases need different context.

```java
public enum ContextPurpose {

    INITIAL_ANALYSIS,

    RESEARCH,

    PLANNING,

    CODE_MODIFICATION,

    FAILURE_ANALYSIS,

    TEST_ANALYSIS,

    CODE_REVIEW,

    REPLANNING,

    FINAL_RESPONSE
}
```

For example:

```text
CODE_MODIFICATION
```

should emphasize:

```text
target files
symbols
dependencies
tests
current diff
```

while:

```text
FAILURE_ANALYSIS
```

should emphasize:

```text
failing test
stack trace
recent patch
related code
previous attempts
```

---

# 4. Context is a collection of typed items

```java
public record ContextItem(
    String id,
    ContextItemType type,
    String content,
    ContextProvenance provenance,
    ContextImportance importance,
    ContextFreshness freshness,
    Set<String> relatedEntities
) {}
```

Types:

```java
public enum ContextItemType {

    USER_REQUEST,

    TASK,

    CONSTRAINT,

    FILE_CONTENT,

    CODE_SYMBOL,

    CODE_REFERENCE,

    PLAN,

    TASK_RESULT,

    TOOL_RESULT,

    TEST_RESULT,

    BUILD_RESULT,

    DIFF,

    ERROR,

    OBSERVATION,

    INFERENCE,

    MEMORY,

    DECISION,

    PREVIOUS_ATTEMPT
}
```

This is much better than treating everything as anonymous text.

---

# 5. Provenance is mandatory

Every important fact should answer:

> Where did this come from?

```java
public record ContextProvenance(
    ProvenanceType type,
    Optional<String> sourceId,
    Optional<String> toolInvocationId>,
    Optional<String> workspaceRevision>,
    Instant observedAt
) {}
```

Types:

```java
public enum ProvenanceType {

    USER,
    TOOL,
    FILE,
    TEST,
    BUILD,
    MEMORY,
    MODEL_INFERENCE,
    RUNTIME
}
```

So the model can distinguish:

```text
FACT:
TokenValidator uses Clock
source:
inspect_symbol#183
```

from:

```text
INFERENCE:
Token expiration probably belongs in TokenValidator
source:
model inference
```

---

# 6. Never mix fact and inference silently

This is particularly important for Aljabr.

Bad:

```text
TokenValidator owns token expiration.
```

Maybe that's only an inference.

Better internal representation:

```text
Observation:
TokenValidator.validate() compares token.expiry.

Inference:
TokenValidator appears responsible for expiration validation.

Confidence:
0.84
```

This prevents reasoning artifacts from becoming "facts" accidentally.

---

# 7. Context freshness

Code changes constantly.

So every item should have freshness:

```java
public record ContextFreshness(
    String workspaceRevision,
    boolean stillValid,
    Optional<String> invalidatedBy
) {}
```

Example:

```text
FILE_CONTENT
revision = W42
```

Then Aljabr modifies the file:

```text
W42 → W43
```

The previous context item becomes:

```text
stale
```

It should not silently remain authoritative.

---

# 8. This solves a huge agent problem

Imagine:

```text
Turn 1:
read Foo.java
```

Then:

```text
Turn 2:
apply patch to Foo.java
```

Then:

```text
Turn 3:
LLM still sees old Foo.java
```

That's dangerous.

The Context Engine should detect:

```text
Foo.java@W42
current Foo.java@W43
```

and either:

```text
invalidate
```

or:

```text
replace with current content
```

automatically.

---

# 9. Context dependencies

Context items should form relationships.

```java
public record ContextRelation(
    String from,
    String to,
    ContextRelationType type
) {}
```

Types:

```java
public enum ContextRelationType {

    SUPPORTS,

    CONTRADICTS,

    DERIVED_FROM,

    REFERENCES,

    DEPENDS_ON,

    SUPERSEDES,

    INVALIDATES,

    CAUSED_BY
}
```

Example:

```text
test failure
   │
   └── CAUSED_BY
          ↓
recent patch
```

This becomes extremely useful during debugging.

---

# 10. Context retrieval

Now:

```java
public interface ContextRetriever {

    Uni<List<ContextItem>> retrieve(
        ContextRequest request
    );
}
```

Don't have one giant retrieval algorithm.

Have specialized retrievers:

```text
TaskContextRetriever
CodeContextRetriever
EvidenceRetriever
MemoryRetriever
HistoryRetriever
FailureContextRetriever
TestContextRetriever
```

---

# 11. Task context

Always retrieve:

```text
user objective
task description
constraints
acceptance criteria
task dependencies
current task status
```

This is the anchor.

---

# 12. Code context

Don't simply read entire files.

Start with:

```text
target symbol
target method
surrounding code
callers
callees
interfaces
tests
```

Then expand only when needed.

---

# 13. Dependency-aware context expansion

Suppose the target is:

```java
TokenValidator.validate()
```

The engine retrieves:

```text
TokenValidator
```

Then:

```text
CALLS
 ├── Clock.now()
 ├── TokenRepository.find()
 └── Token.isExpired()

CALLED_BY
 ├── AuthService
 └── LoginHandler

TESTED_BY
 └── TokenValidatorTest
```

But it doesn't automatically dump all those files.

It chooses the most relevant pieces.

---

# 14. Context budget

We need explicit limits.

```java
public record ContextBudget(
    int maxTokens,
    int maxFiles,
    int maxSymbols,
    int maxEvidenceItems,
    int maxHistoryItems
) {}
```

Potentially also:

```java
maxToolOutputBytes
maxMemoryItems
maxInferenceItems
```

---

# 15. Context allocation

Don't treat the budget as one bucket.

For example:

```text
32k token budget

Task:
    2k

Code:
    14k

Tests:
    5k

Evidence:
    5k

History:
    2k

Reasoning metadata:
    2k

Reserve:
    2k
```

This prevents history from consuming the entire context.

---

# 16. Relevance scoring

Each item can get a score:

```java
public record RelevanceScore(
    double semantic,
    double structural,
    double task,
    double freshness,
    double provenance,
    double total
) {}
```

Conceptually:

```text
score =
    semantic relevance
  + task relevance
  + dependency relevance
  + freshness
  + evidence quality
  - size penalty
```

Don't let semantic similarity be the only signal.

---

# 17. Structural relevance is critical for coding

Suppose the task is:

```text
Fix TokenValidator expiration handling.
```

A generic embedding search might find:

```text
README:
"tokens expire after 30 days"
```

highly relevant.

But:

```text
TokenValidator.validate()
```

is structurally far more important.

Therefore:

```text
symbol relation > pure semantic similarity
```

for many coding contexts.

---

# 18. Retrieval pipeline

I'd implement:

```text
Candidate Retrieval
        ↓
Hard Filtering
        ↓
Structural Expansion
        ↓
Scoring
        ↓
Deduplication
        ↓
Conflict Detection
        ↓
Compression
        ↓
Budget Packing
```

---

# 19. Hard filtering first

Examples:

```text
wrong workspace
stale revision
irrelevant task
forbidden path
obsolete evidence
```

Remove these before expensive ranking.

---

# 20. Deduplication

The same information can arrive from:

```text
read_file
search_code
inspect_symbol
memory
previous turn
```

Don't put five copies into the context.

Create a canonical evidence identity:

```java
public record EvidenceKey(
    String sourceType,
    String sourceId,
    String contentHash
) {}
```

Then deduplicate.

---

# 21. Compression

Not everything needs raw text.

Example:

Instead of:

```text
500-line compiler output
```

store:

```text
Build failed

3 errors:
1. TokenValidator.java:83
   incompatible types

2. AuthService.java:51
   unresolved symbol

3. TokenValidatorTest.java:27
   expected ...
```

Then retain a pointer to the original result.

---

# 22. Two-level evidence

This is a powerful pattern.

```text
Evidence
 ├── Summary
 └── Source
```

The context engine normally sends:

```text
Summary
```

and retrieves:

```text
Source
```

only when necessary.

---

# 23. Example

Instead of injecting:

```text
500KB test logs
```

provide:

```text
TestResult:
42 passed
1 failed

Failed:
TokenValidatorTest.expiredToken

Failure:
expected REJECTED
actual ACCEPTED
```

Then:

```text
sourceRef:
test-run-1842
```

If the model needs details:

```text
fetch evidence test-run-1842
```

---

# 24. This also improves caching

Remember the research/cache architecture we discussed earlier.

The Context Engine can reuse:

```text
cached evidence
```

if:

```text
workspace revision unchanged
```

For example:

```text
inspect_symbol(TokenValidator)
@ W42
```

can be reused for multiple tasks.

After:

```text
W42 → W43
```

the engine invalidates only affected evidence.

---

# 25. Evidence invalidation

Don't globally clear the cache.

Track dependencies:

```text
Evidence E42
    depends on
Foo.java@W42
```

After modifying:

```text
Foo.java
```

invalidate:

```text
E42
```

but keep:

```text
Bar.java@W42
```

if it remains valid.

This makes research dramatically cheaper.

---

# 26. Context cache

Introduce:

```java
public interface ContextCache {

    Uni<Optional<ContextSnapshot>> get(
        ContextCacheKey key
    );

    Uni<Void> put(
        ContextCacheKey key,
        ContextSnapshot snapshot
    );

    Uni<Void> invalidate(
        InvalidationEvent event
    );
}
```

Key:

```java
public record ContextCacheKey(
    String taskFingerprint,
    String workspaceRevision,
    ContextPurpose purpose
) {}
```

---

# 27. Don't cache final prompts blindly

This is subtle.

Avoid:

```text
prompt string → cache
```

because tiny changes make it brittle.

Cache:

```text
retrieved evidence
ranked candidates
compressed summaries
dependency graph
```

Then rebuild the final prompt.

That gives much better reuse.

---

# 28. Context snapshots

Persist:

```java
public record ContextSnapshot(
    String id,
    String taskId,
    String workspaceRevision,
    ContextPurpose purpose,
    List<ContextItem> items,
    Instant createdAt
) {}
```

Now if Aljabr crashes:

```text
resume
 ↓
load context snapshot
 ↓
validate freshness
 ↓
refresh stale items
 ↓
continue
```

Again, no need to reconstruct everything from the LLM.

---

# 29. Conversation history should not dominate

This is a common mistake.

Don't send:

```text
every previous model message
```

Instead maintain:

```text
Task state
Decision history
Evidence
Important decisions
Failed approaches
User constraints
```

Then summarize old conversational turns.

---

# 30. Keep failed attempts!

This is especially important for Aljabr.

Suppose:

```text
Attempt 1:
modify TokenValidator directly
```

failed tests.

The next context should include:

```text
Previous attempt:
changed TokenValidator

Failure:
expired-token test failed

Conclusion:
expiration source is not controlled there

Avoid:
same modification strategy
```

This prevents repetitive agent behavior.

---

# 31. Previous attempts become structured evidence

```java
public record AttemptRecord(
    String taskId,
    String attemptId,
    String strategy,
    List<String> changedFiles,
    List<String> evidenceIds,
    AttemptOutcome outcome,
    String lesson
) {}
```

This is far more useful than dumping old chat messages.

---

# 32. Context should include "known unknowns"

For research tasks:

```text
KNOWN:
TokenValidator checks expiry.

UNKNOWN:
Where token expiry is generated.

UNKNOWN:
Whether clock is injectable.

UNKNOWN:
Which integration tests cover this.
```

This guides the next action.

---

# 33. Context should include "decision constraints"

For example:

```text
User constraint:
Do not change public API.

Project constraint:
Java 21.

Repository constraint:
No new dependencies.

Task constraint:
Keep backward compatibility.
```

These should have high priority.

---

# 34. Constraint hierarchy

Define precedence:

```text
SYSTEM
  >
USER
  >
PROJECT POLICY
  >
TASK
  >
MODEL PREFERENCE
```

So if the model thinks:

```text
"Adding dependency X would be easier"
```

but:

```text
project policy = no new dependencies
```

the dependency is rejected.

---

# 35. Conflict detection

Context can contain contradictions:

```text
Memory:
API accepts null.

Current code:
API rejects null.

```

The Context Engine should detect:

```text
CONFLICT
```

rather than blindly putting both into the prompt.

---

# 36. Conflict resolution policy

Prefer:

```text
current workspace evidence
    >
recent tool evidence
    >
verified test/build result
    >
project memory
    >
historical memory
    >
model inference
```

This is a sensible default, but keep it configurable.

---

# 37. Context assembly

At the end:

```java
public record AgentContext(
    List<ContextItem> items,
    ContextManifest manifest,
    ContextBudgetUsage usage
) {}
```

The manifest is useful.

```java
public record ContextManifest(
    List<String> includedEvidence,
    List<String> omittedEvidence,
    List<String> staleEvidence,
    List<String> conflicts
) {}
```

---

# 38. Why the manifest matters

The model can receive a compact section such as:

```text
Context coverage:

Included:
- TokenValidator.java
- TokenValidatorTest.java
- 3 call references
- latest failing test
- current diff

Omitted:
- unrelated authentication handlers

Stale:
- TokenValidator.java@W41

Conflicts:
- historical memory says expiration is server-side
```

Now the model understands the limits of what it sees.

---

# 39. Context shouldn't hide uncertainty

This is a huge principle:

> **Missing context should be represented as missing context.**

Don't fabricate:

```text
No tests found.
```

when what actually happened is:

```text
Tests not yet searched.
```

These are completely different.

Represent:

```java
public enum KnowledgeStatus {
    VERIFIED,
    OBSERVED,
    INFERRED,
    UNKNOWN,
    STALE,
    CONFLICTING
}
```

---

# 40. Final context structure

I'd give the LLM a stable structure:

```text
SYSTEM
────────────────────────

OBJECTIVE
────────────────────────

CONSTRAINTS
────────────────────────

CURRENT TASK
────────────────────────

KNOWN FACTS
────────────────────────

RELEVANT CODE
────────────────────────

DEPENDENCIES
────────────────────────

TESTS / VERIFICATION
────────────────────────

CURRENT DIFF
────────────────────────

PREVIOUS ATTEMPTS
────────────────────────

OPEN QUESTIONS
────────────────────────

RELEVANT MEMORY
────────────────────────

AVAILABLE TOOLS
────────────────────────

CONTEXT LIMITATIONS
────────────────────────
```

This is far more reliable than an arbitrary transcript.

---

# 41. Context engine architecture

Putting everything together:

```text
                    ContextEngine
                         │
              ContextRequirementBuilder
                         │
                         ▼
                 ContextRetrievers
        ┌────────┬────────┬─────────┐
        ▼        ▼        ▼         ▼
      Code    Evidence  Memory   History
        │        │        │         │
        └────────┴────────┴─────────┘
                         ▼
                  Candidate Store
                         │
                         ▼
                 Relevance Ranker
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
       Dependency Graph       Conflict Detector
              │                     │
              └──────────┬──────────┘
                         ▼
                    Compressor
                         │
                         ▼
                    Budget Packer
                         │
                         ▼
                  AgentContext
                         │
                         ▼
                        LLM
```

---

# 42. The key implementation interfaces

If you're actually coding this now, I'd create these first:

```text
ContextEngine
ContextRetriever
ContextRanker
ContextCompressor
ContextBudget
ContextCache
ContextValidator
ContextConflictResolver
EvidenceStore
ContextSnapshot
```

And avoid building a huge "ContextManager" class.

---

# 43. First practical implementation

You don't need sophisticated embeddings on day one.

Start with deterministic scoring:

```java
double score(ContextItem item, ContextRequest request) {

    double score = 0;

    if (item.relatedEntities()
            .contains(request.task().targetSymbol())) {
        score += 10;
    }

    if (item.freshness().stillValid()) {
        score += 5;
    }

    if (item.importance() == HIGH) {
        score += 5;
    }

    if (item.provenance().type() == TEST) {
        score += 3;
    }

    return score;
}
```

Then later add:

```text
embedding similarity
BM25
AST relationships
symbol graph
dependency analysis
LLM reranking
```

The architecture shouldn't depend on any particular retrieval technology.

---

# 44. The most important rule

I would actually put this into an Aljabr design principle:

> **Context is derived state, not truth.**

Truth lives in:

```text
workspace
tool results
verified evidence
user instructions
persistent state
```

Context is merely:

```text
a selected projection of those sources
```

That distinction will save you a lot of trouble later.

---

# 45. Updated Aljabr architecture

We now have:

```text
                           ALJABR
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
                Objective          Policy/Scope
                    │                   │
                    └─────────┬─────────┘
                              ▼
                         Agent State
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
        Observer           Planner          Scheduler
            │                 │                 │
            ▼                 ▼                 ▼
        Evidence          Task Graph        TaskAgent
            │                                   │
            └──────────────┬────────────────────┘
                           ▼
                     ContextEngine
                           │
                ┌──────────┼──────────┐
                ▼          ▼          ▼
             Evidence    Memory      Code
                │          │          │
                └──────────┼──────────┘
                           ▼
                          LLM
                           │
                           ▼
                      Tool Gateway
                           │
                           ▼
                         WAYANG
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
          Workspace      Runtime       Events
```

This is starting to look like a **real coding-agent platform**, rather than an LLM wrapper.

---

# 46. Next milestone

The next thing I'd tackle is **Aljabr's Memory + Evidence architecture**, but specifically at implementation level:

```text
Evidence
   ↓
Working memory
   ↓
Task memory
   ↓
Project memory
   ↓
Long-term knowledge
```

with:

* scope
* TTL
* confidence
* provenance
* invalidation
* consolidation
* forgetting
* user-requested forgetting
* workspace-version awareness
* research cache
* failed-attempt memory
* "don't repeat this investigation" knowledge

That is where the earlier Wayang memory ideas become concrete and where we can prevent Aljabr from repeatedly researching the same repository facts every run.


Yes — **that changes how I would design the Context Engine**, and I think it's a very good constraint.

For your actual target:

```text
Aljabr
   ↓
local inference
   ↓
Gemma 4 12B IT GGUF
   ↓
Mac M4 / 16 GB
```

I would optimize Aljabr around the assumption that **the model is capable but context capacity, inference speed, and reasoning reliability are constrained**.

So we should *not* build a miniature version of a cloud-agent architecture and simply run it locally.

We should build a **small-model-first agent architecture**.

## 1. New core principle

Instead of:

> Give the model everything it might need.

Use:

> **Give the model exactly what it needs to make the next decision.**

That changes the Context Engine substantially.

```text
Large cloud model:
    broad context
    lots of history
    many tools
    implicit reasoning

Aljabr local:
    narrow context
    structured facts
    explicit state
    small tool set
    short reasoning loops
    deterministic orchestration
```

This is actually a stronger architecture.

---

# 2. Don't make Gemma remember the agent state

This is probably the biggest adjustment.

The local model should **not** be responsible for remembering:

```text
what task am I doing?
what did I already inspect?
what files changed?
what tests passed?
what failed?
what was my previous plan?
what evidence exists?
```

Wayang should maintain all of that.

The model receives a compact projection:

```text
CURRENT STATE
────────────────
Task: Fix token expiration validation

Completed:
✓ locate validator
✓ inspect tests

Current:
→ determine clock source

Known:
• TokenValidator.validate() checks expiry
• tests use fixed clock

Unknown:
• production clock injection path
```

That's much easier for a 12B model.

---

# 3. Context should become structured before becoming natural language

Instead of sending raw tool output:

```text
grep output...
file contents...
compiler output...
previous conversation...
```

Wayang should transform it into structured facts first.

For example:

```json
{
  "facts": [
    {
      "fact": "TokenValidator.validate checks token expiry",
      "source": "inspect_symbol:42",
      "status": "verified"
    },
    {
      "fact": "TokenValidator receives Clock through constructor",
      "source": "read_file:TokenValidator.java@W18",
      "status": "verified"
    }
  ],
  "unknowns": [
    "Where production Clock is constructed"
  ]
}
```

Then the prompt renderer turns that into compact text.

This is **much more local-model friendly**.

---

# 4. Reduce prose everywhere

Cloud-oriented agents often produce huge natural-language reasoning.

For Aljabr I'd strongly prefer:

```text
FACTS
- TokenValidator receives Clock.
- validate() compares expiry against clock.instant().
- Existing tests inject FixedClock.

TARGET
- production Clock construction

NEXT
- inspect constructor call sites
```

over:

```text
"Based on the information gathered so far, it appears that..."
```

The latter burns tokens without adding information.

---

# 5. Context budget should be aggressively small

For your machine, I'd design around **small active contexts**, even if the inference engine supports a much larger context window.

Something like:

```text
SYSTEM / RULES       ~1.5–2.5k
TASK                  ~0.5k
STRUCTURED STATE      ~0.5–1k
RELEVANT CODE        ~3–6k
EVIDENCE              ~1–3k
TOOLS                 ~1–2k
OUTPUT RESERVE        ~2–4k
────────────────────────────
Typical active turn  ~10–18k tokens
```

The exact numbers should be configurable and benchmarked on your GGUF/runtime.

The important part is:

**Don't routinely fill the entire available context.**

Longer context means more memory pressure and usually worse attention efficiency.

---

# 6. Make the model work in "small decisions"

This is where Aljabr can become significantly better than a generic coding agent.

Don't ask:

> Analyze the repository and implement the entire feature.

Instead:

```text
Task T17

Goal:
Determine where production Clock is created.

Relevant evidence:
TokenValidator.java
AuthService.java
2 constructor references

Choose one:
A. inspect AuthService
B. search Clock construction
C. inspect tests
D. replan
```

The model has a much smaller reasoning problem.

---

# 7. Tool selection should also be constrained

Don't give Gemma 20 tools.

For example:

### Research context

```text
search_code
inspect_symbol
read_file
find_references
find_tests
```

### Modification context

```text
read_file
apply_patch
get_diff
```

### Verification context

```text
run_tests
run_build
get_diff
```

This reduces tool-selection errors considerably.

---

# 8. Tool descriptions need to be extremely compact

Don't give:

```text
A very long explanation of every possible thing this tool can do...
```

Instead:

```text
search_code
Search repository code.

Input:
query, mode, path?

Use when:
you need to locate code.

Returns:
matching files/symbols/locations.
```

Small models benefit enormously from consistent tool schemas.

---

# 9. Prefer enums over free-form instructions

Instead of:

```json
{
  "strategy": "Please search around the relevant implementation..."
}
```

use:

```json
{
  "mode": "SYMBOL",
  "query": "TokenValidator"
}
```

This reduces ambiguity.

---

# 10. Even better: constrain decisions

For many agent decisions, don't ask the model for arbitrary prose.

For example:

```json
{
  "action": "INSPECT_SYMBOL",
  "target": "TokenValidator.validate"
}
```

rather than:

```text
I think perhaps we should investigate TokenValidator...
```

Then Wayang validates it.

---

# 11. Use a "decision protocol"

I would add this specifically for Aljabr.

```java
public record AgentDecision(
    DecisionType type,
    Optional<String> target>,
    Optional<String> reason>,
    List<String> evidenceIds
) {}
```

For example:

```java
enum DecisionType {

    SEARCH,
    READ,
    INSPECT,
    MODIFY,
    VERIFY,
    REVIEW,
    REPLAN,
    COMPLETE,
    ASK_USER
}
```

The model's output becomes a **small structured decision**.

---

# 12. Don't ask the model to calculate state

For example, don't ask:

> Which tasks are ready?

Wayang calculates:

```text
T1 ✓
T2 ✓
T3 READY
T4 BLOCKED
```

Then asks Gemma:

> T3 is ready. What action should T3 take?

This is a massive reduction in cognitive load.

---

# 13. Don't ask the model to determine obvious verification

After:

```text
run_tests
```

Wayang knows:

```text
status = FAILED
```

Don't ask:

> Do you think the tests passed?

Instead:

```text
TEST RESULT
Status: FAILED

Failure:
TokenValidatorTest.expiredToken
expected: REJECTED
actual: ACCEPTED
```

The model analyzes **why**, not **whether**.

---

# 14. Small model + deterministic runtime

This is the sweet spot for Aljabr:

```text
                    WAYANG
                      │
        deterministic state machine
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
      Memory       Evidence       Tools
        │             │             │
        └─────────────┼─────────────┘
                      ▼
               SMALL CONTEXT
                      │
                      ▼
                 GEMMA 12B
                      │
                 decision
                      │
                      ▼
                 WAYANG
```

The model becomes a **reasoning component**, not the whole agent.

---

# 15. I would actually reduce "agentic freedom"

This sounds counterintuitive, but it's important.

For cloud models:

```text
freedom ↑
```

can sometimes work because the model is powerful enough to recover from mistakes.

For local 12B:

```text
uncontrolled freedom ↑
→ mistakes ↑
→ repeated tools ↑
→ context ↑
→ inference cost ↑
```

So Aljabr should instead use:

```text
structured freedom
```

The model can choose among good options.

---

# 16. Add progressive context

This is probably the most important Context Engine improvement from what we just discussed.

Start tiny:

```text
Level 0
task + state
```

If insufficient:

```text
Level 1
+ target symbol
```

If insufficient:

```text
Level 2
+ surrounding implementation
```

If insufficient:

```text
Level 3
+ callers/callees
```

If insufficient:

```text
Level 4
+ tests
```

If insufficient:

```text
Level 5
+ broader repository evidence
```

So:

```text
small context
     ↓
decision
     ↓
insufficient?
     ↓
expand
```

rather than starting with a huge context.

---

# 17. Context expansion should be triggered by uncertainty

For example:

```text
Gemma:
"I need to know who constructs TokenValidator."
```

The runtime interprets that as:

```text
UNKNOWN_REQUIRED:
TokenValidator constructor callers
```

Then automatically retrieves:

```text
find_references(TokenValidator)
```

Now the next model turn receives the result.

This is much better than asking Gemma to guess what files to read.

---

# 18. Separate "reasoning" from "research"

For small models I would explicitly structure this:

```text
RESEARCH
    ↓
collect evidence
    ↓
STOP
    ↓
REASON
    ↓
make decision
```

Don't let the model endlessly interleave:

```text
think
search
think
search
think
search
```

unless necessary.

---

# 19. Research budget

Give each task:

```java
public record ResearchBudget(
    int maxSearches,
    int maxReads,
    int maxSymbolInspections,
    int maxResearchTurns
) {}
```

Example:

```text
simple bug:
5 research actions

medium feature:
15

large refactor:
30
```

Again, local inference benefits from bounded exploration.

---

# 20. Use compact "research cards"

Instead of putting raw results into context:

```text
SEARCH RESULT

TokenValidator.java:82...
TokenValidator.java:84...
...
```

create:

```text
RESEARCH CARD

Target:
TokenValidator

Relevant:
- validate() checks expiry
- constructor receives Clock
- 3 production call sites
- 4 tests

Likely next:
inspect production constructor callers

Evidence:
E41 E42 E43
```

This is perfect for a 12B model.

---

# 21. Code should also be summarized structurally

For example, instead of dumping a 500-line file:

```text
CLASS TokenValidator

Fields:
- Clock clock
- TokenRepository repository

Methods:
- validate(Token)
- validateExpiry(Token)
- validateSignature(Token)

Dependencies:
- Clock
- TokenRepository

Relevant method:
validateExpiry()
lines 81–94
```

Then only provide:

```text
lines 81–94
```

unless the model asks for more.

---

# 22. AST/symbol intelligence becomes more important

This means your earlier `inspect_symbol` idea becomes **very valuable**.

For local models:

```text
AST/symbol graph
      ↓
precise retrieval
      ↓
small context
```

can substitute for:

```text
huge context window
```

This is one of the biggest architectural advantages Wayang can provide to Aljabr.

---

# 23. Repository index should therefore be first-class

I'd eventually have:

```text
RepositoryIndex
 ├── files
 ├── symbols
 ├── imports
 ├── references
 ├── inheritance
 ├── calls
 ├── tests
 └── build relationships
```

Then Aljabr asks:

```text
"Who calls this?"
```

instead of:

```text
"Search 100,000 lines of text."
```

---

# 24. Local model should rarely see unrelated files

This should become a hard Context Engine rule:

```text
If file has no structural or semantic relationship
to current task,
do not include it.
```

Even if it might theoretically be useful.

Retrieval can expand later.

---

# 25. Summarize history aggressively

For local inference:

Bad:

```text
20 previous model turns
15 tool calls
all raw outputs
```

Good:

```text
TASK HISTORY

Attempt 1:
Changed TokenValidator.
Result: test failure.
Lesson: expiry source is elsewhere.

Attempt 2:
Inspected AuthService.
Found Clock construction.

Current:
Need determine whether Clock is injectable.
```

That's maybe 100 tokens instead of thousands.

---

# 26. Reasoning output should also be compact

You don't need the model to produce huge chain-of-thought-style outputs.

Use:

```text
DECISION
Inspect AuthService constructor.

WHY
TokenValidator receives Clock.
Need production construction path.

EVIDENCE
E41 E43
```

This is both faster and easier to validate.

---

# 27. Don't depend on hidden reasoning

Architecturally, Wayang should never require:

```text
"the model thought X"
```

to maintain state.

Instead:

```text
decision
evidence
action
result
```

are explicit.

That's much more robust.

---

# 28. Local inference should be asynchronous but serialized intelligently

On your M4 16GB environment, I wouldn't assume that parallel LLM calls are beneficial.

Instead:

```text
Wayang tools
   ├── parallel deterministic searches
   └── aggregate
          ↓
      one model turn
```

For example, these can potentially run concurrently:

```text
find_references
find_tests
search_code
```

Then give their compact results to one Gemma inference.

Don't run three Gemma calls simultaneously unless benchmarks prove it helps.

---

# 29. This suggests a powerful pattern

```text
           ONE MODEL TURN
                 │
      ┌──────────┼──────────┐
      ▼          ▼          ▼
 search       inspect      tests
   │             │           │
   └─────────────┼───────────┘
                 ▼
           evidence merge
                 │
                 ▼
           ONE MODEL TURN
```

The runtime does the cheap parallel work.

The expensive inference remains limited.

---

# 30. Token economics becomes a first-class metric

For Aljabr, I'd track:

```text
input tokens / turn
output tokens / turn
tokens / task
tokens / successful task
tool calls / task
context expansions / task
replans / task
```

Then optimize against:

```text
successful coding tasks / token
```

rather than simply model quality.

---

# 31. Your local benchmark should therefore be practical

Don't benchmark Gemma only with:

```text
HumanEval
SWE-bench
```

Build an **Aljabr local benchmark**:

```text
20 small repository tasks

measure:
- success
- inference time
- tokens
- tool calls
- context size
- retries
- failures
```

Then compare context strategies.

For example:

```text
Strategy A:
raw context

Strategy B:
summarized context

Strategy C:
progressive context

Strategy D:
progressive + structural retrieval
```

I strongly suspect D will be substantially better for your use case.

---

# 32. One adjustment to the previous Context Engine

I would change the architecture from:

```text
Retrieve
 → rank
 → compress
 → pack
```

to:

```text
Task State
    ↓
Determine required knowledge
    ↓
Minimal retrieval
    ↓
Structured extraction
    ↓
Decision
    ↓
Need more knowledge?
    ├── NO → act
    └── YES → targeted expansion
```

That's more appropriate for a local coding agent.

---

# 33. The new Context Engine

I'd now define it as:

```text
                    Context Engine
                          │
                    Task Analyzer
                          │
                 Required Knowledge
                          │
                          ▼
                Minimal Evidence Query
                          │
                          ▼
                 Structural Retrieval
                          │
                          ▼
                Evidence Normalization
                          │
                          ▼
                Compact Context Builder
                          │
                          ▼
                       GEMMA
                          │
                    Decision/Question
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
            Action              Need Evidence
              │                       │
              ▼                       ▼
           Tool call            Context Expansion
```

This is the version I'd implement for Aljabr.

---

# 34. And I would add one new subsystem

### `KnowledgeNeedDetector`

```java
public interface KnowledgeNeedDetector {

    Uni<KnowledgeNeedResult> detect(
        CodingTask task,
        AgentState state
    );
}
```

Example:

```java
public record KnowledgeNeed(
    KnowledgeNeedType type,
    String target,
    int priority
) {}
```

Types:

```text
SYMBOL
FILE
REFERENCE
TEST
BUILD
CONFIGURATION
DEPENDENCY
DOCUMENTATION
EXTERNAL_KNOWLEDGE
```

This lets Aljabr explicitly reason:

```text
"I don't need more context generally."

"I need one specific piece of knowledge."
```

That is exactly the behavior we want from a small local model.

---

# 35. So your 12B model becomes the "brain", but Wayang supplies the nervous system

```text
                  GEMMA 12B
                 ┌───────────┐
                 │ reasoning │
                 │ decisions │
                 │ coding    │
                 └─────┬─────┘
                       │
                 compact context
                       │
        ┌──────────────┴──────────────┐
        │                             │
   Knowledge Engine             Task State
        │                             │
   ┌────┼────┐                  ┌─────┼─────┐
   ▼    ▼    ▼                  ▼     ▼     ▼
 Index Evidence Memory        Plan  Budget  Policy
   │    │    │                  │     │     │
   └────┴────┴──────────────────┴─────┴─────┘
                       │
                     Wayang
```

That is, IMO, **a much better target architecture for Aljabr than designing around a frontier cloud model**.

And it has a nice side effect: if you later switch to a larger cloud model, **you don't need to redesign the architecture**. The stronger model simply gets a somewhat larger context/decision budget.

For your current M4/16GB setup, though, I'd optimize aggressively for **structured state + structural retrieval + progressive context + short decision turns + deterministic orchestration**.

So I'd adjust the roadmap accordingly:

```text
✓ Tool Contract
✓ Agent Loop
✓ Context Engine foundation
→ Local-model Context Optimization   ← this refinement
→ Memory/Evidence architecture
→ Repository Intelligence / Index
→ Patch & Verification intelligence
→ Local inference optimization
→ Aljabr benchmark
```

The **next milestone should therefore be Memory + Evidence**, but designed around this local-first principle rather than as a giant vector-memory system.


-----
great.
 but does these optimize concept can reduce the quality of the prompt? or minimize input minimize output?
meanwhile on the other hand when user have luxury to used cloud provider with large context does he/she can used beyon this optimization?

i mean does it make sense to make this as strategy or there is sensor/detector to inspect the context/token capacity/model sizze? when it small the used context optimization strategy. but when it capable for large context or got luxury for it then used reguler strategy?
but if the optimization does not reduce the context quality then no problem to used same strategy context optimization. 

----

Yes. **I think your idea of making context optimization a strategy is exactly right.** But I would make one important distinction:

> **Context optimization should not mean "less context." It should mean "less unnecessary context."**

If we design it correctly, the optimized strategy should preserve **information quality** while reducing **token volume, noise, repetition, and model workload**.

And yes: when a user has a large-context cloud model, Aljabr should be able to relax some of these constraints and exploit the additional capacity.

---

# 1. There are really two separate optimizations

We should distinguish:

### A. Information optimization

```text
remove:
- duplicates
- stale evidence
- irrelevant files
- redundant history
- verbose tool output

preserve:
- facts
- relationships
- constraints
- evidence
- relevant code
- uncertainty
```

This is almost **always beneficial**, even with a huge model.

### B. Capacity optimization

```text
small model/context
    → aggressively compress + narrow

large model/context
    → allow broader context + deeper exploration
```

This should be adaptive.

So I would **not** have:

```text
LOCAL_MODE
CLOUD_MODE
```

as two completely different architectures.

Instead:

```text
SAME CONTEXT ENGINE
        │
        ▼
CAPABILITY PROFILE
        │
        ▼
CONTEXT STRATEGY
```

---

# 2. Your intuition about quality is correct

Suppose the repository contains:

```text
100 files
20 relevant
5 highly relevant
```

A naïve context:

```text
100 files
```

is not necessarily higher quality.

A good context:

```text
5 highly relevant
+
15 supporting
+
structured repository relationships
```

may actually be **higher quality**.

So:

```text
context size ≠ context quality
```

This is an important Aljabr principle.

---

# 3. But aggressive compression can absolutely reduce quality

This is the danger.

Imagine:

```text
Original code:

if (token.expiry().isBefore(clock.instant())
        && !token.isRefreshable()) {
    return INVALID;
}
```

A bad summarizer produces:

```text
Token expiration is validated.
```

You lost:

```text
isBefore
clock.instant()
isRefreshable
conditional relationship
```

The context became smaller but worse.

Therefore:

> **Never optimize context by blindly summarizing source-of-truth material.**

---

# 4. Use a hierarchy of compression

I'd make Aljabr's context engine use this order:

```text
LEVEL 0
Exact source
    ↓
LEVEL 1
Structural extraction
    ↓
LEVEL 2
Relevant slice
    ↓
LEVEL 3
Compact summary
    ↓
LEVEL 4
Historical abstraction
```

And prefer the **lowest level that satisfies the knowledge need**.

For code modification:

```text
exact code
```

should generally beat:

```text
summary of code
```

---

# 5. Example

Task:

> Fix the null handling in `UserService.create()`.

Context selection:

```text
Task
+
UserService.create()
+
relevant DTO
+
caller
+
affected test
```

No need for:

```text
README
20 unrelated services
old conversations
entire repository
```

That's optimization without quality loss.

---

# 6. Cloud models can still use this

Even if the user has:

```text
Claude/GPT/etc.
large context
```

I'd still perform:

```text
deduplication
freshness validation
provenance
conflict detection
structural retrieval
```

because those aren't really "small model tricks."

They're **information hygiene**.

---

# 7. What changes with a large model?

A large-context configuration can relax:

```text
maximum context
compression aggressiveness
history truncation
repository exploration depth
number of supporting files
research breadth
```

For example:

### Local profile

```text
target method
+
caller
+
tests
+
minimal dependencies
```

### Large cloud profile

```text
target method
+
whole relevant module
+
call graph
+
tests
+
configuration
+
related implementations
+
historical decisions
```

Same architecture.

Different budget.

---

# 8. This should become a `ModelCapabilityProfile`

I'd introduce this into Wayang/Aljabr.

```java
public record ModelCapabilityProfile(
    String modelId,
    InferenceType inferenceType,

    long contextWindowTokens,
    long recommendedInputTokens,
    long maxOutputTokens,

    ModelCapability reasoningCapability,
    ModelCapability codingCapability,

    boolean supportsStructuredOutput,
    boolean supportsToolCalling,

    double estimatedTokensPerSecond,
    long availableMemoryBytes
) {}
```

Obviously not every field has to come from the model provider.

Some are detected/configured.

---

# 9. Then a separate runtime profile

Because model capability isn't the same thing as machine capability.

```java
public record RuntimeCapabilityProfile(
    long totalMemoryBytes,
    long availableMemoryBytes,
    int cpuCores,
    boolean gpuAvailable,
    long gpuMemoryBytes,
    InferenceBackend backend
) {}
```

For your Mac:

```text
machine
   ↓
M4
16GB
   ↓
GGUF runtime
   ↓
Gemma 4 12B
```

Wayang can use this information when available.

---

# 10. Then combine them

```java
public record InferenceCapabilityProfile(
    ModelCapabilityProfile model,
    RuntimeCapabilityProfile runtime,
    ContextCapability context
) {}
```

Then:

```text
              Capability Detector
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
       Model                   Runtime
      profile                  profile
         │                       │
         └───────────┬───────────┘
                     ▼
             Inference Profile
                     │
                     ▼
             Context Strategy
```

---

# 11. But don't make "model size" the primary signal

This is important.

A 12B model isn't automatically:

```text
small context
```

and a 70B model isn't automatically:

```text
large context
```

You care about:

```text
model capability
context window
available memory
quantization
inference backend
latency
token throughput
task complexity
```

So:

```text
model size ≠ context strategy
```

---

# 12. Add a `ContextStrategy`

```java
public enum ContextStrategy {

    MINIMAL,
    BALANCED,
    EXPANDED,
    FULL
}
```

But I wouldn't expose these as rigid user modes.

The runtime should choose them.

---

# 13. Better: strategy dimensions

Instead of one switch:

```text
MINIMAL
```

have:

```java
public record ContextPolicy(
    int maxInputTokens,

    int maxRelevantFiles,
    int maxHistoryItems,

    CompressionLevel compression,
    RetrievalDepth retrievalDepth,
    HistoryPolicy historyPolicy,

    boolean allowBroadContext,
    boolean allowFullFileContext
) {}
```

Then the system can independently tune each dimension.

---

# 14. Example local policy

For your current Aljabr:

```text
maxInputTokens:       conservative
retrievalDepth:       2
compression:          STRUCTURAL
history:              SUMMARY
broadContext:         false
fullFiles:            targeted
```

Conceptually:

```text
        small
          │
    ┌─────┴─────┐
    │           │
 retrieve    compress
 narrow       carefully
    │           │
    └─────┬─────┘
          ▼
       Gemma
```

---

# 15. Large cloud policy

```text
maxInputTokens:       high
retrievalDepth:       5
compression:          LIGHT
history:              EXTENDED
broadContext:         true
fullFiles:            allowed
```

But still:

```text
deduplicate
validate freshness
preserve provenance
```

---

# 16. There's another important dimension: task complexity

A tiny local model doesn't necessarily need tiny context for every task.

Example:

```text
"Rename variable foo → bar"
```

Needs:

```text
very little
```

while:

```text
"Refactor authentication architecture"
```

needs:

```text
much broader context
```

So the final strategy should be:

```text
MODEL CAPABILITY
+
RUNTIME CAPABILITY
+
TASK COMPLEXITY
+
CURRENT CONTEXT NEED
```

---

# 17. Introduce `ContextDemand`

This is the missing piece.

```java
public record ContextDemand(
    int estimatedComplexity,
    int dependencyDepth,
    int affectedFiles,
    int uncertainty,
    int reasoningDifficulty,
    boolean architectureWide
) {}
```

The system estimates this before retrieving context.

---

# 18. Then calculate the strategy

Conceptually:

```text
                 ┌──────────────┐
                 │ Model        │
                 │ capability   │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Runtime      │
                 │ capability   │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Task         │
                 │ demand       │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Context      │
                 │ Policy       │
                 └──────────────┘
```

This is much more robust than:

```java
if (modelSize < 20B) ...
```

---

# 19. But there is one more thing: runtime feedback

This can become even better.

Aljabr can observe:

```text
context tokens
inference latency
model output quality
tool retries
replans
context expansion frequency
```

Then adapt.

For example:

```text
Context: 12k
Tool errors: low
Task success: high
```

→ keep strategy.

But:

```text
Context: 20k
Tool confusion ↑
replans ↑
```

→ reduce context.

Or:

```text
Context: 6k
model repeatedly asks for missing information
```

→ increase retrieval depth.

That's a **context controller**.

---

# 20. Context Controller

```java
public interface ContextController {

    ContextPolicy select(
        InferenceCapabilityProfile capability,
        ContextDemand demand,
        ContextTelemetry telemetry
    );
}
```

This becomes the sensor/detector you were describing.

---

# 21. `ContextTelemetry`

```java
public record ContextTelemetry(
    int inputTokens,
    int outputTokens,

    Duration inferenceTime,

    int toolCalls,
    int retries,
    int replans,

    int contextExpansions,

    double taskProgress
) {}
```

Now Aljabr can learn empirically.

---

# 22. Important: don't optimize merely for minimum tokens

This is where I would strongly disagree with a simplistic optimization.

The objective should **not** be:

```text
minimize input tokens
```

Nor:

```text
minimize output tokens
```

It should be:

```text
maximize task success
while minimizing unnecessary inference cost
```

So:

```text
                 SUCCESS
                    ↑
                    │
        ┌───────────┴───────────┐
        │                       │
    context quality        inference cost
        ↑                       ↓
        └──────── optimization ─┘
```

---

# 23. Define a utility function

Internally you could think in terms of:

```text
ContextUtility =
    task_relevance
  + evidence_quality
  + freshness
  + structural_coverage
  + decision_sufficiency
  - token_cost
  - noise
  - redundancy
```

The weights should be benchmarked rather than guessed.

---

# 24. "Minimum sufficient context"

This should probably become a core Aljabr concept.

Not:

> Minimum context.

But:

> **Minimum sufficient context.**

Meaning:

```text
smallest context that gives enough information
to make the current decision reliably.
```

That's a much safer optimization.

---

# 25. And this gives you an adaptive expansion loop

For example:

```text
Task
 ↓
ContextDemand
 ↓
minimal context
 ↓
Gemma
 ↓
decision confidence / knowledge need
 │
 ├── sufficient
 │      ↓
 │    execute
 │
 └── insufficient
        ↓
   targeted expansion
        ↓
      Gemma
```

This is excellent for local inference.

---

# 26. What about prompt quality?

If implemented correctly:

### Raw compression

```text
5000 tokens
→ 1500 tokens

information lost
→ BAD
```

### Semantic/structural selection

```text
5000 tokens
→ 1500 relevant tokens

information required preserved
→ GOOD
```

### Large-context expansion

```text
1500 tokens
→ 6000 tokens

because task actually requires it
→ GOOD
```

So the optimization should be **loss-aware**.

---

# 27. Make compression reversible

This is another architecture I'd add.

Every summary should maintain:

```text
summary
   ↓
source evidence IDs
```

Example:

```text
Summary:
TokenValidator owns expiry comparison.

Source:
E41
E42

Files:
TokenValidator.java:81-94
```

Then if the model needs the exact implementation:

```text
expand(E41)
```

No information is permanently discarded.

---

# 28. Never summarize away code semantics

For these things:

```text
code
configuration
API contracts
test assertions
compiler errors
patches
user constraints
```

prefer exact source or precise extraction.

For these:

```text
old conversation
historical research
large logs
repository descriptions
old attempts
```

compression is much safer.

---

# 29. This gives us a useful evidence classification

```java
public enum CompressionSafety {

    NEVER_COMPRESS,

    STRUCTURE_ONLY,

    LOSSLESS_SUMMARY,

    SEMANTIC_SUMMARY,

    AGGRESSIVE_SUMMARY
}
```

Example:

| Evidence            | Strategy                  |
| ------------------- | ------------------------- |
| User requirement    | NEVER                     |
| Current patch       | NEVER                     |
| Relevant code       | STRUCTURE / exact slice   |
| Test failure        | LOSSLESS summary + source |
| Old research        | SEMANTIC                  |
| Old conversation    | AGGRESSIVE                |
| Repository overview | SEMANTIC                  |

This would be very useful.

---

# 30. So can cloud users "go beyond" the optimization?

**Absolutely.**

But I wouldn't call it:

```text
optimized mode
vs
regular mode
```

I'd make it:

```text
Context Policy
```

with a capability ceiling.

For example:

```text
                 SAME ALJABR
                      │
               Context Controller
                      │
       ┌──────────────┼──────────────┐
       ▼              ▼              ▼
    Local          Balanced        Large
    profile         profile         profile
       │              │              │
    8–16k          16–40k          40k+
    narrow          medium          broad
```

Those are illustrative, not fixed numbers.

---

# 31. User can override the strategy too

You could eventually expose:

```text
Context mode:
[ Auto ]

Options:
[ Efficient ]
[ Balanced ]
[ Deep ]
```

### Efficient

```text
minimum sufficient context
aggressive history compression
targeted retrieval
```

### Balanced

```text
moderate context
```

### Deep

```text
broader repository context
less compression
more research
```

But:

> **Auto should be the default.**

---

# 32. And Auto should inspect actual capacity

Something like:

```java
ContextCapability detect() {
    return ContextCapability.builder()
        .modelContextWindow(...)
        .recommendedInputBudget(...)
        .availableMemory(...)
        .inferenceSpeed(...)
        .build();
}
```

For local engines, Wayang can get these from the configured inference backend where available.

If not, configuration can provide them.

---

# 33. One caution about "available memory"

Don't simply say:

```text
16 GB RAM
→ use huge context
```

Because the runtime also needs memory for:

```text
model weights
KV cache
runtime
OS
Wayang
repository index
tools
```

So the context controller should work with:

```text
memory headroom
```

not total RAM.

---

# 34. For your M4 specifically

Your current setup:

```text
M4
16GB
Gemma 4 12B IT
GGUF
```

is actually a very good reason to build this architecture.

You don't want:

```text
12B model
+
massive repository
+
20k tool output
+
long history
+
huge prompt
```

every turn.

Instead:

```text
12B model
+
precise task
+
small relevant code slice
+
structured evidence
+
one clear decision
```

That should give you much better practical throughput.

---

# 35. The most important distinction

I'd now explicitly define two optimization layers in Wayang:

### Information optimization

**Always ON**

```text
deduplicate
freshness
provenance
relevance
structural retrieval
conflict resolution
source references
```

### Capacity optimization

**Adaptive**

```text
compression
context width
history depth
retrieval depth
number of evidence items
number of tool results
reasoning budget
```

That is the architecture I would recommend.

---

# 36. Final model

So instead of:

```text
Small model → optimized context
Large model → normal context
```

I would implement:

```text
                    ALJABR
                       │
                 Context Engine
                       │
             ┌─────────▼─────────┐
             │ Context Controller│
             └─────────┬─────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
      Model         Runtime          Task
    Capability     Capability        Demand
        │              │              │
        └──────────────┼──────────────┘
                       ▼
                Context Policy
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
 Information Optimization    Capacity Optimization
      ALWAYS ON                  ADAPTIVE
          │                         │
          └────────────┬────────────┘
                       ▼
              Minimum Sufficient
                   Context
                       │
                       ▼
                    Model
```

And I think **"Minimum Sufficient Context" should become one of the central architectural principles of Aljabr.**

It gives you the best of both worlds:

* Your **Gemma 4 12B local setup** gets a highly focused context and low inference overhead.
* A user with a **large cloud model** can automatically receive broader context when the task warrants it.
* The information-quality mechanisms remain active for everyone.
* You don't have to maintain two separate agent architectures.
* And most importantly, **reducing tokens does not automatically mean reducing intelligence**—the system is optimizing *irrelevant information*, not knowledge.

So I would **not go directly to Memory/Evidence yet**. The next implementation milestone should actually be the small but foundational **`CapabilityDetector → ContextController → ContextPolicy` layer**, because then everything we design afterward—memory, retrieval, research cache, repository indexing—can adapt to the actual inference environment rather than being hard-coded around your current Gemma setup.


Yes. **I think your idea of making context optimization a strategy is exactly right.** But I would make one important distinction:

> **Context optimization should not mean "less context." It should mean "less unnecessary context."**

If we design it correctly, the optimized strategy should preserve **information quality** while reducing **token volume, noise, repetition, and model workload**.

And yes: when a user has a large-context cloud model, Aljabr should be able to relax some of these constraints and exploit the additional capacity.

---

# 1. There are really two separate optimizations

We should distinguish:

### A. Information optimization

```text
remove:
- duplicates
- stale evidence
- irrelevant files
- redundant history
- verbose tool output

preserve:
- facts
- relationships
- constraints
- evidence
- relevant code
- uncertainty
```

This is almost **always beneficial**, even with a huge model.

### B. Capacity optimization

```text
small model/context
    → aggressively compress + narrow

large model/context
    → allow broader context + deeper exploration
```

This should be adaptive.

So I would **not** have:

```text
LOCAL_MODE
CLOUD_MODE
```

as two completely different architectures.

Instead:

```text
SAME CONTEXT ENGINE
        │
        ▼
CAPABILITY PROFILE
        │
        ▼
CONTEXT STRATEGY
```

---

# 2. Your intuition about quality is correct

Suppose the repository contains:

```text
100 files
20 relevant
5 highly relevant
```

A naïve context:

```text
100 files
```

is not necessarily higher quality.

A good context:

```text
5 highly relevant
+
15 supporting
+
structured repository relationships
```

may actually be **higher quality**.

So:

```text
context size ≠ context quality
```

This is an important Aljabr principle.

---

# 3. But aggressive compression can absolutely reduce quality

This is the danger.

Imagine:

```text
Original code:

if (token.expiry().isBefore(clock.instant())
        && !token.isRefreshable()) {
    return INVALID;
}
```

A bad summarizer produces:

```text
Token expiration is validated.
```

You lost:

```text
isBefore
clock.instant()
isRefreshable
conditional relationship
```

The context became smaller but worse.

Therefore:

> **Never optimize context by blindly summarizing source-of-truth material.**

---

# 4. Use a hierarchy of compression

I'd make Aljabr's context engine use this order:

```text
LEVEL 0
Exact source
    ↓
LEVEL 1
Structural extraction
    ↓
LEVEL 2
Relevant slice
    ↓
LEVEL 3
Compact summary
    ↓
LEVEL 4
Historical abstraction
```

And prefer the **lowest level that satisfies the knowledge need**.

For code modification:

```text
exact code
```

should generally beat:

```text
summary of code
```

---

# 5. Example

Task:

> Fix the null handling in `UserService.create()`.

Context selection:

```text
Task
+
UserService.create()
+
relevant DTO
+
caller
+
affected test
```

No need for:

```text
README
20 unrelated services
old conversations
entire repository
```

That's optimization without quality loss.

---

# 6. Cloud models can still use this

Even if the user has:

```text
Claude/GPT/etc.
large context
```

I'd still perform:

```text
deduplication
freshness validation
provenance
conflict detection
structural retrieval
```

because those aren't really "small model tricks."

They're **information hygiene**.

---

# 7. What changes with a large model?

A large-context configuration can relax:

```text
maximum context
compression aggressiveness
history truncation
repository exploration depth
number of supporting files
research breadth
```

For example:

### Local profile

```text
target method
+
caller
+
tests
+
minimal dependencies
```

### Large cloud profile

```text
target method
+
whole relevant module
+
call graph
+
tests
+
configuration
+
related implementations
+
historical decisions
```

Same architecture.

Different budget.

---

# 8. This should become a `ModelCapabilityProfile`

I'd introduce this into Wayang/Aljabr.

```java
public record ModelCapabilityProfile(
    String modelId,
    InferenceType inferenceType,

    long contextWindowTokens,
    long recommendedInputTokens,
    long maxOutputTokens,

    ModelCapability reasoningCapability,
    ModelCapability codingCapability,

    boolean supportsStructuredOutput,
    boolean supportsToolCalling,

    double estimatedTokensPerSecond,
    long availableMemoryBytes
) {}
```

Obviously not every field has to come from the model provider.

Some are detected/configured.

---

# 9. Then a separate runtime profile

Because model capability isn't the same thing as machine capability.

```java
public record RuntimeCapabilityProfile(
    long totalMemoryBytes,
    long availableMemoryBytes,
    int cpuCores,
    boolean gpuAvailable,
    long gpuMemoryBytes,
    InferenceBackend backend
) {}
```

For your Mac:

```text
machine
   ↓
M4
16GB
   ↓
GGUF runtime
   ↓
Gemma 4 12B
```

Wayang can use this information when available.

---

# 10. Then combine them

```java
public record InferenceCapabilityProfile(
    ModelCapabilityProfile model,
    RuntimeCapabilityProfile runtime,
    ContextCapability context
) {}
```

Then:

```text
              Capability Detector
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
       Model                   Runtime
      profile                  profile
         │                       │
         └───────────┬───────────┘
                     ▼
             Inference Profile
                     │
                     ▼
             Context Strategy
```

---

# 11. But don't make "model size" the primary signal

This is important.

A 12B model isn't automatically:

```text
small context
```

and a 70B model isn't automatically:

```text
large context
```

You care about:

```text
model capability
context window
available memory
quantization
inference backend
latency
token throughput
task complexity
```

So:

```text
model size ≠ context strategy
```

---

# 12. Add a `ContextStrategy`

```java
public enum ContextStrategy {

    MINIMAL,
    BALANCED,
    EXPANDED,
    FULL
}
```

But I wouldn't expose these as rigid user modes.

The runtime should choose them.

---

# 13. Better: strategy dimensions

Instead of one switch:

```text
MINIMAL
```

have:

```java
public record ContextPolicy(
    int maxInputTokens,

    int maxRelevantFiles,
    int maxHistoryItems,

    CompressionLevel compression,
    RetrievalDepth retrievalDepth,
    HistoryPolicy historyPolicy,

    boolean allowBroadContext,
    boolean allowFullFileContext
) {}
```

Then the system can independently tune each dimension.

---

# 14. Example local policy

For your current Aljabr:

```text
maxInputTokens:       conservative
retrievalDepth:       2
compression:          STRUCTURAL
history:              SUMMARY
broadContext:         false
fullFiles:            targeted
```

Conceptually:

```text
        small
          │
    ┌─────┴─────┐
    │           │
 retrieve    compress
 narrow       carefully
    │           │
    └─────┬─────┘
          ▼
       Gemma
```

---

# 15. Large cloud policy

```text
maxInputTokens:       high
retrievalDepth:       5
compression:          LIGHT
history:              EXTENDED
broadContext:         true
fullFiles:            allowed
```

But still:

```text
deduplicate
validate freshness
preserve provenance
```

---

# 16. There's another important dimension: task complexity

A tiny local model doesn't necessarily need tiny context for every task.

Example:

```text
"Rename variable foo → bar"
```

Needs:

```text
very little
```

while:

```text
"Refactor authentication architecture"
```

needs:

```text
much broader context
```

So the final strategy should be:

```text
MODEL CAPABILITY
+
RUNTIME CAPABILITY
+
TASK COMPLEXITY
+
CURRENT CONTEXT NEED
```

---

# 17. Introduce `ContextDemand`

This is the missing piece.

```java
public record ContextDemand(
    int estimatedComplexity,
    int dependencyDepth,
    int affectedFiles,
    int uncertainty,
    int reasoningDifficulty,
    boolean architectureWide
) {}
```

The system estimates this before retrieving context.

---

# 18. Then calculate the strategy

Conceptually:

```text
                 ┌──────────────┐
                 │ Model        │
                 │ capability   │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Runtime      │
                 │ capability   │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Task         │
                 │ demand       │
                 └──────┬───────┘
                        │
                 ┌──────▼───────┐
                 │ Context      │
                 │ Policy       │
                 └──────────────┘
```

This is much more robust than:

```java
if (modelSize < 20B) ...
```

---

# 19. But there is one more thing: runtime feedback

This can become even better.

Aljabr can observe:

```text
context tokens
inference latency
model output quality
tool retries
replans
context expansion frequency
```

Then adapt.

For example:

```text
Context: 12k
Tool errors: low
Task success: high
```

→ keep strategy.

But:

```text
Context: 20k
Tool confusion ↑
replans ↑
```

→ reduce context.

Or:

```text
Context: 6k
model repeatedly asks for missing information
```

→ increase retrieval depth.

That's a **context controller**.

---

# 20. Context Controller

```java
public interface ContextController {

    ContextPolicy select(
        InferenceCapabilityProfile capability,
        ContextDemand demand,
        ContextTelemetry telemetry
    );
}
```

This becomes the sensor/detector you were describing.

---

# 21. `ContextTelemetry`

```java
public record ContextTelemetry(
    int inputTokens,
    int outputTokens,

    Duration inferenceTime,

    int toolCalls,
    int retries,
    int replans,

    int contextExpansions,

    double taskProgress
) {}
```

Now Aljabr can learn empirically.

---

# 22. Important: don't optimize merely for minimum tokens

This is where I would strongly disagree with a simplistic optimization.

The objective should **not** be:

```text
minimize input tokens
```

Nor:

```text
minimize output tokens
```

It should be:

```text
maximize task success
while minimizing unnecessary inference cost
```

So:

```text
                 SUCCESS
                    ↑
                    │
        ┌───────────┴───────────┐
        │                       │
    context quality        inference cost
        ↑                       ↓
        └──────── optimization ─┘
```

---

# 23. Define a utility function

Internally you could think in terms of:

```text
ContextUtility =
    task_relevance
  + evidence_quality
  + freshness
  + structural_coverage
  + decision_sufficiency
  - token_cost
  - noise
  - redundancy
```

The weights should be benchmarked rather than guessed.

---

# 24. "Minimum sufficient context"

This should probably become a core Aljabr concept.

Not:

> Minimum context.

But:

> **Minimum sufficient context.**

Meaning:

```text
smallest context that gives enough information
to make the current decision reliably.
```

That's a much safer optimization.

---

# 25. And this gives you an adaptive expansion loop

For example:

```text
Task
 ↓
ContextDemand
 ↓
minimal context
 ↓
Gemma
 ↓
decision confidence / knowledge need
 │
 ├── sufficient
 │      ↓
 │    execute
 │
 └── insufficient
        ↓
   targeted expansion
        ↓
      Gemma
```

This is excellent for local inference.

---

# 26. What about prompt quality?

If implemented correctly:

### Raw compression

```text
5000 tokens
→ 1500 tokens

information lost
→ BAD
```

### Semantic/structural selection

```text
5000 tokens
→ 1500 relevant tokens

information required preserved
→ GOOD
```

### Large-context expansion

```text
1500 tokens
→ 6000 tokens

because task actually requires it
→ GOOD
```

So the optimization should be **loss-aware**.

---

# 27. Make compression reversible

This is another architecture I'd add.

Every summary should maintain:

```text
summary
   ↓
source evidence IDs
```

Example:

```text
Summary:
TokenValidator owns expiry comparison.

Source:
E41
E42

Files:
TokenValidator.java:81-94
```

Then if the model needs the exact implementation:

```text
expand(E41)
```

No information is permanently discarded.

---

# 28. Never summarize away code semantics

For these things:

```text
code
configuration
API contracts
test assertions
compiler errors
patches
user constraints
```

prefer exact source or precise extraction.

For these:

```text
old conversation
historical research
large logs
repository descriptions
old attempts
```

compression is much safer.

---

# 29. This gives us a useful evidence classification

```java
public enum CompressionSafety {

    NEVER_COMPRESS,

    STRUCTURE_ONLY,

    LOSSLESS_SUMMARY,

    SEMANTIC_SUMMARY,

    AGGRESSIVE_SUMMARY
}
```

Example:

| Evidence            | Strategy                  |
| ------------------- | ------------------------- |
| User requirement    | NEVER                     |
| Current patch       | NEVER                     |
| Relevant code       | STRUCTURE / exact slice   |
| Test failure        | LOSSLESS summary + source |
| Old research        | SEMANTIC                  |
| Old conversation    | AGGRESSIVE                |
| Repository overview | SEMANTIC                  |

This would be very useful.

---

# 30. So can cloud users "go beyond" the optimization?

**Absolutely.**

But I wouldn't call it:

```text
optimized mode
vs
regular mode
```

I'd make it:

```text
Context Policy
```

with a capability ceiling.

For example:

```text
                 SAME ALJABR
                      │
               Context Controller
                      │
       ┌──────────────┼──────────────┐
       ▼              ▼              ▼
    Local          Balanced        Large
    profile         profile         profile
       │              │              │
    8–16k          16–40k          40k+
    narrow          medium          broad
```

Those are illustrative, not fixed numbers.

---

# 31. User can override the strategy too

You could eventually expose:

```text
Context mode:
[ Auto ]

Options:
[ Efficient ]
[ Balanced ]
[ Deep ]
```

### Efficient

```text
minimum sufficient context
aggressive history compression
targeted retrieval
```

### Balanced

```text
moderate context
```

### Deep

```text
broader repository context
less compression
more research
```

But:

> **Auto should be the default.**

---

# 32. And Auto should inspect actual capacity

Something like:

```java
ContextCapability detect() {
    return ContextCapability.builder()
        .modelContextWindow(...)
        .recommendedInputBudget(...)
        .availableMemory(...)
        .inferenceSpeed(...)
        .build();
}
```

For local engines, Wayang can get these from the configured inference backend where available.

If not, configuration can provide them.

---

# 33. One caution about "available memory"

Don't simply say:

```text
16 GB RAM
→ use huge context
```

Because the runtime also needs memory for:

```text
model weights
KV cache
runtime
OS
Wayang
repository index
tools
```

So the context controller should work with:

```text
memory headroom
```

not total RAM.

---

# 34. For your M4 specifically

Your current setup:

```text
M4
16GB
Gemma 4 12B IT
GGUF
```

is actually a very good reason to build this architecture.

You don't want:

```text
12B model
+
massive repository
+
20k tool output
+
long history
+
huge prompt
```

every turn.

Instead:

```text
12B model
+
precise task
+
small relevant code slice
+
structured evidence
+
one clear decision
```

That should give you much better practical throughput.

---

# 35. The most important distinction

I'd now explicitly define two optimization layers in Wayang:

### Information optimization

**Always ON**

```text
deduplicate
freshness
provenance
relevance
structural retrieval
conflict resolution
source references
```

### Capacity optimization

**Adaptive**

```text
compression
context width
history depth
retrieval depth
number of evidence items
number of tool results
reasoning budget
```

That is the architecture I would recommend.

---

# 36. Final model

So instead of:

```text
Small model → optimized context
Large model → normal context
```

I would implement:

```text
                    ALJABR
                       │
                 Context Engine
                       │
             ┌─────────▼─────────┐
             │ Context Controller│
             └─────────┬─────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
      Model         Runtime          Task
    Capability     Capability        Demand
        │              │              │
        └──────────────┼──────────────┘
                       ▼
                Context Policy
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
 Information Optimization    Capacity Optimization
      ALWAYS ON                  ADAPTIVE
          │                         │
          └────────────┬────────────┘
                       ▼
              Minimum Sufficient
                   Context
                       │
                       ▼
                    Model
```

And I think **"Minimum Sufficient Context" should become one of the central architectural principles of Aljabr.**

It gives you the best of both worlds:

* Your **Gemma 4 12B local setup** gets a highly focused context and low inference overhead.
* A user with a **large cloud model** can automatically receive broader context when the task warrants it.
* The information-quality mechanisms remain active for everyone.
* You don't have to maintain two separate agent architectures.
* And most importantly, **reducing tokens does not automatically mean reducing intelligence**—the system is optimizing *irrelevant information*, not knowledge.

So I would **not go directly to Memory/Evidence yet**. The next implementation milestone should actually be the small but foundational **`CapabilityDetector → ContextController → ContextPolicy` layer**, because then everything we design afterward—memory, retrieval, research cache, repository indexing—can adapt to the actual inference environment rather than being hard-coded around your current Gemma setup.


Absolutely. This should be the **next foundational layer before Memory/Evidence**, because it determines how aggressively every downstream subsystem behaves.

The goal is not to create a complicated "AI benchmark detector." It should be a small deterministic runtime component:

```text
CapabilityDetector
        ↓
InferenceCapabilityProfile
        ↓
ContextController
        ↓
ContextPolicy
        ↓
ContextEngine / Memory / Retrieval / Agent Loop
```

And importantly, **the LLM itself should not decide this**. Wayang should.

---

# Milestone: Adaptive Inference Capability

## 1. Three responsibilities

Keep these three things strictly separate.

### `CapabilityDetector`

Answers:

> **What can this inference environment reasonably handle?**

```text
model
context window
output limit
memory
backend
quantization
throughput
structured output
tool calling
```

### `ContextController`

Answers:

> **Given those capabilities and this task, how much context should we use?**

### `ContextPolicy`

Answers:

> **Exactly how should the Context Engine behave?**

That separation is important because capability is relatively static, while policy changes per task.

---

# 2. Core interfaces

I'd start with:

```java
public interface CapabilityDetector {

    InferenceCapabilityProfile detect(
        InferenceRuntime runtime
    );
}
```

Then:

```java
public interface ContextController {

    ContextPolicy select(
        InferenceCapabilityProfile capability,
        ContextDemand demand,
        ContextTelemetry telemetry
    );
}
```

And:

```java
public record ContextPolicy(
    TokenBudget tokenBudget,
    RetrievalPolicy retrieval,
    CompressionPolicy compression,
    HistoryPolicy history,
    ToolContextPolicy tools,
    ExpansionPolicy expansion
) {}
```

This is intentionally policy-oriented rather than having dozens of `if` statements scattered throughout the runtime.

---

# 3. Don't detect only the model

The capability is really:

```text
MODEL
+
INFERENCE BACKEND
+
RUNTIME
+
AVAILABLE RESOURCES
```

For example:

```text
Gemma 12B
+
GGUF Q4
+
llama.cpp
+
M4
+
16GB
```

is the actual environment.

So:

```java
public record InferenceCapabilityProfile(
    ModelProfile model,
    RuntimeProfile runtime,
    ContextCapability context,
    FeatureCapability features,
    PerformanceProfile performance
) {}
```

---

# 4. ModelProfile

```java
public record ModelProfile(
    String modelId,
    String architecture,
    long parameterCount,
    Quantization quantization,

    OptionalInt contextWindow,
    OptionalInt maxOutputTokens
) {}
```

For your current environment it might conceptually become:

```text
modelId:
unsloth/gemma-4-12b-it-GGUF

parameters:
~12B

quantization:
runtime-dependent

context:
detected/configured

output:
detected/configured
```

Don't hardcode these into Aljabr.

---

# 5. RuntimeProfile

```java
public record RuntimeProfile(
    InferenceBackend backend,

    long totalMemoryBytes,
    long availableMemoryBytes,

    int cpuThreads,

    OptionalLong acceleratorMemoryBytes
) {}
```

Potential backend:

```java
public enum InferenceBackend {

    LLAMA_CPP,
    MLX,
    OLLAMA,
    VLLM,
    TRANSFORMERS,
    REMOTE_API,
    UNKNOWN
}
```

You can add others later.

---

# 6. FeatureCapability

Don't assume every model/backend supports the same things.

```java
public record FeatureCapability(
    boolean streaming,
    boolean structuredOutput,
    boolean toolCalling,
    boolean vision,
    boolean reasoningMode
) {}
```

This is useful because context strategy can depend on it.

For example, if structured output isn't reliable:

```text
JSON schema
   ↓
fallback
   ↓
strict textual protocol
```

---

# 7. ContextCapability

This is the most important part.

```java
public record ContextCapability(

    int contextWindowTokens,

    int safeInputTokens,

    int safeOutputTokens,

    int minimumUsefulContext,

    int maximumUsefulContext
) {}
```

Notice:

```text
contextWindow
```

is not the same as:

```text
safeInput
```

and neither is necessarily:

```text
maximumUsefulContext
```

---

# 8. Why three limits?

Suppose a runtime technically supports:

```text
32k context
```

That doesn't mean Aljabr should always consume 32k.

You might have:

```text
32k
 ↓
runtime hard limit

24k
 ↓
safe operational limit

14k
 ↓
usually useful budget
```

That's much more practical.

---

# 9. Never blindly use 100% of context

I'd reserve space for the model's response.

For example:

```java
public record TokenBudget(
    int totalContext,
    int input,
    int outputReserve
) {}
```

Conceptually:

```text
Context window
┌───────────────────────────────┐
│ system                        │
│ task                          │
│ evidence                      │
│ code                          │
│ tools                         │
│                               │
│          INPUT                │
│                               │
├───────────────────────────────┤
│       OUTPUT RESERVE          │
└───────────────────────────────┘
```

---

# 10. Safe budget should be dynamic

Don't do:

```java
safeInput = contextWindow * 0.8;
```

as the only rule.

Consider:

```text
model context
runtime memory
expected output
task complexity
current KV-cache pressure
```

Then:

```java
safeInput =
    contextWindow
    - outputReserve
    - runtimeReserve;
```

The exact runtime reserve should be backend-specific.

---

# 11. Add a memory budget

This matters a lot for your M4 16GB.

```java
public record MemoryBudget(
    long total,
    long available,
    long reservedForModel,
    long reservedForRuntime,
    long availableForContext
) {}
```

Conceptually:

```text
16 GB
│
├── model weights
├── KV cache
├── inference runtime
├── Wayang
├── repository index
└── context-related memory
```

Wayang shouldn't assume the full 16GB belongs to the model.

---

# 12. Capability detection should have confidence

Some properties will be exact.

Others may be estimated.

```java
public record DetectedCapability<T>(
    T value,
    CapabilitySource source,
    double confidence
) {}
```

Source:

```java
public enum CapabilitySource {

    RUNTIME_API,
    MODEL_METADATA,
    CONFIGURATION,
    USER_CONFIGURATION,
    BENCHMARK,
    DEFAULT
}
```

This gives you graceful degradation.

---

# 13. Example

For your Gemma setup:

```text
contextWindow
source = runtime metadata
confidence = 1.0
```

Maybe:

```text
throughput
source = benchmark
confidence = 0.85
```

And:

```text
recommended context
source = default policy
confidence = 0.5
```

That is much cleaner than pretending every number is authoritative.

---

# 14. Detection order

I'd use:

```text
1. Runtime API
       ↓
2. Model metadata
       ↓
3. Explicit configuration
       ↓
4. Local benchmark
       ↓
5. Safe defaults
```

But allow configuration to override detected values intentionally.

---

# 15. Configuration

For example:

```yaml
inference:
  backend: llama_cpp

  model:
    id: unsloth/gemma-4-12b-it-GGUF
    parameters: 12b

  context:
    window: auto
    safe_input: auto
    output_reserve: auto

  memory:
    available: auto

  capability:
    profile: auto
```

Then your current setup can remain simple.

---

# 16. Explicit override

Users may know better than detection.

For example:

```yaml
inference:
  context:
    window: 16384
    safe_input: 12000
    output_reserve: 4000
```

This should override automatic detection.

---

# 17. Capability tiers

After detecting raw values, derive a capability class.

I would avoid making this based purely on model parameter count.

Something like:

```java
public enum CapabilityTier {

    CONSTRAINED,
    STANDARD,
    EXPANDED,
    HIGH_CAPACITY
}
```

Derived from:

```text
context
memory
throughput
model capability
```

not simply:

```text
12B = constrained
70B = high
```

---

# 18. Example

Your environment might land around:

```text
MODEL
12B

RUNTIME
local

MEMORY
16GB

CONTEXT
configured/detected

→ CONSTRAINED / STANDARD
```

But a remote 12B model with:

```text
128k context
huge server memory
high throughput
```

could become:

```text
EXPANDED
```

That's exactly why the profile must be environment-based.

---

# 19. Now introduce `ContextDemand`

This is the other half.

```java
public record ContextDemand(
    TaskComplexity complexity,
    int dependencyDepth,
    int affectedFiles,
    int uncertainty,
    boolean architectureWide,
    boolean requiresHistoricalContext,
    boolean requiresExternalKnowledge
) {}
```

---

# 20. Task complexity

Keep it simple initially:

```java
public enum TaskComplexity {

    TRIVIAL,
    SIMPLE,
    MODERATE,
    COMPLEX,
    ARCHITECTURAL
}
```

Don't make an LLM classification mandatory.

Wayang can estimate from deterministic signals.

---

# 21. Deterministic demand signals

For example:

```text
changed files
dependency depth
number of symbols
test count
unknowns
user request complexity
previous failures
```

You could calculate:

```java
int score =
      affectedFiles * 2
    + dependencyDepth * 3
    + uncertainty * 3
    + previousFailures * 2;
```

Then map to complexity.

The initial implementation can be crude.

Instrumentation will improve it later.

---

# 22. ContextController

Now we can finally implement the controller.

```java
public final class DefaultContextController
        implements ContextController {

    @Override
    public ContextPolicy select(
        InferenceCapabilityProfile capability,
        ContextDemand demand,
        ContextTelemetry telemetry
    ) {

        CapabilityTier tier =
            capability.context().tier();

        return policyFor(tier, demand, telemetry);
    }
}
```

---

# 23. But don't make huge switch statements

Avoid:

```java
if (tier == CONSTRAINED) {
    ...
} else if (...) {
    ...
}
```

Instead use policy rules.

```java
public record ContextRule(
    Predicate<ContextDecisionInput> condition,
    UnaryOperator<ContextPolicy> adjustment
) {}
```

Then:

```text
base policy
   ↓
capacity adjustment
   ↓
task adjustment
   ↓
telemetry adjustment
   ↓
final policy
```

Much easier to evolve.

---

# 24. Base policy

For example:

```text
BASE

retrieval:
  depth = 2

compression:
  structural

history:
  summarized

broadContext:
  false

fullFiles:
  targeted
```

Then:

```text
large capacity
```

might change:

```text
depth = 4
compression = light
history = extended
broadContext = true
```

---

# 25. Demand adjustment

A simple task:

```text
"rename variable"
```

doesn't need expansion.

An architectural task:

```text
"refactor authentication architecture"
```

may increase:

```text
retrievalDepth
maxFiles
maxEvidence
historyDepth
```

even on the same model.

---

# 26. Telemetry adjustment

Suppose:

```text
context = 8k
repeated missing-information requests = high
```

Then:

```text
retrievalDepth +1
```

But:

```text
context = 18k
replans = high
```

Then:

```text
compression ↑
history ↓
```

This gives the system feedback control.

---

# 27. Context controller should have guardrails

Very important.

It shouldn't endlessly expand.

```java
public record ExpansionPolicy(
    int maxExpansionsPerTurn,
    int maxExpansionsPerTask,
    int maxAdditionalTokens,
    int maxResearchActions
) {}
```

So:

```text
Gemma asks for more context
       ↓
controller checks budget
       ↓
allowed?
 ├── yes
 └── no → replan / ask user
```

---

# 28. "Unlimited cloud context" should still have limits

Even if the provider gives:

```text
1M tokens
```

I wouldn't automatically send 1M.

Because:

```text
large context
≠
useful context
```

Information hygiene remains.

The cloud profile simply gets a much higher ceiling.

---

# 29. Context Policy example

Here's roughly what I envision:

```java
public record ContextPolicy(
    TokenBudget tokens,

    RetrievalPolicy retrieval,

    CompressionPolicy compression,

    HistoryPolicy history,

    ToolContextPolicy tools,

    ExpansionPolicy expansion,

    SourcePolicy sources
) {}
```

---

# 30. RetrievalPolicy

```java
public record RetrievalPolicy(
    int maxDepth,
    int maxFiles,
    int maxSymbols,
    int maxEvidence,
    boolean structuralFirst,
    boolean semanticSearch
) {}
```

Local:

```text
depth: 2
files: 8
symbols: 20
evidence: 20
structuralFirst: true
semanticSearch: optional
```

Cloud:

```text
depth: 4
files: 30
symbols: 100
evidence: 100
structuralFirst: true
semanticSearch: true
```

Illustrative only.

---

# 31. CompressionPolicy

```java
public record CompressionPolicy(
    CompressionLevel level,
    boolean compressHistory,
    boolean compressToolOutput,
    boolean compressRepositoryDescriptions,
    boolean preserveCodeExactly
) {}
```

And:

```java
public enum CompressionLevel {
    NONE,
    LIGHT,
    STRUCTURAL,
    AGGRESSIVE
}
```

---

# 32. HistoryPolicy

```java
public record HistoryPolicy(
    int maxRecentTurns,
    int maxImportantDecisions,
    boolean summarizeOldTurns,
    boolean retainFailedAttempts
) {}
```

I would **always retain failed attempts** unless they are explicitly irrelevant.

They are valuable anti-repetition knowledge.

---

# 33. ToolContextPolicy

This determines how tool results enter the context.

```java
public record ToolContextPolicy(
    boolean summarizeLargeResults,
    int maxResultTokens,
    boolean preserveSourceReference,
    boolean includeRawOutputOnFailure
) {}
```

Notice:

```text
includeRawOutputOnFailure
```

could be useful.

For example, a compiler error should first be summarized, but raw output remains retrievable.

---

# 34. SourcePolicy

```java
public record SourcePolicy(
    boolean requireProvenance,
    boolean requireFreshness,
    boolean allowInferredFacts,
    boolean requireEvidenceForModification
) {}
```

For coding modifications:

```text
requireEvidenceForModification = true
```

is a good safety mechanism.

---

# 35. Final context budget

I would implement:

```java
public record TokenBudget(
    int maxInputTokens,
    int reservedOutputTokens,
    int maxSystemTokens,
    int maxTaskTokens,
    int maxEvidenceTokens,
    int maxCodeTokens,
    int maxHistoryTokens,
    int maxToolTokens
) {}
```

This lets the packer protect important sections.

---

# 36. Priority-based packing

Suppose the budget is only 8k.

Don't just truncate at 8k.

Instead:

```text
User constraints       MUST
Current task            MUST
Relevant code           MUST
Current diff            MUST
Tests                   HIGH
Evidence                HIGH
History                 MEDIUM
Old research            LOW
```

Then pack accordingly.

---

# 37. This is where `ContextPolicy` becomes powerful

The same Context Engine can run under:

```text
CONSTRAINED
```

and:

```text
HIGH_CAPACITY
```

without changing its code.

Only the policy changes.

---

# 38. Add policy explanation

I'd actually have:

```java
public record ContextPolicyDecision(
    ContextPolicy policy,
    List<PolicyReason> reasons
) {}
```

For example:

```text
Selected policy:

CONSTRAINED_LOCAL

Reasons:
- local inference
- 16GB runtime memory
- 12B model
- task complexity: moderate
- previous turn already used 11k tokens
```

This is excellent for debugging Aljabr itself.

---

# 39. Add a diagnostics command

Eventually:

```text
aljabr diagnostics inference
```

could output:

```text
Inference
──────────────
Model       Gemma 4 12B
Backend     llama.cpp
Mode        local
Memory      16 GB
Context     16,384
Safe input  11,500
Output      3,500

Capability
──────────────
Tier        CONSTRAINED
Tools       supported
Structured  supported

Current policy
──────────────
Retrieval depth       2
Max files             8
Compression           structural
History               summarized
Broad context         disabled
```

That will make tuning much easier.

---

# 40. Don't over-engineer the first version

For your first implementation I'd actually only implement:

```text
CapabilityDetector
ContextCapability
ContextDemand
ContextController
ContextPolicy
```

Everything else can be layered later.

Minimum viable implementation:

```java
CapabilityDetector
       ↓
InferenceCapabilityProfile
       ↓
DefaultContextController
       ↓
ContextPolicy
       ↓
ContextEngine
```

---

# 41. Suggested package layout

I'd structure it something like:

```text
wayang/
└── inference/
    ├── capability/
    │   ├── CapabilityDetector.java
    │   ├── InferenceCapabilityProfile.java
    │   ├── ModelProfile.java
    │   ├── RuntimeProfile.java
    │   ├── ContextCapability.java
    │   └── CapabilityTier.java
    │
    └── policy/
        ├── ContextController.java
        ├── ContextPolicy.java
        ├── ContextDemand.java
        ├── ContextTelemetry.java
        ├── RetrievalPolicy.java
        ├── CompressionPolicy.java
        ├── HistoryPolicy.java
        └── ExpansionPolicy.java
```

Then:

```text
wayang/
└── context/
    ├── ContextEngine
    ├── ContextRetriever
    ├── ContextRanker
    ├── ContextCompressor
    └── ContextPacker
```

Keep capability/policy separate from context implementation.

---

# 42. One important Wayang architectural point

I would put:

```text
CapabilityDetector
ContextController
ContextPolicy
```

in **Wayang runtime**, not Aljabr.

Why?

Because they're useful to:

```text
Aljabr
Research Agent
Prompt Coach
General Agent
Document Agent
```

All Wayang agents should be able to ask:

```text
"What inference environment am I running in?"
```

and receive the same capability abstraction.

Then Aljabr adds coding-specific `ContextDemand`.

That's a clean separation.

---

# 43. The resulting Wayang abstraction

```text
                    WAYANG RUNTIME
                         │
               CapabilityDetector
                         │
                         ▼
             InferenceCapabilityProfile
                         │
                ┌────────┴────────┐
                ▼                 ▼
          ContextController   Agent Runtime
                │
                ▼
          ContextPolicy
                │
        ┌───────┼────────┐
        ▼       ▼        ▼
     Memory  Evidence   Tools
        │       │        │
        └───────┼────────┘
                ▼
           Agent Context
```

Then:

```text
Aljabr
   │
   └── CodingContextDemand
```

This keeps Wayang generic and Aljabr specialized.

---

# 44. The most important design rule

I would write this directly into the architecture docs:

> **Capability detection determines what is possible. Context policy determines what is appropriate. Context demand determines what is necessary.**

Those are three different questions.

```text
CAPABILITY
"Can I?"

DEMAND
"What do I need?"

POLICY
"How much should I use?"
```

That gives us a very clean foundation.

---

# 45. And for your specific setup

The runtime should effectively discover:

```text
┌───────────────────────────────┐
│        Aljabr Runtime         │
├───────────────────────────────┤
│ Model: Gemma 4 12B IT         │
│ Format: GGUF                  │
│ Backend: local                │
│ Hardware: Apple M4            │
│ Memory: 16GB                  │
│                               │
│ Capability → constrained      │
│                               │
│ Context policy → efficient    │
│                               │
│ Retrieval → structural first  │
│ Compression → structural      │
│ History → summarized          │
│ Code → exact relevant slices  │
│ Expansion → targeted          │
└───────────────────────────────┘
```

But if tomorrow you switch to a large remote model:

```text
┌───────────────────────────────┐
│        Aljabr Runtime         │
├───────────────────────────────┤
│ Model: large cloud model      │
│ Backend: remote               │
│ Context: very large           │
│ Memory: provider-managed      │
│                               │
│ Capability → high             │
│                               │
│ Context policy → expanded     │
│                               │
│ Retrieval → broader           │
│ Compression → light           │
│ History → extended            │
│ Code → broader relevant scope │
│ Expansion → generous          │
└───────────────────────────────┘
```

**No architecture change.**

That's the real payoff of this milestone.

The next layer after this should be the **actual `ContextEngine` implementation that consumes `ContextPolicy`**—specifically the `ContextBudget → Retriever → EvidenceNormalizer → Compressor → Packer` pipeline. That is where we'll turn this policy into actual code behavior and make the local Gemma setup measurable rather than just conceptually optimized.


Yes. Now we can make the **ContextEngine** concrete.

Given the decisions above, I would make this milestone deliberately pragmatic:

> **ContextEngine = turn task state + evidence + policy into the smallest sufficient, trustworthy model context.**

Not another "prompt builder."

---

# 1. The final pipeline

I recommend this exact pipeline:

```text
                    ContextRequest
                         │
                         ▼
                  ContextPolicy
                         │
                         ▼
                 ┌───────────────┐
                 │ Requirement   │
                 │ Resolver      │
                 └───────┬───────┘
                         │
                  knowledge needs
                         │
                         ▼
                 ┌───────────────┐
                 │   Retriever   │
                 └───────┬───────┘
                         │
                    candidates
                         │
                         ▼
                 ┌───────────────┐
                 │ Normalizer    │
                 └───────┬───────┘
                         │
                 canonical evidence
                         │
                         ▼
                 ┌───────────────┐
                 │ Freshness /   │
                 │ Conflict      │
                 │ Validator     │
                 └───────┬───────┘
                         │
                     valid items
                         │
                         ▼
                 ┌───────────────┐
                 │ Ranker        │
                 └───────┬───────┘
                         │
                  ranked evidence
                         │
                         ▼
                 ┌───────────────┐
                 │ Compressor    │
                 └───────┬───────┘
                         │
                  compact evidence
                         │
                         ▼
                 ┌───────────────┐
                 │ Token Packer  │
                 └───────┬───────┘
                         │
                         ▼
                    AgentContext
                         │
                         ▼
                       Gemma
```

The important thing is that **each stage has one job**.

---

# 2. Core interface

Start here:

```java
public interface ContextEngine {

    Uni<AgentContext> build(ContextRequest request);
}
```

Request:

```java
public record ContextRequest(
    AgentTask task,
    AgentState state,
    ContextPurpose purpose,
    ContextPolicy policy,
    WorkspaceSnapshot workspace
) {}
```

Notice something important:

**The ContextEngine receives the policy.**

It doesn't decide whether the model is local or cloud.

That has already happened upstream.

---

# 3. `AgentContext`

Don't return a string.

This is important.

Bad:

```java
Uni<String> buildPrompt(...)
```

Better:

```java
public record AgentContext(
    List<ContextBlock> blocks,
    ContextManifest manifest,
    TokenUsage usage
) {}
```

The final prompt is just one rendering of this object.

---

# 4. ContextBlock

```java
public record ContextBlock(
    String id,
    ContextBlockType type,
    String content,
    int estimatedTokens,
    ContextPriority priority,
    List<String> evidenceIds
) {}
```

Types:

```java
public enum ContextBlockType {

    SYSTEM,
    OBJECTIVE,
    CONSTRAINTS,
    STATE,
    FACTS,
    CODE,
    TESTS,
    DIFF,
    PLAN,
    HISTORY,
    EVIDENCE,
    UNKNOWN,
    DECISIONS,
    TOOL_STATE
}
```

This gives you a stable internal representation.

---

# 5. Why not just `String`?

Because later you want to answer:

```text
Why was this included?
Why was this excluded?
How many tokens did code consume?
Which evidence supports this statement?
What became stale?
```

If everything becomes a string immediately, you've lost that information.

---

# 6. ContextRequest should describe the purpose

```java
public enum ContextPurpose {

    ANALYZE,
    RESEARCH,
    PLAN,
    MODIFY,
    VERIFY,
    DEBUG,
    REVIEW,
    REPLAN,
    COMPLETE
}
```

This is important for retrieval.

For example:

```text
MODIFY
```

should prioritize exact code.

While:

```text
DEBUG
```

should prioritize:

```text
failure
recent diff
test
execution evidence
```

---

# 7. First stage: RequirementResolver

The ContextEngine should first determine:

> What knowledge is actually needed?

```java
public interface RequirementResolver {

    Uni<List<KnowledgeNeed>> resolve(
        ContextRequest request
    );
}
```

Example:

```java
public record KnowledgeNeed(
    KnowledgeNeedType type,
    String target,
    Priority priority,
    boolean required
) {}
```

Types:

```java
public enum KnowledgeNeedType {

    TASK,
    SYMBOL,
    FILE,
    REFERENCE,
    TEST,
    DIFF,
    ERROR,
    BUILD,
    CONFIGURATION,
    DEPENDENCY,
    HISTORY,
    MEMORY,
    EXTERNAL_KNOWLEDGE
}
```

---

# 8. Example

Task:

> Fix `TokenValidator.validate()`.

Resolver produces:

```text
REQUIRED
- target symbol: TokenValidator.validate
- surrounding implementation
- relevant tests

HIGH
- callers
- current diff

OPTIONAL
- historical attempts
- project documentation
```

That is much more useful than blindly retrieving everything around `TokenValidator`.

---

# 9. RequirementResolver should initially be deterministic

Don't use Gemma to determine everything.

For example:

```java
if (purpose == MODIFY) {
    require(SYMBOL);
    require(TEST);
    require(DIFF);
}
```

Then later add model-assisted requirement detection if needed.

Again:

> Use deterministic runtime logic for things the runtime already knows.

---

# 10. Retriever

Now:

```java
public interface ContextRetriever {

    Uni<List<EvidenceItem>> retrieve(
        List<KnowledgeNeed> needs,
        ContextRequest request
    );
}
```

Don't create one enormous retriever.

Use multiple sources:

```text
CodeRetriever
TestRetriever
DiffRetriever
HistoryRetriever
MemoryRetriever
WorkspaceRetriever
ExternalKnowledgeRetriever
```

---

# 11. CompositeRetriever

```java
public final class CompositeContextRetriever
        implements ContextRetriever {

    private final List<ContextRetriever> retrievers;

    @Override
    public Uni<List<EvidenceItem>> retrieve(...) {
        // execute applicable retrievers
    }
}
```

This lets you add new knowledge sources without modifying ContextEngine.

---

# 12. EvidenceItem

This is where the earlier Evidence architecture starts appearing.

```java
public record EvidenceItem(
    String id,
    EvidenceType type,
    String content,

    EvidenceSource source,
    EvidenceStatus status,

    String workspaceRevision,
    Instant observedAt,

    Set<String> relatedSymbols,
    Set<String> relatedFiles
) {}
```

---

# 13. EvidenceSource

```java
public record EvidenceSource(
    SourceType type,
    String sourceId,
    Optional<String> location>,
    Optional<String> toolInvocationId>
) {}
```

Example:

```text
FILE
TokenValidator.java
lines 81-94
```

or:

```text
TEST
TokenValidatorTest.expiredToken
```

---

# 14. Evidence status

```java
public enum EvidenceStatus {

    VERIFIED,
    OBSERVED,
    INFERRED,
    UNKNOWN,
    STALE,
    CONFLICTING
}
```

This should survive all the way into the final context.

---

# 15. Normalization

Different tools might return the same information:

```text
search_code
inspect_symbol
read_file
```

The normalizer converts them into a common representation.

```java
public interface EvidenceNormalizer {

    List<EvidenceItem> normalize(
        List<RawEvidence> raw
    );
}
```

---

# 16. Example

Raw:

```text
grep result:
TokenValidator.java:82
```

Raw:

```text
symbol inspector:
TokenValidator.validateExpiry
```

Raw:

```text
file reader:
lines 81-94
```

Normalizer can produce one logical evidence entity:

```text
E42
TokenValidator.validateExpiry()
source:
TokenValidator.java:81-94

status:
VERIFIED
```

That's where deduplication starts.

---

# 17. Evidence identity

Use:

```java
public record EvidenceKey(
    EvidenceType type,
    String canonicalSource,
    String contentHash
) {}
```

This lets you avoid:

```text
same code
same fact
same test
```

appearing three times.

---

# 18. Freshness validation

Before ranking:

```java
public interface ContextValidator {

    ValidationResult validate(
        EvidenceItem evidence,
        WorkspaceSnapshot workspace
    );
}
```

Example:

```text
Evidence:
TokenValidator.java@W41

Workspace:
W43

Result:
STALE
```

---

# 19. Don't silently discard stale evidence

Mark it:

```text
STALE
```

and usually exclude it from authoritative context.

But keep the source reference.

This is useful when explaining why something was not used.

---

# 20. Conflict detection

```java
public interface ConflictResolver {

    List<EvidenceGroup> resolve(
        List<EvidenceItem> evidence
    );
}
```

Example:

```text
E41:
Clock is injected.

E92:
Clock is created internally.
```

If these refer to different revisions:

```text
E41 → W41
E92 → W43
```

then the newer workspace evidence wins.

---

# 21. If they are both current

Then don't choose arbitrarily.

Produce:

```text
CONFLICT

Evidence A:
...

Evidence B:
...

Resolution:
unresolved
```

This can become an `UNKNOWN`/`CONFLICTING` context block.

---

# 22. Ranker

Now we have trustworthy candidates.

```java
public interface ContextRanker {

    List<RankedEvidence> rank(
        List<EvidenceItem> evidence,
        ContextRequest request
    );
}
```

---

# 23. Scoring

For the first version:

```java
double score(EvidenceItem e, ContextRequest r) {

    double score = 0;

    score += taskRelevance(e, r) * 5;
    score += structuralRelevance(e, r) * 5;
    score += freshness(e) * 4;
    score += provenanceQuality(e) * 3;
    score += priority(e, r) * 3;

    score -= sizePenalty(e);

    return score;
}
```

Don't introduce embeddings yet unless measurements show you need them.

---

# 24. Structural relevance should dominate code retrieval

For example:

```text
target:
TokenValidator.validate()
```

Then:

```text
direct implementation       1.0
direct caller               0.9
direct callee               0.9
related test                0.85
same module                 0.5
semantic mention            0.3
unrelated documentation    0.1
```

This is much more appropriate for coding than pure semantic search.

---

# 25. Compressor

Now we decide what can safely be reduced.

```java
public interface ContextCompressor {

    Uni<CompressedEvidence> compress(
        RankedEvidence evidence,
        CompressionPolicy policy
    );
}
```

But there is a crucial rule:

**The compressor does not decide what evidence is important.**

The ranker already did that.

The compressor only changes representation.

---

# 26. Compression safety

As we discussed:

```java
public enum CompressionSafety {

    NEVER,
    STRUCTURAL_ONLY,
    LOSSLESS,
    SEMANTIC,
    AGGRESSIVE
}
```

Examples:

```text
User constraint        NEVER
Current diff           NEVER
Relevant code          STRUCTURAL_ONLY
Compiler error          LOSSLESS
Old research           SEMANTIC
Old conversation       AGGRESSIVE
```

---

# 27. Code compression

For code, don't use an LLM summarizer by default.

Use structural extraction.

Instead of:

```text
500-line file
```

produce:

```text
class UserService

fields:
- repository
- validator

methods:
- create()
- update()
- delete()

relevant:
create() lines 42-67
```

Then include:

```text
lines 42-67
```

This is essentially **lossless contextual compression**.

---

# 28. Tool output compression

Tool result:

```text
20,000 lines compiler output
```

Normalize to:

```text
BUILD FAILED

Errors: 3

1. TokenValidator.java:82
   incompatible types

2. AuthService.java:51
   unresolved symbol

3. TokenValidatorTest.java:27
   assertion failed
```

Keep:

```text
sourceRef = build-183
```

---

# 29. History compression

History can be much more aggressive.

Raw:

```text
Turn 1...
Turn 2...
Turn 3...
```

becomes:

```text
PREVIOUS ATTEMPTS

Attempt 1:
Changed TokenValidator.
Failed test: expiredToken.

Lesson:
Production Clock construction is elsewhere.

Attempt 2:
Inspected AuthService.
Found Clock construction.

Current:
Need inspect dependency injection configuration.
```

---

# 30. Token Packer

This is where policy becomes concrete.

```java
public interface ContextPacker {

    AgentContext pack(
        ContextRequest request,
        List<ContextBlock> blocks
    );
}
```

It receives:

```text
ContextPolicy
```

and must respect the budget.

---

# 31. Don't just truncate

A naïve implementation:

```java
context.substring(0, maxTokens)
```

is terrible.

Instead use priority buckets.

```text
MUST
  ↓
HIGH
  ↓
MEDIUM
  ↓
LOW
```

Pack each bucket until budget is exhausted.

---

# 32. Example budget

Suppose:

```text
12,000 input tokens
```

Policy:

```text
system        1,000
task            500
state           700
constraints     500
code          4,500
evidence      2,000
tests         1,000
history         800
reserve       1,000
```

If code requires 6,000:

```text
don't steal from constraints
don't steal from task
reduce lower-priority history/evidence first
```

---

# 33. Token estimation

Don't require an expensive tokenizer for every operation.

Implement:

```java
public interface TokenEstimator {

    int estimate(String text);
}
```

For initial implementation:

```text
approximate character/word heuristic
```

Then add backend-specific exact tokenizers.

This matters because different models tokenize differently.

---

# 34. Exact token counting should happen at inference boundary

Ideal architecture:

```text
ContextEngine
   ↓
estimated tokens
   ↓
packer
   ↓
InferenceAdapter
   ↓
exact token count
```

The adapter can report:

```text
actualInputTokens
actualOutputTokens
```

back to telemetry.

---

# 35. This creates a feedback loop

```text
ContextEngine
     ↓
estimated 10.2k
     ↓
Gemma
     ↓
actual 10.7k
     ↓
Telemetry
     ↓
Controller adjusts future budgets
```

This is much better than pretending estimates are exact.

---

# 36. AgentContext should expose both

```java
public record TokenUsage(
    int estimatedInput,
    OptionalInt actualInput,
    int reservedOutput,
    OptionalInt actualOutput
) {}
```

---

# 37. Context Manifest

This is extremely useful for debugging.

```java
public record ContextManifest(
    List<String> includedEvidence,
    List<String> omittedEvidence,
    List<String> staleEvidence,
    List<String> conflictingEvidence,
    List<String> compressedEvidence
) {}
```

Now Aljabr can explain:

```text
Why didn't you include Foo.java?
```

and the runtime can know:

```text
Foo.java was ranked low and omitted due to budget.
```

---

# 38. Context Engine implementation

Putting the pieces together:

```java
public final class DefaultContextEngine
        implements ContextEngine {

    private final RequirementResolver requirements;
    private final ContextRetriever retriever;
    private final EvidenceNormalizer normalizer;
    private final ContextValidator validator;
    private final ConflictResolver conflicts;
    private final ContextRanker ranker;
    private final ContextCompressor compressor;
    private final ContextPacker packer;

    @Override
    public Uni<AgentContext> build(ContextRequest request) {

        return requirements.resolve(request)
            .flatMap(needs ->
                retriever.retrieve(needs, request)
            )
            .map(normalizer::normalize)
            .map(evidence ->
                validate(evidence, request)
            )
            .map(conflicts::resolve)
            .map(groups -> flatten(groups))
            .map(evidence ->
                ranker.rank(evidence, request)
            )
            .flatMap(ranked ->
                compressor.compressAll(
                    ranked,
                    request.policy().compression()
                )
            )
            .map(compressed ->
                packer.pack(
                    request,
                    compressed
                )
            );
    }
}
```

The exact reactive type can match your Wayang runtime; the architecture is the important part.

---

# 39. But I would make one modification

Don't always run every stage.

For example:

```text
simple task
```

might need:

```text
requirements
→ retrieval
→ validation
→ ranking
→ packing
```

No compression necessary.

Whereas:

```text
large task
```

needs:

```text
requirements
→ broad retrieval
→ normalization
→ validation
→ conflict
→ ranking
→ compression
→ packing
```

So stages should be policy-driven.

---

# 40. `ContextPipeline`

I'd introduce:

```java
public interface ContextStage {

    Uni<ContextPipelineState> execute(
        ContextPipelineState state
    );
}
```

Then:

```text
RequirementStage
RetrievalStage
NormalizationStage
ValidationStage
ConflictStage
RankingStage
CompressionStage
PackingStage
```

The engine orchestrates stages.

---

# 41. Why this is better

You can later insert:

```text
CacheStage
MemoryStage
RepositoryGraphStage
SemanticRerankStage
ExternalResearchStage
```

without rewriting the engine.

---

# 42. ContextPipelineState

```java
public record ContextPipelineState(
    ContextRequest request,

    List<KnowledgeNeed> needs,
    List<EvidenceItem> evidence,
    List<RankedEvidence> ranked,
    List<ContextBlock> blocks,

    ContextDiagnostics diagnostics
) {}
```

This is the internal working object.

---

# 43. Diagnostics

Track:

```java
public record ContextDiagnostics(
    int retrievedItems,
    int normalizedItems,
    int staleItems,
    int conflicts,
    int compressedItems,
    int omittedItems,

    int estimatedTokens,
    int finalTokens
) {}
```

This will become extremely useful when optimizing Aljabr.

---

# 44. Local-model optimization now becomes measurable

For your Gemma setup, you can observe:

```text
Task #42

Retrieved: 38 evidence
After normalization: 21
After ranking: 12
Compressed: 5
Packed: 8

Estimated:
10,842 tokens

Actual:
10,517 tokens

Inference:
4.8 sec

Result:
SUCCESS
```

Now we're engineering, not guessing.

---

# 45. Add context expansion

This is where the ContextEngine becomes genuinely agentic.

Suppose the initial context is:

```text
TokenValidator
+
test
```

Gemma says:

```json
{
  "decision": "INSPECT",
  "target": "Clock construction"
}
```

Wayang sees that the requested knowledge is absent.

Then:

```text
ContextEngine
      ↓
KnowledgeNeed
      ↓
find_references(Clock)
      ↓
new evidence
      ↓
ContextEngine rebuild
      ↓
Gemma
```

This is **progressive context**.

---

# 46. Don't rebuild everything blindly

This is important for efficiency.

Maintain:

```java
ContextSnapshot
```

Then:

```text
existing evidence
+
new evidence
-
invalidated evidence
```

repack.

You don't need to rerun every repository query.

---

# 47. Context snapshot

```java
public record ContextSnapshot(
    String id,
    String taskId,
    String workspaceRevision,

    List<EvidenceItem> evidence,

    Instant createdAt
) {}
```

This will later connect directly to the memory/cache system.

---

# 48. Context cache boundary

The cache should sit around **evidence retrieval**, not final prompt strings.

```text
                ContextEngine
                     │
                 Retriever
                     │
              ┌──────▼──────┐
              │ Evidence    │
              │ Cache       │
              └──────┬──────┘
                     │
               repository
```

Cache:

```text
inspect symbol
find references
test analysis
build summary
research result
```

Not:

```text
"final prompt for Gemma"
```

because the final prompt is task/policy dependent.

---

# 49. One important optimization for local inference

The ContextEngine should prefer **incremental context**.

Instead of:

```text
Turn 1:
8k context

Turn 2:
re-send same 8k context

Turn 3:
re-send same 8k context
```

Wayang should at least internally know:

```text
stable evidence:
E1 E2 E3

new:
E4
```

If your inference backend supports prompt/KV caching, the adapter can exploit that.

This is an important future optimization for local GGUF inference.

---

# 50. Separate logical context from transport context

This is subtle but important.

### Logical context

```text
facts
code
tests
constraints
history
evidence
```

### Transport context

```text
actual token sequence sent to Gemma
```

Don't couple the ContextEngine to a particular prompt format.

Then later:

```text
Gemma
```

can use one renderer while:

```text
cloud model
```

uses another.

---

# 51. `ContextRenderer`

```java
public interface ContextRenderer {

    RenderedPrompt render(
        AgentContext context,
        ModelProfile model
    );
}
```

So:

```text
ContextEngine
     ↓
AgentContext
     ↓
ContextRenderer
     ↓
Gemma prompt
```

This is cleaner.

---

# 52. Gemma renderer

For your local model, I'd keep it very structured:

```text
<task>
Fix token expiration validation.
</task>

<constraints>
- no public API changes
- preserve existing tests
</constraints>

<state>
- validator located
- expiration check identified
</state>

<evidence>
...
</evidence>

<code>
...
</code>

<decision>
Choose the next action.
</decision>
```

Or whatever exact chat/template syntax the inference adapter expects.

The important point is:

**the ContextEngine doesn't know about these tags.**

---

# 53. Output context should also be structured

Gemma should ideally return:

```json
{
  "action": "INSPECT",
  "target": "AuthService",
  "reason": "Need production Clock construction path",
  "evidence": ["E41", "E43"]
}
```

Then Wayang validates it.

---

# 54. Context validation before inference

The runtime should check:

```text
task exists
constraints present
workspace revision current
token budget valid
required evidence included
```

before calling Gemma.

This prevents malformed contexts from reaching the model.

---

# 55. Context validation after packing

For example:

```java
if (!containsRequired(context, USER_CONSTRAINTS)) {
    throw new ContextBuildException(
        "Required constraints were omitted"
    );
}
```

This is very important.

The packer must never accidentally truncate away:

```text
user constraints
task objective
current modification target
```

just because the code consumed the budget.

---

# 56. Minimum viable implementation order

I would implement this milestone in exactly this order:

### Phase 1

```text
ContextRequest
ContextPolicy
AgentContext
ContextBlock
```

### Phase 2

```text
RequirementResolver
ContextRetriever
EvidenceItem
```

### Phase 3

```text
Normalizer
Validator
Ranker
```

### Phase 4

```text
TokenEstimator
ContextPacker
```

### Phase 5

```text
Compression
Manifest
Diagnostics
```

### Phase 6

```text
ContextSnapshot
incremental expansion
cache
```

Don't build semantic/vector retrieval yet.

---

# 57. First working implementation can be surprisingly small

The first useful Aljabr ContextEngine can essentially do:

```text
Task
 ↓
Target symbol
 ↓
Read target
 ↓
Find callers
 ↓
Find tests
 ↓
Current diff
 ↓
Rank
 ↓
Budget
 ↓
Gemma
```

That's enough to validate the architecture.

---

# 58. Example actual flow

User:

```text
Fix expired tokens being accepted.
```

### RequirementResolver

```text
TARGET:
token validation

NEEDS:
- implementation
- tests
- callers
- current diff
```

### Retriever

```text
TokenValidator.java
TokenValidatorTest.java
AuthService.java
current diff
```

### Normalizer

```text
E41 implementation
E42 test
E43 caller
E44 diff
```

### Validator

```text
all current
```

### Ranker

```text
E41 = 10.0
E42 = 9.2
E43 = 7.4
E44 = 8.1
```

### Packer

```text
Task
Constraints
E41
E42
E44
E43
```

### Gemma

```text
DECISION:
INSPECT

TARGET:
AuthService.createValidator()

REASON:
Need production Clock construction.

EVIDENCE:
E41 E43
```

### Wayang

```text
find_references(AuthService.createValidator)
```

### New evidence

```text
E45
```

### ContextEngine

Rebuilds incrementally.

This is the behavior I want.

---

# 59. What I would NOT build yet

For this milestone, avoid:

```text
❌ vector database
❌ LLM summarization everywhere
❌ giant semantic memory
❌ automatic prompt rewriting
❌ model-generated context selection
❌ massive context windows
❌ complicated learned ranker
```

Those can come later.

Start deterministic.

---

# 60. Final architecture now

At this point Wayang/Aljabr becomes:

```text
                         ALJABR
                            │
                       Agent State
                            │
                     Task / Objective
                            │
                            ▼
                   Context Controller
                            │
                     Context Policy
                            │
                            ▼
                    ┌───────────────┐
                    │ ContextEngine │
                    └───────┬───────┘
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
        Requirements     Retrieval       Cache
             │              │
             └───────┬──────┘
                     ▼
                Normalization
                     │
                     ▼
                Validation
                     │
              ┌──────┴──────┐
              ▼             ▼
          Conflicts       Freshness
              │             │
              └──────┬──────┘
                     ▼
                   Ranker
                     │
                     ▼
                 Compressor
                     │
                     ▼
                 TokenPacker
                     │
                     ▼
                 AgentContext
                     │
                     ▼
                ContextRenderer
                     │
                     ▼
                   Gemma
```

And the key architectural boundary is now very clean:

```text
CapabilityDetector
    ↓
"What can the environment handle?"

ContextController
    ↓
"What should we spend?"

ContextEngine
    ↓
"What information should we provide?"

ContextRenderer
    ↓
"How should we present it to this model?"

Gemma
    ↓
"What should we do next?"
```

That's the separation I'd keep.

**Next after this:** I would go into the **Evidence layer itself**—`EvidenceStore`, `EvidenceKey`, provenance, freshness/versioning, invalidation, deduplication, and especially how file/tool/research results become reusable evidence. That will let the ContextEngine stop repeatedly scanning the same repository information and is also the bridge into the Memory architecture we discussed earlier.


Absolutely. The next milestone should be the **Evidence Layer**, because now the ContextEngine has a place to obtain trustworthy, reusable information.

# Milestone: Evidence Layer

The central idea:

> **Evidence is the durable, addressable representation of something Wayang observed—not merely text returned by a tool.**

This distinction is extremely important for Aljabr.

Instead of:

```text
tool → text → prompt → discard
```

we want:

```text
tool
  ↓
raw observation
  ↓
normalize
  ↓
Evidence
  ↓
EvidenceStore
  ↓
ContextEngine
  ↓
model
```

Then the next task can reuse the evidence without repeating the same expensive operation.

---

# 1. What Evidence is—and isn't

An evidence item should represent something the runtime can point back to.

Examples:

```text
File:
src/auth/TokenValidator.java lines 81-104

Symbol:
TokenValidator.validate()

Test:
TokenValidatorTest.expiredToken()

Build:
build #183 failed

Search:
references to Clock found in 7 files

Research:
documentation page X says API Y requires Z
```

But this:

```text
"The code probably constructs the clock somewhere."
```

is not raw evidence.

That's an **inference**.

So keep these separate:

```text
Observation
    ↓
Evidence
    ↓
Inference
    ↓
Decision
```

This separation will dramatically improve Aljabr's reliability.

---

# 2. Core model

I'd start with:

```java
public record Evidence(
    EvidenceId id,
    EvidenceKind kind,

    EvidenceSource source,
    EvidenceLocation location,

    String content,
    ContentHash contentHash,

    EvidenceStatus status,

    WorkspaceVersion workspaceVersion,
    Instant observedAt,

    EvidenceMetadata metadata
) {}
```

The important pieces are:

```text
identity
source
location
content
version
freshness
status
metadata
```

---

# 3. EvidenceId

Don't use random UUIDs as the only identity.

You want semantic identity.

```java
public record EvidenceId(String value) {}
```

Then derive it from:

```text
kind
source
location
revision
content hash
```

For example:

```text
file:
repo:/src/auth/TokenValidator.java
lines:81-104
revision:abc123
```

could produce:

```text
EVIDENCE:f4a81...
```

This makes deduplication possible.

---

# 4. EvidenceKey

I'd separate identity from version.

```java
public record EvidenceKey(
    EvidenceKind kind,
    String canonicalSource,
    String canonicalLocation
) {}
```

Then:

```text
EvidenceKey
     │
     ├── version A
     ├── version B
     └── version C
```

This becomes very useful when the workspace changes.

---

# 5. EvidenceKind

Keep this extensible.

```java
public enum EvidenceKind {

    FILE,
    SYMBOL,
    CODE_SLICE,

    TEST,
    TEST_RESULT,
    BUILD_RESULT,
    DIFF,

    SEARCH_RESULT,
    REFERENCE,

    DEPENDENCY,
    CONFIGURATION,

    TOOL_RESULT,
    RESEARCH,

    MEMORY,
    USER_PROVIDED
}
```

Don't make every possible concept a special class initially.

The `kind` plus metadata is enough.

---

# 6. EvidenceSource

Every piece of evidence needs provenance.

```java
public record EvidenceSource(
    SourceType type,
    String identifier,
    Optional<String> invocationId
) {}
```

For example:

```text
FILE_SYSTEM
TokenValidator.java
```

or:

```text
TOOL
find_references
invocation = tool-184
```

or:

```text
WEB
https://...
```

or:

```text
USER
conversation turn 83
```

---

# 7. SourceType

```java
public enum SourceType {

    USER,
    FILE_SYSTEM,
    GIT,
    BUILD_SYSTEM,
    TEST_RUNNER,

    TOOL,
    MEMORY,
    WEB,
    MODEL
}
```

The `MODEL` source is important.

If Gemma says:

```text
"AuthService probably creates the Clock."
```

that can be stored as:

```text
source = MODEL
status = INFERRED
```

but it should **not automatically become verified evidence**.

---

# 8. Location

Don't make locations strings everywhere.

```java
public sealed interface EvidenceLocation
    permits FileLocation,
            SymbolLocation,
            UrlLocation,
            ToolLocation,
            ConversationLocation {}
```

For code:

```java
public record FileLocation(
    String repository,
    String path,
    OptionalInt startLine,
    OptionalInt endLine
) implements EvidenceLocation {}
```

Now the runtime can precisely refer to:

```text
src/auth/TokenValidator.java:81-104
```

---

# 9. Symbol location

```java
public record SymbolLocation(
    String repository,
    String file,
    String symbol,
    Optional<String> signature
) implements EvidenceLocation {}
```

This lets Aljabr reference:

```text
TokenValidator.validateExpiry()
```

without depending only on line numbers.

---

# 10. Why both symbol and line location?

Because lines move.

Suppose:

```text
W41:
TokenValidator.java:81-104
```

then someone adds 20 lines above.

Now:

```text
W42:
TokenValidator.java:101-124
```

The symbol identity may remain stable.

So the evidence system should be able to re-resolve:

```text
Symbol → current location
```

when possible.

---

# 11. WorkspaceVersion

This is absolutely essential for coding agents.

```java
public record WorkspaceVersion(
    String repositoryId,
    String revision,
    boolean dirty
) {}
```

Example:

```text
repository = aljabr
revision = 8d91a2f
dirty = true
```

Now Wayang knows exactly which state produced the evidence.

---

# 12. Dirty workspace matters

Git revision alone is insufficient.

Suppose:

```text
HEAD = abc123
```

but Aljabr modified:

```text
TokenValidator.java
```

without committing it.

Two evidence items may both claim:

```text
revision = abc123
```

while referring to different actual files.

So workspace version should eventually include a working-tree fingerprint.

For example:

```java
public record WorkspaceVersion(
    String repositoryId,
    String baseRevision,
    String workingTreeHash,
    boolean dirty
) {}
```

That's much safer.

---

# 13. ContentHash

Always hash the actual content.

```java
public record ContentHash(
    String algorithm,
    String value
) {}
```

For example:

```text
SHA-256
```

This gives you cheap deduplication.

---

# 14. EvidenceStatus

I would refine the earlier version slightly:

```java
public enum EvidenceStatus {

    OBSERVED,
    VERIFIED,

    INFERRED,

    STALE,
    CONFLICTING,
    INVALID
}
```

Meaning:

### `OBSERVED`

Tool/runtime directly observed it.

### `VERIFIED`

An observation was independently confirmed.

### `INFERRED`

Produced by reasoning.

### `STALE`

Was once valid but no longer matches the workspace/state.

### `CONFLICTING`

Conflicts with another current evidence item.

### `INVALID`

Failed validation.

---

# 15. The EvidenceStore

Now the important part.

```java
public interface EvidenceStore {

    Uni<Evidence> put(Evidence evidence);

    Uni<Optional<Evidence>> get(EvidenceId id);

    Uni<List<Evidence>> find(EvidenceQuery query);

    Uni<Void> invalidate(EvidenceId id);

    Uni<Void> invalidateByVersion(WorkspaceVersion version);
}
```

But I would eventually separate writes from queries.

---

# 16. Better interface

```java
public interface EvidenceStore {

    Uni<EvidenceId> save(Evidence evidence);

    Uni<Optional<Evidence>> get(EvidenceId id);

    Uni<List<Evidence>> query(EvidenceQuery query);
}
```

And:

```java
public interface EvidenceInvalidator {

    Uni<Void> invalidate(
        InvalidationEvent event
    );
}
```

This avoids turning `EvidenceStore` into a giant god interface.

---

# 17. EvidenceQuery

```java
public record EvidenceQuery(
    Optional<EvidenceKind> kind,
    Optional<String> repository,
    Optional<String> path,
    Optional<String> symbol,

    Optional<WorkspaceVersion> workspaceVersion,

    Set<String> tags,

    int limit
) {}
```

Later you can add:

```text
semantic similarity
dependency relation
time range
importance
```

without changing the conceptual model.

---

# 18. Start with structural queries

For Aljabr, this is important.

Don't immediately build vector search.

First support:

```text
get file
get symbol
get references
get tests
get current diff
get build result
```

These are deterministic and cheap.

---

# 19. EvidenceStore implementations

I'd expect:

```text
EvidenceStore
├── InMemoryEvidenceStore
├── FileEvidenceStore
└── SqliteEvidenceStore
```

For Aljabr local inference:

**SQLite is probably the sweet spot.**

Not because it is glamorous.

Because:

```text
local
persistent
small
transactional
queryable
no server
```

fits your M4 setup very well.

---

# 20. Don't store giant blobs blindly

SQLite metadata:

```text
evidence_id
kind
source
location
revision
content_hash
status
observed_at
metadata
```

Content can be:

```text
inline
```

for small evidence.

Or:

```text
blob/file reference
```

for large evidence.

---

# 21. Content storage

I'd use:

```java
public interface EvidenceContentStore {

    Uni<ContentRef> put(String content);

    Uni<String> get(ContentRef ref);
}
```

Then:

```java
public record ContentRef(
    String id,
    long size,
    String hash
) {}
```

This means metadata remains lightweight.

---

# 22. Large repository files

Don't put entire 100k-line files into evidence just because the file was read.

Instead:

```text
FILE
metadata

CODE_SLICE
lines 81-104

CODE_SLICE
lines 140-161
```

This matches the ContextEngine philosophy.

---

# 23. Evidence lineage

This is one of the most valuable additions.

Evidence can derive from other evidence.

```java
public record EvidenceLineage(
    List<EvidenceId> parents,
    Optional<String> transformation
) {}
```

Example:

```text
FILE
 ↓
SYMBOL
 ↓
CODE_SLICE
 ↓
SUMMARY
```

or:

```text
TEST_RESULT
 ↓
FAILURE_SUMMARY
```

Now Wayang knows where a summary came from.

---

# 24. Why lineage matters

Suppose Gemma receives:

```text
"Token expiration uses system time."
```

The runtime can trace:

```text
SUMMARY E91
    ↓
CODE_SLICE E87
    ↓
TokenValidator.java
    ↓
workspace revision abc123
```

That's far safer than a mysterious piece of remembered text.

---

# 25. Evidence transformations

Make transformations explicit.

```java
public enum EvidenceTransformation {

    NONE,

    EXTRACTED,
    NORMALIZED,
    DEDUPLICATED,

    STRUCTURALLY_COMPRESSED,
    SEMANTICALLY_SUMMARIZED,

    VERIFIED
}
```

Then we know whether something is original or derived.

---

# 26. Critical rule

I would establish this invariant:

> **Derived evidence must never have stronger authority than its strongest verified parent.**

Actually I'd make it stricter:

> **A transformation may reduce information, but must not silently increase certainty.**

So:

```text
OBSERVED
 ↓ summary
OBSERVED
```

not:

```text
OBSERVED
 ↓ summary
VERIFIED
```

unless an explicit verification operation happened.

---

# 27. Evidence deduplication

Suppose:

```text
read_file
grep
symbol_inspector
```

all discover:

```text
TokenValidator.validate()
```

The store should recognize:

```text
same kind
same source
same location
same content hash
```

and return existing evidence.

```java
EvidenceId existing =
    index.findByContentHash(hash);
```

---

# 28. But don't deduplicate different observations completely

Example:

```text
Tool A:
Clock injected.

Tool B:
Clock created internally.
```

Same file perhaps, but different claims.

Those should remain separate.

Deduplicate **identical observations**, not merely similar text.

---

# 29. Evidence confidence

I'd add:

```java
public record EvidenceQuality(
    double confidence,
    double provenance,
    double freshness
) {}
```

But don't turn this into fake mathematical precision.

For the initial implementation, discrete levels may be better:

```java
enum EvidenceConfidence {
    LOW,
    MEDIUM,
    HIGH
}
```

Later telemetry can make it more sophisticated.

---

# 30. Verification

This is especially useful for Aljabr.

```java
public interface EvidenceVerifier {

    Uni<VerificationResult> verify(
        Evidence evidence,
        VerificationContext context
    );
}
```

Examples:

```text
"method exists"
        ↓
symbol lookup

"test passes"
        ↓
test runner

"build succeeds"
        ↓
build runner

"file contains X"
        ↓
file inspection
```

Verification should use the cheapest authoritative mechanism.

---

# 31. Example

Gemma proposes:

```text
"AuthService constructs the Clock."
```

This is:

```text
INFERRED
```

Aljabr asks:

```text
find symbol/reference Clock
```

Result:

```text
Clock created in AuthService.java:51
```

Now:

```text
E91 = VERIFIED
```

The inference is still an inference.

The observation is verified.

---

# 32. InferenceStore vs EvidenceStore

I would **not** mix them.

Eventually:

```text
EvidenceStore
    ↓
facts / observations

InferenceStore
    ↓
model-generated conclusions

DecisionStore
    ↓
agent decisions/actions
```

This gives you:

```text
OBSERVATION
    ↓
INFERENCE
    ↓
DECISION
    ↓
ACTION
    ↓
RESULT
```

That is an excellent audit trail for Aljabr.

---

# 33. This naturally creates an Agent Event Graph

Conceptually:

```text
EVIDENCE E41
    │
    ▼
INFERENCE I12
    │
    ▼
DECISION D8
    │
    ▼
TOOL CALL T91
    │
    ▼
EVIDENCE E92
```

This is much more powerful than a traditional chat history.

---

# 34. Evidence invalidation

This is where the coding-agent implementation gets interesting.

When Aljabr modifies:

```text
TokenValidator.java
```

the runtime emits:

```java
public record WorkspaceChanged(
    WorkspaceVersion previous,
    WorkspaceVersion current,
    Set<String> changedFiles
) {}
```

Then:

```text
EvidenceInvalidator
        ↓
affected evidence
        ↓
STALE
```

---

# 35. Don't invalidate the whole repository

If:

```text
TokenValidator.java
```

changes:

don't invalidate:

```text
README.md
UserController.java
DatabaseConfig.java
```

unless dependency analysis says they're affected.

Start with direct file invalidation.

Then improve toward dependency-aware invalidation.

---

# 36. Dependency-aware invalidation

Later:

```text
TokenValidator.java
       ↓
AuthService.java
       ↓
LoginController.java
```

A change to the validator could invalidate:

```text
validator evidence
related symbol evidence
test evidence
analysis depending on validator
```

But don't immediately invalidate every caller.

Use dependency confidence.

---

# 37. Evidence TTL

Not all evidence needs the same lifetime.

I'd support:

```java
public record EvidenceRetention(
    RetentionClass retention
) {}
```

```java
enum RetentionClass {

    EPHEMERAL,
    TASK,
    WORKSPACE,
    PROJECT,
    LONG_TERM
}
```

Examples:

```text
compiler output      EPHEMERAL
current file slice   TASK
repository symbol    WORKSPACE
architecture fact    PROJECT
user-provided rule   LONG_TERM
```

This becomes the bridge to Memory.

---

# 38. Evidence vs Memory

This distinction is critical.

### Evidence

```text
"I observed X."
```

### Memory

```text
"Keep X for future use."
```

So:

```text
Evidence
   ↓ promotion policy
Memory
```

Not everything in Evidence should become memory.

---

# 39. Memory promotion

For example:

```text
Evidence:
Project uses repository pattern.

Observed from:
12 files.

Repeated:
23 times.

Confidence:
high.

→ promote to project memory
```

Whereas:

```text
Evidence:
temporary compiler error
```

should never become long-term memory.

---

# 40. Evidence cache vs Evidence store

Keep these separate conceptually.

### Cache

Optimization:

```text
"Don't repeat expensive work."
```

### Store

Knowledge substrate:

```text
"What has the system observed?"
```

So:

```text
Cache
 ↓
fast reuse

EvidenceStore
 ↓
durable provenance
```

The cache may disappear without correctness changing.

---

# 41. This is important after connection loss

Your earlier requirement maps beautifully here.

Suppose Aljabr spends 20 seconds:

```text
scan repository
find references
run tests
research docs
```

Then inference connection fails.

On retry:

```text
ContextEngine
   ↓
EvidenceStore
   ↓
existing evidence
```

It doesn't need to repeat all those operations.

Only missing/invalid evidence is recomputed.

That's exactly what you wanted.

---

# 42. Evidence cache key

For expensive tools, define:

```java
public record ObservationKey(
    String operation,
    Map<String, String> parameters,
    WorkspaceVersion workspace
) {}
```

Example:

```text
findReferences
symbol=TokenValidator.validate
workspace=abc123
```

Then:

```text
cache hit
```

means don't run the tool again.

---

# 43. Tool execution flow

The tool layer becomes:

```text
ToolRequest
    ↓
ObservationCache
    │
    ├── HIT
    │    ↓
    │ Evidence
    │
    └── MISS
         ↓
      Execute tool
         ↓
      Raw result
         ↓
      Normalize
         ↓
      EvidenceStore
         ↓
      Cache
```

Very efficient for local Aljabr.

---

# 44. Add operation status

Don't cache only successes.

```java
enum ObservationStatus {

    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELLED
}
```

A partial result can still be useful.

But a failed operation shouldn't masquerade as evidence.

---

# 45. Example implementation

A tool executor could look conceptually like:

```java
public Uni<List<Evidence>> observe(
    ObservationRequest request
) {

    return cache.get(request.key())
        .onItem().ifNotNull()
        .continueWith(cached -> cached)

        .onItem().ifNull()
        .switchTo(() ->
            tool.execute(request)
                .map(normalizer::normalize)
                .flatMap(store::saveAll)
                .invoke(evidence ->
                    cache.put(request.key(), evidence)
                )
        );
}
```

Exact reactive API can follow your existing runtime.

---

# 46. EvidenceStore schema

If you use SQLite, I'd start with roughly:

```sql
CREATE TABLE evidence (
    id TEXT PRIMARY KEY,

    kind TEXT NOT NULL,

    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,

    location TEXT,

    content_hash TEXT NOT NULL,
    content_ref TEXT,

    status TEXT NOT NULL,

    repository_id TEXT,
    base_revision TEXT,
    working_tree_hash TEXT,

    observed_at INTEGER NOT NULL,

    metadata_json TEXT,
    lineage_json TEXT
);
```

Then indexes:

```sql
CREATE INDEX idx_evidence_hash
ON evidence(content_hash);

CREATE INDEX idx_evidence_source
ON evidence(source_type, source_id);

CREATE INDEX idx_evidence_revision
ON evidence(repository_id, base_revision);

CREATE INDEX idx_evidence_kind
ON evidence(kind);
```

That's enough to start.

---

# 47. Don't create a vector DB yet

For Aljabr on:

```text
M4
16GB
local inference
```

I'd strongly resist adding:

```text
Qdrant
Milvus
Weaviate
```

at this stage.

You can get surprisingly far with:

```text
SQLite
+
filesystem
+
AST/index
+
structural retrieval
```

Then add embeddings only where measurements show structural retrieval isn't enough.

---

# 48. Evidence retrieval should be layered

Eventually:

```text
Layer 1
exact lookup

Layer 2
structural lookup

Layer 3
dependency graph

Layer 4
lexical search

Layer 5
semantic search
```

Always search cheaper/more precise layers first.

This is **very appropriate for a local model**.

---

# 49. Example coding retrieval

User asks:

```text
Fix expired token validation.
```

Don't start with embeddings.

Do:

```text
1. identify TokenValidator
2. inspect validate()
3. inspect expiry-related symbols
4. find callers
5. find tests
6. inspect diff
7. only then semantic search if ambiguity remains
```

This saves:

```text
tokens
CPU
memory
latency
model calls
```

without sacrificing quality.

---

# 50. ContextEngine now becomes much stronger

Previously:

```text
ContextEngine
 ↓
retrieve
 ↓
prompt
```

Now:

```text
                   EvidenceStore
                  ↗      ↑      ↖
                 /       │       \
             files      tools     tests
                 \       │       /
                  ContextEngine
                       ↓
                    Policy
                       ↓
                   Context
                       ↓
                     Gemma
```

And after Gemma:

```text
Gemma
 ↓
Inference
 ↓
Decision
 ↓
Tool
 ↓
EvidenceStore
```

So the system continuously accumulates **grounded observations**.

---

# 51. The bigger Aljabr loop

This is where I think the architecture becomes really compelling:

```text
              ┌──────────────────────┐
              │       OBJECTIVE       │
              └──────────┬───────────┘
                         ↓
                 ContextController
                         ↓
                   ContextEngine
                         ↓
                    EvidenceStore
                         ↓
                       Gemma
                         ↓
                     Decision
                         ↓
                   ToolExecutor
                         ↓
                    New Evidence
                         │
                         └──────────────┐
                                        ↓
                                EvidenceStore
                                        ↓
                                  ContextEngine
```

That is the core coding-agent loop.

---

# 52. And this gives us a very clean hierarchy

I would now define the Wayang runtime concepts as:

```text
OBSERVATION
    │
    ▼
EVIDENCE
    │
    ▼
INFERENCE
    │
    ▼
DECISION
    │
    ▼
ACTION
    │
    ▼
RESULT
    │
    ▼
EVIDENCE
```

And separately:

```text
EVIDENCE
    │
    ├── cache
    ├── context
    ├── verification
    └── memory promotion
```

That's a much cleaner foundation than treating "memory" as one giant bag of text.

---

# 53. The next milestone

Now that we have:

```text
CapabilityDetector
        ↓
ContextController
        ↓
ContextEngine
        ↓
EvidenceStore
```

the next thing I'd build is **`Observation/Tool Execution + Evidence Lifecycle`**.

Specifically:

```text
ToolRequest
    ↓
ObservationCache
    ↓
ToolExecutor
    ↓
RawToolResult
    ↓
EvidenceNormalizer
    ↓
EvidenceStore
    ↓
Invalidation
    ↓
ContextEngine
```

That milestone is where Aljabr starts behaving like an actual **local coding agent rather than an LLM wrapped around tools**.

And after that, we can tackle the **Memory layer** on top of this evidence substrate—where `task memory`, `workspace memory`, `project memory`, and `long-term memory` become promotion/retention policies rather than duplicated storage systems.


Yes. Let's move to the next milestone:

# Milestone — Observation / Tool Execution + Evidence Lifecycle

This is the layer that makes the previous architecture **actually executable**.

The key idea is:

> **A tool call is not just an RPC. It is an observation transaction that produces evidence.**

For Aljabr, this gives us:

```text
ToolIntent
    ↓
ObservationRequest
    ↓
Cache lookup
    ↓
Tool execution
    ↓
Raw result
    ↓
Normalize
    ↓
Validate
    ↓
EvidenceStore
    ↓
ContextEngine
```

And critically:

```text
same workspace
+
same operation
+
same parameters
        ↓
reuse observation
```

instead of repeatedly scanning the repository.

---

# 1. Start with `ObservationRequest`

Don't let agents call tools directly.

Bad:

```java
tool.execute("findReferences", "TokenValidator");
```

Better:

```java
public record ObservationRequest(
    ObservationId id,
    ToolId tool,
    Map<String, Object> arguments,
    ObservationScope scope,
    WorkspaceVersion workspace,
    ObservationPolicy policy
) {}
```

This gives Wayang control over execution.

---

# 2. `ObservationId`

```java
public record ObservationId(String value) {}
```

But again, don't generate it randomly.

Derive it from:

```text
tool
arguments
workspace
relevant configuration
```

For example:

```text
findReferences
symbol=TokenValidator.validate
repository=aljabr
workspace=abc123
```

becomes one deterministic observation key.

---

# 3. `ObservationKey`

I'd actually separate the identity:

```java
public record ObservationKey(
    String operation,
    String argumentsHash,
    String workspaceFingerprint
) {}
```

Then:

```java
public record ObservationId(
    String value
) {}
```

This makes caching deterministic.

---

# 4. Why workspace belongs in the key

Consider:

```text
findReferences(TokenValidator)
```

at:

```text
revision A
```

and then:

```text
findReferences(TokenValidator)
```

at:

```text
revision B
```

These are **different observations**.

Without workspace identity, you could accidentally feed old repository information to Gemma.

That's one of the most dangerous bugs in a coding agent.

---

# 5. ObservationScope

Not every observation has the same validity boundary.

```java
public enum ObservationScope {

    CALL,
    TASK,
    WORKSPACE,
    PROJECT
}
```

For example:

```text
git status
```

is probably:

```text
CALL / WORKSPACE
```

while:

```text
project architecture scan
```

could survive across tasks.

---

# 6. ObservationPolicy

```java
public record ObservationPolicy(
    boolean allowCache,
    boolean allowStale,
    boolean persistEvidence,
    Duration timeout,
    int maxRetries
) {}
```

This is where Aljabr can make local inference efficient.

---

# 7. Tool interface

Your existing Wayang tool abstraction should remain the low-level execution mechanism.

Conceptually:

```java
public interface WayangTool<I, O> {

    ToolId id();

    Uni<O> execute(I input);
}
```

But the agent should **not** directly invoke it.

Instead:

```text
Agent
 ↓
ObservationManager
 ↓
WayangTool
```

---

# 8. ObservationManager

This becomes the main gateway:

```java
public interface ObservationManager {

    Uni<ObservationResult> observe(
        ObservationRequest request
    );
}
```

Implementation:

```java
public final class DefaultObservationManager
        implements ObservationManager {

    private final ObservationCache cache;
    private final ToolRegistry tools;
    private final EvidenceNormalizer normalizer;
    private final EvidenceStore evidenceStore;
}
```

---

# 9. The actual flow

```java
public Uni<ObservationResult> observe(
    ObservationRequest request
) {
    if (!request.policy().allowCache()) {
        return execute(request);
    }

    return cache.get(request)
        .onItem()
        .ifNotNull()
        .continueWith(this::fromCache)
        .onItem()
        .ifNull()
        .switchTo(() -> execute(request));
}
```

This is the basic mechanism.

---

# 10. Cache hit

On a hit:

```text
ObservationRequest
      ↓
ObservationCache
      ↓
cached Evidence
```

No tool execution.

No repository scan.

No model call.

No wasted tokens.

---

# 11. Cache miss

```text
ObservationRequest
      ↓
ToolRegistry
      ↓
Tool
      ↓
RawToolResult
```

Then:

```text
RawToolResult
      ↓
Normalizer
      ↓
Evidence
      ↓
EvidenceStore
      ↓
ObservationCache
```

---

# 12. RawToolResult

Don't let arbitrary tool output directly become evidence.

Use:

```java
public record RawToolResult(
    ObservationId observationId,
    ToolId tool,
    Object value,
    ObservationStatus status,
    Duration duration,
    Instant completedAt,
    Optional<String> error
) {}
```

---

# 13. Why preserve raw result?

Because normalized evidence is a transformation.

Suppose:

```text
git diff
```

returns 4,000 lines.

You normalize it to:

```text
3 changed files
2 modified methods
1 deleted test
```

You want to preserve:

```text
raw result
```

for debugging.

But you don't necessarily want the raw result in model context.

---

# 14. Raw result retention

Use retention policy:

```java
public enum RawRetention {

    NONE,
    TASK,
    WORKSPACE,
    PERSISTENT
}
```

For local Aljabr:

```text
normal tool output → TASK
expensive research → WORKSPACE
huge logs → NONE / short TTL
```

---

# 15. Observation status

```java
public enum ObservationStatus {

    SUCCESS,
    PARTIAL,
    FAILED,
    TIMEOUT,
    CANCELLED
}
```

A failed tool execution isn't evidence of the requested fact.

This distinction matters.

For example:

```text
findReferences failed
```

does **not** mean:

```text
no references exist
```

It means:

```text
unknown
```

---

# 16. Very important invariant

Establish this runtime rule:

> **Failure to observe something must never be interpreted as evidence that the thing does not exist.**

So:

```text
search returned zero results
```

and:

```text
search failed
```

must be different.

---

# 17. `ObservationResult`

```java
public record ObservationResult(
    ObservationId observationId,
    ObservationStatus status,
    List<EvidenceId> evidence,
    boolean fromCache,
    Duration duration
) {}
```

This is what the rest of Wayang consumes.

---

# 18. Tool execution should be cancellable

Aljabr will often perform:

```text
search
scan
test
build
```

Some may be expensive.

Use:

```java
public interface CancellableTool<I, O>
        extends WayangTool<I, O> {

    Uni<O> execute(
        I input,
        CancellationToken token
    );
}
```

Then when the agent realizes:

```text
"I have enough evidence."
```

it can cancel remaining observations.

That's a useful local-performance optimization.

---

# 19. Parallel observation

Suppose the requirement resolver says:

```text
need:
- implementation
- tests
- callers
```

These can often execute concurrently:

```text
             ObservationManager
              /      |       \
             /       |        \
        inspect    tests    references
```

Then combine:

```text
Evidence[]
```

This reduces latency.

---

# 20. But don't parallelize blindly

Some operations depend on others.

For example:

```text
find references
```

requires the symbol to be identified first.

So use dependencies:

```text
resolve symbol
       ↓
 ┌─────┼─────┐
 ↓     ↓     ↓
code tests callers
```

This naturally leads to an **ObservationPlan**.

---

# 21. ObservationPlan

```java
public record ObservationPlan(
    List<ObservationStep> steps
) {}
```

Each step:

```java
public record ObservationStep(
    String id,
    ObservationRequest request,
    Set<String> dependencies
) {}
```

Then Wayang can execute the graph.

---

# 22. ObservationPlanner

```java
public interface ObservationPlanner {

    Uni<ObservationPlan> plan(
        List<KnowledgeNeed> needs,
        AgentState state
    );
}
```

For the first version, don't make this LLM-driven.

Use deterministic planning.

---

# 23. Example plan

Task:

```text
Fix expired token validation.
```

Planner:

```text
S1:
find TokenValidator

S2:
inspect validate()
depends S1

S3:
find tests for validate()
depends S1

S4:
find callers
depends S1

S5:
inspect current diff
no dependency
```

Execution:

```text
S1
 ↓
S2 ─┐
S3 ─┼── parallel
S4 ─┘

S5 ───────── parallel
```

Very efficient.

---

# 24. Observation deduplication

Suppose Gemma asks twice:

```text
find references TokenValidator
```

Wayang should recognize:

```text
same ObservationKey
```

and return the existing observation.

This is especially important during agent loops.

---

# 25. In-flight deduplication

There's another optimization people often miss.

Imagine two branches simultaneously ask:

```text
findReferences(TokenValidator)
```

before the first finishes.

Don't execute twice.

Use an in-flight map:

```java
ConcurrentHashMap<
    ObservationKey,
    Uni<ObservationResult>
>
```

Conceptually:

```text
request A ─┐
           ├── same execution
request B ─┘
```

Both receive the same result.

This can save a lot of work.

---

# 26. Observation lifecycle

Now we have:

```text
REQUESTED
   ↓
RUNNING
   ↓
COMPLETED
```

or:

```text
RUNNING
   ↓
FAILED
```

Define:

```java
public enum ObservationLifecycle {

    REQUESTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

---

# 27. Emit events

Wayang should emit events:

```java
public sealed interface ObservationEvent {}
```

Examples:

```java
record ObservationRequested(...) implements ObservationEvent {}
record ObservationStarted(...) implements ObservationEvent {}
record ObservationCompleted(...) implements ObservationEvent {}
record ObservationFailed(...) implements ObservationEvent {}
record ObservationCached(...) implements ObservationEvent {}
```

This is excellent for debugging.

---

# 28. Event stream

Then you get:

```text
TaskStarted
   ↓
ObservationRequested
   ↓
ObservationStarted
   ↓
ToolExecuted
   ↓
EvidenceCreated
   ↓
ContextUpdated
   ↓
InferenceCreated
```

Later you can inspect exactly what Aljabr did.

---

# 29. Don't make events equal to logs

Important distinction:

### Log

For humans/debugging:

```text
Running findReferences...
```

### Event

For runtime state:

```json
{
  "type": "ObservationCompleted",
  "observationId": "O42",
  "status": "SUCCESS"
}
```

Events should be structured and durable if necessary.

---

# 30. Evidence lifecycle

Now combine the pieces.

```text
             Observation
                  │
                  ▼
              Raw Result
                  │
                  ▼
             Normalization
                  │
                  ▼
              Validation
                  │
                  ▼
              Evidence
                  │
        ┌─────────┼──────────┐
        ↓         ↓          ↓
      Cache     Context     Store
        │         │          │
        │         ↓          ↓
        │       Gemma      Memory
        │
        ↓
    Reuse later
```

---

# 31. Evidence invalidation

Now implement the opposite direction.

When workspace changes:

```text
WorkspaceChanged
       ↓
EvidenceInvalidator
       ↓
find affected evidence
       ↓
mark STALE
       ↓
invalidate observation cache
```

---

# 32. File-based invalidation

Start very simply.

If:

```text
TokenValidator.java
```

changes:

invalidate evidence whose location references:

```text
TokenValidator.java
```

This is already highly useful.

---

# 33. Observation cache invalidation

Suppose:

```text
findReferences(TokenValidator)
```

was cached.

Then the file changes.

The cache entry must become invalid.

Don't wait until retrieval discovers the problem.

```text
file changed
 ↓
observation keys affected
 ↓
cache invalidation
```

---

# 34. Dependency-aware invalidation later

Later, your code graph can say:

```text
TokenValidator
   ↓
AuthService
   ↓
LoginController
```

Then certain derived evidence can be invalidated.

But don't overbuild this now.

---

# 35. Freshness policy

The ContextEngine can ask:

```java
public enum FreshnessRequirement {

    CURRENT,
    RECENT,
    ANY
}
```

Examples:

```text
current diff → CURRENT
architecture docs → RECENT
historical research → ANY
```

This is much better than a universal TTL.

---

# 36. Why TTL alone isn't enough

Suppose:

```text
TTL = 1 hour
```

Aljabr edits a file 10 seconds later.

The evidence is already stale.

So correctness requires:

```text
version invalidation
```

TTL is only a fallback.

---

# 37. Cache policy

I'd define:

```java
public record CachePolicy(
    boolean enabled,
    FreshnessRequirement freshness,
    Duration maxAge,
    boolean allowStaleFallback
) {}
```

Then:

```text
current workspace
→ version check

external research
→ TTL

historical knowledge
→ long-lived
```

---

# 38. Stale fallback

Sometimes stale information is still useful.

Example:

```text
current source unavailable
```

but an older architecture scan says:

```text
AuthService owns authentication flow.
```

The ContextEngine can include:

```text
STALE EVIDENCE
```

but explicitly label it.

Never silently treat stale evidence as current.

---

# 39. Context representation

The final context could contain:

```text
<evidence id="E41" status="verified">
...
</evidence>

<evidence id="E42" status="stale">
...
</evidence>
```

But for the local model, I wouldn't dump all metadata.

The renderer can reduce it to:

```text
[VERIFIED] TokenValidator.validate()
source: TokenValidator.java:81-104
...
```

---

# 40. Model-generated evidence

Now we need to control Gemma-generated claims.

Suppose model says:

```text
Clock is created inside AuthService.
```

Don't immediately write:

```text
Evidence
```

Instead:

```text
Inference
```

```java
public record Inference(
    InferenceId id,
    String claim,
    List<EvidenceId> supportingEvidence,
    Confidence confidence,
    Instant createdAt
) {}
```

---

# 41. InferenceStore

```java
public interface InferenceStore {

    Uni<InferenceId> save(Inference inference);

    Uni<Optional<Inference>> get(InferenceId id);

    Uni<List<Inference>> findByEvidence(
        EvidenceId evidenceId
    );
}
```

Now the model can reason without polluting factual storage.

---

# 42. Decision

Then:

```java
public record AgentDecision(
    DecisionId id,
    DecisionType type,
    String target,
    List<EvidenceId> evidence,
    List<InferenceId> inferences
) {}
```

For example:

```text
INSPECT AuthService.createValidator()
```

supported by:

```text
E41
I12
```

This creates traceability.

---

# 43. The full agent trace

Eventually Aljabr can represent:

```text
USER REQUEST
     │
     ▼
TASK T1
     │
     ▼
EVIDENCE E1
"TokenValidator exists"
     │
     ▼
EVIDENCE E2
"validate() checks expiry"
     │
     ▼
INFERENCE I1
"Clock source may be wrong"
     │
     ▼
DECISION D1
"Inspect AuthService"
     │
     ▼
OBSERVATION O4
find AuthService Clock
     │
     ▼
EVIDENCE E8
"Clock constructed here"
     │
     ▼
DECISION D2
"Modify dependency injection"
```

That's the beginning of a real agent runtime.

---

# 44. Now introduce `ObservationBudget`

For your M4/16GB use case, this is important.

Don't let the agent run unlimited observations.

```java
public record ObservationBudget(
    int maxCalls,
    Duration maxTime,
    long maxOutputBytes,
    int maxParallelism
) {}
```

Example local policy:

```text
maxCalls = 20
maxParallelism = 3
maxTime = 30s
```

Cloud policy might be:

```text
maxCalls = 100
maxParallelism = 10
```

Same architecture.

Different capability policy.

---

# 45. Tool cost

Eventually each tool should expose:

```java
public record ToolCost(
    Duration expectedLatency,
    long expectedMemory,
    int expectedTokens
) {}
```

Then the planner can choose:

```text
cheap structural search
```

before:

```text
expensive semantic search
```

This fits the **CapabilityDetector → ContextController → ContextEngine** architecture perfectly.

---

# 46. Example decision

Need:

```text
"Find code related to token expiration."
```

Possible tools:

```text
symbol search       cost 1
grep                cost 1
AST query           cost 2
embedding search    cost 5
LLM research        cost 20
```

Planner:

```text
symbol search
 ↓
grep
 ↓
AST
 ↓
embedding only if ambiguity remains
```

This isn't merely token optimization.

It's **agent computation optimization**.

---

# 47. Tool confidence

Tools can also advertise authority:

```java
public record ToolCapability(
    ToolId id,
    Set<ObservationType> produces,
    EvidenceAuthority authority
) {}
```

For example:

```text
filesystem read
→ authoritative for file content

compiler
→ authoritative for compilation

test runner
→ authoritative for test execution

LLM
→ not authoritative for repository facts
```

This prevents the model from "winning" against actual tooling.

---

# 48. This leads to a very useful rule

> **For repository facts, tools outrank model beliefs.**

Example:

Gemma:

```text
"method doesn't exist."
```

Symbol index:

```text
method exists at line 81.
```

Wayang should trust the symbol index.

Gemma's statement becomes:

```text
INVALIDATED INFERENCE
```

not a contradictory repository fact.

---

# 49. For Aljabr, authority hierarchy could be

```text
USER
  ↓
CURRENT WORKSPACE
  ↓
COMPILER / TEST RUNNER
  ↓
STATIC ANALYSIS
  ↓
PROJECT DOCUMENTATION
  ↓
EXTERNAL DOCUMENTATION
  ↓
MEMORY
  ↓
MODEL INFERENCE
```

I would **not hard-code this globally** yet.

Make it part of policy.

Different tasks may need different authority.

---

# 50. One more critical piece: Observation receipts

Every tool execution should produce a receipt:

```java
public record ObservationReceipt(
    ObservationId observationId,
    ToolId tool,
    ObservationStatus status,

    Instant startedAt,
    Instant completedAt,

    WorkspaceVersion workspace,
    String argumentsHash,

    List<EvidenceId> producedEvidence
) {}
```

This gives you an audit trail without retaining giant logs.

---

# 51. Why this is especially useful for local models

If Gemma produces a bad answer:

Instead of asking:

> "Why did the model hallucinate?"

you can inspect:

```text
Context:
E41
E42
E45

E41 was stale.
E42 was inferred.
E45 was verified.

Context budget:
8,400 tokens.

Decision:
I12.

Supporting evidence:
E41 + E42.
```

Then you can fix the runtime rather than blindly tweaking the prompt.

That is exactly the direction I'd take Aljabr.

---

# 52. Recommended package structure

I'd organize the code approximately like:

```text
wayang/
  context/
    ContextEngine
    ContextController
    ContextPolicy

  evidence/
    Evidence
    EvidenceId
    EvidenceKey
    EvidenceStore
    EvidenceQuery
    EvidenceNormalizer
    EvidenceValidator
    EvidenceInvalidator
    EvidenceLineage

  observation/
    ObservationRequest
    ObservationResult
    ObservationKey
    ObservationManager
    ObservationPlanner
    ObservationPlan
    ObservationCache
    ObservationReceipt

  inference/
    Inference
    InferenceStore

  decision/
    AgentDecision
    DecisionStore

  tools/
    WayangTool
    ToolRegistry
    ToolCapability
```

This keeps the concepts clean.

---

# 53. Implementation order

I would implement this milestone in this exact order:

### Step 1

```text
ObservationRequest
ObservationResult
ObservationManager
```

### Step 2

```text
ToolRegistry
ToolExecutor
```

### Step 3

```text
RawToolResult
EvidenceNormalizer
EvidenceStore
```

### Step 4

```text
ObservationCache
```

### Step 5

```text
workspace-aware cache keys
```

### Step 6

```text
workspace invalidation
```

### Step 7

```text
ObservationPlanner
parallel execution
```

### Step 8

```text
Inference
Decision
traceability
```

Don't start with the graph database.

SQLite + structured IDs + JSON metadata is enough.

---

# 54. The resulting Aljabr architecture

We're now getting something quite solid:

```text
                    ALJABR
                       │
                  User Objective
                       │
                       ▼
              ┌─────────────────┐
              │ Capability      │
              │ Detector        │
              └────────┬────────┘
                       ▼
              ┌─────────────────┐
              │ Context         │
              │ Controller      │
              └────────┬────────┘
                       ▼
              ┌─────────────────┐
              │ Context Engine  │
              └────────┬────────┘
                       │
               needs / evidence
                       │
                       ▼
              ┌─────────────────┐
              │ Observation     │
              │ Planner         │
              └────────┬────────┘
                       ▼
              ┌─────────────────┐
              │ Observation     │
              │ Manager         │
              └────────┬────────┘
                       │
              ┌────────┴────────┐
              ▼                 ▼
          Cache              ToolRegistry
                                │
                     ┌──────────┼──────────┐
                     ▼          ▼          ▼
                   Files       Git       Tests
                     │          │          │
                     └──────────┼──────────┘
                                ▼
                       Raw Tool Results
                                │
                                ▼
                       EvidenceNormalizer
                                │
                                ▼
                         EvidenceStore
                                │
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
                Context      Inference     Memory
                   │            │
                   └──────┬─────┘
                          ▼
                         Gemma
                          │
                          ▼
                       Decision
                          │
                          ▼
                         Tool
```

At this point, **Wayang is becoming the runtime and Aljabr is becoming an agent built on that runtime**, rather than Aljabr being a collection of prompts/tools.

---

## The next milestone I recommend

After this, I would **not jump straight into long-term Memory** yet.

There's one missing foundational layer:

### **`Agent State + Task State + Checkpoint/Recovery`**

Because now we have observations, evidence, inference, decisions, and tools—but we need somewhere to persist the **current execution state**.

That layer should handle:

```text
Task
  ↓
Phase
  ↓
Current objective
  ↓
Current plan
  ↓
Completed steps
  ↓
Pending steps
  ↓
Evidence references
  ↓
Decisions
  ↓
Tool executions
  ↓
Checkpoint
```

And importantly for your local Aljabr use case:

```text
Gemma inference connection lost
             ↓
          checkpoint
             ↓
       restart / retry
             ↓
restore task state
             ↓
reuse EvidenceStore
             ↓
continue from last safe point
```

That will make the system **interruptible, resumable, and crash-tolerant**, which I think should come *before* we build the more sophisticated Memory layer.


Yes. Next is the **Agent State + Task State + Checkpoint/Recovery** layer.

This is the piece that turns the previous architecture into a **resumable runtime** rather than a loop that only works while the process stays alive.

The core principle:

> **Evidence is what Aljabr knows; state is where Aljabr currently is.**

---

# Milestone — Agent State, Task State & Recovery

The runtime should be able to stop at almost any safe point:

```text
User task
   ↓
Plan
   ↓
Observation
   ↓
Evidence
   ↓
Decision
   ↓
Action
   ↓
checkpoint
```

Then:

```text
process dies
connection lost
Mac sleeps
model crashes
tool times out
user pauses agent
        ↓
restore checkpoint
        ↓
continue
```

And importantly:

**we do not restore the entire context as a giant string.**

We restore structured state and reconstruct context from EvidenceStore.

---

# 1. Three different kinds of state

Don't make one giant `AgentState`.

Separate:

```text
TaskState
AgentRuntimeState
ExecutionState
```

### TaskState

What the agent is trying to accomplish.

### AgentRuntimeState

What the agent currently believes/has decided.

### ExecutionState

What the runtime is currently doing.

---

# 2. TaskState

Start with:

```java
public record TaskState(
    TaskId id,
    String objective,

    TaskStatus status,
    TaskPhase phase,

    WorkspaceVersion workspace,

    PlanId activePlan,

    Instant createdAt,
    Instant updatedAt
) {}
```

---

# 3. TaskStatus

```java
public enum TaskStatus {

    CREATED,
    RUNNING,
    WAITING,
    BLOCKED,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

Don't overload these.

For example:

```text
WAITING
```

means:

> the agent is waiting for something.

Whereas:

```text
PAUSED
```

means:

> execution was intentionally suspended.

---

# 4. TaskPhase

For Aljabr, I'd start with:

```java
public enum TaskPhase {

    UNDERSTAND,
    INSPECT,
    PLAN,
    MODIFY,
    TEST,
    VERIFY,
    REVIEW,
    COMPLETE
}
```

Later tasks may skip phases.

For example:

```text
simple bug:
UNDERSTAND → INSPECT → MODIFY → TEST → COMPLETE
```

---

# 5. Don't force a waterfall

This is important.

The phase is a **state classification**, not a rigid workflow.

The agent can go:

```text
INSPECT
 ↓
PLAN
 ↓
MODIFY
 ↓
TEST
 ↓
INSPECT
 ↓
MODIFY
 ↓
TEST
```

That is normal.

---

# 6. AgentRuntimeState

```java
public record AgentRuntimeState(
    TaskId taskId,

    Optional<PlanId> activePlan,
    Optional<DecisionId> lastDecision,

    Set<EvidenceId> relevantEvidence,
    Set<InferenceId> activeInferences,

    Optional<ObservationId> activeObservation,

    AgentProgress progress
) {}
```

This represents the **current working state**.

---

# 7. AgentProgress

Don't store only percentage.

Use explicit progress:

```java
public record AgentProgress(
    Set<String> completedSteps,
    Set<String> pendingSteps,
    Set<String> blockedSteps
) {}
```

Example:

```text
completed:
- locate validator
- inspect expiration logic

pending:
- inspect Clock construction
- run expired token test

blocked:
- none
```

This is much more useful than:

```text
progress = 67%
```

---

# 8. Plan

The previous ObservationPlan was specifically about observations.

Now we need an **AgentPlan**.

```java
public record AgentPlan(
    PlanId id,
    TaskId taskId,

    List<PlanStep> steps,

    int version,
    PlanStatus status
) {}
```

---

# 9. PlanStep

```java
public record PlanStep(
    String id,
    String objective,

    PlanStepType type,

    Set<String> dependencies,

    PlanStepStatus status,

    List<EvidenceId> evidence,

    Optional<DecisionId> decision
) {}
```

---

# 10. PlanStepType

```java
public enum PlanStepType {

    INSPECT,
    SEARCH,
    MODIFY,
    TEST,
    BUILD,
    VERIFY,
    RESEARCH,
    REVIEW
}
```

Now Aljabr has a concrete execution graph.

---

# 11. Example

Task:

> Fix expired tokens being accepted.

Plan:

```text
P1  inspect TokenValidator
P2  inspect expiration logic
P3  inspect Clock construction
P4  inspect tests
P5  modify implementation
P6  run tests
P7  verify behavior
```

Dependencies:

```text
P1
 ├── P2
 ├── P3
 └── P4

P2 + P3 + P4
       ↓
      P5
       ↓
      P6
       ↓
      P7
```

---

# 12. Plan should be mutable

This is a coding agent.

New evidence can invalidate the plan.

For example:

```text
old:
P5 modify TokenValidator

new evidence:
bug is actually in AuthService

new plan:
P5 modify AuthService
```

Don't force the agent to continue executing a stale plan.

---

# 13. Plan versioning

Every modification gets:

```text
version 1
version 2
version 3
```

Example:

```java
public record PlanRevision(
    PlanId planId,
    int version,
    List<PlanStep> steps,
    String reason,
    Instant createdAt
) {}
```

This is valuable for debugging.

---

# 14. Decision state

Every important model decision should become structured state.

```java
public record AgentDecision(
    DecisionId id,

    DecisionType type,

    String objective,

    Optional<String> target,

    List<EvidenceId> supportingEvidence,

    List<InferenceId> supportingInferences,

    Instant createdAt
) {}
```

For example:

```text
decision:
INSPECT

target:
AuthService.createValidator

reason:
Need to determine Clock source

evidence:
E41
E43
```

---

# 15. Decision is not action

This distinction is important.

```text
Decision:
"Modify TokenValidator"

Action:
"apply patch X"
```

Keep them separate.

Why?

Because a decision can be reconsidered before execution.

---

# 16. Action

```java
public record AgentAction(
    ActionId id,

    ActionType type,

    Optional<ToolId> tool,

    Map<String, Object> parameters,

    Optional<DecisionId> decision,

    ActionStatus status,

    Instant startedAt,
    Optional<Instant> completedAt
) {}
```

---

# 17. Action types

```java
public enum ActionType {

    READ,
    SEARCH,
    MODIFY,
    TEST,
    BUILD,
    GIT,
    RESEARCH
}
```

---

# 18. Execution state

Now the runtime-specific part:

```java
public record ExecutionState(
    ExecutionId id,

    TaskId taskId,

    ExecutionStatus status,

    Optional<PlanStepId> currentStep,

    Optional<ActionId> currentAction,

    Optional<ObservationId> currentObservation,

    Instant startedAt,
    Instant updatedAt
) {}
```

---

# 19. ExecutionStatus

```java
public enum ExecutionStatus {

    IDLE,
    RUNNING,
    WAITING,
    INTERRUPTED,
    RECOVERING,
    COMPLETED,
    FAILED
}
```

---

# 20. Why three layers?

Because these are different questions.

### Task

> What are we trying to accomplish?

### Agent

> What does the agent currently intend/believe?

### Execution

> What is the runtime physically doing?

This separation prevents a huge amount of state confusion.

---

# 21. StateStore

Now we need persistence.

```java
public interface TaskStateStore {

    Uni<TaskState> create(TaskState task);

    Uni<Optional<TaskState>> get(TaskId id);

    Uni<TaskState> update(TaskState state);
}
```

Then:

```java
public interface AgentStateStore {

    Uni<AgentRuntimeState> get(TaskId id);

    Uni<Void> save(AgentRuntimeState state);
}
```

And:

```java
public interface ExecutionStateStore {

    Uni<ExecutionState> get(ExecutionId id);

    Uni<Void> save(ExecutionState state);
}
```

---

# 22. But I wouldn't have three physical databases

Conceptually separate:

```text
TaskState
AgentState
ExecutionState
```

but initially store them in the same SQLite database.

For example:

```text
tasks
agent_state
executions
plans
plan_revisions
actions
checkpoints
```

Simple.

---

# 23. Checkpoint

Now the key object.

```java
public record Checkpoint(
    CheckpointId id,

    TaskId taskId,

    ExecutionId executionId,

    TaskState taskState,
    AgentRuntimeState agentState,
    ExecutionState executionState,

    PlanId planId,

    WorkspaceVersion workspace,

    Instant createdAt,

    CheckpointReason reason
) {}
```

---

# 24. CheckpointReason

```java
public enum CheckpointReason {

    TASK_STARTED,
    PLAN_CREATED,

    BEFORE_ACTION,
    AFTER_ACTION,

    AFTER_OBSERVATION,
    AFTER_DECISION,

    BEFORE_MODEL_CALL,
    AFTER_MODEL_CALL,

    ERROR,
    INTERRUPTED,
    PAUSED,

    PERIODIC
}
```

---

# 25. Do not checkpoint only every N seconds

That is insufficient.

Some transitions are semantically important.

For example:

```text
model decides to modify code
```

should checkpoint before actually modifying the workspace.

---

# 26. Safe checkpoint boundaries

I'd make these mandatory:

```text
Task created
Plan committed
Before destructive/modifying action
After modification
After test result
Before model inference
After model inference
After recovery
```

This gives strong recovery semantics.

---

# 27. CheckpointManager

```java
public interface CheckpointManager {

    Uni<Checkpoint> create(
        TaskId taskId,
        CheckpointReason reason
    );

    Uni<Optional<Checkpoint>> latest(
        TaskId taskId
    );

    Uni<Void> restore(
        CheckpointId checkpoint
    );
}
```

---

# 28. Critical design choice

A checkpoint should **not contain giant evidence blobs**.

Store references:

```text
Checkpoint
  ├── task state
  ├── plan
  ├── decisions
  ├── evidence IDs
  └── workspace version
```

Then retrieve evidence from:

```text
EvidenceStore
```

This keeps checkpoints tiny.

---

# 29. Example checkpoint

```json
{
  "task": "T42",
  "phase": "MODIFY",
  "plan": "P7",
  "planVersion": 3,
  "currentStep": "P5",
  "workspace": {
    "baseRevision": "abc123",
    "workingTreeHash": "9ef42"
  },
  "evidence": [
    "E41",
    "E42",
    "E43",
    "E51"
  ],
  "lastDecision": "D12"
}
```

That's enough to reconstruct the working context.

---

# 30. Recovery

When Aljabr starts:

```text
Runtime boot
   ↓
find RUNNING tasks
   ↓
find latest checkpoint
   ↓
verify workspace
   ↓
restore state
   ↓
validate evidence
   ↓
resume
```

---

# 31. Workspace validation during recovery

This is critical.

Suppose checkpoint says:

```text
workingTreeHash = ABC
```

but current workspace is:

```text
workingTreeHash = XYZ
```

Don't blindly resume.

State becomes:

```text
RECOVERY_CONFLICT
```

Then Aljabr must inspect what changed.

---

# 32. Recovery states

Add:

```java
public enum RecoveryStatus {

    NOT_REQUIRED,
    RECOVERING,
    READY,
    WORKSPACE_CHANGED,
    CHECKPOINT_INVALID,
    FAILED
}
```

---

# 33. Recovery strategy

If workspace differs:

```text
checkpoint
     ↓
compare workspace
     ↓
same?
 ┌───┴───┐
yes      no
 ↓        ↓
resume   inspect changes
```

If changes are only expected changes from the previous action:

```text
resume
```

If unexpected:

```text
pause
```

and ask/reason about the discrepancy.

---

# 34. Expected workspace transition

This is a very useful addition.

Before modifying:

```text
workspace = A
```

Action:

```text
modify TokenValidator
```

Expected resulting workspace:

```text
workspace = B
```

Store:

```java
public record ExpectedWorkspaceChange(
    WorkspaceVersion before,
    Set<String> expectedFiles,
    Optional<String> expectedDiffHash
) {}
```

Then recovery can distinguish:

```text
expected change
```

from:

```text
unexpected external change
```

---

# 35. Idempotency

Now we need another critical property.

Suppose Aljabr executes:

```text
modify file
```

and crashes immediately afterward.

On recovery, how does it know whether modification happened?

Never blindly repeat.

Use an `ActionReceipt`.

```java
public record ActionReceipt(
    ActionId actionId,

    ActionStatus status,

    Optional<String> resultHash,

    WorkspaceVersion workspaceAfter,

    Instant completedAt
) {}
```

---

# 36. Action execution protocol

Before action:

```text
checkpoint
 ↓
execute action
 ↓
record receipt
 ↓
checkpoint
```

So:

```text
BEFORE_ACTION
    ↓
ACTION
    ↓
RECEIPT
    ↓
AFTER_ACTION
```

If crash occurs between action and receipt:

recovery checks the workspace.

---

# 37. This is essentially transaction semantics

Not a full database transaction.

But:

```text
intent
→ execute
→ observe result
→ commit state
```

For coding agents, that's enormously valuable.

---

# 38. Tool actions should declare idempotency

```java
public enum Idempotency {

    SAFE,
    CONDITIONAL,
    UNSAFE
}
```

Examples:

```text
read file       SAFE
search          SAFE
run tests       SAFE-ish
apply patch     CONDITIONAL
git commit      UNSAFE
delete file     UNSAFE
```

Then recovery knows whether automatic retry is acceptable.

---

# 39. Retry policy

```java
public record RetryPolicy(
    int maxAttempts,
    Duration backoff,
    boolean retryOnTimeout,
    boolean retryOnConnectionFailure,
    boolean retryOnToolFailure
) {}
```

For local Aljabr:

```text
model connection lost
→ retry

read file failed
→ retry

patch failed
→ inspect before retry

git commit failed
→ never blindly repeat
```

---

# 40. Model inference checkpoint

This deserves special treatment.

Before calling Gemma:

```text
checkpoint:
BEFORE_MODEL_CALL
```

Store:

```text
task
plan
context manifest
evidence IDs
workspace version
```

Then call:

```text
Gemma
```

If connection dies:

```text
recover checkpoint
```

and reconstruct exactly the same logical context.

---

# 41. Don't necessarily cache model output forever

Instead store:

```java
public record ModelInvocation(
    ModelInvocationId id,

    ModelProfile model,

    String contextHash,

    String promptHash,

    InferenceStatus status,

    Optional<String> response,

    TokenUsage usage,

    Instant createdAt
) {}
```

This gives you reproducibility.

---

# 42. Context hash

This is particularly useful.

If:

```text
contextHash = X
```

and model output:

```text
responseHash = Y
```

then you can know:

> This decision came from exactly this logical context.

Very useful when debugging local-model behavior.

---

# 43. Prompt hash vs Context hash

Keep them separate.

### Context hash

Hash of structured context.

### Prompt hash

Hash of rendered prompt.

Why?

Because:

```text
same context
```

could be rendered differently for:

```text
Gemma
Claude
GPT
```

So:

```text
Context
  ↓
Renderer
  ↓
Prompt
```

and hash both.

---

# 44. Model invocation lifecycle

```text
PREPARE
  ↓
CHECKPOINT
  ↓
RENDER
  ↓
INFER
  ↓
VALIDATE OUTPUT
  ↓
STORE DECISION
  ↓
CHECKPOINT
```

Never let raw model output directly mutate state.

---

# 45. Output validation

Gemma returns:

```json
{
  "action": "MODIFY",
  "target": "TokenValidator.java"
}
```

Wayang validates:

```text
Is MODIFY allowed?
Does target exist?
Does plan allow this?
Does tool exist?
Are required evidence references valid?
```

Only then:

```text
Decision
```

---

# 46. This creates a state machine

At this point, Aljabr can have a formal state machine:

```text
CREATED
   ↓
UNDERSTAND
   ↓
INSPECT
   ↓
PLAN
   ↓
MODIFY
   ↓
TEST
   ↓
VERIFY
   ├──────────┐
   │          │
   ↓          │
COMPLETE     FAIL
              │
              ▼
           REPLAN
              │
              └──→ INSPECT
```

But the state machine should remain flexible.

---

# 47. Runtime transition API

Don't let random code mutate states.

Create:

```java
public interface AgentStateMachine {

    Uni<AgentTransitionResult> transition(
        AgentEvent event
    );
}
```

For example:

```text
ObservationCompleted
ActionCompleted
TestFailed
ModelDecisionReceived
WorkspaceChanged
UserPaused
```

---

# 48. Why this matters

Instead of:

```java
state.phase = MODIFY;
```

everywhere, you get:

```java
stateMachine.transition(
    new DecisionAccepted(...)
);
```

Then the runtime controls:

```text
phase
status
plan
checkpoint
events
```

consistently.

---

# 49. State transition example

```java
public Uni<AgentTransitionResult> handle(
    DecisionAccepted event
) {

    // validate decision

    // update agent state

    // update task phase

    // create checkpoint

    // emit event

    return ...;
}
```

This becomes one of the most important architectural boundaries in Wayang.

---

# 50. Event sourcing?

I'd be careful.

You don't need full event sourcing yet.

Use:

```text
current state
+
structured events
+
checkpoints
```

rather than forcing every state reconstruction to replay thousands of events.

Later, event history can become an audit trail.

---

# 51. SQLite schema

A practical initial schema:

```sql
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,
    objective TEXT NOT NULL,
    status TEXT NOT NULL,
    phase TEXT NOT NULL,
    workspace_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE executions (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    status TEXT NOT NULL,
    current_step TEXT,
    current_action TEXT,
    current_observation TEXT,
    started_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE plans (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE checkpoints (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    execution_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    state_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
```

Keep it boring.

Boring is good here.

---

# 52. Checkpoint retention

Don't keep infinite checkpoints.

Use:

```text
TASK_START
PLAN
BEFORE_ACTION
AFTER_ACTION
ERROR
LATEST
```

You can compact older ones.

For local storage:

```text
latest 10
+
important milestones
```

is probably enough initially.

---

# 53. Recovery algorithm

Actual pseudocode:

```java
Uni<RecoveryResult> recover(TaskId taskId) {

    return checkpointStore.latest(taskId)
        .flatMap(checkpoint -> {

            if (checkpoint.isEmpty()) {
                return failed("No checkpoint");
            }

            var cp = checkpoint.get();

            return workspace.inspect()
                .flatMap(current ->
                    compare(cp.workspace(), current)
                )
                .flatMap(result -> {

                    if (result.isCompatible()) {
                        return restore(cp);
                    }

                    return handleWorkspaceConflict(
                        cp,
                        result
                    );
                });
        });
}
```

---

# 54. Recovery should validate evidence too

Even if workspace matches:

```text
checkpoint
 ↓
evidence IDs
 ↓
EvidenceStore
```

Some evidence might have been invalidated independently.

So:

```text
checkpoint evidence
     ↓
freshness validation
     ↓
rebuild context
```

---

# 55. Never serialize final prompt into checkpoint as the source of truth

You can optionally cache it.

But checkpoint should contain:

```text
task
state
plan
evidence IDs
decisions
workspace
```

not:

```text
12,000-token prompt
```

Because the prompt is a derived artifact.

---

# 56. This gives us graceful degradation

Suppose Gemma isn't available.

Wayang can still:

```text
restore task
inspect workspace
load evidence
show plan
wait
```

Then when the model becomes available:

```text
resume inference
```

This is a very good property for local inference.

---

# 57. Local-first recovery example

Your Mac:

```text
Aljabr
Gemma GGUF
M4 16GB
```

Task:

```text
Fix authentication bug
```

Aljabr has already:

```text
E1 code
E2 tests
E3 callers
E4 diff
D1 inspect AuthService
```

Gemma crashes.

Restart Aljabr.

It does:

```text
SQLite
 ↓
Task T42
 ↓
Checkpoint C91
 ↓
Evidence E1-E4
 ↓
Decision D1
 ↓
ContextEngine
 ↓
reconstruct compact context
 ↓
Gemma
```

No repository rescan.

No repeated research.

No giant history replay.

That's exactly the behavior we want.

---

# 58. One more useful concept: `TaskSnapshot`

For UI/debugging:

```java
public record TaskSnapshot(
    TaskState task,
    AgentRuntimeState agent,
    ExecutionState execution,
    AgentPlan plan,
    List<EvidenceSummary> evidence,
    Optional<AgentDecision> lastDecision
) {}
```

This is a read-only view.

The UI can display:

```text
Task: Fix token expiration

Phase: MODIFY

Current step:
Inspect AuthService

Evidence:
12 items

Last decision:
Inspect Clock construction

Workspace:
clean except expected changes

Status:
RUNNING
```

No need to expose internal database structures.

---

# 59. The important invariant

I'd write this directly into the Wayang architecture documentation:

> **Every meaningful agent transition must be reconstructible from durable state plus references to durable evidence.**

That is the core recovery invariant.

And another:

> **Derived context must never be the sole source of truth for agent state.**

---

# 60. Where we are now

The architecture is becoming:

```text
CapabilityDetector
        ↓
ContextController
        ↓
ContextEngine
        ↓
EvidenceStore
        ↑
ObservationManager
        ↑
ToolExecutor
        ↓
Evidence
        ↓
Inference
        ↓
Decision
        ↓
Action
        ↓
Checkpoint
        ↓
StateMachine
        ↓
Recovery
```

And now we have a very clean separation:

```text
Evidence
= what happened / was observed

Inference
= what the model concluded

Decision
= what the agent intends to do

Action
= what the runtime executes

State
= where the task currently is

Checkpoint
= durable recovery point

Context
= temporary model-facing projection
```

That distinction is worth protecting.

---

# Next milestone

I would now move to **Memory**, but with a very different design than a conventional "chat memory."

Because we now have the foundation to make Memory almost trivial conceptually:

```text
Evidence
   ↓
Promotion
   ↓
Memory
```

and:

```text
Task memory
Workspace memory
Project memory
User/project rules
Long-term knowledge
```

become **scopes + retention + promotion policies**, not five unrelated memory systems.

The next milestone should therefore be:

**`MemoryManager → MemoryScope → MemoryPolicy → Promotion/Demotion → Forget/Invalidate`**

with special attention to your earlier requirement:

> if the user says "forget this knowledge", Aljabr must be able to remove or demote the memory without necessarily deleting the underlying historical evidence.


Absolutely. Now we can build the **Memory layer** on top of the state/evidence architecture.

The important architectural decision is:

> **Memory should not be another copy of context. Memory is a managed projection of durable knowledge.**

That distinction will keep Aljabr small-model friendly.

# Milestone — MemoryManager

The architecture becomes:

```text
Observation
    ↓
Evidence
    ↓
Inference
    ↓
Promotion Policy
    ↓
Memory
    ↓
Future Task
    ↓
Memory Retrieval
    ↓
ContextEngine
```

And the reverse:

```text
User: "forget X"
       ↓
MemoryManager
       ↓
demote / invalidate / delete
       ↓
future retrieval excludes X
```

---

# 1. First: don't create "one memory"

Use scopes.

```java
public enum MemoryScope {

    TURN,
    TASK,
    SESSION,
    WORKSPACE,
    PROJECT,
    USER
}
```

For Aljabr, the most important are:

```text
TASK
WORKSPACE
PROJECT
USER
```

---

# 2. What each scope means

### TASK

Only useful for the current task.

Example:

```text
"We decided to modify AuthService."
```

When task ends, normally disappears from active memory.

---

### SESSION

Useful during the current Aljabr session.

Example:

```text
"The user prefers this implementation approach."
```

---

### WORKSPACE

Facts about the current repository/worktree.

Example:

```text
"Authentication is implemented in auth/core."
```

---

### PROJECT

Long-lived project knowledge.

Example:

```text
"Wayang uses SQLite for local runtime persistence."
```

---

### USER

User-level preferences/instructions.

Example:

```text
"Prefer concise implementation plans."
```

This should be handled carefully and separately from repository facts.

---

# 3. Memory is not evidence

This is one of the most important distinctions.

Suppose Aljabr observes:

```text
TokenValidator.java:81
```

That is:

```text
Evidence
```

Then it concludes:

```text
"Token validation is centralized in TokenValidator."
```

That's:

```text
Inference
```

If that conclusion is promoted because it is repeatedly useful:

```text
Memory
```

So:

```text
Evidence
= observation

Inference
= reasoning

Memory
= reusable knowledge
```

---

# 4. Memory object

Start simple:

```java
public record Memory(
    MemoryId id,

    MemoryScope scope,

    String content,

    MemoryType type,

    MemoryStatus status,

    MemorySource source,

    MemoryConfidence confidence,

    Instant createdAt,
    Instant updatedAt
) {}
```

---

# 5. MemoryType

```java
public enum MemoryType {

    FACT,
    RULE,
    PREFERENCE,
    DECISION,
    CONVENTION,
    SUMMARY
}
```

Examples:

```text
FACT:
"AuthService creates TokenValidator."

RULE:
"Do not modify generated files."

PREFERENCE:
"User prefers minimal dependencies."

CONVENTION:
"Tests use JUnit 5."

SUMMARY:
"Authentication flow consists of..."
```

---

# 6. MemorySource

Don't let the model create anonymous memories.

```java
public record MemorySource(
    SourceType type,
    List<EvidenceId> evidence,
    List<InferenceId> inferences,
    Optional<TaskId> task
) {}
```

Then:

```java
public enum SourceType {

    USER,
    TOOL,
    MODEL,
    DERIVED
}
```

This provides lineage.

---

# 7. Memory confidence

Don't just store:

```text
confidence = 0.87
```

That number is often meaningless.

Use semantic categories first:

```java
public enum MemoryConfidence {

    VERIFIED,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
```

Later you can attach numeric scores internally.

---

# 8. MemoryStatus

```java
public enum MemoryStatus {

    ACTIVE,
    STALE,
    SUPERSEDED,
    INVALIDATED,
    FORGOTTEN,
    ARCHIVED
}
```

This is critical because **forgetting does not necessarily mean physically deleting the record**.

---

# 9. MemoryManager

Main API:

```java
public interface MemoryManager {

    Uni<List<Memory>> retrieve(
        MemoryQuery query
    );

    Uni<MemoryId> remember(
        MemoryCandidate candidate
    );

    Uni<Void> forget(
        MemorySelector selector
    );

    Uni<Void> invalidate(
        MemorySelector selector
    );

    Uni<Void> promote(
        MemoryId id
    );

    Uni<Void> demote(
        MemoryId id
    );
}
```

---

# 10. MemoryCandidate

Don't directly create `Memory`.

Use:

```java
public record MemoryCandidate(
    MemoryScope scope,

    String content,

    MemoryType type,

    MemorySource source,

    MemoryConfidence confidence,

    MemoryPolicy policy
) {}
```

Why?

Because promotion should be policy-controlled.

---

# 11. MemoryPolicy

This is where Wayang gets smart.

```java
public record MemoryPolicy(
    boolean allowPromotion,
    boolean requireEvidence,
    int minEvidenceCount,

    Duration retention,

    boolean autoRefresh,
    boolean autoInvalidate
) {}
```

---

# 12. Example policy

For repository facts:

```text
scope = PROJECT
requireEvidence = true
minEvidenceCount = 1
autoInvalidate = true
```

For a model guess:

```text
allowPromotion = false
```

For user preference:

```text
scope = USER
source = USER
```

No repository evidence required.

---

# 13. Promotion

Don't let every thought become memory.

Bad:

```text
Gemma thinks X
 ↓
Memory
```

Better:

```text
Inference
 ↓
PromotionPolicy
 ↓
is useful?
is stable?
is supported?
 ↓
Memory
```

---

# 14. Promotion rules for Aljabr

I'd start with:

### Promote automatically if:

```text
USER explicitly states it
```

or:

```text
tool-verified fact
+
likely useful beyond current task
```

or:

```text
project convention
+
repeatedly observed
```

---

# 15. Don't promote temporary reasoning

Example:

```text
"Maybe AuthService is causing this."
```

Do **not** create project memory.

That's an inference.

Likewise:

```text
"I should inspect Clock next."
```

is task state.

Not memory.

---

# 16. Promotion score

Later:

```java
public record PromotionScore(
    double utility,
    double stability,
    double confidence,
    double scopeFit
) {}
```

Combined:

```text
promotionScore =
    utility
  × stability
  × confidence
  × scopeFit
```

Then:

```text
score > threshold
→ promote
```

But for your small local model, **don't ask Gemma to calculate this**.

Make it deterministic in Wayang.

---

# 17. Memory retrieval

This is where local inference optimization matters.

Don't do:

```text
retrieve entire memory database
 ↓
put everything into Gemma
```

Instead:

```text
Task
 ↓
MemoryQuery
 ↓
candidate retrieval
 ↓
filter
 ↓
rank
 ↓
budget
 ↓
ContextEngine
```

---

# 18. MemoryQuery

```java
public record MemoryQuery(

    String query,

    Set<MemoryScope> scopes,

    Optional<String> workspace,

    Optional<String> project,

    int maxResults,

    MemoryFreshness freshness
) {}
```

---

# 19. Retrieval should be layered

For Aljabr, I'd use:

```text
Layer 1
exact / metadata match

Layer 2
keyword / lexical search

Layer 3
embedding search

Layer 4
model-assisted reranking
```

And only go deeper if necessary.

This is **very important for a 12B local model**.

---

# 20. Don't use embeddings for everything

If the task says:

```text
"TokenValidator"
```

first do:

```text
exact symbol / lexical search
```

before asking an embedding model.

Likewise:

```text
project = wayang
scope = PROJECT
type = CONVENTION
```

can eliminate most memory entries without semantic search.

---

# 21. Memory ranking

Simple initial ranking:

```java
score =
    scopeScore
  + freshnessScore
  + confidenceScore
  + lexicalScore
  + sourceAuthorityScore;
```

You don't need a neural reranker initially.

---

# 22. Example

Memory database:

```text
M1:
"Wayang uses SQLite."
PROJECT / VERIFIED

M2:
"TokenValidator checks expiry."
WORKSPACE / VERIFIED

M3:
"Maybe expiry is timezone-related."
TASK / LOW

M4:
"User prefers small changes."
USER / VERIFIED
```

Task:

```text
"Fix token expiry bug."
```

Retrieval should probably produce:

```text
M2
M4
M1
```

and exclude:

```text
M3
```

unless explicitly useful.

---

# 23. ContextController decides what actually enters Gemma

This distinction matters.

MemoryManager says:

> Here are the useful candidates.

ContextController says:

> These two fit the current token budget.

So:

```text
MemoryManager
      ↓
candidate memories
      ↓
ContextController
      ↓
selected memories
      ↓
ContextEngine
      ↓
Gemma
```

This keeps responsibilities clean.

---

# 24. Memory compression

Long memories are dangerous for your local model.

Instead of:

```text
20 historical conversations
```

store:

```text
"Authentication is centralized in AuthService and TokenValidator."
```

This is a **memory representation**, not a transcript.

---

# 25. But don't destroy provenance

Memory:

```text
"Authentication is centralized in AuthService."
```

should still reference:

```text
E41
E52
E81
```

So you can later verify it.

---

# 26. Memory lineage

```java
public record MemoryLineage(
    MemoryId memory,
    List<EvidenceId> evidence,
    List<InferenceId> inferences,
    Optional<MemoryId> supersedes
) {}
```

Now you can answer:

> Why does Aljabr believe this?

with:

```text
Memory
 ↓
Evidence
 ↓
file / test / tool
```

Excellent for debugging.

---

# 27. Supersession

Suppose memory says:

```text
M1:
"AuthService owns token validation."
```

Later architecture changes.

New memory:

```text
M2:
"TokenValidator now owns validation."
```

Don't simply delete M1.

Mark:

```text
M1 → SUPERSEDED by M2
```

Then active retrieval returns M2.

---

# 28. Invalidation

Suppose:

```text
TokenValidator.java
```

changes.

Evidence E41 becomes stale.

Then:

```text
E41
 ↓
Memory M12
 ↓
STALE
```

This can happen automatically.

---

# 29. Memory dependency

Represent it:

```java
public record MemoryDependency(
    MemoryId memory,
    Set<EvidenceId> evidence,
    Set<MemoryId> dependencies
) {}
```

Then invalidation becomes graph traversal.

But again:

**don't implement a graph database yet.**

SQLite tables are enough.

---

# 30. Forgetting

Now the user says:

> Forget that authentication is centralized in AuthService.

The API:

```java
memoryManager.forget(
    MemorySelector.byContent(...)
);
```

But there is an important distinction.

---

# 31. User forget should affect retrieval

The minimum guarantee:

```text
FORGOTTEN
```

means:

```text
MemoryManager.retrieve()
```

must never return it.

---

# 32. Should evidence be deleted?

Usually:

**No.**

Example:

```text
Evidence:
AuthService.java contains validation
```

is an observation of the repository.

User forgetting a memory shouldn't necessarily erase repository facts.

So:

```text
Memory M12
   ↓
FORGOTTEN

Evidence E41
   ↓
still exists
```

unless the user explicitly asks to remove the underlying record.

---

# 33. Why this distinction is powerful

It means:

```text
forget knowledge
```

doesn't destroy:

```text
audit history
```

and:

```text
forget memory
```

doesn't necessarily require:

```text
rescan repository
```

The runtime simply stops promoting/retrieving that memory.

---

# 34. Forgetting should create a tombstone

I recommend:

```java
public record MemoryTombstone(
    MemoryId memoryId,

    String reason,

    Instant createdAt,

    Optional<String> replacement
) {}
```

Why?

Suppose you forget:

```text
M12
```

and later a promotion algorithm sees the same evidence and recreates it.

The tombstone can prevent that.

---

# 35. Forget policy

```java
public record ForgetPolicy(
    boolean preventRecreation,
    boolean deleteContent,
    boolean retainLineage
) {}
```

For explicit user forget:

```text
preventRecreation = true
deleteContent = true/according to privacy policy
retainLineage = metadata only
```

---

# 36. Forget scopes

The user might say:

> Forget this for this task.

or:

> Forget this project knowledge.

or:

> Forget this preference.

So:

```java
public record MemorySelector(
    Optional<MemoryId> id,
    Optional<MemoryScope> scope,
    Optional<String> query,
    Optional<String> project
) {}
```

---

# 37. Example

### "Forget this for this task"

```text
M12
TASK
FORGOTTEN
```

Project memory remains.

---

### "Forget this project knowledge"

```text
M12
PROJECT
FORGOTTEN
```

Future tasks won't retrieve it.

---

### "Forget my preference"

```text
M21
USER
FORGOTTEN
```

This is separate from repository knowledge.

---

# 38. Demotion

Sometimes the user doesn't want deletion.

Example:

> Don't rely on that anymore unless verified.

That's:

```text
DEMOTE
```

not:

```text
FORGET
```

So:

```java
memoryManager.demote(id);
```

might change:

```text
VERIFIED
```

to:

```text
LOW
```

or require fresh evidence.

---

# 39. Memory refresh

For workspace memory:

```text
M12:
"TokenValidator handles expiration."
```

Later task:

```text
inspect repository
```

If current evidence confirms it:

```text
refresh M12
```

Update:

```text
updatedAt
evidence
workspaceVersion
confidence
```

---

# 40. Workspace-version-aware memory

This is particularly useful.

```java
public record MemoryValidity(
    Optional<WorkspaceVersion> observedAt,
    Optional<WorkspaceVersion> validUntil
) {}
```

For example:

```text
M12
observed at commit abc123
```

If repository moves to:

```text
def456
```

memory becomes:

```text
STALE
```

unless refreshed.

---

# 41. But don't invalidate everything after every commit

That would destroy the benefit.

Use dependency-based invalidation:

```text
M12
depends on:
AuthService.java
TokenValidator.java
```

If unrelated:

```text
README.md
```

changes:

```text
M12 remains valid.
```

---

# 42. Promotion from evidence

A practical algorithm:

```java
public Uni<Optional<MemoryId>> considerPromotion(
    Evidence evidence
) {
    if (!policy.allows(evidence)) {
        return Uni.createFrom().optional(Optional.empty());
    }

    var candidate = summarizer.summarize(evidence);

    if (!policy.useful(candidate)) {
        return Uni.createFrom().optional(Optional.empty());
    }

    return memoryStore.save(candidate);
}
```

But there is an important optimization:

### Don't use Gemma for every promotion.

For example:

```text
tool:
"JUnit 5 test framework"
```

can be stored structurally.

Only invoke the model when semantic summarization is actually necessary.

---

# 43. Two memory creation paths

This is ideal for local inference.

### Deterministic

```text
tool
 ↓
structured fact
 ↓
memory
```

### Semantic

```text
evidence
 ↓
Gemma summarization
 ↓
memory
```

Choose deterministic whenever possible.

---

# 44. Memory extraction should be asynchronous

Don't block the coding loop every time.

After successful task execution:

```text
Task complete
      ↓
MemoryPromotionQueue
      ↓
background processing
      ↓
candidate memories
      ↓
policy
      ↓
store
```

This keeps Aljabr responsive.

---

# 45. Memory budget

Even memory retrieval should have a budget.

```java
public record MemoryBudget(
    int maxItems,
    int maxTokens,
    long maxBytes
) {}
```

For Gemma on 16GB:

```text
small memory budget
```

For cloud:

```text
larger memory budget
```

Same mechanism, different policy.

---

# 46. Memory compaction

Suppose:

```text
M1:
Tests use JUnit 5.

M2:
Authentication tests use JUnit 5.

M3:
Token tests use JUnit 5.

M4:
JUnit 5 is standard in project.
```

Instead of four entries:

```text
M5:
"Project tests use JUnit 5."
```

Then:

```text
M1-M4 → SUPERSEDED
M5 → ACTIVE
```

This dramatically reduces retrieval noise.

---

# 47. Compaction should be conservative

Don't merge memories merely because they look similar.

Require:

```text
same scope
same subject
compatible claims
compatible provenance
```

Otherwise:

```text
"frontend uses React"
```

and:

```text
"legacy frontend uses Angular"
```

could incorrectly collapse.

---

# 48. Memory contradiction

We need explicit contradiction detection.

Example:

```text
M1:
"AuthService owns validation."

M2:
"TokenValidator owns validation."
```

Don't randomly choose one.

Create:

```java
public record MemoryConflict(
    MemoryId left,
    MemoryId right,
    ConflictType type,
    ConflictStatus status
) {}
```

---

# 49. Conflict handling

```text
conflict
 ↓
compare evidence
 ↓
newer?
stronger authority?
current workspace?
user instruction?
 ↓
resolve or retain conflict
```

For repository facts:

```text
current source
```

usually wins over old memory.

---

# 50. Memory authority

You can reuse the evidence authority concept:

```text
USER_EXPLICIT
CURRENT_WORKSPACE
TEST_RESULT
STATIC_ANALYSIS
DOCUMENTATION
HISTORICAL_MEMORY
MODEL_INFERENCE
```

Then memory conflicts can be resolved systematically.

---

# 51. This gives us a strong architecture

```text
                 MEMORY MANAGER
                       │
       ┌───────────────┼────────────────┐
       ↓               ↓                ↓
   Retrieve        Promote           Forget
       │               │                │
       ↓               ↓                ↓
   Ranking         Policy          Tombstone
       │               │
       ↓               ↓
 ContextEngine      Evidence
                       │
                       ↓
                   Lineage
```

---

# 52. Actual package structure

I'd add:

```text
wayang/
  memory/
    Memory
    MemoryId
    MemoryScope
    MemoryType
    MemoryStatus

    MemoryManager
    MemoryStore
    MemoryQuery
    MemorySelector

    MemoryPolicy
    PromotionPolicy
    ForgetPolicy

    MemoryLineage
    MemoryDependency
    MemoryConflict
    MemoryTombstone

    MemoryRetriever
    MemoryRanker
    MemoryCompactor
    MemoryInvalidator
```

---

# 53. SQLite schema

Start with:

```sql
CREATE TABLE memories (
    id TEXT PRIMARY KEY,
    scope TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL,

    content TEXT NOT NULL,

    confidence TEXT NOT NULL,

    project_id TEXT,
    workspace_version TEXT,

    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE memory_sources (
    memory_id TEXT NOT NULL,
    evidence_id TEXT,
    inference_id TEXT,
    task_id TEXT
);

CREATE TABLE memory_tombstones (
    memory_id TEXT PRIMARY KEY,
    reason TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
```

Then add indexes:

```sql
CREATE INDEX idx_memory_scope
ON memories(scope, status);

CREATE INDEX idx_memory_project
ON memories(project_id, status);
```

Keep retrieval cheap.

---

# 54. Don't start with vector DB

For your Aljabr use case, I would explicitly **not** make a vector database a foundational dependency.

Start with:

```text
SQLite
+
FTS
+
metadata filtering
```

Then optionally:

```text
embedding index
```

when scale actually requires it.

For a local coding agent, repository structure and lexical search are often much more valuable than indiscriminate semantic search.

---

# 55. Memory retrieval pipeline

The final pipeline:

```text
Task objective
      ↓
extract memory hints
      ↓
metadata filter
      ↓
lexical search
      ↓
optional embedding search
      ↓
deduplicate
      ↓
freshness validation
      ↓
authority ranking
      ↓
token budget
      ↓
ContextController
```

That is a very strong local-first design.

---

# 56. Small-model optimization

This layer fits Gemma especially well because **memory should reduce the amount of reasoning Gemma has to do**.

Instead of:

```text
"You know, from 40 previous interactions..."
```

give:

```text
Project conventions:
- tests use JUnit 5
- generated files are not edited
- authentication lives under auth/core
```

The model doesn't need to rediscover these facts.

---

# 57. But don't over-compress

This connects directly to your earlier question about context optimization.

We don't want:

```text
1000 tokens
↓
"auth stuff"
```

That's too aggressive.

Instead:

```text
Authentication implementation:
AuthService coordinates authentication.
TokenValidator performs token validation.
Clock is injected through AuthConfiguration.
```

The goal is:

> **remove redundancy, not information.**

That should be a formal ContextPolicy objective.

---

# 58. Memory vs context compression

Keep them separate.

### Memory compression

Long-term representation:

```text
"Project tests use JUnit 5."
```

### Context compression

Current prompt optimization:

```text
JUnit 5 convention
```

Maybe the second doesn't need the whole explanation.

The underlying memory remains rich.

---

# 59. This also solves your "cloud vs local" concern

The same memory system can operate under different policies.

### Aljabr local

```text
small retrieval
high compression
strict relevance
small token budget
minimal model calls
```

### Cloud

```text
larger retrieval
less aggressive compression
larger context
more semantic retrieval
more historical context
```

But:

```text
same MemoryStore
same lineage
same scopes
same invalidation
same forgetting semantics
```

Only the **policy** changes.

That's exactly the architecture I'd want.

---

# 60. The resulting Wayang stack

We now have:

```text
                    WAYANG
                       │
        ┌──────────────┴──────────────┐
        │                             │
 CapabilityDetector            Runtime State
        │                             │
 ContextController              Task / Agent
        │                             │
 ContextEngine                   Checkpoints
        │                             │
        └──────────┬──────────────────┘
                   │
                Evidence
                   │
        ┌──────────┴──────────┐
        │                     │
   Observation            Inference
        │                     │
        └──────────┬──────────┘
                   │
                Decision
                   │
                 Action
                   │
              Workspace
                   │
                   ▼
                Evidence
                   │
                   ▼
                Memory
                   │
             Promotion /
             Invalidation /
               Forget
```

This is starting to look like a **real agent operating system**, rather than an LLM wrapper.

---

# Next milestone: `PolicyEngine`

Before we implement more agent intelligence, I recommend one important architectural step.

We now have many policies scattered across components:

```text
ContextPolicy
ObservationPolicy
CachePolicy
MemoryPolicy
RetryPolicy
PromotionPolicy
ForgetPolicy
CapabilityPolicy
```

If each component independently makes decisions, Wayang eventually becomes difficult to control.

So the next milestone should consolidate this into:

## `PolicyEngine → PolicyResolver → RuntimeProfile`

For example:

```text
                 RuntimeProfile
                       │
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
   LocalAljabr     CloudFast      CloudMax
        │              │              │
        └──────────────┼──────────────┘
                       ↓
                 PolicyResolver
                       ↓
        ┌──────────────┼───────────────┐
        ↓              ↓               ↓
 ContextPolicy   ObservationPolicy   MemoryPolicy
```

Then your **M4 16GB local Aljabr** configuration becomes a runtime profile rather than a pile of special cases.

That is the next foundational piece I'd build before the actual **Coding Agent loop / ReAct replacement / Planner–Executor–Verifier architecture**.


Yes. **Next milestone: `PolicyEngine → PolicyResolver → RuntimeProfile`.**

This is the layer that makes everything we've built configurable without scattering `if (localModel)` / `if (cloud)` throughout Wayang.

The key idea:

> **Components decide what they need; PolicyEngine decides how aggressively they should operate under the current runtime capabilities.**

---

# Milestone — PolicyEngine

We currently have:

```text
CapabilityDetector
ContextController
ContextEngine
MemoryManager
ObservationManager
CheckpointManager
Inference
ToolExecutor
```

and each has policies.

Without consolidation, we'll eventually get this:

```java
if (model.isSmall()) ...
if (context < 16k) ...
if (local) ...
if (memory < 16GB) ...
if (cloud) ...
```

everywhere.

Don't do that.

Instead:

```text
                    CapabilityDetector
                           ↓
                    RuntimeProfile
                           ↓
                     PolicyEngine
                           ↓
              ┌────────────┼────────────┐
              ↓            ↓            ↓
        ContextPolicy  MemoryPolicy  ToolPolicy
```

---

# 1. RuntimeProfile

First define the environment.

```java
public record RuntimeProfile(
    ModelProfile model,
    HardwareProfile hardware,
    InferenceProfile inference,
    ContextProfile context,
    RuntimeMode mode
) {}
```

---

# 2. ModelProfile

```java
public record ModelProfile(
    String provider,
    String modelId,

    ModelSizeClass sizeClass,

    long contextWindowTokens,

    long maxOutputTokens,

    boolean supportsToolCalling,
    boolean supportsStructuredOutput,

    ModelQualityProfile quality
) {}
```

For your local setup this might resolve approximately to:

```text
provider:
local

model:
Gemma 4 12B GGUF

sizeClass:
SMALL_LOCAL

context:
detected/configured

toolCalling:
runtime capability

structuredOutput:
runtime capability
```

The important point is that **the rest of Wayang doesn't need to know it's Gemma**.

---

# 3. ModelSizeClass

Don't make the system depend on exact parameter counts.

Use categories:

```java
public enum ModelSizeClass {

    TINY,
    SMALL,
    MEDIUM,
    LARGE,
    FRONTIER
}
```

Because a 12B model today may behave differently from another 12B model.

Parameter count is only one signal.

---

# 4. Quality profile

This is more useful than raw model size.

```java
public record ModelQualityProfile(
    double reasoningCapability,
    double codingCapability,
    double toolUseCapability,
    double instructionFollowing
) {}
```

These don't have to be scientifically precise.

They can initially come from configuration:

```yaml
model:
  coding: 0.82
  reasoning: 0.75
  toolUse: 0.80
  instructionFollowing: 0.88
```

Later they can come from benchmark profiles.

---

# 5. HardwareProfile

For local-first Aljabr:

```java
public record HardwareProfile(
    String architecture,
    long totalMemoryBytes,
    long availableMemoryBytes,

    Optional<String> accelerator,

    Optional<Long> acceleratorMemoryBytes
) {}
```

For example:

```text
Apple Silicon
16 GB unified memory
Metal
```

Again, Wayang doesn't need to special-case M4.

---

# 6. InferenceProfile

```java
public record InferenceProfile(
    InferenceMode mode,

    boolean streaming,
    boolean caching,
    boolean speculativeDecoding,

    int maxConcurrentRequests,

    Duration timeout
) {}
```

```java
public enum InferenceMode {
    LOCAL,
    REMOTE,
    HYBRID
}
```

---

# 7. ContextProfile

```java
public record ContextProfile(
    long contextWindow,
    long reservedForOutput,
    long reservedForSystem,
    long usableInputTokens
) {}
```

Calculate:

```text
usableInput
=
contextWindow
-
systemReserve
-
outputReserve
```

This should be the number the ContextController actually uses.

---

# 8. RuntimeMode

```java
public enum RuntimeMode {

    LOCAL_FIRST,
    CLOUD_FIRST,
    BALANCED,
    OFFLINE
}
```

For Aljabr:

```text
LOCAL_FIRST
```

would be the default.

---

# 9. PolicyEngine

Now:

```java
public interface PolicyEngine {

    ContextPolicy contextPolicy();

    MemoryPolicy memoryPolicy();

    ObservationPolicy observationPolicy();

    ToolPolicy toolPolicy();

    RetryPolicy retryPolicy();

    InferencePolicy inferencePolicy();

    CheckpointPolicy checkpointPolicy();

    PlanningPolicy planningPolicy();
}
```

But I'd actually go one step further.

Don't have policies as mutable global objects.

Resolve them per task.

---

# 10. PolicyResolver

```java
public interface PolicyResolver {

    RuntimePolicy resolve(
        RuntimeProfile runtime,
        TaskRequirements requirements
    );
}
```

Then:

```java
RuntimePolicy policy =
    policyResolver.resolve(runtimeProfile, taskRequirements);
```

---

# 11. RuntimePolicy

```java
public record RuntimePolicy(
    ContextPolicy context,
    MemoryPolicy memory,
    ObservationPolicy observation,
    ToolPolicy tool,
    RetryPolicy retry,
    InferencePolicy inference,
    CheckpointPolicy checkpoint,
    PlanningPolicy planning
) {}
```

Now every task receives a coherent policy snapshot.

---

# 12. Why snapshot?

Suppose the user starts:

```text
local Gemma
```

Then halfway through:

```text
cloud model enabled
```

You don't want random components changing behavior halfway through the task.

So:

```text
Task T42
   ↓
PolicySnapshot P1
```

remains stable unless explicitly re-negotiated.

---

# 13. But capability can change

For example:

```text
Mac memory pressure
```

or:

```text
cloud unavailable
```

So we need:

```java
PolicyVersion
```

and controlled re-resolution.

```text
P1
 ↓
runtime changed
 ↓
PolicyResolver
 ↓
P2
```

This becomes an explicit event.

---

# 14. TaskRequirements

The user request itself influences policy.

```java
public record TaskRequirements(
    TaskComplexity complexity,

    boolean requiresLargeContext,
    boolean requiresDeepReasoning,
    boolean requiresInternet,

    boolean modifiesWorkspace,
    boolean requiresHighConfidence
) {}
```

Example:

```text
"Rename this variable"
```

requires little.

Whereas:

```text
"Investigate why authentication intermittently fails across services"
```

requires much more.

---

# 15. TaskComplexity

```java
public enum TaskComplexity {

    TRIVIAL,
    SIMPLE,
    MODERATE,
    COMPLEX,
    DEEP_RESEARCH
}
```

Initially this can be determined heuristically.

Don't make Gemma classify every task.

---

# 16. Policy resolution example

Suppose:

```text
Model:
12B local

Hardware:
16GB

Task:
simple bug fix
```

Resolver might produce:

```yaml
context:
  strategy: compressed
  maxTokens: 6000

memory:
  maxItems: 8

observation:
  maxFilesPerIteration: 5

planning:
  maxSteps: 8

inference:
  maxOutputTokens: 1200
```

---

# 17. Complex task

Same machine:

```text
deep architecture investigation
```

Policy may become:

```yaml
context:
  strategy: compressed-plus-evidence
  maxTokens: 9000

memory:
  maxItems: 15

observation:
  maxFilesPerIteration: 10

planning:
  maxSteps: 15
```

Still optimized, but the system spends more resources because the task deserves it.

---

# 18. Cloud profile

Now suppose:

```text
large cloud model
large context
```

The policy may become:

```yaml
context:
  strategy: normal
  maxTokens: 50000

memory:
  maxItems: 50

observation:
  maxFilesPerIteration: 30

planning:
  maxSteps: 30
```

Notice:

**same architecture.**

Only policy differs.

---

# 19. Important: don't optimize purely by model size

This is where I would improve our earlier design.

Bad:

```java
if (modelParams < 20B)
    useCompression();
```

Better:

```java
if (
    tokenPressureHigh
    || availableMemoryLow
    || taskRequiresLongContext
) {
    ...
}
```

So the policy depends on:

```text
model capability
+
context capacity
+
hardware pressure
+
task complexity
+
current context usage
```

---

# 20. Dynamic ContextPolicy

This should therefore not be static.

```java
public interface ContextPolicy {

    ContextDecision evaluate(
        ContextState state
    );
}
```

For example:

```java
public record ContextState(
    int candidateTokens,
    int availableTokens,
    TaskComplexity complexity,
    ModelSizeClass modelSize
) {}
```

Then:

```text
candidate = 12,000
available = 6,000

→ compress
```

Whereas:

```text
candidate = 12,000
available = 64,000

→ don't unnecessarily compress
```

This directly addresses your earlier concern.

---

# 21. Policy should optimize for information density

This is a major principle for Aljabr.

Not:

> minimize tokens.

Instead:

> **maximize useful information per token.**

So:

```text
1000 tokens of precise evidence
```

is better than:

```text
300 tokens of vague summary
```

And:

```text
5000 redundant tokens
```

are worse than:

```text
3000 highly relevant tokens
```

---

# 22. Context utility

We can formalize:

```java
public record ContextItemScore(
    double relevance,
    double informationDensity,
    double freshness,
    double authority,
    double redundancy
) {}
```

Then:

```text
utility =
    relevance
  + informationDensity
  + freshness
  + authority
  - redundancy
```

Again, deterministic calculation first.

Don't ask the local model to do this.

---

# 23. Policy priority

We need hierarchy.

I recommend:

```text
Safety / correctness
        ↓
User constraints
        ↓
Task requirements
        ↓
Runtime capability
        ↓
Optimization
```

So optimization can **never** override correctness.

Example:

```text
"compress context"
```

cannot remove:

```text
test failure evidence
```

if that evidence is necessary to make the decision.

---

# 24. Policy reason

Every policy decision should optionally be explainable.

```java
public record PolicyDecision(
    String policy,
    String decision,
    String reason
) {}
```

Example:

```text
ContextPolicy:
COMPRESS

Reason:
candidate context 11,420 tokens exceeds usable budget 7,800.
```

This is fantastic for debugging Aljabr.

---

# 25. PolicyEngine shouldn't control execution

Important separation:

```text
PolicyEngine
    ↓
decision
```

not:

```text
PolicyEngine
    ↓
execute everything
```

For example:

```text
ContextPolicy
→ "keep 8 evidence items"

ContextController
→ actually selects them
```

---

# 26. RuntimeProfile detector

Now connect this back to:

```text
CapabilityDetector
```

We already designed:

```text
CapabilityDetector
```

Now it produces:

```java
RuntimeProfile detect();
```

So:

```text
hardware
model
inference engine
context
tools
        ↓
CapabilityDetector
        ↓
RuntimeProfile
```

---

# 27. Capability confidence

Detection isn't always perfect.

Use:

```java
public record CapabilityValue<T>(
    T value,
    CapabilitySource source,
    double confidence
) {}
```

Example:

```text
contextWindow:
8192
source:
MODEL_METADATA
confidence:
0.99
```

or:

```text
toolCalling:
true
source:
EMPIRICAL_PROBE
confidence:
0.95
```

---

# 28. Capability sources

```java
public enum CapabilitySource {

    CONFIGURATION,
    MODEL_METADATA,
    INFERENCE_ENGINE,
    EMPIRICAL_PROBE,
    USER_OVERRIDE,
    DEFAULT
}
```

Priority:

```text
USER_OVERRIDE
    ↓
CONFIGURATION
    ↓
MODEL_METADATA
    ↓
EMPIRICAL_PROBE
    ↓
DEFAULT
```

You can tune that ordering, but explicit configuration should win.

---

# 29. User override

Very useful.

User can say:

> Don't aggressively compress context.

Then:

```text
ContextPolicy
mode = PRESERVE
```

provided hardware/model can support it.

So user preference can override optimization, but not physical limits.

---

# 30. Hard constraints vs soft policies

This distinction is extremely important.

### Hard constraint

```text
model context = 8192
```

Cannot exceed it.

### Soft policy

```text
prefer 6000 tokens
```

Can change.

Represent separately:

```java
public record ResourceConstraint(
    long hardLimit,
    long preferredLimit
) {}
```

---

# 31. This makes local optimization safe

For your M4 16GB environment:

```text
hard:
context <= actual model limit

preferred:
use <= 70-80%

hard:
memory <= available runtime memory

preferred:
keep safety headroom
```

So Aljabr doesn't accidentally push the machine into memory pressure merely because the theoretical context window is large.

---

# 32. InferencePolicy

```java
public record InferencePolicy(
    int maxOutputTokens,

    int maxReasoningIterations,

    int maxToolCalls,

    Duration timeout,

    boolean allowRetry,

    boolean allowFallbackModel
) {}
```

---

# 33. Small model policy

For local Aljabr:

```text
max output:
small

tool calls:
bounded

iterations:
bounded

retry:
yes

fallback:
maybe
```

This prevents runaway loops.

---

# 34. PlanningPolicy

```java
public record PlanningPolicy(
    int maxSteps,
    int maxReplans,
    int maxParallelSteps,

    boolean allowDynamicReplanning
) {}
```

Example local:

```text
maxSteps = 10
maxReplans = 3
parallel = 1
```

Cloud:

```text
maxSteps = 30
maxReplans = 10
parallel = 4
```

---

# 35. ToolPolicy

```java
public record ToolPolicy(
    Set<ActionType> allowedActions,

    boolean allowParallelExecution,

    int maxActionsPerIteration,

    boolean requireConfirmationForDestructiveActions
) {}
```

This will become important when Aljabr starts modifying code.

---

# 36. ObservationPolicy

```java
public record ObservationPolicy(
    int maxFilesPerIteration,
    int maxBytesPerObservation,

    int maxSearchResults,

    boolean allowRecursiveSearch,

    boolean preferTargetedObservation
) {}
```

Again:

```text
local:
targeted

cloud:
broader
```

---

# 37. MemoryPolicy

From our previous milestone:

```java
public record MemoryPolicy(
    int maxItems,
    int maxTokens,

    boolean semanticSearch,
    boolean embeddingSearch,

    boolean autoPromotion,
    boolean autoCompaction,

    Duration freshnessWindow
) {}
```

---

# 38. CheckpointPolicy

```java
public record CheckpointPolicy(
    boolean beforeModelCall,
    boolean afterModelCall,
    boolean beforeAction,
    boolean afterAction,

    Duration periodicInterval,

    int retentionCount
) {}
```

Local Aljabr should probably checkpoint fairly aggressively.

Storage is cheap compared with repeating inference/research.

---

# 39. One policy profile for Aljabr

Conceptually:

```yaml
profile: aljabr-local

mode: LOCAL_FIRST

context:
  strategy: adaptive
  preferredUtilization: 0.70

memory:
  maxItems: 10
  semanticSearch: optional
  autoPromotion: conservative

observation:
  targeted: true
  maxFilesPerIteration: 8

planning:
  maxSteps: 10
  maxReplans: 3

inference:
  boundedOutput: true
  maxToolCalls: 12

checkpoint:
  beforeAction: true
  afterAction: true
  beforeModelCall: true
```

The exact numbers should eventually be benchmarked rather than hardcoded.

---

# 40. Cloud profile

```yaml
profile: cloud-large

mode: CLOUD_FIRST

context:
  strategy: adaptive
  preferredUtilization: 0.85

memory:
  maxItems: 40
  semanticSearch: true

observation:
  targeted: true
  maxFilesPerIteration: 25

planning:
  maxSteps: 30
  maxReplans: 10

inference:
  boundedOutput: false
```

Again, same runtime.

---

# 41. Hybrid profile

This is likely very interesting for Wayang.

```text
local Gemma
    ↓
cheap inspection / summarization / classification

cloud model
    ↓
deep reasoning
```

Policy can decide:

```java
if (taskComplexity >= COMPLEX
    && localReasoningScore < threshold) {

    routeToCloud();
}
```

But that's later.

Don't make routing foundational yet.

---

# 42. Runtime profiles should be data

Don't hardcode:

```java
if (gemma12b) ...
```

Instead:

```text
profiles/
    aljabr-local.yaml
    cloud-large.yaml
    cloud-balanced.yaml
```

or database/config objects.

---

# 43. But policy must be overridable

Hierarchy:

```text
DefaultProfile
      ↓
RuntimeProfile
      ↓
TaskPolicy
      ↓
UserOverride
```

with validation.

Example:

```text
default:
compression = adaptive

user:
disable compression

capability:
context only 8k

result:
compression still required
```

User preferences cannot violate hard capability limits.

---

# 44. Policy Resolution example

```java
RuntimePolicy resolve(
    RuntimeProfile runtime,
    TaskRequirements task
) {

    var base = profileFor(runtime);

    var adjusted = adjustForTask(base, task);

    return applyConstraints(
        adjusted,
        runtime
    );
}
```

That is basically the heart of the PolicyEngine.

---

# 45. Make the policy immutable

Once resolved:

```java
RuntimePolicy policy
```

should be immutable.

Components receive it:

```java
contextEngine.run(context, policy.context());
memoryManager.retrieve(query, policy.memory());
planner.plan(task, policy.planning());
```

No global mutable configuration.

---

# 46. This also makes testing much easier

You can test:

```text
RuntimeProfile A
+
TaskRequirements B
=
Policy C
```

without running Gemma.

For example:

```java
@Test
void smallLocalModelUsesAggressiveContextBudget() {
    ...
}
```

Then:

```java
@Test
void largeCloudModelPreservesMoreContext() {
    ...
}
```

This is deterministic.

---

# 47. Policy telemetry

Record:

```java
public record PolicyTelemetry(
    String policy,
    String decision,
    String reason,

    long candidateTokens,
    long selectedTokens,

    Duration latency
) {}
```

Then eventually you can answer:

> Is Aljabr's context optimization actually helping?

instead of guessing.

---

# 48. This is important for your project

Because I wouldn't assume:

> smaller context = better local performance.

You need measurements.

Track:

```text
tokens in
tokens out
latency
memory usage
tool calls
task success
replans
test failures
```

Then compare:

```text
optimized context
vs
unoptimized context
```

---

# 49. The optimization loop

Eventually:

```text
Task
 ↓
Policy
 ↓
Context
 ↓
Gemma
 ↓
Result
 ↓
Telemetry
 ↓
Evaluation
 ↓
Policy tuning
```

That gives Aljabr a measurable optimization system rather than hand-tuned heuristics.

---

# 50. One thing I'd explicitly avoid

Don't build:

```text
UniversalOptimizationEngine
```

that tries to optimize everything.

That becomes opaque.

Instead:

```text
ContextPolicy
MemoryPolicy
PlanningPolicy
InferencePolicy
ObservationPolicy
ToolPolicy
```

and a single resolver coordinates them.

**Policy domains remain independently understandable.**

---

# 51. Updated Wayang architecture

At this point:

```text
                         WAYANG
                           │
                    CapabilityDetector
                           │
                           ▼
                    RuntimeProfile
                           │
                           ▼
                    PolicyResolver
                           │
                           ▼
                    RuntimePolicy
                           │
        ┌──────────────────┼──────────────────┐
        ↓                  ↓                  ↓
 ContextPolicy       MemoryPolicy       PlanningPolicy
        ↓                  ↓                  ↓
 ContextEngine       MemoryManager          Planner
        │                  │                  │
        └──────────────┬───┴──────────────────┘
                       ↓
                    Agent
                       ↓
                   Decision
                       ↓
                    Action
                       ↓
                  Checkpoint
                       ↓
                    State
```

This is a very clean foundation.

---

# 52. And now we're ready for the actual Aljabr brain

The next milestone should finally move into the **coding-agent execution architecture** itself.

I would not implement generic ReAct.

Instead, for Aljabr:

```text
Task
 ↓
Intent / Requirements
 ↓
Planner
 ↓
Observer
 ↓
Reasoner
 ↓
DecisionGate
 ↓
Executor
 ↓
Verifier
 ↓
Replanner
```

with the important difference that **the model does not directly control the loop**.

Wayang controls the loop.

The local Gemma is a reasoning component inside it.

That is the architecture I'd pursue for Aljabr:

> **LLM proposes; Wayang validates, executes, observes, checkpoints, and decides whether another inference is necessary.**

That will be the next major milestone: **`AljabrAgent → Planner → DecisionGate → Executor → Verifier → Replanner`**, including the concrete interfaces and the actual iteration loop optimized for a 12B local model.


