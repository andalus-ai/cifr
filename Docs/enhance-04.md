Absolutely. Now we move into the **actual Aljabr coding-agent core**.

The central architectural decision is:

> **Gemma should not be the agent loop. Wayang should be the agent loop.**

Gemma proposes reasoning/decisions; Wayang controls what is observed, what can be executed, what gets verified, and when another inference is necessary.

---

# Milestone — `AljabrAgent → Planner → DecisionGate → Executor → Verifier → Replanner`

The complete loop:

```text
                    USER TASK
                       │
                       ▼
                 TaskAnalyzer
                       │
                       ▼
                    Planner
                       │
                       ▼
                  Plan / Steps
                       │
                       ▼
                  Observer
                       │
                       ▼
                    Context
                       │
                       ▼
                    Gemma
                       │
                       ▼
              Proposed Decision
                       │
                       ▼
                 DecisionGate
                  /         \
             reject          accept
               │                │
               ▼                ▼
             replan          Executor
                                │
                                ▼
                           Workspace
                                │
                                ▼
                             Tests
                                │
                                ▼
                            Verifier
                           /        \
                        pass        fail
                         │             │
                         ▼             ▼
                      next step      Replanner
                         │             │
                         └──────┬──────┘
                                ▼
                              DONE
```

The important thing is that **the model never gets to skip the gate**.

---

# 1. `AljabrAgent`

Keep the public interface extremely small.

```java
public interface AljabrAgent {

    Uni<AgentResult> run(TaskRequest request);

    Uni<Void> pause(TaskId taskId);

    Uni<Void> resume(TaskId taskId);

    Uni<Void> cancel(TaskId taskId);
}
```

That's it.

The internals can evolve without changing the public API.

---

# 2. `TaskRequest`

```java
public record TaskRequest(
    String objective,

    Optional<String> workspace,

    Optional<String> project,

    Optional<TaskRequirements> requirements
) {}
```

Example:

```text
Fix the token expiration bug and add regression tests.
```

---

# 3. `AgentResult`

Don't just return model output.

```java
public record AgentResult(
    TaskId taskId,

    TaskStatus status,

    Optional<String> summary,

    List<ChangeSummary> changes,

    VerificationResult verification
) {}
```

The user ultimately cares about:

```text
what changed?
did it work?
```

not:

```text
what did Gemma think?
```

---

# 4. The agent loop

Actual orchestration:

```java
public Uni<AgentResult> run(TaskRequest request) {

    return taskManager.create(request)
        .flatMap(taskAnalyzer::analyze)
        .flatMap(planner::createPlan)
        .flatMap(loop::execute);
}
```

But the loop itself should be explicit.

---

# 5. `AgentLoop`

```java
public interface AgentLoop {

    Uni<AgentResult> execute(
        TaskId taskId
    );
}
```

Implementation:

```java
public Uni<AgentResult> execute(TaskId taskId) {

    return step(taskId)
        .repeat()
        .until(LoopCondition::finished);
}
```

Conceptually:

```text
while (!finished) {
    executeIteration();
}
```

But **bounded**.

---

# 6. Never allow an infinite agent loop

Create:

```java
public record LoopBudget(
    int maxIterations,
    int maxModelCalls,
    int maxToolCalls,
    int maxReplans,
    Duration maxDuration
) {}
```

For your local Aljabr:

```text
maxIterations = 30
maxModelCalls = 20
maxToolCalls = 50
maxReplans = 5
```

These should be policy-driven, not hardcoded.

---

# 7. One iteration

An iteration is:

```text
Observe
 ↓
Build Context
 ↓
Infer
 ↓
Validate Decision
 ↓
Execute
 ↓
Verify
```

Not:

```text
Gemma
 ↓
Gemma
 ↓
Gemma
 ↓
Gemma
```

This distinction is critical for small models.

---

# 8. `AgentIteration`

```java
public record AgentIteration(
    int number,

    TaskPhase phase,

    Optional<Observation> observation,

    Optional<ModelDecision> decision,

    List<AgentAction> actions,

    Optional<VerificationResult> verification
) {}
```

This becomes useful for telemetry/debugging.

---

# 9. Planner

The planner creates a **small executable plan**.

```java
public interface Planner {

    Uni<AgentPlan> createPlan(
        TaskState task,
        RuntimePolicy policy
    );

    Uni<AgentPlan> replan(
        ReplanRequest request
    );
}
```

---

# 10. Don't ask Gemma to generate a 30-step plan

For local Aljabr, this is one of the biggest improvements.

Instead:

```text
Goal
 ↓
3–7 meaningful steps
```

Example:

```text
1. Inspect authentication flow
2. Locate expiration check
3. Determine root cause
4. Patch implementation
5. Add regression test
6. Run tests
7. Verify
```

The plan is a **navigation skeleton**, not the entire reasoning trace.

---

# 11. Planning should be progressive

The model doesn't need to know everything upfront.

Use:

```text
coarse plan
    ↓
inspect
    ↓
refine current step
```

Instead of:

```text
huge plan
    ↓
execute blindly
```

This is especially important with local models.

---

# 12. `PlanStep`

We already defined:

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

Now add an execution constraint:

```java
Optional<ObservationRequirement> observationRequirement
```

So the plan says what evidence is needed.

---

# 13. Observation requirement

```java
public record ObservationRequirement(
    Set<ObservationType> required,
    int maxResults,
    boolean mustBeFresh
) {}
```

Example:

```text
Step:
Determine expiration logic

Required:
- source inspection
- symbol references

Fresh:
yes
```

---

# 14. Observer

The observer is one of Aljabr's most important components.

```java
public interface Observer {

    Uni<ObservationResult> observe(
        ObservationRequest request
    );
}
```

The model should **not freely search the repository**.

The Observer does.

---

# 15. Observation request

```java
public record ObservationRequest(
    String objective,

    Set<ObservationType> allowedTypes,

    Optional<String> target,

    ObservationBudget budget
) {}
```

---

# 16. Observation budget

```java
public record ObservationBudget(
    int maxFiles,
    int maxBytes,
    int maxSearchResults,
    int maxToolCalls
) {}
```

Again, policy-controlled.

---

# 17. Why this matters for Gemma

Bad:

```text
Gemma:
"Search the whole repository and tell me what matters."
```

Good:

```text
Wayang:
Here are the relevant files and symbols.
What should we inspect next?
```

The second dramatically reduces cognitive load.

---

# 18. Targeted observation

For example:

```text
Task:
Fix token expiration.
```

Initial Observer:

```text
search:
TokenValidator
expiresAt
expiration
Clock
```

Then results:

```text
TokenValidator.java
AuthService.java
TokenValidatorTest.java
```

Only those enter the next context.

---

# 19. Progressive widening

If insufficient evidence:

```text
targeted search
     ↓
not enough
     ↓
expand references
     ↓
still insufficient
     ↓
broader search
```

This should be policy-driven.

Not:

```text
always scan entire repo
```

---

# 20. Context construction

Now:

```java
ContextRequest contextRequest = new ContextRequest(
    task,
    planStep,
    observation,
    memory
);
```

Then:

```java
Context context =
    contextEngine.build(contextRequest, policy.context());
```

The model sees only the selected projection.

---

# 21. Gemma interface

Do not make the agent depend directly on an inference engine.

```java
public interface Reasoner {

    Uni<ModelResponse> infer(
        ReasoningRequest request
    );
}
```

Then:

```text
GemmaReasoner
OpenAIReasoner
AnthropicReasoner
OllamaReasoner
llama.cpp Reasoner
```

can all implement it.

---

# 22. ReasoningRequest

```java
public record ReasoningRequest(
    Context context,

    ReasoningTask task,

    OutputSchema schema,

    InferencePolicy policy
) {}
```

---

# 23. ReasoningTask

Don't send vague prompts like:

```text
"Analyze this."
```

Use explicit tasks.

```java
public enum ReasoningTask {

    IDENTIFY_NEXT_ACTION,
    DIAGNOSE_FAILURE,
    SELECT_TARGET,
    PROPOSE_PATCH,
    INTERPRET_TEST_FAILURE,
    VERIFY_RESULT,
    REPLAN
}
```

This is extremely helpful for a 12B model.

---

# 24. Task-specific prompting

Instead of one gigantic system prompt:

```text
You are Aljabr...
Here are 12 rules...
Here is memory...
Here is history...
Now reason...
```

use:

```text
ROLE
+
TASK
+
RELEVANT EVIDENCE
+
CONSTRAINTS
+
OUTPUT SCHEMA
```

This is much easier for a local model to follow.

---

# 25. Example local reasoning prompt

Conceptually:

```text
TASK:
Determine why expired tokens are accepted.

KNOWN EVIDENCE:
- TokenValidator.java:72
- AuthService.java:41
- TokenValidatorTest.java:93

CONSTRAINTS:
- Do not modify generated files.
- Prefer minimal change.
- Existing tests must remain valid.

QUESTION:
What is the next action?

OUTPUT:
{
  "action": "...",
  "target": "...",
  "reason": "...",
  "evidence": [...]
}
```

Notice how little irrelevant prose exists.

---

# 26. Structured output

This should be mandatory whenever possible.

```java
public record ModelDecision(
    DecisionType type,

    Optional<String> target,

    Optional<String> rationale,

    List<EvidenceId> evidence,

    Optional<ActionProposal> action
) {}
```

No parsing:

```text
"Sure! Here's what I think..."
```

if the inference engine supports structured output.

---

# 27. DecisionGate

This is the **safety and correctness boundary**.

```java
public interface DecisionGate {

    Uni<DecisionResult> evaluate(
        ModelDecision decision,
        AgentState state,
        RuntimePolicy policy
    );
}
```

---

# 28. DecisionGate checks

At minimum:

```text
Does the action exist?
Does the tool exist?
Is the action allowed?
Does the plan allow it?
Is the target valid?
Is evidence sufficient?
Is the action destructive?
Is confirmation required?
```

---

# 29. Example

Gemma says:

```json
{
  "action": "MODIFY",
  "target": "generated/AuthClient.java"
}
```

DecisionGate:

```text
target = generated file
policy = generated files protected
```

Result:

```text
REJECT
```

Gemma doesn't get to execute it.

---

# 30. DecisionGate result

```java
public record DecisionResult(
    DecisionStatus status,

    Optional<String> reason,

    Optional<CorrectiveRequest> correctiveRequest
) {}
```

```java
public enum DecisionStatus {
    ACCEPT,
    REJECT,
    NEED_MORE_EVIDENCE,
    NEED_USER_CONFIRMATION,
    REPLAN
}
```

---

# 31. Important distinction

Don't always reject a bad decision.

Sometimes the correct response is:

```text
NEED_MORE_EVIDENCE
```

Example:

Gemma wants to modify:

```text
AuthService
```

but evidence doesn't establish root cause.

Instead of:

```text
REJECT
```

we do:

```text
observe:
Clock construction
```

This prevents unnecessary replanning.

---

# 32. Executor

Only accepted decisions reach:

```java
public interface Executor {

    Uni<ActionResult> execute(
        AgentAction action
    );
}
```

---

# 33. Executor must be dumb

This is intentional.

Executor should not decide:

> "Maybe I'll also edit this other file."

It should:

```text
receive validated action
→ execute
→ report result
```

Planning/reasoning stays above it.

---

# 34. Example

Decision:

```text
MODIFY
target:
TokenValidator.java
patch:
...
```

Executor:

```text
validate patch
 ↓
apply
 ↓
record receipt
 ↓
return result
```

---

# 35. Workspace safety boundary

Before modification:

```text
WorkspaceSnapshot
```

After modification:

```text
WorkspaceSnapshot
```

Then:

```text
diff
```

must be captured as evidence.

So:

```text
before
 ↓
patch
 ↓
after
 ↓
diff evidence
```

---

# 36. Never let the model report success

This is a major principle.

Gemma says:

```text
"Patch applied successfully."
```

That means nothing.

The Executor must determine:

```text
ActionResult.success
```

from the actual tool.

Likewise:

```text
"Tests pass."
```

must come from:

```text
TestRunner
```

not the model.

---

# 37. Verifier

```java
public interface Verifier {

    Uni<VerificationResult> verify(
        VerificationRequest request
    );
}
```

---

# 38. Verification levels

```java
public enum VerificationLevel {

    NONE,
    STATIC,
    TARGETED_TEST,
    FULL_TEST,
    BUILD,
    BEHAVIORAL
}
```

Policy decides the minimum required.

---

# 39. For a simple patch

Use:

```text
compile
+
targeted tests
```

rather than:

```text
entire test suite
```

if the policy allows it.

---

# 40. Verification result

```java
public record VerificationResult(
    VerificationStatus status,

    VerificationLevel level,

    List<TestResult> tests,

    List<EvidenceId> evidence,

    Optional<String> summary
) {}
```

---

# 41. Verification status

```java
public enum VerificationStatus {

    PASSED,
    FAILED,
    PARTIAL,
    BLOCKED,
    NOT_RUN
}
```

---

# 42. Verifier evidence

A test failure should immediately become evidence:

```text
E91:
Test:
TokenValidatorTest.expiredToken

Result:
FAILED

Output:
...
```

Then the next model call gets:

```text
new evidence
```

rather than the entire previous execution transcript.

---

# 43. Replanner

If verification fails:

```java
public interface Replanner {

    Uni<ReplanResult> replan(
        ReplanRequest request
    );
}
```

---

# 44. Replan request

```java
public record ReplanRequest(
    AgentPlan currentPlan,

    PlanStep failedStep,

    VerificationResult verification,

    List<EvidenceId> newEvidence
) {}
```

The model doesn't need the whole history.

It gets:

```text
current plan
+
failed step
+
failure evidence
```

That's much better for Gemma.

---

# 45. Replan should be incremental

Suppose:

```text
P5 Modify
P6 Test
```

fails.

Don't regenerate the entire plan.

Instead:

```text
P6 failed
 ↓
diagnose
 ↓
replace P5
 ↓
continue
```

This is cheaper and more stable.

---

# 46. Example

Initial:

```text
P5:
Change expiration comparison.
```

Test:

```text
FAIL:
timezone mismatch.
```

Replan:

```text
P5a:
Inspect Clock timezone.

P5b:
Normalize timestamp comparison.

P6:
Run targeted tests.
```

Only the affected section changes.

---

# 47. Replan budget

```text
max replans = 3
```

After that:

```text
BLOCKED
```

rather than:

```text
Gemma tries 20 random patches.
```

This is essential for local agents.

---

# 48. The actual iteration

Now we can write the real loop.

```java
Uni<LoopResult> iteration(TaskState task) {

    return observer.observe(
            observationRequest(task)
        )
        .flatMap(observation ->
            contextEngine.build(
                task,
                observation,
                policy.context()
            )
        )
        .flatMap(context ->
            reasoner.infer(
                reasoningRequest(context)
            )
        )
        .flatMap(decision ->
            decisionGate.evaluate(
                decision,
                state,
                policy
            )
        )
        .flatMap(this::handleDecision);
}
```

---

# 49. Decision handling

```java
Uni<LoopResult> handleDecision(
    DecisionResult result
) {

    return switch (result.status()) {

        case ACCEPT ->
            execute(result);

        case NEED_MORE_EVIDENCE ->
            observeMore(result);

        case REPLAN ->
            replan(result);

        case NEED_USER_CONFIRMATION ->
            pause();

        case REJECT ->
            recoverFromRejectedDecision();
    };
}
```

This is the real agent control loop.

---

# 50. Notice what's missing

There is no:

```text
while model says continue
```

There is no:

```text
agent thought:
...
```

There is no:

```text
model chooses arbitrary tool
```

Instead:

```text
Wayang decides what happens next.
```

That's the major architectural advantage.

---

# 51. Small-model optimization

For Gemma 12B, make reasoning **event-driven**.

Don't call the model after every observation.

For example:

```text
read file
read another file
search symbol
```

can be batched:

```text
ObservationBatch
        ↓
ONE model call
```

rather than:

```text
read
→ model

read
→ model

search
→ model
```

---

# 52. `ReasoningTrigger`

Introduce:

```java
public enum ReasoningTrigger {

    INITIAL_ANALYSIS,
    NEW_RELEVANT_EVIDENCE,
    ACTION_REQUIRED,
    TEST_FAILURE,
    PLAN_COMPLETE,
    VERIFICATION,
    USER_INPUT,
    RECOVERY
}
```

The runtime decides when inference is actually needed.

---

# 53. This is much better than ReAct

Classic ReAct:

```text
Thought
Action
Observation
Thought
Action
Observation
```

Aljabr:

```text
State
 ↓
Observation batch
 ↓
Context
 ↓
Reasoning request
 ↓
Decision
 ↓
Gate
 ↓
Action
 ↓
Verification
 ↓
State transition
```

The model doesn't have to narrate every tiny thought.

---

# 54. Model output should be minimal

For a local model, don't request:

```text
"Explain your complete reasoning in detail."
```

Request:

```json
{
  "decision": "INSPECT",
  "target": "Clock",
  "reason": "Expiration comparison depends on injected time source.",
  "evidence": ["E41", "E43"]
}
```

Short, structured, actionable.

This saves output tokens **without reducing decision quality**.

---

# 55. Keep reasoning internally structured

Instead of chain-of-thought:

```text
"First I think X, then perhaps Y..."
```

store:

```text
Decision
Evidence
Confidence
Reason
```

That's enough for the runtime.

---

# 56. Decision confidence

You can optionally add:

```java
public record DecisionConfidence(
    ConfidenceLevel level,
    List<EvidenceId> supportingEvidence
) {}
```

But don't blindly trust model-provided confidence.

Better:

```text
confidence =
evidence sufficiency
+
source authority
+
decision validity
```

calculated by Wayang.

---

# 57. A very useful concept: `DecisionClass`

Not all decisions are equally dangerous.

```java
public enum DecisionClass {

    OBSERVATION,
    ANALYSIS,
    READONLY_ACTION,
    MODIFY_ACTION,
    DESTRUCTIVE_ACTION,
    EXTERNAL_ACTION
}
```

Then policies can say:

```text
READONLY
→ automatic

MODIFY
→ gate

DESTRUCTIVE
→ confirmation

EXTERNAL
→ stronger restrictions
```

---

# 58. This becomes Aljabr's safety boundary

For example:

```text
Gemma:
DELETE directory build/

DecisionGate:
DESTRUCTIVE_ACTION

Policy:
confirmation required

→ PAUSE
```

The model cannot accidentally turn a reasoning mistake into an irreversible action.

---

# 59. User confirmation

Eventually:

```text
Aljabr:
I found 3 unrelated changes in the workspace.
Proceed?

[Continue]
[Review]
[Cancel]
```

That is a state transition:

```text
WAITING_USER
```

not a special hack.

---

# 60. Complete architecture now

```text
                         USER
                          │
                          ▼
                    TaskRequest
                          │
                          ▼
                     TaskAnalyzer
                          │
                          ▼
                       Planner
                          │
                          ▼
                     AgentPlan
                          │
                    ┌─────┴─────┐
                    │           │
                    ▼           │
                 Observer       │
                    │           │
                    ▼           │
                Evidence        │
                    │           │
                    ▼           │
                ContextEngine   │
                    │           │
                    ▼           │
                  Gemma         │
                    │           │
                    ▼           │
                DecisionGate    │
                    │           │
              ┌─────┼──────┐    │
              │     │      │    │
              ▼     ▼      ▼    │
           Observe Execute Replan
                    │
                    ▼
                 Executor
                    │
                    ▼
                Workspace
                    │
                    ▼
                 Verifier
                    │
              ┌─────┴─────┐
              │           │
             PASS        FAIL
              │           │
              ▼           ▼
             Next       Replanner
                          │
                          └──────→ Observer
```

And underneath everything:

```text
EvidenceStore
MemoryManager
StateStore
CheckpointManager
PolicyEngine
Telemetry
```

---

# 61. One refinement I'd make before coding

Don't create one giant:

```text
AljabrAgentImpl.java
```

with all of this.

Use an orchestration layer:

```text
aljabr/
    AljabrAgent
    AgentLoop
    AgentIteration

    planner/
        Planner
        Replanner

    observe/
        Observer
        ObservationStrategy

    reason/
        Reasoner
        ReasoningTask

    decision/
        DecisionGate
        DecisionValidator

    execute/
        Executor
        ActionDispatcher

    verify/
        Verifier
        VerificationStrategy

    state/
        AgentStateMachine

    recovery/
        RecoveryManager
```

Each component remains independently testable.

---

# 62. And the most important implementation rule

For Aljabr local:

### Don't optimize the model first.

Optimize the **number of times you need the model**.

That's a much bigger win.

Prefer:

```text
5 good tool operations
→ 1 strong reasoning call
→ 1 execution
→ 1 verification
```

over:

```text
tool
→ model
→ tool
→ model
→ tool
→ model
→ tool
→ model
```

The latter wastes both tokens and latency.

---

# 63. Next milestone

Now that the agent loop is defined, the next thing I'd implement is the **`Tool System`**, because Aljabr's real capability will come from a controlled tool layer:

```text
ToolRegistry
ToolDescriptor
ToolPermission
ToolExecutor
ToolResult
ToolObservation
ToolTransaction
```

especially coding-specific tools:

```text
read_file
list_files
search
find_symbol
find_references
git_diff
apply_patch
run_test
run_build
run_linter
inspect_ast
```

And crucially, we'll design them around **structured observations rather than dumping raw terminal output into Gemma**.

That will be the next big optimization for your 12B local model.


Yes. **Next milestone: the Tool System.**

For Aljabr, I would make this a particularly strong boundary because the local model should **never directly operate the filesystem, shell, Git, or test runner**.

The architecture should be:

```text
Gemma
  ↓
ToolProposal
  ↓
ToolRegistry
  ↓
ToolPolicy
  ↓
ToolValidator
  ↓
ToolExecutor
  ↓
ToolResult
  ↓
ObservationNormalizer
  ↓
ContextEngine
  ↓
Gemma
```

The important optimization is:

> **Tools return compact, structured facts—not raw command output.**

That is especially valuable on your 12B / 16 GB local setup.

---

# 1. `Tool`

Start with a very small contract.

```java
public interface Tool {

    ToolDescriptor descriptor();

    Uni<ToolResult> execute(
        ToolRequest request,
        ToolExecutionContext context
    );
}
```

A tool is an implementation.

Examples:

```text
ReadFileTool
SearchTool
FindSymbolTool
ApplyPatchTool
RunTestTool
GitDiffTool
```

---

# 2. `ToolDescriptor`

The model doesn't need implementation details.

```java
public record ToolDescriptor(
    String name,
    String description,

    ToolCategory category,

    JsonSchema inputSchema,

    Set<ToolCapability> capabilities,

    ToolRisk risk
) {}
```

Example:

```text
name:
read_file

description:
Read a source file or selected line range.

category:
READ

risk:
NONE
```

---

# 3. Tool categories

```java
public enum ToolCategory {

    FILESYSTEM,
    SEARCH,
    CODE_ANALYSIS,
    GIT,
    BUILD,
    TEST,
    PATCH,
    SHELL,
    NETWORK
}
```

This becomes useful for policy.

For example:

```text
local Aljabr:
SHELL → restricted

cloud:
SHELL → still restricted
```

Don't let cloud capability automatically mean unrestricted capability.

---

# 4. Tool capabilities

```java
public enum ToolCapability {

    READ_ONLY,
    WRITE,
    DELETE,
    EXECUTE_PROCESS,
    NETWORK_ACCESS,
    MUTATES_WORKSPACE,
    MUTATES_GIT
}
```

Now Wayang can reason about consequences mechanically.

---

# 5. Tool risk

```java
public enum ToolRisk {

    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

For example:

```text
read_file
→ NONE

apply_patch
→ MEDIUM

run_test
→ LOW

git_reset
→ HIGH

rm -rf
→ CRITICAL
```

---

# 6. ToolRegistry

```java
public interface ToolRegistry {

    Optional<Tool> find(String name);

    List<ToolDescriptor> descriptors();

    boolean contains(String name);
}
```

Implementation:

```java
public final class DefaultToolRegistry
        implements ToolRegistry {

    private final Map<String, Tool> tools;

    public DefaultToolRegistry(
        List<Tool> tools
    ) {
        this.tools = tools.stream()
            .collect(toUnmodifiableMap(
                t -> t.descriptor().name(),
                identity()
            ));
    }
}
```

---

# 7. Don't expose every tool to Gemma

This is important.

Bad:

```text
Available tools:
47 tools...
```

For a 12B model this creates unnecessary decision complexity.

Instead:

```text
ToolSelector
```

selects a small relevant subset.

---

# 8. `ToolSelector`

```java
public interface ToolSelector {

    List<ToolDescriptor> select(
        ToolSelectionRequest request
    );
}
```

Example:

```text
Task:
Find why token validation fails.

Relevant tools:

find_symbol
search
read_file
find_references
run_test
```

No need to expose:

```text
git_reset
network_request
delete_file
```

---

# 9. Tool selection should be deterministic first

Don't ask Gemma:

> Which of these 50 tools should I use?

Wayang can filter by:

```text
task type
+
plan step
+
permissions
+
risk
+
policy
```

Then Gemma chooses among perhaps 3–8 tools.

This is much easier for a small model.

---

# 10. `ToolRequest`

```java
public record ToolRequest(
    String toolName,

    JsonNode arguments,

    Optional<String> reason,

    List<EvidenceId> supportingEvidence
) {}
```

Gemma proposes:

```json
{
  "tool": "find_symbol",
  "arguments": {
    "name": "TokenValidator"
  },
  "reason": "Need to inspect expiration logic"
}
```

---

# 11. Validate before execution

Never:

```text
Gemma → ToolExecutor
```

Always:

```text
Gemma
 ↓
ToolRequest
 ↓
ToolValidator
 ↓
ToolPolicy
 ↓
Executor
```

---

# 12. `ToolValidator`

```java
public interface ToolValidator {

    ValidationResult validate(
        ToolRequest request,
        ToolExecutionContext context
    );
}
```

Checks:

```text
tool exists
arguments valid
tool allowed
target allowed
risk allowed
workspace allowed
budget available
```

---

# 13. `ToolExecutionContext`

```java
public record ToolExecutionContext(
    TaskId taskId,
    WorkspaceId workspaceId,

    ToolPolicy policy,

    Optional<CheckpointId> checkpoint,

    CancellationToken cancellation
) {}
```

This prevents tools from becoming global uncontrolled utilities.

---

# 14. Workspace boundary

A tool should never casually receive:

```java
Path("/")
```

Instead:

```java
public interface Workspace {

    Path root();

    Path resolveSafe(String relativePath);

    boolean contains(Path path);
}
```

---

# 15. Safe path resolution

Conceptually:

```java
public Path resolveSafe(String relative) {

    Path resolved =
        root.resolve(relative).normalize();

    if (!resolved.startsWith(root)) {
        throw new SecurityException(
            "Path escapes workspace"
        );
    }

    return resolved;
}
```

This protects against:

```text
../../secrets
```

and similar traversal.

---

# 16. `ReadFileTool`

Now an actual coding tool.

```java
public final class ReadFileTool
        implements Tool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "read_file",
            "Read source content from the workspace.",
            ToolCategory.FILESYSTEM,
            schema(),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(
        ToolRequest request,
        ToolExecutionContext context
    ) {
        ...
    }
}
```

---

# 17. Don't allow arbitrary huge reads

Request:

```json
{
  "path": "src/auth/TokenValidator.java",
  "startLine": 60,
  "endLine": 110
}
```

instead of:

```json
{
  "path": "src/auth/TokenValidator.java"
}
```

when the file is large.

Wayang can automatically constrain it.

---

# 18. `ReadFileResult`

Don't return:

```text
entire file
```

as an unstructured string.

Use:

```java
public record ReadFileResult(
    String path,

    int startLine,
    int endLine,

    List<SourceLine> lines,

    FileMetadata metadata
) {}
```

---

# 19. `SourceLine`

```java
public record SourceLine(
    int number,
    String text
) {}
```

Now evidence can refer to:

```text
TokenValidator.java:72
```

precisely.

---

# 20. Search tool

```java
public interface SearchTool extends Tool {
}
```

Input:

```json
{
  "query": "expiresAt",
  "path": "src"
}
```

Output:

```java
public record SearchResult(
    String query,
    List<SearchMatch> matches,
    boolean truncated
) {}
```

---

# 21. SearchMatch

```java
public record SearchMatch(
    String path,
    int line,
    String snippet
) {}
```

Example:

```text
src/auth/TokenValidator.java:72
if (now.isAfter(expiresAt)) ...
```

That's enough.

Don't dump 500 grep lines into context.

---

# 22. Search budget

```java
public record SearchBudget(
    int maxMatches,
    int maxFiles,
    int maxBytes
) {}
```

If exceeded:

```text
truncated = true
```

The model can then request refinement.

---

# 23. Progressive search

Example:

```text
query:
expiresAt

results:
43 matches
```

Don't inject all 43.

Instead:

```text
top 8 matches
+
"43 total matches"
```

Then Gemma can request:

```text
inspect TokenValidator
```

This is a huge context optimization.

---

# 24. `FindSymbolTool`

This should eventually use an AST/LSP/index rather than text search.

```java
public record FindSymbolRequest(
    String symbol,
    Optional<String> language,
    Optional<String> path
) {}
```

Result:

```java
public record SymbolResult(
    String name,
    SymbolKind kind,
    String file,
    int startLine,
    int endLine,
    List<SymbolReference> references
) {}
```

This gives the model semantic information.

---

# 25. Why AST tools matter

For coding agents:

```text
grep
```

is useful.

But:

```text
find_symbol
find_references
find_implementation
find_definition
```

are much more information-dense.

For a small model:

> **semantic tools reduce reasoning burden.**

---

# 26. `ApplyPatchTool`

This is one of the most important tools.

Don't let Gemma write the whole file.

Use:

```java
public record ApplyPatchRequest(
    String path,
    List<FilePatch> patches
) {}
```

---

# 27. FilePatch

```java
public record FilePatch(
    int startLine,
    int endLine,

    String expectedContent,
    String replacement
) {}
```

The expected content is important.

Before applying:

```text
actual == expected
```

If not:

```text
PATCH_CONFLICT
```

rather than blindly modifying the file.

---

# 28. Patch result

```java
public record PatchResult(
    boolean applied,

    String path,

    List<DiffHunk> diff,

    Optional<String> conflictReason
) {}
```

Now the model gets:

```text
PATCH APPLIED

TokenValidator.java

- if (expiration < now)
+ if (expiration.isBefore(now))
```

Very compact.

---

# 29. Patch safety

Before applying:

```text
DecisionGate
 ↓
PatchValidator
 ↓
WorkspaceSnapshot
 ↓
ApplyPatch
 ↓
Diff
```

The patch tool itself should reject:

```text
binary files
generated files
outside workspace
unexpected file changes
```

depending on policy.

---

# 30. Transaction-like patch execution

I recommend:

```text
prepare
 ↓
validate
 ↓
snapshot
 ↓
apply
 ↓
verify diff
 ↓
commit
```

If anything fails:

```text
rollback
```

This gives Aljabr transactional behavior.

---

# 31. `RunTestTool`

```java
public record RunTestRequest(
    Optional<String> testName,
    Optional<String> testPath,

    TestScope scope
) {}
```

```java
public enum TestScope {

    SINGLE_TEST,
    TEST_CLASS,
    MODULE,
    PROJECT
}
```

The planner should normally start small.

---

# 32. Don't send raw test output

Bad:

```text
Gradle output:
...
500 lines...
```

Instead normalize:

```java
public record TestResult(
    String test,
    TestStatus status,
    Optional<String> failureType,
    Optional<String> failureMessage,
    List<SourceLocation> locations
) {}
```

Gemma sees:

```text
FAILED
TokenValidatorTest.expiredToken

AssertionError
expected: false
actual: true

TokenValidator.java:72
```

Much more useful.

---

# 33. Build tool

```java
public record BuildResult(
    BuildStatus status,
    Duration duration,
    List<CompilationError> errors,
    List<Warning> warnings
) {}
```

Again, normalize.

---

# 34. Git tools

Start with read-only:

```text
git_status
git_diff
git_log
git_show
```

Then later:

```text
git_commit
git_branch
```

And keep destructive operations out of the default tool set.

---

# 35. `ToolResult`

All tools should have a common envelope.

```java
public record ToolResult(
    ToolExecutionStatus status,

    Object data,

    List<Evidence> evidence,

    ToolMetrics metrics,

    Optional<String> error
) {}
```

---

# 36. Tool execution status

```java
public enum ToolExecutionStatus {

    SUCCESS,
    INVALID_REQUEST,
    DENIED,
    NOT_FOUND,
    CONFLICT,
    TIMEOUT,
    FAILED,
    CANCELLED
}
```

---

# 37. Tool metrics

```java
public record ToolMetrics(
    Duration duration,

    long bytesRead,
    long bytesWritten,

    int resultCount
) {}
```

This becomes useful later for policy tuning.

---

# 38. Every tool result should produce evidence

This is a very powerful connection.

```text
Tool
 ↓
ToolResult
 ↓
EvidenceStore
```

For example:

```text
E104

type:
SOURCE

origin:
read_file

location:
TokenValidator.java:60-90

content:
...
```

Then the context system references:

```text
E104
```

rather than copying everything repeatedly.

---

# 39. Evidence references

Your context can become:

```text
Relevant evidence:

[E104] TokenValidator.java:72
[E107] TokenValidatorTest.java:91
[E112] test failure
```

The model only receives the actual content selected by ContextEngine.

---

# 40. This creates an important separation

```text
ToolResult
    ↓
EvidenceStore
    ↓
ContextEngine
    ↓
Prompt
```

not:

```text
ToolResult
    ↓
Prompt
```

That separation is essential for the memory/context architecture we've been designing.

---

# 41. Tool output compression

A tool can return:

```text
raw result
```

then:

```text
ObservationNormalizer
```

produces:

```text
canonical result
```

For example:

```text
raw:
148 lines compiler output

normalized:
3 compilation errors
2 files
line locations
error messages
```

---

# 42. Never use the model for deterministic normalization

Don't do:

```text
compiler output
→ Gemma summarize
```

when a parser can do:

```text
compiler output
→ CompilerOutputParser
```

This saves:

* tokens
* inference time
* model calls
* hallucination opportunities

---

# 43. `ObservationNormalizer`

```java
public interface ObservationNormalizer {

    NormalizedObservation normalize(
        ToolResult result
    );
}
```

Each tool type can have a specialized normalizer.

---

# 44. Normalized observation

```java
public record NormalizedObservation(
    ObservationType type,

    List<Evidence> evidence,

    ObservationSummary summary,

    ObservationMetrics metrics
) {}
```

---

# 45. The model should receive summaries + evidence

For example:

```text
SEARCH RESULT

Query: expiresAt
Matches: 43
Selected: 6

Relevant:
[E21] TokenValidator.java:72
[E22] AuthService.java:41
[E23] TokenValidatorTest.java:93

Additional matches available.
```

That's an excellent small-model context.

---

# 46. Tool selection should also consider token cost

Add:

```java
public record ToolCost(
    int expectedInputTokens,
    int expectedOutputTokens,
    Duration expectedLatency
) {}
```

Then ToolSelector can prefer:

```text
find_symbol
```

over:

```text
search entire repository
```

when both could answer the question.

---

# 47. Tool economics

Eventually:

```text
Tool A:
cheap
high information density

Tool B:
expensive
low information density
```

Wayang should prefer A.

This is basically **active information gathering**.

---

# 48. Example

Question:

> Where is `TokenValidator` instantiated?

Possible tools:

```text
search("TokenValidator")
find_references("TokenValidator")
```

Wayang chooses:

```text
find_references
```

because:

```text
higher semantic precision
lower output
```

Excellent for local inference.

---

# 49. Tool chain

A common coding flow becomes:

```text
find_symbol
     ↓
read_file
     ↓
find_references
     ↓
read_file
     ↓
apply_patch
     ↓
run_test
     ↓
git_diff
```

But Gemma doesn't have to explicitly micromanage every transition.

The plan + tool policy can guide the process.

---

# 50. Tool permissions

Create:

```java
public interface ToolPermission {

    boolean allowed(
        ToolDescriptor tool,
        ToolExecutionContext context
    );
}
```

Rules can include:

```text
workspace boundary
risk
task phase
user approval
policy
```

---

# 51. Phase restrictions

This is particularly useful.

```java
public enum AgentPhase {

    DISCOVERY,
    DIAGNOSIS,
    IMPLEMENTATION,
    VERIFICATION,
    FINALIZATION
}
```

Example:

```text
DISCOVERY:
read/search only

DIAGNOSIS:
read/search/test

IMPLEMENTATION:
read/search/patch

VERIFICATION:
test/build/diff
```

This prevents weird actions.

---

# 52. Example protection

Gemma proposes:

```text
git_commit
```

while:

```text
phase = DIAGNOSIS
```

Gate:

```text
DENIED
```

No model reasoning required.

---

# 53. Shell tool

I would **not** expose a generic shell tool as the primary coding interface.

Prefer:

```text
run_test
run_build
run_linter
run_formatter
```

over:

```text
shell("whatever")
```

Because structured tools are:

* safer
* easier to validate
* easier to normalize
* easier for Gemma
* easier to reproduce

---

# 54. If shell is eventually needed

Make it constrained:

```java
public record ShellRequest(
    String command,
    List<String> arguments,
    ShellPolicy policy
) {}
```

Never:

```java
Runtime.exec(modelGeneratedString)
```

---

# 55. Shell allowlist

Example:

```yaml
shell:
  allowed:
    - npm
    - pnpm
    - cargo
    - go
    - gradle
    - mvn
    - pytest

  denied:
    - rm
    - sudo
    - chmod
    - chown
```

Even this should be treated as only one layer of protection.

---

# 56. Tool execution receipt

Every execution should generate:

```java
public record ToolReceipt(
    String executionId,
    String tool,
    Instant started,
    Instant finished,

    ToolExecutionStatus status,

    HashMap<String, String> fingerprints
) {}
```

This makes actions auditable and reproducible.

---

# 57. Checkpoint integration

Before mutating tools:

```text
DecisionGate
 ↓
CheckpointManager
 ↓
Executor
```

For example:

```text
apply_patch
 ↓
checkpoint C42
 ↓
apply
```

If patch/test sequence goes wrong:

```text
rollback C42
```

---

# 58. Tool event model

Everything can emit events:

```java
public sealed interface ToolEvent
    permits ToolStarted,
            ToolCompleted,
            ToolDenied,
            ToolFailed {}
```

This plugs nicely into the Wayang runtime/event architecture.

---

# 59. The complete Tool System

```text
                      ToolRegistry
                           │
                           ▼
                      ToolSelector
                           │
                           ▼
                    ToolDescriptor[]
                           │
                           ▼
                    Gemma ToolRequest
                           │
                           ▼
                     ToolValidator
                           │
                    ┌──────┴──────┐
                    │             │
                 denied          valid
                    │             │
                    ▼             ▼
                 Result       ToolExecutor
                                  │
                                  ▼
                               Tool
                                  │
                                  ▼
                              ToolResult
                                  │
                                  ▼
                         ObservationNormalizer
                                  │
                                  ▼
                             EvidenceStore
                                  │
                                  ▼
                           ContextEngine
                                  │
                                  ▼
                                Gemma
```

That's a strong architecture for Aljabr.

---

# 60. What I would implement first

Don't implement 30 tools immediately.

For **Aljabr v0.1**, I'd build exactly these:

```text
1. read_file
2. list_files
3. search
4. find_symbol
5. find_references
6. apply_patch
7. git_diff
8. run_test
9. run_build
```

These nine can already build a surprisingly capable coding agent.

Then:

```text
10. inspect_ast
11. run_linter
12. run_formatter
13. git_status
14. git_log
15. find_implementation
```

Later:

```text
shell
network
package management
browser
```

---

# 61. Most important local-model optimization

For your Gemma setup, I would make every tool have two representations:

### Full internal result

```text
complete data
```

### Model observation

```text
minimal useful representation
```

For example:

```text
Tool:
run_test

Internal:
120 KB output

Model:
FAILED
1 failed / 42 passed

TokenValidatorTest.expiredToken
TokenValidator.java:72
expected false
actual true
```

That distinction should become a **first-class Wayang primitive**, not an ad-hoc optimization.

---

# 62. And this connects everything we've built

At this point the architecture is becoming:

```text
                 ┌─────────────────────┐
                 │ CapabilityDetector  │
                 └──────────┬──────────┘
                            ↓
                     RuntimeProfile
                            ↓
                     PolicyResolver
                            ↓
                      RuntimePolicy
                            │
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
 ContextEngine        ToolSystem           MemorySystem
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ↓
                         Aljabr
                            ↓
                         Planner
                            ↓
                         Observer
                            ↓
                         Gemma
                            ↓
                      DecisionGate
                            ↓
                         Executor
                            ↓
                         Verifier
                            ↓
                        Replanner
                            ↓
                         Checkpoint
```

This is now a **real runtime architecture**, rather than just a prompt wrapper around an LLM.

---

## Next milestone

The next piece I'd tackle is **`EvidenceStore + ObservationNormalizer + ContextEngine` as one concrete pipeline**.

That's where the local-model optimization becomes particularly powerful:

```text
raw tool output
    ↓
normalize
    ↓
evidence
    ↓
deduplicate
    ↓
rank
    ↓
compress only when necessary
    ↓
build task-specific context
    ↓
Gemma
```

And I'd make that implementation concrete enough to show the actual Java classes, storage model, evidence IDs, token budgeting, deduplication, relevance scoring, and **how Aljabr avoids re-reading/re-searching the same code after a connection loss or repeated reasoning cycle**.


Absolutely. **Next milestone: `EvidenceStore → ObservationNormalizer → ContextEngine`**.

This is probably the most important subsystem for making Aljabr work well with **Gemma 12B on 16 GB**, because it determines *what the model actually sees*.

The key principle:

> **Never treat context as a transcript. Treat it as a queryable evidence projection.**

---

# Milestone — Evidence & Context Pipeline

```text
                    TOOL
                     │
                     ▼
                 ToolResult
                     │
                     ▼
          ObservationNormalizer
                     │
                     ▼
                  Evidence
                     │
                     ▼
               EvidenceStore
                     │
             ┌───────┴────────┐
             │                │
       deduplicate          index
             │                │
             └───────┬────────┘
                     ▼
              ContextSelector
                     │
                     ▼
              ContextController
                     │
                     ▼
             ContextAssembler
                     │
                     ▼
                Context
                     │
                     ▼
                  Gemma
```

And importantly:

```text
Tool output ≠ Context
Evidence ≠ Context
Context = projection of Evidence
```

That distinction gives you a lot of flexibility.

---

# 1. `Evidence`

First, establish a canonical evidence object.

```java
public record Evidence(
    EvidenceId id,

    EvidenceType type,

    EvidenceSource source,

    String subject,

    EvidenceContent content,

    EvidenceMetadata metadata
) {}
```

For example:

```text
E104
type:
SOURCE_CODE

source:
read_file

subject:
TokenValidator.java

content:
lines 60-90

metadata:
line range
language
hash
timestamp
```

---

# 2. Evidence IDs

Use stable IDs.

```java
public record EvidenceId(String value) {}
```

For example:

```text
E104
E105
E106
```

But internally I'd actually use something like:

```text
task-id + sequence
```

or a UUID/ULID.

The important part is:

> The model can refer to evidence without reproducing it.

---

# 3. Evidence types

```java
public enum EvidenceType {

    SOURCE_CODE,
    SEARCH_RESULT,
    SYMBOL,
    REFERENCE,
    TEST_RESULT,
    BUILD_RESULT,
    DIFF,
    GIT_STATE,
    FILE_METADATA,
    USER_INPUT,
    MODEL_DECISION,
    ERROR,
    DOCUMENTATION
}
```

This becomes very useful for ranking.

---

# 4. `EvidenceSource`

```java
public record EvidenceSource(
    String tool,
    String executionId,
    Instant timestamp
) {}
```

So we know:

```text
E104
came from:
read_file
execution:
T-9281
```

---

# 5. Evidence metadata

```java
public record EvidenceMetadata(
    Optional<String> path,

    Optional<Integer> startLine,
    Optional<Integer> endLine,

    Optional<String> contentHash,

    Set<String> tags,

    EvidenceFreshness freshness
) {}
```

Now evidence can be compared without loading the full content.

---

# 6. Evidence content

Don't force everything into a String.

```java
public sealed interface EvidenceContent
    permits TextContent,
            SourceContent,
            StructuredContent {}
```

For example:

```java
public record TextContent(
    String text
) implements EvidenceContent {}
```

```java
public record SourceContent(
    String language,
    List<SourceLine> lines
) implements EvidenceContent {}
```

```java
public record StructuredContent(
    JsonNode data
) implements EvidenceContent {}
```

This matters because a test result should not be treated like source code.

---

# 7. `EvidenceStore`

```java
public interface EvidenceStore {

    Uni<EvidenceId> put(Evidence evidence);

    Uni<Optional<Evidence>> get(EvidenceId id);

    Uni<List<Evidence>> getMany(
        Collection<EvidenceId> ids
    );

    Uni<Void> remove(EvidenceId id);

    Uni<List<Evidence>> query(
        EvidenceQuery query
    );
}
```

---

# 8. Evidence query

```java
public record EvidenceQuery(
    Optional<EvidenceType> type,

    Optional<String> subject,

    Set<String> tags,

    Optional<String> path,

    int limit
) {}
```

Later this can be backed by:

```text
SQLite
RocksDB
Postgres
in-memory
embedded index
```

For local Aljabr I'd start extremely simple.

---

# 9. Start with SQLite

For your use case, SQLite is attractive because:

```text
single file
+
persistent
+
local
+
transactional
+
no server
```

Something like:

```text
.aljabr/
    state.db
```

This also gives you recovery after a process crash.

---

# 10. Don't store gigantic blobs in every table

Use:

```text
evidence
evidence_content
evidence_relation
evidence_index
```

or equivalent storage abstraction.

Metadata can remain cheap to query.

---

# 11. Evidence hash

This is one of the most important mechanisms.

```java
public record ContentFingerprint(
    String algorithm,
    String hash
) {}
```

For source:

```text
SHA-256(file path + relevant content)
```

Then:

```text
same content
→ same fingerprint
→ don't create duplicate evidence
```

---

# 12. Why this matters

Suppose Gemma asks:

```text
read TokenValidator.java
```

Then later asks:

```text
read TokenValidator.java again
```

Wayang checks:

```text
fingerprint exists?
```

If unchanged:

```text
reuse E104
```

No filesystem read.

No token cost.

No repeated observation.

---

# 13. Freshness

But don't blindly reuse forever.

```java
public enum EvidenceFreshness {

    CURRENT,
    STALE,
    UNKNOWN
}
```

For example:

```text
file modified?
    ↓
yes
    ↓
invalidate evidence
```

---

# 14. File fingerprint

A lightweight mechanism:

```text
path
+
mtime
+
size
+
optional content hash
```

For a small local project, you can cheaply check:

```text
mtime + size
```

before calculating a full hash.

---

# 15. Evidence invalidation

```java
public interface EvidenceInvalidator {

    Uni<Void> invalidateFor(
        WorkspaceChange change
    );
}
```

When:

```text
apply_patch(TokenValidator.java)
```

then:

```text
invalidate:
E104
E105
...
```

for affected source observations.

---

# 16. Dependency-aware invalidation

Even better:

```text
TokenValidator.java
       │
       ├── E104 source
       ├── E109 symbol
       └── E113 references
```

When the file changes:

```text
invalidate E104
invalidate E109
invalidate E113
```

but perhaps preserve:

```text
E122 test failure
```

because that is historical evidence.

This distinction is important.

---

# 17. Current vs historical evidence

Don't delete old evidence.

Mark it:

```java
public enum EvidenceState {

    CURRENT,
    SUPERSEDED,
    HISTORICAL,
    INVALID
}
```

Why?

Because later the agent may need to understand:

```text
before patch
vs
after patch
```

---

# 18. Evidence relations

Now we can build a graph.

```java
public record EvidenceRelation(
    EvidenceId from,
    EvidenceRelationType type,
    EvidenceId to
) {}
```

Types:

```java
public enum EvidenceRelationType {

    SUPPORTS,
    CONTRADICTS,
    DERIVED_FROM,
    SUPERSEDES,
    INVALIDATES,
    REFERENCES,
    CAUSED_BY,
    VALIDATES
}
```

---

# 19. Example evidence graph

```text
E101
TokenValidator.java:72
      │
      │ SUPPORTS
      ▼
E108
Model decision:
expiration comparison is wrong
      │
      │ CAUSED_BY
      ▼
E115
Test failure
```

Then:

```text
E130
patch
      │
      ▼
E131
new test result
```

This becomes a lightweight **reasoning substrate**.

---

# 20. But don't give the entire graph to Gemma

This is important.

The graph is for Wayang.

Gemma receives only:

```text
relevant evidence
```

The runtime handles the complexity.

---

# 21. `ObservationNormalizer`

Now connect tools to evidence.

```java
public interface ObservationNormalizer {

    Uni<List<Evidence>> normalize(
        ToolResult result
    );
}
```

Example:

```text
RunTestTool
 ↓
raw process output
 ↓
JUnit parser
 ↓
TestResult
 ↓
Evidence
```

---

# 22. Specialized normalizers

```text
ReadFileNormalizer
SearchNormalizer
TestNormalizer
BuildNormalizer
DiffNormalizer
GitNormalizer
```

Do not make one giant parser.

---

# 23. Search normalization

Raw:

```text
grep output:
...
```

Normalized:

```text
SEARCH_RESULT

query:
expiresAt

matches:
43

selected:
6

E201:
TokenValidator.java:72

E202:
AuthService.java:41
```

The raw output can remain stored separately.

---

# 24. Context should never depend on raw output

This is a very useful rule:

> **Raw tool output is diagnostic data. Normalized evidence is reasoning data.**

That means model context is deterministic.

---

# 25. `ContextRequest`

Now the ContextEngine starts.

```java
public record ContextRequest(
    TaskState task,

    PlanStep step,

    Optional<ReasoningTrigger> trigger,

    ContextBudget budget
) {}
```

---

# 26. ContextBudget

This connects directly to the CapabilityDetector work.

```java
public record ContextBudget(
    int maxInputTokens,

    int reservedOutputTokens,

    int maxEvidenceItems,

    int maxSourceLines
) {}
```

Example local Gemma:

```text
maxInputTokens:
~6000

reservedOutput:
~1000

working context:
~5000
```

The exact numbers should come from runtime capability/policy rather than hardcoded values.

---

# 27. Context selection

```java
public interface ContextSelector {

    Uni<List<EvidenceId>> select(
        ContextRequest request
    );
}
```

This is where relevance happens.

---

# 28. Evidence scoring

Don't just use:

```text
latest evidence first
```

Use multiple signals.

For example:

```text
score =
    task relevance
  + step relevance
  + evidence type relevance
  + dependency relevance
  + freshness
  + explicit reference
  + recency
  - redundancy
  - size penalty
```

---

# 29. Concrete score

Something like:

```java
double score(Evidence e, ContextRequest request) {

    return
        0.30 * taskRelevance(e, request)
      + 0.20 * stepRelevance(e, request)
      + 0.15 * typeRelevance(e, request)
      + 0.10 * freshness(e)
      + 0.10 * dependencyRelevance(e)
      + 0.10 * explicitReference(e)
      - 0.05 * redundancy(e);
}
```

Don't obsess over these coefficients initially.

Make them configurable.

---

# 30. Explicit evidence is extremely valuable

Suppose Gemma previously said:

```text
Evidence E104 is relevant.
```

Then:

```text
E104
```

should get a strong boost in the next context.

This creates continuity without replaying the entire conversation.

---

# 31. Context sections

Don't assemble one giant string.

Use sections:

```java
public record Context(
    ContextHeader header,

    List<ContextSection> sections,

    ContextMetrics metrics
) {}
```

---

# 32. Context section

```java
public record ContextSection(
    ContextSectionType type,

    String title,

    List<EvidenceId> evidence,

    String renderedContent
) {}
```

Types:

```java
public enum ContextSectionType {

    TASK,
    PLAN,
    CONSTRAINTS,
    CURRENT_STATE,
    RELEVANT_CODE,
    TEST_FAILURES,
    CHANGES,
    DECISION_HISTORY,
    USER_REQUIREMENTS
}
```

---

# 33. Section priority

For local models:

```text
TASK
 ↓
CURRENT STEP
 ↓
CONSTRAINTS
 ↓
DIRECT EVIDENCE
 ↓
FAILURES
 ↓
RELEVANT HISTORY
```

not:

```text
full history
 ↓
full history
 ↓
full history
 ↓
task buried at bottom
```

---

# 34. Context ordering

I recommend:

```text
SYSTEM/ROLE
TASK
CURRENT PLAN STEP
CONSTRAINTS
CURRENT WORKSPACE STATE
PRIMARY EVIDENCE
SECONDARY EVIDENCE
RECENT VERIFICATION
OUTPUT SCHEMA
```

The exact prompt syntax can vary.

The semantic ordering is what matters.

---

# 35. Context deduplication

Suppose:

```text
E104:
TokenValidator.java lines 60-90

E110:
Search result showing TokenValidator.java:72
```

Both contain the same fact.

Don't send both.

Use:

```java
EvidenceDeduplicator
```

---

# 36. Deduplication strategy

First:

```text
content hash
```

Then:

```text
normalized text fingerprint
```

Then optionally:

```text
semantic similarity
```

But don't immediately use embeddings.

For your local system:

> deterministic deduplication first.

It's cheaper and predictable.

---

# 37. Context compaction

If context exceeds budget:

```text
select
 ↓
deduplicate
 ↓
remove low-value evidence
 ↓
compress sections
 ↓
recalculate
```

Only then consider summarization.

---

# 38. Very important:

Do **not** automatically summarize everything.

Suppose the source is:

```java
if (expiresAt.isBefore(now)) {
    return INVALID;
}
```

A summary like:

```text
"Expiration is checked."
```

destroyed useful information.

So:

> **Compress metadata before compressing source semantics.**

---

# 39. Compression hierarchy

Use:

```text
Level 0:
exact evidence

Level 1:
trim irrelevant lines

Level 2:
reduce duplicate evidence

Level 3:
replace repeated context with references

Level 4:
structured summary

Level 5:
model-generated summary
```

Try to remain at levels 0–3.

---

# 40. ContextController

This is where your earlier architecture comes together.

```java
public interface ContextController {

    ContextBudget calculateBudget(
        RuntimeProfile profile,
        TaskState state
    );

    ContextDecision decide(
        ContextRequest request
    );
}
```

---

# 41. Local model policy

For Gemma 12B:

```text
prefer:
- compact context
- structured evidence
- current task
- current step
- relevant source
- recent failures

avoid:
- long transcripts
- redundant search results
- verbose reasoning
- repeated instructions
- irrelevant files
```

---

# 42. Large cloud model policy

If capability is large:

```text
allow:
- more evidence
- larger source windows
- broader history
- more plan context
- richer documentation
```

But here's the important architectural point:

> **The evidence architecture doesn't change. Only the projection budget changes.**

That means you don't need two completely different Aljabr systems.

---

# 43. Same EvidenceStore, different ContextPolicy

Local:

```text
Evidence:
100 items

Context:
8 items
```

Large model:

```text
Evidence:
100 items

Context:
35 items
```

Same underlying knowledge.

Different projection.

That's exactly the strategy you were asking about earlier.

---

# 44. `ContextPolicy`

```java
public interface ContextPolicy {

    ContextBudget budget(
        RuntimeProfile profile
    );

    SelectionStrategy selectionStrategy();

    CompressionPolicy compressionPolicy();
}
```

---

# 45. Example policies

```java
public enum ContextMode {

    COMPACT,
    BALANCED,
    EXPANSIVE
}
```

Then:

```text
local 12B → COMPACT
medium    → BALANCED
large     → EXPANSIVE
```

But don't rely solely on model size.

Use:

```text
context window
+
actual available memory
+
latency budget
+
inference engine
+
task complexity
```

---

# 46. Context mode should be dynamic

For example:

```text
simple bug
→ COMPACT

complex architecture refactor
→ BALANCED

large model + huge repository analysis
→ EXPANSIVE
```

So:

```java
ContextMode mode =
    policyResolver.resolve(task, runtimeProfile);
```

---

# 47. Context pressure

This is a useful runtime metric.

```java
public record ContextPressure(
    int estimatedTokens,
    int availableTokens,
    double ratio
) {}
```

Example:

```text
estimated:
4800

available:
5000

pressure:
0.96
```

Now the controller knows it needs to compact.

---

# 48. Don't wait until overflow

Set thresholds:

```text
< 0.70
healthy

0.70–0.85
watch

0.85–0.95
compact

> 0.95
aggressive reduction
```

Again, policy configurable.

---

# 49. Context assembly

Actual implementation:

```java
public Uni<Context> build(
    ContextRequest request
) {

    return selector.select(request)
        .flatMap(evidenceStore::getMany)
        .map(deduplicator::deduplicate)
        .map(evidence -> ranker.rank(
            evidence,
            request
        ))
        .map(evidence -> budgeter.fit(
            evidence,
            request.budget()
        ))
        .map(renderer::render);
}
```

This is the core.

---

# 50. `ContextRenderer`

Keep rendering separate.

```java
public interface ContextRenderer {

    Context render(
        ContextBuildInput input
    );
}
```

Why?

Because later you can support:

```text
Gemma prompt
OpenAI prompt
Claude prompt
structured API input
local llama.cpp format
```

without changing evidence selection.

---

# 51. Model-specific rendering

For Gemma:

```text
TASK
...
CURRENT STEP
...
EVIDENCE
...
DECISION FORMAT
...
```

For another model:

```text
system
user
tool
assistant
```

Same underlying context.

---

# 52. Context cache

Now we reach the optimization you asked about earlier.

Cache the **context projection**, not just raw files.

```java
public interface ContextCache {

    Optional<CachedContext> get(
        ContextFingerprint fingerprint
    );

    void put(
        ContextFingerprint fingerprint,
        Context context
    );
}
```

---

# 53. Context fingerprint

It should include:

```text
task
+
step
+
evidence IDs
+
evidence versions
+
policy version
+
model profile
```

So:

```text
same evidence
+
same task
+
same policy
=
reusable context
```

---

# 54. Don't cache blindly

If:

```text
TokenValidator.java
```

changes:

```text
E104 version changes
```

therefore:

```text
context fingerprint changes
```

and the old context is naturally invalid.

---

# 55. This helps connection loss

Suppose:

```text
Aljabr
 ↓
search
 ↓
read
 ↓
build context
 ↓
Gemma inference
 ↓
network/inference failure
```

Don't start over.

Persist:

```text
TaskState
Evidence
Plan
Tool receipts
Context fingerprint
```

Then:

```text
resume
 ↓
check cached context
 ↓
reuse
 ↓
retry inference
```

No repeated repository scan.

---

# 56. Checkpoint state

Persist:

```java
public record AgentCheckpoint(
    TaskId taskId,

    AgentPhase phase,

    AgentPlan plan,

    Set<EvidenceId> evidence,

    Set<EvidenceId> invalidEvidence,

    Optional<ContextFingerprint> lastContext,

    LoopBudget budget,

    Instant timestamp
) {}
```

---

# 57. Recovery flow

```text
PROCESS CRASH
     ↓
load checkpoint
     ↓
validate workspace fingerprint
     ↓
validate evidence freshness
     ↓
invalidate changed evidence
     ↓
reuse remaining evidence
     ↓
resume current step
```

This is much better than:

```text
"Start agent again."
```

---

# 58. Workspace fingerprint

Checkpoint should include:

```java
public record WorkspaceFingerprint(
    String repositoryState,
    Optional<String> gitHead,
    String relevantFilesFingerprint
) {}
```

If the workspace changed externally:

```text
checkpoint may no longer be valid
```

Then Aljabr performs targeted re-observation.

---

# 59. This also prevents hallucinated continuity

Suppose Gemma previously believed:

```text
TokenValidator.java:72
```

But user changed the file.

Wayang detects:

```text
E104 stale
```

and refuses to reuse it as current evidence.

Excellent.

---

# 60. The complete pipeline

Now:

```text
                     TOOL
                       │
                       ▼
                  ToolResult
                       │
                       ▼
            ObservationNormalizer
                       │
                       ▼
                    Evidence
                       │
                       ▼
                 EvidenceStore
                  │          │
                  │          └──► EvidenceGraph
                  │
                  ▼
               Selector
                  │
                  ▼
             Deduplicator
                  │
                  ▼
                Ranker
                  │
                  ▼
             ContextBudget
                  │
                  ▼
              Compressor
                  │
                  ▼
             ContextRenderer
                  │
                  ▼
                Context
                  │
                  ▼
                Gemma
```

---

# 61. The really important architectural boundary

At this point Aljabr has three different kinds of information:

### Raw reality

```text
filesystem
compiler
tests
git
processes
```

### Evidence

```text
what Wayang observed
```

### Model context

```text
what Gemma is currently allowed to see
```

These must **not** be conflated.

```text
Reality
  ↓
Observation
  ↓
Evidence
  ↓
Context Projection
  ↓
Model
```

That is probably one of the most important foundational concepts in the entire Aljabr design.

---

# 62. Why this is particularly good for Gemma 12B

You aren't making the model smarter.

You're making the problem smaller.

Instead of asking:

```text
"Understand this entire repository."
```

you ask:

```text
"Given these 5 relevant pieces of evidence,
determine the next action."
```

The model's reasoning capacity is therefore spent on the **actual decision**, not on rediscovering the environment.

---

# 63. One more optimization: `Context Purpose`

I'd add this now.

```java
public enum ContextPurpose {

    PLAN,
    DIAGNOSE,
    SELECT_TOOL,
    IMPLEMENT,
    INTERPRET_FAILURE,
    VERIFY,
    REPLAN,
    FINAL_RESPONSE
}
```

Because context requirements differ dramatically.

For example:

### Diagnose

```text
source
references
tests
errors
```

### Implement

```text
target source
interfaces
constraints
relevant tests
```

### Verify

```text
diff
test result
build result
requirements
```

There is no reason to send the same context for all three.

---

# 64. This should be the final selection API

```java
public record ContextRequest(
    TaskState task,
    PlanStep step,
    ContextPurpose purpose,
    ContextBudget budget
) {}
```

Then:

```java
Context context =
    contextEngine.build(request);
```

The ContextEngine determines the projection.

---

# 65. Final architecture after this milestone

```text
                    ┌──────────────────┐
                    │ RuntimeProfile   │
                    └────────┬─────────┘
                             ▼
                       ContextPolicy
                             │
                             ▼
                         AljabrAgent
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
       Planner            Observer          Memory
          │                  │                  │
          │                  ▼                  │
          │              ToolSystem             │
          │                  │                  │
          │                  ▼                  │
          │             ToolResult              │
          │                  │                  │
          │                  ▼                  │
          │        ObservationNormalizer       │
          │                  │                  │
          │                  ▼                  │
          └────────────► EvidenceStore ◄────────┘
                             │
                             ▼
                       ContextSelector
                             │
                             ▼
                       ContextController
                             │
                             ▼
                        ContextEngine
                             │
                             ▼
                           Gemma
                             │
                             ▼
                       DecisionGate
                             │
                             ▼
                         Executor
                             │
                             ▼
                         Verifier
                             │
                       ┌─────┴─────┐
                       ▼           ▼
                     PASS        FAIL
                       │           │
                       ▼           ▼
                     Next       Replanner
```

---

## What I'd build next

The next milestone should be **`AgentState + Checkpoint + Recovery`**.

We've now established:

```text
Tool → Evidence → Context → Model → Decision → Execution → Verification
```

The missing piece is making that process **durable**:

```text
TaskState
PlanState
StepState
EvidenceState
ToolState
InferenceState
Checkpoint
Recovery
Resume
Pause
Cancel
Retry
```

That is especially important for Aljabr because local inference can be interrupted, the Mac can sleep, a model process can crash, or a long coding task can span multiple sessions.

And this is where we can make the earlier **"don't repeat research/thought after connection loss"** requirement concrete rather than merely caching prompts.


Yes. **Next milestone: `AgentState → Checkpoint → Recovery → Resume`.**

This is where Aljabr stops behaving like a one-shot coding assistant and becomes a **durable coding agent**.

The key design principle:

> **Persist state transitions, not conversations.**

You don't need to save every token Gemma generated. You need to save enough structured state to reconstruct exactly where the agent is and what it already knows.

---

# 1. The durable agent loop

We want this:

```text
┌──────────────────────────────────────┐
│              Aljabr                  │
│                                      │
│  PLAN                                │
│   ↓                                  │
│  OBSERVE                             │
│   ↓                                  │
│  DECIDE                              │
│   ↓                                  │
│  EXECUTE                             │
│   ↓                                  │
│  VERIFY                              │
│   ↓                                  │
│  UPDATE STATE                        │
│   ↓                                  │
│  CHECKPOINT                          │
│   ↓                                  │
│  NEXT STEP                           │
└──────────────────────────────────────┘
```

If the process dies anywhere:

```text
                 crash
                   ↓
             load checkpoint
                   ↓
             validate state
                   ↓
             invalidate stale data
                   ↓
             resume exact step
```

---

# 2. `AgentState`

Don't make one giant mutable object.

Split state by responsibility.

```java
public record AgentState(
    TaskState task,
    PlanState plan,
    ExecutionState execution,
    EvidenceState evidence,
    WorkspaceState workspace,
    InferenceState inference,
    BudgetState budget,
    AgentLifecycle lifecycle
) {}
```

This is much easier to reason about.

---

# 3. `TaskState`

```java
public record TaskState(
    TaskId id,
    String userRequest,

    TaskStatus status,

    Instant createdAt,
    Instant updatedAt
) {}
```

```java
public enum TaskStatus {
    CREATED,
    PLANNING,
    RUNNING,
    WAITING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}
```

---

# 4. `PlanState`

The plan should be structured.

```java
public record PlanState(
    List<PlanStep> steps,
    int currentStep
) {}
```

And:

```java
public record PlanStep(
    StepId id,
    String objective,

    StepStatus status,

    List<EvidenceId> requiredEvidence,

    List<EvidenceId> producedEvidence
) {}
```

---

# 5. Don't store the plan as prose

Avoid:

```text
"I will inspect the authentication code,
then probably modify the validator..."
```

Instead:

```json
{
  "id": "step-03",
  "objective": "Fix token expiration comparison",
  "status": "READY",
  "requiredEvidence": [
    "E104",
    "E107"
  ]
}
```

This is much easier for a small model and runtime.

---

# 6. `ExecutionState`

```java
public record ExecutionState(
    Optional<ToolExecutionId> activeTool,
    List<ToolReceipt> recentExecutions,
    int retryCount
) {}
```

If Aljabr crashes during a tool call, you know what happened.

---

# 7. Tool execution idempotency

This is critical.

Every tool call gets:

```java
public record ToolExecutionId(
    String value
) {}
```

Suppose:

```text
apply_patch
execution = EX-9281
```

The process crashes after the filesystem changed but before the runtime recorded success.

On recovery:

```text
"Did EX-9281 actually happen?"
```

The tool can inspect its receipt/fingerprint.

This prevents:

```text
patch
→ crash
→ retry
→ patch same code twice
```

---

# 8. `ExecutionReceipt`

Make mutation receipts durable.

```java
public record ExecutionReceipt(
    ToolExecutionId executionId,

    String toolName,

    ExecutionStatus status,

    Instant startedAt,
    Instant finishedAt,

    Optional<String> inputFingerprint,
    Optional<String> outputFingerprint
) {}
```

For a patch:

```text
input fingerprint
+
before-file hash
+
patch hash
```

and:

```text
after-file hash
```

---

# 9. `EvidenceState`

```java
public record EvidenceState(
    Set<EvidenceId> active,
    Set<EvidenceId> stale,
    Set<EvidenceId> historical
) {}
```

But I would actually avoid storing the full evidence objects inside `AgentState`.

The state should contain references.

```text
AgentState
    ↓
Evidence IDs
    ↓
EvidenceStore
```

This keeps checkpoints small.

---

# 10. `WorkspaceState`

```java
public record WorkspaceState(
    String root,

    Optional<String> gitHead,

    WorkspaceFingerprint fingerprint,

    Instant observedAt
) {}
```

The fingerprint lets us detect external changes.

---

# 11. Workspace fingerprint

For example:

```java
public record WorkspaceFingerprint(
    Optional<String> gitHead,
    String fileTreeHash,
    long modifiedFileCount
) {}
```

You don't necessarily need to hash the entire repository every time.

Use an incremental mechanism.

---

# 12. `InferenceState`

This one is easy to get wrong.

Don't persist raw chain-of-thought.

Persist **decision state**.

```java
public record InferenceState(
    Optional<InferenceId> lastInference,
    Optional<Decision> lastDecision,

    int inferenceCount,

    Optional<String> modelProfile
) {}
```

For example:

```java
public record Decision(
    DecisionType type,
    Optional<String> selectedTool,
    Optional<String> selectedStep,
    List<EvidenceId> supportingEvidence
) {}
```

---

# 13. Example

Instead of saving hidden reasoning like:

```text
"I think the expiration logic might..."
```

save:

```json
{
  "type": "EXECUTE_TOOL",
  "tool": "read_file",
  "supportingEvidence": ["E104"],
  "step": "step-03"
}
```

That's enough to resume.

---

# 14. `BudgetState`

Make budgets durable too.

```java
public record BudgetState(
    int toolCalls,
    int inferenceCalls,

    long inputTokens,
    long outputTokens,

    Duration elapsed
) {}
```

This prevents recovery from accidentally resetting:

```text
100 tool calls
```

back to:

```text
0 tool calls
```

and bypassing the agent's limits.

---

# 15. Lifecycle

```java
public enum AgentLifecycle {

    CREATED,
    RUNNING,
    WAITING_FOR_USER,
    PAUSED,
    RECOVERING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

---

# 16. State machine

Now define legal transitions.

```text
CREATED
   ↓
PLANNING
   ↓
RUNNING
   ↓
VERIFYING
   ↓
COMPLETED
```

Failure:

```text
RUNNING
   ↓
FAILED
   ↓
RECOVERING
   ↓
RUNNING
```

User pause:

```text
RUNNING
   ↓
PAUSED
   ↓
RUNNING
```

---

# 17. Never let random code mutate lifecycle

Use:

```java
public interface AgentStateMachine {

    AgentState transition(
        AgentState state,
        AgentEvent event
    );
}
```

Example:

```java
public record ToolCompleted(
    ToolExecutionId executionId,
    ToolResult result
) implements AgentEvent {}
```

Then:

```text
ToolCompleted
      ↓
state machine
      ↓
new AgentState
```

This gives you deterministic transitions.

---

# 18. Event sourcing — but keep it lightweight

I would **not** immediately build a massive event-sourcing framework.

Instead persist:

```text
current state
+
append-only important events
```

For Aljabr:

```text
state.db
```

could contain:

```text
tasks
plans
steps
evidence_refs
tool_executions
checkpoints
events
```

---

# 19. `AgentEvent`

```java
public sealed interface AgentEvent
    permits
        TaskCreated,
        PlanCreated,
        StepStarted,
        ToolRequested,
        ToolCompleted,
        EvidenceAdded,
        DecisionMade,
        PatchApplied,
        VerificationCompleted,
        CheckpointCreated,
        TaskCompleted {}
```

This gives you a very useful audit trail.

---

# 20. Why events matter

Suppose the user asks:

> Why did Aljabr change this file?

You can reconstruct:

```text
user request
 ↓
step-03
 ↓
E104 source
 ↓
test failure E107
 ↓
decision D12
 ↓
patch EX92
 ↓
verification E115
```

That is much better than:

> "The model decided to."

---

# 21. `Checkpoint`

Now create the actual persistence object.

```java
public record AgentCheckpoint(
    CheckpointId id,

    TaskId taskId,

    long stateVersion,

    AgentState state,

    Instant createdAt
) {}
```

---

# 22. Version everything

This is important for recovery.

```text
checkpoint:
version 41
```

Current runtime:

```text
version 42
```

If two processes try to modify the task:

```text
expected version = 41
actual version = 42
```

Reject the stale update.

This is optimistic concurrency.

---

# 23. `CheckpointStore`

```java
public interface CheckpointStore {

    Uni<Optional<AgentCheckpoint>> latest(
        TaskId taskId
    );

    Uni<Void> save(
        AgentCheckpoint checkpoint
    );

    Uni<List<AgentCheckpoint>> history(
        TaskId taskId
    );
}
```

---

# 24. When to checkpoint

Don't checkpoint every token.

That's wasteful.

Checkpoint after meaningful boundaries:

```text
plan created
step started
tool completed
patch applied
verification completed
important decision
pause
before risky mutation
```

---

# 25. Checkpoint policy

```java
public interface CheckpointPolicy {

    boolean shouldCheckpoint(
        AgentEvent event,
        AgentState state
    );
}
```

For example:

```text
ToolCompleted(read_file)
→ maybe no checkpoint

ToolCompleted(apply_patch)
→ YES

VerificationCompleted
→ YES
```

---

# 26. Atomic checkpoint

The critical operation:

```text
state update
+
checkpoint
+
event
```

should be transactionally coordinated.

Conceptually:

```text
BEGIN

append event
update state
create checkpoint

COMMIT
```

If it fails:

```text
ROLLBACK
```

SQLite is very useful here.

---

# 27. Recovery manager

```java
public interface RecoveryManager {

    Uni<RecoveryResult> recover(
        TaskId taskId
    );
}
```

---

# 28. Recovery flow

```text
load latest checkpoint
        ↓
validate state schema
        ↓
validate workspace
        ↓
validate evidence
        ↓
inspect unfinished tool
        ↓
reconcile tool receipt
        ↓
repair state
        ↓
resume
```

---

# 29. Workspace reconciliation

This is especially important.

Checkpoint says:

```text
TokenValidator.java hash = A
```

Current filesystem:

```text
TokenValidator.java hash = B
```

Then:

```text
external modification detected
```

Don't blindly continue.

Instead:

```text
invalidate affected evidence
re-observe affected files
recalculate plan assumptions
```

---

# 30. Tool reconciliation

Suppose checkpoint says:

```text
EX-42 = RUNNING
```

but process crashed.

Recovery asks:

```text
Does the tool have a durable receipt?
```

If:

```text
EX-42 = SUCCESS
```

then:

```text
mark completed
```

If:

```text
EX-42 = UNKNOWN
```

then use tool-specific reconciliation.

---

# 31. Tool idempotency classes

Add:

```java
public enum Idempotency {

    IDEMPOTENT,
    SAFE_TO_RETRY,
    REQUIRES_RECONCILIATION,
    NEVER_AUTO_RETRY
}
```

Examples:

```text
read_file
→ IDEMPOTENT

search
→ IDEMPOTENT

run_test
→ SAFE_TO_RETRY

apply_patch
→ REQUIRES_RECONCILIATION

git_push
→ NEVER_AUTO_RETRY
```

This is extremely useful.

---

# 32. Retry policy

```java
public record RetryPolicy(
    int maxAttempts,
    Duration backoff,
    Set<ToolExecutionStatus> retryable
) {}
```

Don't let the model decide retry count.

Runtime owns that.

---

# 33. Inference retry

Inference is different.

Suppose:

```text
Gemma generated:
invalid JSON
```

Don't restart the whole task.

Use:

```text
same context
+
structured error
+
small retry
```

For example:

```text
Your previous response did not match the required action schema.
Return only the required action.
```

This is another reason to persist context fingerprint.

---

# 34. Inference checkpoint

Persist:

```java
public record InferenceAttempt(
    InferenceId id,

    ContextFingerprint context,

    Optional<Decision> decision,

    InferenceStatus status,

    int attempt
) {}
```

Then you know:

```text
context X
attempt 1
failed parsing
```

and can retry intelligently.

---

# 35. Don't persist full prompt unnecessarily

You already have:

```text
ContextFingerprint
```

and:

```text
Evidence IDs
```

Therefore you can reconstruct the prompt.

Store raw prompt only optionally for debugging.

This keeps local storage smaller.

---

# 36. `ResumeStrategy`

Recovery doesn't always mean "continue exactly".

Use:

```java
public enum ResumeStrategy {

    CONTINUE,
    REOBSERVE,
    REPLAN,
    RETRY,
    ROLLBACK,
    ASK_USER
}
```

---

# 37. Recovery decision

Example:

```text
No workspace changes
+
tool completed
+
evidence current
+
plan valid

→ CONTINUE
```

But:

```text
workspace changed externally
+
important source changed

→ REOBSERVE
```

And:

```text
patch partially applied
+
state uncertain

→ RECONCILE / ROLLBACK
```

---

# 38. Recovery should be deterministic

Do not ask Gemma:

> "What should we do after recovering?"

First let runtime determine the safe recovery state.

Only if ambiguity remains:

```text
RecoveryManager
      ↓
ASK MODEL
```

This saves inference.

---

# 39. Resume example

Imagine:

```text
Task:
Fix expired-token bug
```

Aljabr did:

```text
Step 1
search TokenValidator
✓

Step 2
read TokenValidator
✓

Step 3
read tests
✓

Step 4
apply patch
✓

Step 5
run test
```

Crash occurs here.

Checkpoint:

```text
step = 5
patch = completed
test = RUNNING
```

Recovery:

```text
load checkpoint
 ↓
inspect test execution
 ↓
execution disappeared
 ↓
retry test
```

No search.

No re-reading source.

No replanning.

That's the desired behavior.

---

# 40. Another example: external modification

Suppose:

```text
Aljabr checkpoint:
TokenValidator.java = hash A
```

User edits the file:

```text
hash B
```

Recovery:

```text
detect B != A
       ↓
invalidate source evidence
       ↓
invalidate dependent decisions
       ↓
preserve unrelated evidence
       ↓
re-read file
       ↓
re-evaluate current step
```

That's intelligent recovery.

---

# 41. Dependency invalidation

This is where your EvidenceGraph becomes valuable.

```text
E104 source
  ↓
E108 diagnosis
  ↓
D12 decision
  ↓
P42 patch
```

If E104 becomes stale:

```text
E104 ✗
 ↓
E108 ✗
 ↓
D12 ✗
 ↓
P42 assumption ✗
```

But:

```text
E200 unrelated documentation
```

remains valid.

You don't reset the entire task.

---

# 42. State invalidation graph

So Aljabr effectively has:

```text
Workspace
    ↓
Evidence
    ↓
Observation
    ↓
Decision
    ↓
Action
    ↓
Verification
```

Changes propagate forward.

This is much better than simply:

```text
"clear cache"
```

---

# 43. `DependencyTracker`

```java
public interface DependencyTracker {

    void addDependency(
        StateId from,
        StateId dependsOn
    );

    Set<StateId> dependentsOf(
        StateId id
    );
}
```

This can initially be backed by SQLite.

No need for a graph database.

---

# 44. Agent state should therefore be declarative

A useful mental model:

```text
AgentState =
    facts
    +
references
    +
current position
    +
pending work
```

Not:

```text
AgentState =
    gigantic transcript
```

That's exactly what makes local inference practical.

---

# 45. Pause/resume

User can say:

> Pause.

Runtime:

```text
RUNNING
 ↓
PAUSING
 ↓
finish safe operation
 ↓
checkpoint
 ↓
PAUSED
```

Not:

```text
kill process immediately
```

unless forced.

---

# 46. Cancellation

Cancellation should propagate:

```text
Agent
 ↓
Inference
 ↓
Tool
 ↓
Process
```

Use:

```java
CancellationToken
```

which we already introduced in the ToolExecutionContext.

---

# 47. Graceful cancellation

For example:

```text
run_build
   ↓
user cancel
   ↓
cancel process
   ↓
collect final output
   ↓
record CANCELLED
   ↓
checkpoint
```

Now the task can resume later.

---

# 48. Agent snapshots

For debugging, add:

```java
public interface StateSnapshotter {

    Uni<Path> snapshot(
        TaskId taskId
    );
}
```

A snapshot could contain:

```text
task.json
plan.json
state.json
evidence-refs.json
checkpoint.json
```

This is extremely useful during early Aljabr development.

---

# 49. Debugging mode

I would add a runtime flag:

```yaml
aljabr:
  diagnostics:
    persist-events: true
    persist-prompts: false
    persist-tool-output: true
    persist-context: true
```

Production/local normal mode can reduce storage.

Development mode can be much more verbose.

---

# 50. One thing I strongly recommend

Create a **replay engine**.

```java
public interface AgentReplay {

    AgentState replay(
        List<AgentEvent> events
    );
}
```

Then:

```text
events
 ↓
replay
 ↓
state
```

This lets you test the runtime without running Gemma.

---

# 51. Deterministic runtime testing

For example:

```text
TaskCreated
PlanCreated
StepStarted
ToolCompleted(search)
EvidenceAdded(E101)
ToolCompleted(read_file)
EvidenceAdded(E102)
DecisionMade(D1)
PatchApplied(P1)
VerificationCompleted(V1)
```

Replay should produce exactly:

```text
COMPLETED
```

If not, you have a runtime bug.

---

# 52. This is extremely valuable for Aljabr

You can record a real session:

```text
Gemma
+
tools
+
events
```

Then replay it 100 times while changing:

```text
ContextPolicy
ToolSelector
RecoveryPolicy
```

without repeatedly invoking Gemma.

That makes architecture iteration dramatically easier.

---

# 53. Local model optimization appears again

Notice what we're doing:

Instead of:

```text
Gemma remembers everything
```

we have:

```text
Wayang remembers everything important
Gemma receives only what it needs now
```

This is the correct division of labor.

---

# 54. Final durable architecture

At this point:

```text
                         Aljabr
                           │
                    ┌──────┴──────┐
                    ▼             ▼
                 AgentState    EventStore
                    │             │
                    └──────┬──────┘
                           ▼
                    StateMachine
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
          Planner       ToolSystem    ContextEngine
             │             │             │
             │             ▼             ▼
             │          Evidence ◄── EvidenceStore
             │                           │
             └──────────────┬────────────┘
                            ▼
                          Gemma
                            │
                            ▼
                         Decision
                            │
                            ▼
                        Executor
                            │
                            ▼
                        Verifier
                            │
                            ▼
                       Checkpoint
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
             Continue               Recovery
                                       │
                             ┌─────────┼─────────┐
                             ▼         ▼         ▼
                          Continue  Reobserve  Replan
```

---

# 55. What I would implement concretely now

I'd create this package structure:

```text
aljabr/
└── runtime/
    ├── state/
    │   ├── AgentState.java
    │   ├── TaskState.java
    │   ├── PlanState.java
    │   ├── ExecutionState.java
    │   ├── EvidenceState.java
    │   ├── WorkspaceState.java
    │   ├── InferenceState.java
    │   └── BudgetState.java
    │
    ├── event/
    │   ├── AgentEvent.java
    │   ├── EventStore.java
    │   └── AgentReplay.java
    │
    ├── checkpoint/
    │   ├── AgentCheckpoint.java
    │   ├── CheckpointStore.java
    │   ├── CheckpointPolicy.java
    │   └── RecoveryManager.java
    │
    ├── evidence/
    │   ├── Evidence.java
    │   ├── EvidenceStore.java
    │   ├── EvidenceGraph.java
    │   ├── EvidenceInvalidator.java
    │   └── DependencyTracker.java
    │
    ├── context/
    │   ├── ContextEngine.java
    │   ├── ContextSelector.java
    │   ├── ContextBudget.java
    │   ├── ContextPolicy.java
    │   ├── ContextRenderer.java
    │   └── ContextCache.java
    │
    └── recovery/
        ├── ResumeStrategy.java
        ├── WorkspaceReconciler.java
        ├── ToolReconciler.java
        └── StateRepairer.java
```

---

## The next milestone after this

I'd then move into **`Planner + Replanner + VerificationLoop`**.

That gives Aljabr its actual coding-agent intelligence:

```text
USER
 ↓
TASK
 ↓
PLAN
 ↓
STEP
 ↓
OBSERVE
 ↓
DECIDE
 ↓
ACT
 ↓
VERIFY
 ├── PASS → next step
 │
 └── FAIL
       ↓
    diagnose
       ↓
    replan
       ↓
    act again
```

And I'd make that planner **small-model friendly** too: instead of asking Gemma to generate a giant multi-step plan, use **short structured plans + one active step + runtime-controlled replanning**. This is likely the next major piece that will determine whether Aljabr feels genuinely capable on your M4/16 GB machine.


Absolutely. **Next milestone: `Planner → ActiveStep → Executor → Verifier → Replanner`**.

This is the core **coding-agent control loop** for Aljabr. The previous milestones gave us durable state, evidence, context, and recovery. Now we make those pieces actually drive software engineering work.

The design I recommend for your local Gemma setup is:

> **Small model decides locally; runtime controls the workflow.**

Don't ask Gemma to autonomously manage the entire task. Let Wayang own orchestration, safety, state, retries, verification, and evidence.

---

# 1. The Aljabr coding loop

The complete loop becomes:

```text
USER REQUEST
     │
     ▼
 TASK
     │
     ▼
 PLANNER
     │
     ▼
 PLAN
     │
     ▼
 ACTIVE STEP
     │
     ▼
 CONTEXT ENGINE
     │
     ▼
 GEMMA
     │
     ▼
 DECISION
     │
     ▼
 EXECUTOR
     │
     ▼
 OBSERVATION
     │
     ▼
 VERIFIER
     │
 ┌───┴──────────────┐
 │                  │
PASS               FAIL
 │                  │
 ▼                  ▼
NEXT STEP        DIAGNOSE
                    │
                    ▼
                 REPLAN
                    │
                    └──────► ACTIVE STEP
```

Notice something important:

**Gemma isn't the loop.**

Wayang is the loop.

Gemma is a reasoning component inside it.

---

# 2. `Planner`

Start with a deliberately small interface.

```java
public interface Planner {

    Uni<Plan> createPlan(
        PlanningRequest request
    );

    Uni<Plan> revisePlan(
        ReplanningRequest request
    );
}
```

---

# 3. Planning request

```java
public record PlanningRequest(
    TaskState task,
    WorkspaceState workspace,
    Context context,
    PlanningConstraints constraints
) {}
```

The planner shouldn't receive the entire repository.

It receives:

```text
user objective
+
workspace summary
+
relevant evidence
+
constraints
```

---

# 4. The plan should be tiny

For Gemma 12B, I would strongly prefer:

```json
{
  "goal": "Fix token expiration validation",
  "steps": [
    {
      "id": "S1",
      "objective": "Inspect expiration validation"
    },
    {
      "id": "S2",
      "objective": "Implement correction"
    },
    {
      "id": "S3",
      "objective": "Run relevant tests"
    }
  ]
}
```

Rather than:

```text
50-step gigantic implementation plan
```

The runtime can generate additional steps when necessary.

---

# 5. Why short plans are better

Long plans become stale.

Suppose:

```text
S1 inspect
S2 modify
S3 test
S4 refactor
S5 update docs
```

Then S3 discovers:

```text
unexpected architecture
```

Now S4/S5 may be invalid.

So:

> **Plan the direction globally, but reason about execution locally.**

---

# 6. `Plan`

```java
public record Plan(
    PlanId id,
    String objective,
    List<PlanStep> steps,
    PlanStatus status
) {}
```

---

# 7. `PlanStep`

I would extend the previous definition:

```java
public record PlanStep(
    StepId id,

    String objective,

    StepStatus status,

    StepKind kind,

    List<EvidenceId> inputs,

    List<EvidenceId> outputs,

    VerificationPolicy verification
) {}
```

---

# 8. Step kinds

```java
public enum StepKind {

    OBSERVE,
    ANALYZE,
    MODIFY,
    TEST,
    VERIFY,
    DOCUMENT
}
```

This helps the runtime determine what should happen.

For example:

```text
OBSERVE
→ observation tools

MODIFY
→ mutation tools

TEST
→ test/build tools

VERIFY
→ verification tools
```

Gemma doesn't need to decide all of this.

---

# 9. `ActiveStep`

Don't constantly send the entire plan into the model.

Create:

```java
public record ActiveStep(
    PlanId planId,
    StepId stepId,
    String objective,
    StepKind kind
) {}
```

Then context becomes:

```text
TASK
+
ACTIVE STEP
+
RELEVANT EVIDENCE
+
CONSTRAINTS
```

That's exactly what a small model wants.

---

# 10. Active-step invariant

At any moment:

```text
0 or 1 active step
```

Never:

```text
S1 active
S2 active
S3 active
```

simultaneously.

This makes state management much simpler.

---

# 11. Step lifecycle

```text
READY
  ↓
ACTIVE
  ↓
DECIDING
  ↓
EXECUTING
  ↓
VERIFYING
  ↓
COMPLETED
```

Failure:

```text
VERIFYING
    ↓
FAILED
    ↓
DIAGNOSING
    ↓
REPLANNING
```

---

# 12. The model's job

Gemma should answer a very constrained question:

> **Given the current step and evidence, what should the runtime do next?**

Not:

> "Solve the entire software project."

This is a huge quality improvement for smaller models.

---

# 13. `AgentDecision`

```java
public sealed interface AgentDecision
    permits ExecuteTool,
            CompleteStep,
            RequestObservation,
            RequestReplan,
            AskUser {}
```

---

# 14. Tool decision

```java
public record ExecuteTool(
    String tool,
    JsonNode arguments,
    List<EvidenceId> supportingEvidence
) implements AgentDecision {}
```

Example:

```json
{
  "type": "EXECUTE_TOOL",
  "tool": "read_file",
  "arguments": {
    "path": "src/TokenValidator.java",
    "startLine": 60,
    "endLine": 100
  },
  "supportingEvidence": [
    "E104"
  ]
}
```

---

# 15. This is much better than free-form output

Avoid:

```text
"I think we should probably inspect..."
```

Use:

```json
{
  "type": "REQUEST_OBSERVATION",
  "target": "TokenValidator.java",
  "range": "60-100"
}
```

The runtime can validate this.

---

# 16. `DecisionGate`

Before executing anything:

```java
public interface DecisionGate {

    Uni<DecisionResult> validate(
        AgentDecision decision,
        AgentState state
    );
}
```

This is extremely important.

Gemma proposes.

Wayang approves.

---

# 17. Decision validation

For:

```text
apply_patch
```

the gate checks:

```text
tool exists?
arguments valid?
path allowed?
step allows mutation?
evidence exists?
workspace unchanged?
budget available?
user authorization required?
```

Only then:

```text
EXECUTE
```

---

# 18. Model hallucination containment

Suppose Gemma says:

```json
{
  "tool": "delete_repository"
}
```

Runtime says:

```text
unknown tool
→ reject
```

Or:

```text
apply_patch
path:
src/unknown/File.java
```

Runtime:

```text
file does not exist
→ reject
```

The model cannot directly control the machine.

---

# 19. `Executor`

```java
public interface AgentExecutor {

    Uni<ExecutionResult> execute(
        ExecuteTool decision,
        ExecutionContext context
    );
}
```

It delegates to the existing ToolSystem.

```text
AgentExecutor
     ↓
ToolRegistry
     ↓
Tool
     ↓
ToolExecutionContext
     ↓
ToolResult
```

---

# 20. Don't let planner call tools

This is an important separation.

Bad:

```text
Planner
 ↓
tool
```

Better:

```text
Planner
 ↓
Plan

DecisionEngine
 ↓
Decision

Executor
 ↓
Tool
```

Each layer has one job.

---

# 21. Observation after execution

Every tool result goes through the pipeline we designed:

```text
ToolResult
    ↓
ObservationNormalizer
    ↓
Evidence
    ↓
EvidenceStore
    ↓
EvidenceGraph
```

Then:

```text
AgentState
```

is updated.

---

# 22. The step loop

The runtime implementation can look roughly like:

```java
Uni<StepResult> runStep(ActiveStep step) {

    return contextEngine.build(
        contextRequest(step)
    )
    .flatMap(context ->
        decisionEngine.decide(context)
    )
    .flatMap(decisionGate::validate)
    .flatMap(this::executeDecision)
    .flatMap(this::observe)
    .flatMap(this::verify);
}
```

That is the heart of Aljabr.

---

# 23. But add a bounded loop

Don't allow:

```text
while(true)
```

Use:

```java
for (int iteration = 0;
     iteration < policy.maxStepIterations();
     iteration++) {
    ...
}
```

---

# 24. `StepBudget`

```java
public record StepBudget(
    int maxIterations,
    int maxToolCalls,
    int maxInferenceCalls,
    Duration maxDuration
) {}
```

For example:

```text
simple step:
10 iterations

complex step:
25 iterations
```

Configurable.

---

# 25. Why this matters

Small models can get stuck:

```text
read
→ think
→ search
→ read
→ search
→ read
→ search
```

The runtime must detect that.

---

# 26. Loop detection

Create:

```java
public interface LoopDetector {

    LoopStatus inspect(
        AgentState state,
        DecisionHistory history
    );
}
```

---

# 27. Detect repeated actions

For example:

```text
read_file(TokenValidator.java)
read_file(TokenValidator.java)
read_file(TokenValidator.java)
```

Detect:

```text
REPEATED_ACTION
```

Then:

```text
don't execute again
```

Instead:

```text
ask model for another action
```

or:

```text
request replanning
```

---

# 28. Detect repeated context

Another subtle failure:

```text
same context
+
same decision
+
same failure
```

three times.

That means:

```text
STAGNATION
```

---

# 29. `StagnationDetector`

```java
public record StateFingerprint(
    String task,
    String step,
    String evidence,
    String decision
) {}
```

If:

```text
fingerprint N
==
fingerprint N-1
```

we know progress isn't happening.

---

# 30. Progress must be measurable

Define:

```java
public record ProgressSignal(
    boolean newEvidence,
    boolean workspaceChanged,
    boolean verificationImproved,
    boolean stepAdvanced
) {}
```

If all are false repeatedly:

```text
no progress
```

---

# 31. This is extremely important for local Gemma

You don't want the model consuming:

```text
2000 tokens
```

just to repeatedly do:

```text
read same file
```

Runtime detects it cheaply.

---

# 32. `Verifier`

Now the most important component after execution.

```java
public interface Verifier {

    Uni<VerificationResult> verify(
        VerificationRequest request
    );
}
```

---

# 33. Verification must be objective

Whenever possible:

```text
tests
compiler
type checker
lint
static analysis
git diff
file existence
schema validation
```

not:

```text
"Does the model think it worked?"
```

---

# 34. Verification hierarchy

I'd implement:

```text
LEVEL 0
structural

LEVEL 1
tool result

LEVEL 2
targeted test

LEVEL 3
build

LEVEL 4
broader test suite

LEVEL 5
semantic review
```

Don't jump to level 4 unnecessarily.

---

# 35. Example

After patching:

```text
TokenValidator.java
```

Verifier might do:

```text
1. file compiles
2. relevant unit test
3. targeted integration test
```

No need to run:

```text
entire 40-minute test suite
```

unless required.

---

# 36. `VerificationPolicy`

```java
public record VerificationPolicy(
    VerificationLevel minimum,
    List<String> requiredChecks,
    boolean allowModelReview
) {}
```

---

# 37. Verification result

```java
public record VerificationResult(
    VerificationStatus status,

    List<EvidenceId> evidence,

    List<VerificationFailure> failures
) {}
```

---

# 38. Verification states

```java
public enum VerificationStatus {

    PASSED,
    FAILED,
    INCONCLUSIVE,
    BLOCKED
}
```

Important distinction:

```text
FAILED
```

means evidence says it is wrong.

```text
INCONCLUSIVE
```

means we don't know.

Those should trigger different behavior.

---

# 39. Failure classification

Don't immediately ask Gemma to fix every failure.

First classify.

```java
public enum FailureKind {

    COMPILATION,
    TEST,
    LINT,
    TOOL,
    ENVIRONMENT,
    TIMEOUT,
    EXPECTATION,
    UNKNOWN
}
```

---

# 40. Failure diagnosis

```java
public interface FailureDiagnoser {

    Uni<Diagnosis> diagnose(
        VerificationResult result,
        Context context
    );
}
```

But use deterministic classification first.

For example:

```text
javac exit code != 0
→ COMPILATION
```

No model required.

---

# 41. Then ask Gemma only when necessary

```text
test failed
 ↓
parse failure
 ↓
known assertion mismatch?
    ↓ yes
structured diagnosis

otherwise
    ↓
Gemma diagnosis
```

Again:

> Runtime first, model second.

---

# 42. `Diagnosis`

```java
public record Diagnosis(
    FailureKind kind,

    String summary,

    List<EvidenceId> evidence,

    double confidence,

    DiagnosisSource source
) {}
```

---

# 43. `DiagnosisSource`

```java
public enum DiagnosisSource {

    DETERMINISTIC,
    MODEL,
    USER,
    HYBRID
}
```

This makes the provenance clear.

---

# 44. Replanning

Now:

```java
public interface Replanner {

    Uni<PlanRevision> replan(
        ReplanningRequest request
    );
}
```

---

# 45. Don't regenerate the entire plan

This is another important local-model optimization.

Instead of:

```text
old plan
+
failure
→
new 20-step plan
```

ask:

```text
Current step failed.

Given:
- current objective
- failure
- relevant evidence

what is the smallest plan revision?
```

---

# 46. `PlanRevision`

```java
public record PlanRevision(
    RevisionType type,

    Optional<StepId> replaceStep,

    List<PlanStep> newSteps,

    String reason
) {}
```

---

# 47. Revision types

```java
public enum RevisionType {

    RETRY,
    MODIFY_STEP,
    INSERT_STEP,
    REMOVE_STEP,
    REPLACE_STEP,
    ABORT
}
```

Most failures should be:

```text
RETRY
```

or:

```text
MODIFY_STEP
```

rather than rebuilding everything.

---

# 48. Example

Plan:

```text
S1 inspect
S2 patch
S3 test
```

Test fails because:

```text
missing null handling
```

Revision:

```text
INSERT:
S2b inspect null behavior
```

Then:

```text
S2b
 ↓
S2
 ↓
S3
```

That's elegant.

---

# 49. Replanner constraints

Don't let Gemma arbitrarily rewrite history.

Give it:

```text
current plan
+
failed step
+
diagnosis
+
relevant evidence
+
remaining budget
```

and require:

```text
minimal revision
```

---

# 50. Plan stability

Track:

```java
public record PlanMetrics(
    int revisions,
    int completedSteps,
    int failedSteps
) {}
```

Then policy can say:

```text
> 3 revisions
→ pause/review

> 5
→ ask user
```

---

# 51. User escalation

Eventually the agent must know:

> I don't know.

Use:

```java
public record UserEscalation(
    String question,
    List<EvidenceId> evidence,
    List<String> options
) {}
```

Example:

```text
I found two possible interpretations:

A. Preserve backward compatibility
B. Change expiration behavior

Which should I use?
```

Much better than hallucinating a choice.

---

# 52. Confidence should not be blind

Don't trust:

```text
Gemma:
confidence = 0.95
```

Instead derive confidence from evidence.

For example:

```text
test passed
+
build passed
+
expected diff
```

→ high confidence.

While:

```text
model says looks correct
```

→ low confidence.

---

# 53. Verification confidence

```java
public record VerificationConfidence(
    double score,
    List<String> reasons
) {}
```

This can be entirely runtime-derived.

---

# 54. The complete coding step

Now the real loop:

```text
ACTIVE STEP
    │
    ▼
BUILD CONTEXT
    │
    ▼
GEMMA DECISION
    │
    ▼
VALIDATE DECISION
    │
    ├── invalid → repair/retry
    │
    ▼
EXECUTE
    │
    ▼
NORMALIZE RESULT
    │
    ▼
STORE EVIDENCE
    │
    ▼
VERIFY
    │
    ├── PASS ─────────────► COMPLETE STEP
    │
    ├── INCONCLUSIVE ────► OBSERVE MORE
    │
    └── FAIL
           │
           ▼
        DIAGNOSE
           │
           ▼
        REPLAN
           │
           └──────────────► ACTIVE STEP
```

---

# 55. The most important separation

At this point we have:

```text
Planner
    decides WHAT should happen.

DecisionEngine
    decides WHAT TO DO NEXT.

Executor
    performs it.

Verifier
    determines WHETHER it worked.

Replanner
    determines HOW to adapt.
```

Don't collapse these into one `Agent` class.

---

# 56. Recommended interfaces

Your core runtime could now look like:

```java
public interface AljabrAgent {

    Uni<AgentResult> run(
        TaskRequest request
    );

    Uni<Void> pause(
        TaskId taskId
    );

    Uni<Void> cancel(
        TaskId taskId
    );

    Uni<AgentResult> resume(
        TaskId taskId
    );
}
```

Internally:

```java
public final class DefaultAljabrAgent
    implements AljabrAgent {

    Planner planner;
    ContextEngine contextEngine;
    DecisionEngine decisionEngine;
    DecisionGate decisionGate;
    AgentExecutor executor;
    Verifier verifier;
    FailureDiagnoser diagnoser;
    Replanner replanner;
    CheckpointManager checkpointManager;
    RecoveryManager recoveryManager;
    LoopDetector loopDetector;
}
```

This is the **real basic agent** I'd keep.

Not a giant `DefaultWayangAgent` with generic behavior.

---

# 57. And this answers our earlier `DefaultWayangAgent` question

I would now make the distinction:

```text
Wayang
 └── Runtime / framework

Aljabr
 └── Coding-agent implementation
```

Wayang should provide:

```text
state
tools
memory
evidence
context
execution
checkpoint
policy
model interface
```

Aljabr provides:

```text
planner
coding strategy
verification strategy
replanning
software-engineering heuristics
```

That keeps Wayang clean.

---

# 58. Local vs cloud remains policy-driven

The architecture remains identical.

For your local Gemma:

```text
Planner:
short

Active step:
one

Context:
compact

Decision:
structured

Output:
small

Verification:
runtime-heavy

Replanning:
minimal
```

For a large cloud model:

```text
Planner:
larger

Context:
broader

Evidence:
more

Planning horizon:
larger

Model output:
richer
```

No architectural fork.

---

# 59. One optimization I'd add now: `Action Grammar`

For a small model, don't rely entirely on JSON generation.

Define a tiny action vocabulary:

```text
OBSERVE
READ
SEARCH
EDIT
TEST
BUILD
VERIFY
REPLAN
DONE
ASK
```

Then structured arguments.

Conceptually:

```text
ACTION=READ
PATH=src/Auth.java
START=40
END=80
```

or constrained JSON if your inference engine supports grammar-constrained decoding.

This can dramatically reduce malformed decisions.

---

# 60. Even better: decision schema

```java
public record AgentAction(
    ActionType type,

    Optional<ToolName> tool,

    JsonNode arguments,

    List<EvidenceId> evidence
) {}
```

And:

```java
public enum ActionType {

    OBSERVE,
    EXECUTE,
    VERIFY,
    REPLAN,
    COMPLETE,
    ASK_USER
}
```

The model only chooses from this small space.

---

# 61. Gemma prompt should therefore be tiny

Conceptually:

```text
ROLE
You are Aljabr's coding decision engine.

TASK
Fix token expiration validation.

CURRENT STEP
Inspect the expiration implementation.

KNOWN EVIDENCE
E104:
TokenValidator.java lines 60-90

E107:
Test failure: expired token accepted.

ALLOWED ACTIONS
READ
SEARCH
EDIT
TEST
VERIFY
REPLAN
DONE
ASK

Return exactly one action.
```

That's much more suitable for a 12B local model than dumping the entire agent history into the prompt.

---

# 62. And the runtime preserves the intelligence

The model doesn't need to remember:

```text
what happened 30 steps ago.
```

Wayang already knows:

```text
TaskState
PlanState
EvidenceStore
DecisionHistory
WorkspaceState
Checkpoint
```

The model only gets the **projection necessary for this decision**.

That is the fundamental Aljabr optimization.

---

# 63. Next milestone

With this in place, the next thing I'd tackle is **`Tool Intelligence + Coding Operations`**.

Not just generic tools like:

```text
read_file
write_file
run_command
```

but a proper coding-oriented tool layer:

```text
Repository
 ├── Search
 ├── Read
 ├── Symbol
 ├── References
 ├── Diff
 ├── Patch
 ├── Test
 ├── Build
 ├── Format
 ├── Lint
 └── Git
```

with **tool cost, risk, preconditions, postconditions, idempotency, and evidence production**.

That is where Aljabr starts becoming a genuinely capable coding agent rather than just an LLM wrapped around shell commands.


Absolutely. **Next milestone: Tool Intelligence + Coding Operations.**

This is where Aljabr becomes much more capable **without requiring Gemma to be smarter**.

The central idea:

> **Don't give the model a huge toolbox. Give it a small set of high-level, well-described coding capabilities whose runtime contracts are extremely strong.**

For your M4 16 GB + Gemma 12B setup, this is especially important.

---

# 1. The coding tool architecture

Instead of:

```text
Gemma
  ↓
shell
  ↓
anything
```

build:

```text
                    Aljabr
                       │
                 DecisionEngine
                       │
                  ToolSelector
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Observe       Modify       Verify
          │            │            │
     ┌────┴────┐    ┌──┴───┐    ┌──┴─────┐
     │         │    │      │    │        │
   Search    Read  Patch   Git  Test    Build
```

The runtime understands what each operation means.

---

# 2. `CodingTool`

Don't make coding tools just:

```java
interface Tool {
    execute(...);
}
```

Extend the metadata.

```java
public interface CodingTool extends Tool {

    ToolDescriptor descriptor();

    ToolPreconditions preconditions();

    ToolCapabilities capabilities();

    ToolCost cost();

    ToolRisk risk();
}
```

Now the runtime can make intelligent decisions **without asking Gemma**.

---

# 3. `ToolDescriptor`

```java
public record ToolDescriptor(
    String name,
    String description,
    ToolCategory category,
    InputSchema inputSchema
) {}
```

Example:

```text
read_file

category:
OBSERVATION

description:
Read a bounded section of a source file.

input:
path
startLine
endLine
```

The description should be **short**.

Don't send a 500-line tool manual to Gemma.

---

# 4. Tool categories

```java
public enum ToolCategory {

    SEARCH,
    INSPECT,
    MODIFY,
    TEST,
    BUILD,
    VERSION_CONTROL,
    ANALYSIS,
    ENVIRONMENT
}
```

This lets the runtime filter tools before the model sees them.

---

# 5. Tool capability

```java
public record ToolCapabilities(
    boolean readsWorkspace,
    boolean modifiesWorkspace,
    boolean executesProcess,
    boolean networkAccess,
    boolean requiresConfirmation
) {}
```

This is useful for policy.

For example:

```text
read_file
→ readsWorkspace=true
→ modifiesWorkspace=false

apply_patch
→ readsWorkspace=true
→ modifiesWorkspace=true

git_push
→ modifies external state
→ requiresConfirmation=true
```

---

# 6. Tool risk

```java
public enum ToolRisk {

    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

Examples:

```text
search         NONE
read_file      NONE
apply_patch    MEDIUM
run_tests      LOW
git_commit     MEDIUM
git_push       HIGH
delete_file    HIGH
```

---

# 7. Important principle

**Risk belongs to the runtime, not the model.**

Gemma can request:

```text
git_push
```

but that doesn't mean it gets to execute it.

Runtime evaluates:

```text
tool risk
+
task policy
+
user authorization
```

---

# 8. Tool preconditions

This is one of the highest-value additions.

```java
public interface ToolPrecondition {

    ValidationResult check(
        ToolContext context
    );
}
```

Examples:

```text
apply_patch:
- file exists
- expected content exists
- workspace not stale
- patch valid
```

---

# 9. Example

Suppose Gemma proposes:

```text
apply_patch(
  TokenValidator.java,
  patch=X
)
```

Before execution:

```text
file exists?             ✓
expected old content?    ✓
workspace changed?       ✗
patch parses?            ✓
step allows mutation?    ✓
budget available?        ✓
```

Only then:

```text
EXECUTE
```

---

# 10. Postconditions

Tools should also describe what they guarantee.

```java
public interface ToolPostcondition {

    List<Evidence> derive(
        ToolResult result
    );
}
```

For:

```text
apply_patch
```

postconditions might produce:

```text
PatchApplied
FileChanged
NewFileHash
Diff
```

That automatically becomes evidence.

---

# 11. Tool contract

So conceptually:

```text
Tool
 ├── descriptor
 ├── input schema
 ├── preconditions
 ├── capabilities
 ├── cost
 ├── risk
 ├── idempotency
 └── postconditions
```

This is much more powerful than a plain function.

---

# 12. `ToolCost`

For local inference, cost matters.

```java
public record ToolCost(
    int latencyWeight,
    int tokenWeight,
    int cpuWeight,
    int ioWeight
) {}
```

For example:

```text
read_file
→ cheap

repository_search
→ cheap/moderate

full_build
→ expensive

full_test_suite
→ very expensive
```

---

# 13. Tool selection should be hierarchical

Don't give Gemma 50 tools.

Instead:

```text
Task
 ↓
ToolCategory
 ↓
small candidate set
 ↓
Gemma selects exact action
```

For example:

```text
Current step = INSPECT

Available categories:
SEARCH
INSPECT
```

Gemma sees:

```text
search_code
read_file
find_symbol
find_references
```

Not:

```text
git_push
docker
network
delete
shell
```

---

# 14. `ToolRouter`

```java
public interface ToolRouter {

    List<ToolDescriptor> candidates(
        ActiveStep step,
        AgentState state
    );
}
```

Then:

```text
ActiveStep
 ↓
ToolRouter
 ↓
4 relevant tools
 ↓
ContextEngine
 ↓
Gemma
```

This reduces both **token usage and model confusion**.

---

# 15. The first coding tools

I'd implement these first:

```text
search_code
read_file
read_symbol
find_references
get_diff
apply_patch
run_test
run_build
```

That's enough to build a surprisingly capable first coding loop.

---

# 16. `search_code`

Don't expose raw grep as the primary abstraction.

```java
public record SearchCodeRequest(
    String query,
    SearchScope scope,
    int maxResults
) {}
```

Result:

```java
public record SearchMatch(
    Path path,
    int line,
    String snippet
) {}
```

---

# 17. Search should be bounded

Never return:

```text
20,000 lines
```

Return:

```text
max 10-30 relevant matches
```

Then Gemma can request more if necessary.

Again:

> progressive disclosure.

---

# 18. `read_file`

Never default to the entire file.

```java
public record ReadFileRequest(
    Path path,
    int startLine,
    int endLine
) {}
```

And enforce:

```text
max lines
max bytes
```

---

# 19. Smart read windows

If Gemma requests:

```text
line 100
```

runtime can return:

```text
95-110
```

instead of exactly one line.

Likewise:

```text
function declaration
+
body
+
small surrounding context
```

This improves usefulness without exploding tokens.

---

# 20. `read_symbol`

This is more valuable than making Gemma read files.

```java
public record ReadSymbolRequest(
    String symbol,
    Optional<Path> file
) {}
```

Result:

```text
class TokenValidator
method validateExpiration
```

with only the relevant source.

---

# 21. `find_references`

This should become a first-class operation.

```java
public record FindReferencesRequest(
    String symbol,
    Optional<Path> scope
) {}
```

Result:

```text
TokenValidator.validateExpiration
 ├── AuthService.java:82
 ├── LoginController.java:44
 └── RefreshService.java:109
```

That gives Gemma architectural information cheaply.

---

# 22. Eventually use language intelligence

For supported languages:

```text
Tree-sitter
LSP
compiler AST
language server
```

can power:

```text
symbols
references
definitions
diagnostics
```

But don't make this mandatory for v1.

Fallback:

```text
text search
```

is perfectly acceptable.

---

# 23. `get_diff`

This should be extremely cheap.

```java
public record DiffRequest(
    Optional<Path> path
) {}
```

And:

```text
before
+
after
+
summary
```

The model should frequently inspect the diff instead of rereading entire files.

---

# 24. `apply_patch`

This is probably your most important mutation primitive.

I recommend:

```java
public record ApplyPatchRequest(
    Path path,
    String expectedOldContentHash,
    Patch patch
) {}
```

The expected hash is important.

---

# 25. Why hash the expected state?

Suppose Gemma generated a patch against:

```text
version A
```

but the file is now:

```text
version B
```

Don't apply.

Return:

```text
STALE_TARGET
```

Then Aljabr re-observes the file.

This prevents accidental corruption.

---

# 26. Never let the model overwrite blindly

Avoid:

```java
writeFile(path, giantString);
```

as your primary coding primitive.

Prefer:

```text
expected state
+
small patch
+
validation
```

This dramatically reduces destructive errors.

---

# 27. Patch size policy

Add:

```java
public record MutationPolicy(
    int maxFiles,
    int maxAddedLines,
    int maxDeletedLines,
    boolean allowNewFiles,
    boolean allowDeletes
) {}
```

For local development, you might start conservative.

Example:

```text
max files = 5
max additions = 300
max deletions = 150
```

These aren't universal defaults; make them configurable.

---

# 28. Large changes should be split

If Gemma proposes:

```text
change 17 files
```

runtime should respond:

```text
CHANGE_TOO_LARGE
```

Then:

```text
replan
```

into smaller operations.

This is both safer and easier for a small model.

---

# 29. `run_test`

Make tests structured.

```java
public record RunTestRequest(
    Optional<String> testSelector,
    TestScope scope
) {}
```

Not:

```text
shell = "whatever"
```

when a structured test runner is available.

---

# 30. Test scope

```java
public enum TestScope {

    TARGET,
    MODULE,
    PROJECT
}
```

Then the runtime can prefer:

```text
TARGET
```

before:

```text
PROJECT
```

---

# 31. `run_build`

Same idea:

```java
public record RunBuildRequest(
    BuildScope scope
) {}
```

Result:

```java
public record BuildResult(
    boolean success,
    List<Diagnostic> diagnostics,
    Duration duration
) {}
```

---

# 32. Diagnostics become evidence

Compiler says:

```text
TokenValidator.java:84
incompatible types
```

Don't dump raw compiler output into the model every time.

Normalize:

```json
{
  "kind": "COMPILATION",
  "file": "TokenValidator.java",
  "line": 84,
  "message": "incompatible types"
}
```

Then store it as evidence.

---

# 33. This creates a very useful pipeline

```text
Tool
 ↓
Raw result
 ↓
Normalizer
 ↓
Structured observation
 ↓
Evidence
 ↓
Evidence graph
 ↓
ContextEngine
 ↓
Gemma
```

This is one of the core architectural patterns of Aljabr.

---

# 34. Shell should be the escape hatch

You still need:

```text
run_command
```

but it should be:

```text
EscapeHatchTool
```

not the primary coding API.

Use it when:

```text
no specialized tool exists
```

---

# 35. Shell policy

```java
public record CommandPolicy(
    Set<String> allowedCommands,
    Set<String> deniedCommands,
    Duration timeout,
    long maxOutputBytes,
    boolean networkAllowed
) {}
```

And ideally:

```text
working directory
environment
network
filesystem
```

are explicitly controlled.

---

# 36. Command output must be truncated

A command can produce:

```text
500,000 lines
```

Don't send that to Gemma.

Use:

```text
head
tail
error extraction
summary
```

and preserve full output in storage.

---

# 37. `OutputArtifact`

```java
public record OutputArtifact(
    ArtifactId id,
    String mimeType,
    long size,
    Path storagePath,
    String contentHash
) {}
```

Then the model receives:

```text
summary
+
relevant excerpt
```

while the full output remains available.

---

# 38. Tool result should distinguish data from evidence

For example:

```java
public record ToolResult(
    ToolExecutionId executionId,
    ToolStatus status,
    List<OutputArtifact> artifacts,
    List<Observation> observations
) {}
```

This gives you a clean boundary.

---

# 39. Tool errors should be typed

Avoid:

```text
Exception("something went wrong")
```

Prefer:

```java
public enum ToolErrorKind {

    INVALID_ARGUMENT,
    PRECONDITION_FAILED,
    NOT_FOUND,
    PERMISSION_DENIED,
    TIMEOUT,
    PROCESS_FAILED,
    STALE_STATE,
    OUTPUT_LIMIT,
    ENVIRONMENT_FAILURE,
    INTERNAL_ERROR
}
```

---

# 40. This makes recovery much smarter

Example:

```text
STALE_STATE
```

→ reobserve.

```text
TIMEOUT
```

→ maybe retry.

```text
PERMISSION_DENIED
```

→ ask user.

```text
INVALID_ARGUMENT
```

→ model correction.

```text
PROCESS_FAILED
```

→ inspect diagnostics.

No generic:

```text
"try again"
```

---

# 41. Tool state machine

Every execution:

```text
REQUESTED
   ↓
VALIDATING
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

or:

```text
RUNNING
   ↓
CANCELLED
```

This integrates directly with the checkpoint/recovery system from the previous milestone.

---

# 42. Tool execution record

```java
public record ToolExecution(
    ToolExecutionId id,

    String tool,

    JsonNode arguments,

    ToolExecutionStatus status,

    Instant startedAt,
    Instant completedAt,

    Optional<ToolError> error,

    List<ArtifactId> artifacts,

    List<EvidenceId> evidence
) {}
```

This becomes your audit trail.

---

# 43. Tool caching

This is another major optimization for local models.

Some operations are cacheable:

```text
search
read_file
symbol lookup
references
build diagnostics
```

But cache according to workspace state.

Key:

```text
tool
+
normalized arguments
+
workspace fingerprint
```

---

# 44. Example

```text
read_file(
 TokenValidator.java,
 60-100
)
```

cache key:

```text
SHA256(
  workspaceHash
  +
toolName
  +
arguments
)
```

If workspace hasn't changed:

```text
CACHE HIT
```

No filesystem work.

---

# 45. Better still: evidence cache

Rather than thinking only:

```text
tool result cache
```

think:

```text
observation cache
```

For example:

```text
E104:
TokenValidator.java lines 60-100
```

If still valid:

```text
reuse E104
```

No need to call the tool.

---

# 46. This ties directly into your earlier context work

Now we have:

```text
ContextEngine
     │
     ├── EvidenceStore
     ├── ToolResultCache
     ├── WorkspaceFingerprint
     └── ContextPolicy
```

So Aljabr can say:

> "I already know this."

without relying on Gemma's memory.

---

# 47. Tool selection scoring

The runtime can score tools before presenting them.

```java
public record ToolScore(
    double relevance,
    double cost,
    double risk,
    double freshness
) {}
```

Then:

```text
score =
    relevance
    - costPenalty
    - riskPenalty
    + freshnessBonus
```

The exact formula should be policy-driven.

---

# 48. Example

Current objective:

```text
Find where expiration is validated.
```

Candidates:

```text
search_code      0.92
find_symbol      0.88
read_file        0.61
run_build        0.12
git_push         0.00
```

Only top candidates go to Gemma.

---

# 49. This is a powerful small-model trick

Instead of trying to make Gemma understand:

```text
100 tools
```

make Wayang understand:

```text
100 tools
```

and give Gemma:

```text
3-6 relevant tools
```

This moves complexity from inference into deterministic code.

Exactly what you want for local inference.

---

# 50. Tool affordance context

The prompt might therefore contain:

```text
AVAILABLE ACTIONS

SEARCH_CODE
Find relevant source locations.

READ_FILE
Read a bounded source region.

FIND_REFERENCES
Find usages of a symbol.

APPLY_PATCH
Modify existing source using a validated patch.

RUN_TEST
Run a targeted test.
```

Only relevant tools.

---

# 51. Don't expose implementation details

Gemma shouldn't need to know:

```text
SQLite
filesystem locking
cache backend
thread pools
process IDs
```

It sees:

```text
READ_FILE
SEARCH_CODE
APPLY_PATCH
RUN_TEST
```

Wayang handles the machinery.

---

# 52. Coding-specific tool bundle

I'd define:

```text
CodingToolSet
├── SearchTool
├── FileReader
├── SymbolTool
├── ReferenceTool
├── DiffTool
├── PatchTool
├── TestTool
├── BuildTool
└── CommandTool
```

Then:

```java
CodingToolSet tools =
    CodingToolSet.defaultFor(workspace);
```

---

# 53. Workspace adapters

Don't hardcode Git or Java assumptions into the agent.

Use:

```java
public interface WorkspaceAdapter {

    WorkspaceInfo inspect();

    RepositoryInfo repository();

    BuildSystem buildSystem();

    TestSystem testSystem();

    LanguageSupport languages();
}
```

---

# 54. Example

Java project:

```text
Language:
Java

Build:
Gradle

Tests:
JUnit

Repository:
Git
```

Node project:

```text
Language:
TypeScript

Build:
npm

Tests:
Vitest

Repository:
Git
```

Same Aljabr architecture.

---

# 55. `ProjectProfile`

Cache the detected workspace profile:

```java
public record ProjectProfile(
    List<String> languages,
    Optional<String> buildSystem,
    Optional<String> testFramework,
    Optional<String> packageManager,
    Optional<String> repositoryType
) {}
```

This should be discovered once and reused.

---

# 56. Project discovery itself should be staged

Don't immediately scan everything.

```text
cheap:
├── root files
├── package/build files
├── git status
└── language markers

only if necessary:
├── repository search
├── dependency analysis
└── deep indexing
```

Again:

> progressive disclosure.

---

# 57. One important architectural addition: `CapabilityRegistry`

Wayang should know:

```java
public interface CapabilityRegistry {

    List<Capability> available(
        Workspace workspace
    );
}
```

Capabilities could be:

```text
JAVA
GRADLE
JUNIT
GIT
TYPESCRIPT
NPM
PYTHON
PYTEST
DOCKER
```

Then Aljabr adapts automatically.

---

# 58. Example

If project has:

```text
build.gradle
```

activate:

```text
GradleBuildTool
GradleTestTool
```

If:

```text
pom.xml
```

activate:

```text
MavenBuildTool
MavenTestTool
```

Gemma doesn't need to figure this out.

---

# 59. The resulting coding stack

```text
                 Aljabr
                    │
             DecisionEngine
                    │
              ToolRouter
                    │
          CapabilityRegistry
                    │
          WorkspaceAdapter
                    │
        ┌───────────┴───────────┐
        │                       │
 Observation                 Mutation
        │                       │
 ┌──────┼──────┐          ┌─────┴─────┐
 │      │      │          │           │
Search Read Symbol      Patch       Git
 │      │      │          │
 └──────┴──────┘          │
        │                 │
        └────────┬────────┘
                 ▼
             Evidence
                 │
                 ▼
           Verification
```

---

# 60. What I would implement first

Don't implement the entire tree immediately.

### Phase A

```text
search_code
read_file
apply_patch
run_test
run_build
get_diff
```

### Phase B

```text
read_symbol
find_references
diagnostics
```

### Phase C

```text
git
language intelligence
project indexing
```

### Phase D

```text
shell escape hatch
advanced environment tools
```

---

# 61. The first Aljabr vertical slice

Before building 30 tools, make this work end-to-end:

```text
User:
"Fix the failing expiration test."

        ↓

search_code
        ↓
read_file
        ↓
read_file
        ↓
apply_patch
        ↓
get_diff
        ↓
run_test
        ↓
PASS
        ↓
DONE
```

If that works reliably, you have the foundation.

---

# 62. Then failure path

Make sure this also works:

```text
apply_patch
      ↓
run_test
      ↓
FAIL
      ↓
diagnose
      ↓
read relevant code
      ↓
apply correction
      ↓
run_test
      ↓
PASS
```

This is far more valuable than implementing sophisticated planning prematurely.

---

# 63. And the really important invariant

For every mutation:

```text
OBSERVE
  ↓
DECIDE
  ↓
VALIDATE
  ↓
MUTATE
  ↓
OBSERVE
  ↓
VERIFY
```

Never:

```text
DECIDE
  ↓
MUTATE
  ↓
assume success
```

That invariant should be built directly into Aljabr's runtime.

---

# 64. Where we are now

We have now built the conceptual foundation for:

```text
Wayang
│
├── Runtime
│   ├── AgentState
│   ├── Events
│   ├── Checkpoints
│   ├── Recovery
│   └── Policies
│
├── Context
│   ├── CapabilityDetector
│   ├── ContextController
│   ├── ContextPolicy
│   ├── ContextEngine
│   └── Evidence
│
└── Aljabr
    ├── Planner
    ├── ActiveStep
    ├── DecisionEngine
    ├── ToolRouter
    ├── Executor
    ├── Verifier
    ├── Diagnoser
    ├── Replanner
    └── CodingTools
```

And the next milestone I'd take is **Repository Intelligence / Code Index**.

That's where we build the layer that lets Aljabr answer cheaply:

```text
"Where is this implemented?"
"What calls this?"
"What depends on this?"
"Which files are probably relevant?"
"Which tests cover this?"
"What changed?"
```

without repeatedly asking Gemma to rediscover the repository from scratch.

For your **12B local model**, I consider that next layer particularly important because it lets the runtime do much of the repository navigation deterministically and gives Gemma a small, high-quality evidence packet instead of a giant codebase context.


