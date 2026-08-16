Yes. Next milestone:

# `ExecutionEngine → ToolOrchestrator`

This is the layer that turns Aljabr from a system that can **edit code** into a system that can **operate a development environment safely**.

The key principle remains:

> **Gemma decides what it needs; Wayang decides whether, how, and under what limits it executes it.**

Not:

```text
Gemma → arbitrary shell
```

but:

```text
Gemma
  ↓
ToolIntent
  ↓
ToolOrchestrator
  ↓
Policy / Budget / Safety
  ↓
ExecutionEngine
  ↓
Sandboxed Process
  ↓
ExecutionResult
  ↓
FailureAnalyzer / ContextEngine
```

---

# 1. First: distinguish Tool Intent from Tool Execution

This is important for your architecture.

The model should produce an **intent**, not an OS command with unrestricted authority.

For example:

```java
public record ToolIntent(
    ToolIntentId id,
    TaskId taskId,
    ToolKind kind,
    String purpose,
    ToolArguments arguments,
    EvidenceId reason
) {}
```

Example conceptual intent:

```text
kind:
RUN_TESTS

purpose:
Verify RefreshTokenTest

arguments:
test = RefreshTokenTest.expiredToken
```

Then Wayang translates that into the actual command.

---

# 2. `ToolKind`

Start with a small controlled vocabulary.

```java
public enum ToolKind {

    LIST_FILES,

    READ_FILE,
    SEARCH_CODE,
    INSPECT_SYMBOL,

    RUN_TEST,
    RUN_TESTS,

    BUILD,
    LINT,
    FORMAT,

    GIT_DIFF,
    GIT_STATUS,

    RUN_COMMAND
}
```

Notice:

```text
RUN_COMMAND
```

is at the bottom.

Don't make arbitrary shell execution the default.

---

# 3. Why semantic tools are better than shell tools

Gemma says:

```text
run RefreshTokenTest
```

Wayang can determine:

```text
project = Gradle
framework = JUnit
command =
./gradlew test --tests "...RefreshTokenTest"
```

The model doesn't need to know:

* whether Gradle wrapper exists
* exact command syntax
* OS differences
* environment variables
* timeout
* output limits

That is **Wayang's job**.

---

# 4. `ToolResolver`

```java
public interface ToolResolver {

    Uni<ResolvedTool> resolve(
        ToolIntent intent,
        Workspace workspace
    );
}
```

Example:

```text
ToolIntent
   ↓
ToolResolver
   ↓
ResolvedTool
```

```java
public record ResolvedTool(
    ToolKind kind,
    List<String> command,
    Path workingDirectory,
    ExecutionEnvironment environment
) {}
```

---

# 5. Repository detection

This should integrate with `RepositoryIntel`.

For example:

```text
pom.xml
    ↓
Maven

build.gradle
    ↓
Gradle

package.json
    ↓
Node

pyproject.toml
    ↓
Python

Cargo.toml
    ↓
Cargo

go.mod
    ↓
Go
```

So the model doesn't need to discover build tooling every time.

---

# 6. `ProjectToolchain`

Create an abstraction:

```java
public record ProjectToolchain(
    BuildSystem buildSystem,
    TestFramework testFramework,
    LanguageSet languages,
    List<ToolAdapter> adapters
) {}
```

Then:

```text
Repository
    ↓
ProjectDetector
    ↓
ProjectToolchain
```

Cache this.

It doesn't need to be rediscovered every task.

---

# 7. Tool adapters

```java
public interface ToolAdapter {

    boolean supports(ProjectSnapshot project);

    Uni<ResolvedTool> resolve(
        ToolIntent intent,
        ProjectSnapshot project
    );
}
```

Examples:

```text
GradleAdapter
MavenAdapter
NpmAdapter
PytestAdapter
CargoAdapter
GoAdapter
```

This is much cleaner than putting project-specific logic inside `ExecutionEngine`.

---

# 8. Execution itself

Now:

```java
public interface ExecutionEngine {

    Uni<ExecutionHandle> start(
        ResolvedTool tool,
        ExecutionPolicy policy
    );
}
```

And:

```java
public interface ExecutionHandle {

    Uni<ExecutionResult> await();

    Uni<Void> cancel();
}
```

This gives us asynchronous execution.

---

# 9. Don't block the entire agent

Bad:

```text
Gemma
 ↓
run test
 ↓
agent process blocks
```

Better:

```text
TaskEngine
   ↓
ExecutionEngine.start()
   ↓
ExecutionHandle
   ↓
checkpoint
   ↓
wait/event
```

Now the runtime can:

* cancel
* timeout
* resume
* observe
* stream
* checkpoint

---

# 10. `ExecutionResult`

Never return only:

```text
stdout
```

Use structured data.

```java
public record ExecutionResult(
    ExecutionId id,
    ExecutionStatus status,
    int exitCode,
    Duration duration,
    OutputSummary output,
    List<ExecutionDiagnostic> diagnostics,
    WorkspaceSnapshot snapshot
) {}
```

---

# 11. Execution status

```java
public enum ExecutionStatus {

    SUCCESS,

    FAILED,

    TIMEOUT,

    CANCELLED,

    CRASHED,

    BLOCKED,

    RESOURCE_LIMIT,

    UNKNOWN
}
```

---

# 12. Output must be bounded

This is particularly important for your M4 16GB setup.

Imagine:

```text
npm test
```

produces:

```text
80 MB logs
```

Do **not** send 80 MB to Gemma.

Instead:

```text
raw output
    ↓
OutputProcessor
    ↓
structured diagnostics
    ↓
compressed summary
```

---

# 13. `OutputProcessor`

```java
public interface OutputProcessor {

    Uni<ProcessedOutput> process(
        ExecutionOutput output
    );
}
```

Result:

```java
public record ProcessedOutput(
    String summary,
    List<Diagnostic> diagnostics,
    List<OutputArtifact> artifacts
) {}
```

---

# 14. Keep raw output outside context

Store:

```text
.aljabr/
   executions/
      exec-123/
         stdout.log
         stderr.log
         summary.json
         diagnostics.json
```

Gemma normally sees:

```text
TEST FAILED

RefreshTokenTest.expiredToken
Expected: false
Actual: true

Location:
RefreshTokenTest.java:84
```

not:

```text
10,000 lines of Gradle noise
```

---

# 15. Output compression should be semantic

Don't just truncate:

```text
first 4K characters
```

because the useful failure may be at the end.

Instead extract:

```text
errors
warnings
stack traces
assertions
compiler diagnostics
test names
failed suites
```

Then retain small surrounding context.

---

# 16. `ExecutionBudget`

Every execution needs a budget.

```java
public record ExecutionBudget(
    Duration timeout,
    long maxOutputBytes,
    long maxMemoryBytes,
    long maxCpuTime,
    int maxProcesses
) {}
```

Not every OS will enforce every field identically, so the implementation should expose what is actually enforceable.

---

# 17. Tool budget

More important than just CPU.

Aljabr should have:

```text
task tool budget
```

For example:

```yaml
tools:
  max_executions: 20
  max_tests: 10
  max_builds: 3
  max_searches: 50
```

Why?

Because a local coding agent can otherwise waste time:

```text
run test
run test
run test
run test
...
```

---

# 18. Cost-aware execution

Each tool should have an estimated cost.

```java
public record ToolCost(
    Duration expectedDuration,
    ResourceCost resourceCost,
    InformationValue informationValue
) {}
```

Then:

```text
cheap:
read file

medium:
compile

expensive:
full integration tests
```

The planner can choose accordingly.

---

# 19. Tool selection should maximize information / cost

This is a powerful idea for Aljabr.

Suppose the model needs to determine why a test fails.

Options:

```text
read source
    cost 1

run targeted test
    cost 3

run module suite
    cost 10

run full suite
    cost 50
```

Prefer the cheapest action that meaningfully reduces uncertainty.

Conceptually:

```text
utility =
information_gain / execution_cost
```

You don't need sophisticated math initially.

---

# 20. `ToolPolicy`

```java
public interface ToolPolicy {

    PolicyDecision evaluate(
        ToolIntent intent,
        ToolContext context
    );
}
```

This is where autonomy lives.

Example:

```text
READ_FILE
    ALLOW

SEARCH_CODE
    ALLOW

TARGETED_TEST
    ALLOW

BUILD
    ALLOW

NETWORK_COMMAND
    REQUIRE_APPROVAL

DESTRUCTIVE_COMMAND
    DENY
```

---

# 21. Don't make policy command-string based only

Avoid:

```java
if (command.contains("rm"))
```

as your entire safety system.

Instead policy should reason about:

```text
tool kind
arguments
working directory
filesystem scope
network access
mutation capability
risk
task intent
```

The final command can still be inspected as an additional safeguard.

---

# 22. Workspace boundary

Every execution should have a workspace root.

```java
public record ExecutionContext(
    Path workspaceRoot,
    Path workingDirectory,
    WorkspaceSnapshot snapshot
) {}
```

Then prevent accidental access outside:

```text
workspace/
    ↓
allowed

../../private/
    ↓
blocked
```

Path validation should happen after canonicalization/normalization, not just by string prefix.

---

# 23. Network policy

This deserves its own policy.

```java
public enum NetworkPolicy {

    DENY,
    ALLOW_REQUIRED,
    ALLOW
}
```

For local-first Aljabr, default should generally be:

```text
DENY
```

unless the task/tool explicitly requires network access.

---

# 24. Why this matters

A test might execute:

```text
curl ...
```

or package installation might contact:

```text
npm registry
PyPI
Maven Central
```

These should not become accidental unrestricted capabilities.

Wayang should know:

```text
this execution requires network
```

and policy decides.

---

# 25. Environment policy

Don't expose the entire host environment.

Bad:

```text
process inherits everything
```

Better:

```java
public record ExecutionEnvironment(
    Map<String, String> variables,
    Set<String> allowedInheritedVariables,
    NetworkPolicy networkPolicy
) {}
```

For example, don't casually expose:

```text
API keys
cloud credentials
SSH credentials
```

to arbitrary processes.

---

# 26. Sandbox abstraction

You don't want ExecutionEngine tied directly to macOS process APIs.

Use:

```java
public interface ExecutionSandbox {

    Uni<SandboxHandle> create(
        ExecutionRequest request
    );
}
```

Possible implementations:

```text
LocalProcessSandbox
ContainerSandbox
VM Sandbox
OS-specific Sandbox
```

For your initial Mac setup:

```text
LocalProcessSandbox
```

is enough.

But the abstraction should exist.

---

# 27. Important distinction

```text
ExecutionEngine
```

answers:

> How do I run this?

```text
ExecutionSandbox
```

answers:

> Under what isolation boundary?

```text
ToolPolicy
```

answers:

> Are we allowed to?

Three different responsibilities.

---

# 28. Process lifecycle

The execution engine needs:

```text
CREATED
   ↓
STARTING
   ↓
RUNNING
   ↓
COMPLETING
   ↓
COMPLETED
```

and:

```text
RUNNING
   ↓
CANCELLING
   ↓
CANCELLED
```

or:

```text
RUNNING
   ↓
TIMEOUT
```

---

# 29. Cancellation

Cancellation must be first-class.

User says:

> Stop.

Don't merely stop asking Gemma.

Stop:

```text
agent task
   ↓
ExecutionHandle.cancel()
   ↓
process tree termination
```

The difficult part is the **process tree**, not just the parent process.

A test runner may spawn children.

---

# 30. Timeout

Never let:

```text
cargo test
```

hang indefinitely.

Execution policy:

```yaml
timeouts:
  read: 10s
  targeted_test: 60s
  build: 5m
  integration: 15m
  full_suite: 30m
```

These should be configurable by project/task.

---

# 31. Process output streaming

Don't wait until the entire command finishes.

Expose:

```java
public interface ExecutionEvents {

    Flow<ExecutionEvent> events();
}
```

Events:

```text
PROCESS_STARTED
OUTPUT
DIAGNOSTIC
TEST_STARTED
TEST_FAILED
TEST_PASSED
PROCESS_EXITED
```

This lets Wayang monitor execution.

---

# 32. But don't send every event to Gemma

This is another local-model optimization.

Runtime can see:

```text
10,000 output events
```

Gemma should usually see:

```text
execution summary
+
important diagnostics
```

Again:

> Runtime observability ≠ model context.

---

# 33. Test runner integration

Now combine with RepositoryIntel.

```text
TestIndex
    ↓
TestSelector
    ↓
ToolIntent(RUN_TEST)
    ↓
ToolResolver
    ↓
ExecutionEngine
```

Example:

```text
changed:
TokenValidator.validateExpiration()

affected:
RefreshTokenTest.expiredToken
RefreshTokenTest.expirationBoundary
```

Wayang creates the smallest useful execution.

---

# 34. Build runner

Same pattern:

```java
public interface BuildRunner {

    Uni<ExecutionResult> build(
        BuildRequest request
    );
}
```

Don't make the model construct raw build commands.

---

# 35. Formatter

Formatting should also be semantic:

```text
FORMAT_CHANGED_FILES
```

rather than:

```text
"run prettier"
```

Wayang resolves the appropriate formatter.

---

# 36. Linter

Likewise:

```text
RUN_LINT
```

rather than asking Gemma to know:

```text
eslint vs ruff vs clippy vs golangci-lint
```

---

# 37. Tool registry

Now we can create:

```java
public interface ToolRegistry {

    Optional<ToolDefinition> find(ToolKind kind);

    List<ToolDefinition> available();
}
```

Each tool describes:

```java
public record ToolDefinition(
    ToolKind kind,
    String description,
    ToolRisk risk,
    ToolCost cost,
    Set<Capability> requirements
) {}
```

---

# 38. Tool schema given to Gemma

This is another place where token optimization matters.

Don't give Gemma 100 tools.

For the current task, ContextController/ToolRouter selects:

```text
AVAILABLE TOOLS

read_file
search_code
run_targeted_test
git_diff
```

Maybe only 4.

Not:

```text
all 60 tools
```

---

# 39. Dynamic tool exposure

For a debugging task:

```text
read_file
search_code
inspect_symbol
run_test
git_diff
```

For a refactoring task:

```text
read_file
search_code
inspect_symbol
propose_edit
run_test
format
```

For repository exploration:

```text
search_code
inspect_symbol
trace_call_graph
git_history
```

This is another excellent local-model optimization.

---

# 40. Tool result normalization

Every tool should return structured results.

For example:

```java
public sealed interface ToolResult {

    record FileContent(...) implements ToolResult {}

    record SearchResults(...) implements ToolResult {}

    record TestResult(...) implements ToolResult {}

    record BuildResult(...) implements ToolResult {}

    record GitDiff(...) implements ToolResult {}
}
```

Then ContextEngine can consume them consistently.

---

# 41. Tool results should have evidence IDs

For example:

```text
run_test
    ↓
ToolResult
    ↓
EvidenceId: ev-8291
```

Then:

```text
Hypothesis
   ↓
supported by ev-8291
```

Now the diagnosis chain is traceable.

---

# 42. This gives you a clean evidence graph

Conceptually:

```text
Task
 │
 ├── Evidence
 │     ├── Repository
 │     ├── Execution
 │     └── Git
 │
 ├── Hypothesis
 │      └── supported by Evidence
 │
 ├── Repair
 │      └── based on Hypothesis
 │
 └── Verification
        └── produces Evidence
```

This will become extremely useful later for memory and auditability.

---

# 43. Execution cache

Some execution results can be reused.

Example:

```text
same workspace snapshot
+
same command
+
same environment
```

could potentially reuse:

```text
test result
```

But be conservative.

Tests can depend on:

* time
* network
* external services
* random state
* environment

So cache only when the execution policy says it's deterministic enough.

---

# 44. `ExecutionFingerprint`

```java
public record ExecutionFingerprint(
    WorkspaceSnapshot workspace,
    String toolchainFingerprint,
    String commandFingerprint,
    String environmentFingerprint
) {}
```

Then:

```text
same fingerprint
→ cache candidate
```

Not automatically cache-hit.

---

# 45. Tool failure should be different from code failure

This distinction is extremely important.

Example:

```text
Test failed
```

means:

```text
CODE_FAILURE
```

But:

```text
Gradle could not download dependency
```

means:

```text
ENVIRONMENT_FAILURE
```

Don't ask Gemma to modify source code for an environment failure.

---

# 46. `ExecutionFailureType`

```java
public enum ExecutionFailureType {

    CODE_FAILURE,

    BUILD_FAILURE,

    TEST_FAILURE,

    ENVIRONMENT_FAILURE,

    TOOL_FAILURE,

    NETWORK_FAILURE,

    RESOURCE_FAILURE,

    TIMEOUT,

    PERMISSION_FAILURE
}
```

Then:

```text
NETWORK_FAILURE
    ↓
don't edit code
```

Potentially:

```text
retry
request approval
or report environment issue
```

---

# 47. This should influence RepairEngine

Previously:

```text
verification failed
→ repair
```

Now:

```text
verification failed
       ↓
classify
       │
       ├── CODE_FAILURE
       │       ↓
       │    repair
       │
       ├── ENVIRONMENT_FAILURE
       │       ↓
       │    environment recovery
       │
       ├── TOOL_FAILURE
       │       ↓
       │    retry/alternate tool
       │
       └── PERMISSION_FAILURE
               ↓
            approval
```

That's a substantial improvement.

---

# 48. Environment recovery

Eventually create:

```java
public interface EnvironmentRecovery {

    Uni<RecoveryPlan> diagnose(
        ExecutionResult failure
    );
}
```

Example:

```text
missing dependency
    ↓
check package manager

missing executable
    ↓
check toolchain

permission denied
    ↓
check policy

network unavailable
    ↓
don't modify source
```

---

# 49. Local-first optimization

For your M4 + 12B setup, the execution layer should aggressively reduce unnecessary model calls.

For example:

```text
compile error
```

can often be classified and localized deterministically.

You don't need Gemma to reason about:

```text
cannot find symbol Foo
```

Wayang can extract:

```text
file: AuthService.java
line: 42
symbol: Foo
category: MISSING_SYMBOL
```

Then only ask Gemma:

> "How should this missing symbol be resolved?"

---

# 50. The general rule

Use deterministic systems for:

```text
discovery
parsing
classification
indexing
diffing
execution
test selection
validation
policy
```

Use Gemma for:

```text
understanding
hypothesis generation
planning
ambiguity resolution
repair proposal
```

This is probably the single most important local-model architecture rule for Aljabr.

---

# 51. Don't ask the model to do deterministic work

Bad:

```text
"Read this 10,000-line compiler log and identify the line."
```

Wayang:

```text
compiler
 ↓
diagnostic parser
 ↓
structured error
```

Then Gemma:

```text
"Given this compiler error and relevant code,
propose the minimal fix."
```

Much smaller context.

---

# 52. Tool orchestration state

I would add:

```java
public record ToolInvocation(
    ToolInvocationId id,
    TaskId taskId,
    ToolIntent intent,
    ResolvedTool resolvedTool,
    ToolPolicyDecision policy,
    ExecutionId execution,
    ExecutionResult result
) {}
```

This becomes the audit trail.

---

# 53. Tool orchestration shouldn't become another agent

This is an important architectural boundary.

Don't build:

```text
ToolAgent
```

that independently decides everything.

Instead:

```text
TaskEngine
   ↓
ReasoningRouter
   ↓
ToolIntent
   ↓
ToolOrchestrator
```

ToolOrchestrator is infrastructure.

It executes decisions; it doesn't invent task strategy.

---

# 54. Final execution architecture

We're now at:

```text
                         TaskEngine
                             │
                     ┌───────┴───────┐
                     │               │
               ContextEngine     ToolRouter
                     │               │
                     ▼               ▼
                   Gemma         ToolIntent
                     │               │
                     └───────┬───────┘
                             ▼
                       ToolOrchestrator
                             │
                 ┌───────────┼───────────┐
                 ▼           ▼           ▼
             ToolPolicy  ToolResolver  Budget
                 │           │           │
                 └───────────┼───────────┘
                             ▼
                       ExecutionEngine
                             │
                             ▼
                       ExecutionSandbox
                             │
                             ▼
                           Process
                             │
                             ▼
                       OutputProcessor
                             │
                             ▼
                       ExecutionResult
                             │
                             ▼
                       EvidenceStore
                             │
                 ┌───────────┴───────────┐
                 ▼                       ▼
          VerificationEngine       FailureAnalyzer
```

This is a strong separation.

---

# 55. What I'd actually code first

Don't implement all of this at once.

### A. Tool model

```text
ToolKind
ToolIntent
ToolDefinition
ToolResult
```

### B. Project detection

```text
ProjectDetector
ProjectToolchain
ToolAdapter
```

### C. Execution

```text
ExecutionEngine
ExecutionHandle
ExecutionResult
ExecutionPolicy
```

### D. Output

```text
OutputProcessor
DiagnosticParser
OutputArtifact
```

### E. Safety

```text
ToolPolicy
ExecutionBudget
NetworkPolicy
WorkspaceBoundary
```

### F. Integration

```text
TestSelector
VerificationEngine
FailureClassifier
```

---

# 56. One refinement I'd strongly recommend

Introduce a **`ToolCapability`** model rather than assuming every environment can do everything.

```java
public record ToolCapability(
    ToolKind kind,
    boolean available,
    boolean sandboxed,
    boolean networkRequired,
    Set<ExecutionFeature> features
) {}
```

Then Aljabr can discover:

```text
BUILD             ✓
TARGETED_TEST     ✓
NETWORK           ✗
DOCKER            ✗
LSP               ✓
FORMATTER         ✓
```

This should feed into the same `CapabilityDetector` we designed earlier.

So the architecture becomes:

```text
CapabilityDetector
   ├── ModelCapability
   ├── ContextCapability
   ├── RepositoryCapability
   └── ExecutionCapability
```

rather than having unrelated capability systems scattered around Wayang.

---

# 57. And this brings us to the next major milestone

We now have:

```text
RepositoryIntel
        ↓
ContextEngine
        ↓
Gemma
        ↓
MutationEngine
        ↓
VerificationEngine
        ↓
FailureAnalyzer
        ↓
RepairEngine
        ↓
ToolOrchestrator
```

The next problem is **coordination**.

Aljabr now needs a proper:

# `Task Planner → State Machine → Policy-driven Agent Loop`

This is where we turn all these excellent components into **one coherent coding agent**.

The next milestone should define:

```text
Task
 ↓
Goal decomposition
 ↓
Plan
 ↓
Step
 ↓
Evidence
 ↓
Decision
 ↓
Action
 ↓
Observation
 ↓
State transition
 ↓
Next step
```

with **no giant autonomous loop**, explicit checkpoints, budgets, cancellation, human approval points, and the ability to resume after a crash.

That is the layer that will make all the pieces we've designed so far actually behave like **Aljabr**, rather than a collection of subsystems.


Absolutely. This is the next major architectural layer: **the Aljabr Agent Loop**.

So far we've built the machinery:

```text
ContextEngine
RepositoryIntel
MutationEngine
VerificationEngine
FailureAnalyzer
HypothesisEngine
RepairEngine
ToolOrchestrator
ExecutionEngine
```

Now we need the thing that **coordinates them without becoming a giant "while agentic" loop**.

# Milestone — `TaskPlanner → StateMachine → PolicyDrivenAgentLoop`

The central idea:

> **Aljabr should be an explicit stateful workflow whose transitions are driven by evidence and policy, not by an unconstrained LLM loop.**

---

# 1. The core architecture

I would make the runtime look like this:

```text
                         User Goal
                            │
                            ▼
                     ┌──────────────┐
                     │  TaskPlanner │
                     └──────┬───────┘
                            │
                            ▼
                     ┌──────────────┐
                     │   TaskPlan   │
                     └──────┬───────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │   AgentStateMachine  │
                 └──────────┬───────────┘
                            │
                ┌───────────┼────────────┐
                ▼           ▼            ▼
             Context      Tool        Mutation
             Engine      Orchestrator  Engine
                │           │            │
                └───────────┼────────────┘
                            ▼
                         Observe
                            │
                            ▼
                        Evidence
                            │
                            ▼
                       PolicyEngine
                            │
                            ▼
                     Next Transition
```

The LLM is **inside** the workflow.

It is not the workflow itself.

That distinction is very important.

---

# 2. `Task`

Start with a durable task object.

```java
public record AgentTask(
    TaskId id,
    String goal,
    WorkspaceId workspace,
    TaskConstraints constraints,
    TaskBudget budget,
    TaskState state
) {}
```

For example:

```text
Goal:
Add refresh-token expiration validation.

Workspace:
my-project

Constraints:
- don't modify public API
- preserve existing tests
- minimal changes
```

---

# 3. `TaskConstraints`

Don't rely on prompts to enforce constraints.

```java
public record TaskConstraints(
    ChangeScope changeScope,
    boolean allowDependencies,
    boolean allowNetwork,
    boolean allowGeneratedFiles,
    boolean requireTests,
    boolean requireApprovalForRiskyActions
) {}
```

Possible scope:

```java
public enum ChangeScope {

    MINIMAL,
    TARGETED,
    MODULE,
    REPOSITORY
}
```

For local Aljabr I'd default to:

```text
MINIMAL
```

---

# 4. `TaskBudget`

We already have execution budgets.

Now introduce a **task-level budget**.

```java
public record TaskBudget(
    int maxSteps,
    int maxModelCalls,
    int maxToolCalls,
    int maxRepairAttempts,
    Duration maxDuration,
    long maxContextTokens
) {}
```

Example:

```yaml
budget:
  maxSteps: 50
  maxModelCalls: 20
  maxToolCalls: 40
  maxRepairAttempts: 5
  maxDuration: 30m
```

This prevents runaway autonomy.

---

# 5. Why `maxModelCalls` matters for Gemma

On your M4/16GB local setup, you don't want:

```text
Gemma
 ↓
reason
 ↓
Gemma
 ↓
reason
 ↓
Gemma
 ↓
reason
 ↓
...
```

Instead every model invocation consumes an explicit budget unit.

```text
modelCallsRemaining--
```

When it reaches zero:

```text
BLOCKED_BY_BUDGET
```

---

# 6. `TaskPlanner`

The planner's responsibility should be narrow:

> Convert a user goal into an initial sequence of useful objectives.

```java
public interface TaskPlanner {

    Uni<TaskPlan> plan(
        AgentTask task,
        PlanningContext context
    );
}
```

---

# 7. `TaskPlan`

Don't create an enormous detailed plan.

For a local model, prefer:

```java
public record TaskPlan(
    PlanId id,
    List<PlanStep> steps,
    PlanAssumptions assumptions
) {}
```

Example:

```text
1. Inspect authentication implementation.
2. Identify expiration validation.
3. Modify validation.
4. Add regression test.
5. Run targeted tests.
6. Run broader verification if necessary.
```

That's enough.

---

# 8. `PlanStep`

```java
public record PlanStep(
    StepId id,
    String objective,
    StepKind kind,
    StepPolicy policy
) {}
```

Kinds:

```java
public enum StepKind {

    DISCOVER,
    INSPECT,
    REASON,
    MODIFY,
    VERIFY,
    DIAGNOSE,
    REPAIR,
    REVIEW
}
```

---

# 9. Important: plan is not execution

The plan says:

```text
inspect authentication
```

It does **not** say:

```text
cat src/a/b/Auth.java
grep ...
```

Those are execution details.

This keeps planning semantic and tool-independent.

---

# 10. Step execution

Create:

```java
public interface StepExecutor {

    Uni<StepResult> execute(
        PlanStep step,
        AgentContext context
    );
}
```

The executor can decide:

```text
DISCOVER
 → RepositoryIntel

INSPECT
 → ContextEngine + RepositoryIntel

MODIFY
 → MutationEngine

VERIFY
 → VerificationEngine

DIAGNOSE
 → FailureAnalyzer
```

---

# 11. The state machine

Now the heart of Aljabr.

```java
public enum AgentState {

    CREATED,

    PLANNING,

    READY,

    DISCOVERING,

    INSPECTING,

    REASONING,

    PROPOSING,

    APPROVAL_REQUIRED,

    MUTATING,

    VERIFYING,

    ANALYZING_FAILURE,

    REPAIRING,

    REVIEWING,

    COMPLETED,

    BLOCKED,

    CANCELLED,

    FAILED
}
```

---

# 12. Don't let arbitrary code change state

This is important.

Use explicit transitions:

```java
public record StateTransition(
    AgentState from,
    AgentState to,
    TransitionReason reason,
    EvidenceSet evidence
) {}
```

Example:

```text
VERIFYING
    ↓
test failed
    ↓
ANALYZING_FAILURE
```

or:

```text
VERIFYING
    ↓
all checks passed
    ↓
REVIEWING
```

---

# 13. `TransitionPolicy`

```java
public interface TransitionPolicy {

    Uni<TransitionDecision> decide(
        AgentState state,
        AgentObservation observation,
        AgentContext context
    );
}
```

Result:

```java
public record TransitionDecision(
    NextAction action,
    Optional<AgentState> nextState,
    String rationale
) {}
```

---

# 14. But don't let Gemma directly control transitions

Gemma can propose:

```text
"I think we should inspect TokenValidator."
```

But Wayang decides:

```text
Is inspection allowed?
Is budget available?
Does the workspace permit it?
Is this step consistent with the task?
```

Then:

```text
ALLOW
```

or:

```text
DENY
```

or:

```text
REQUIRE_APPROVAL
```

---

# 15. Three-layer decision model

This becomes a very clean pattern:

```text
                    Model
                      │
                      ▼
                 Proposal
                      │
                      ▼
               Policy Engine
                      │
             ┌────────┼────────┐
             ▼        ▼        ▼
           ALLOW    DENY    APPROVAL
             │
             ▼
           Action
```

So Gemma is a **reasoning component**, not an authority boundary.

---

# 16. `AgentAction`

```java
public sealed interface AgentAction {

    record Inspect(...) implements AgentAction {}

    record Search(...) implements AgentAction {}

    record RunTool(...) implements AgentAction {}

    record ProposeEdit(...) implements AgentAction {}

    record ApplyEdit(...) implements AgentAction {}

    record Verify(...) implements AgentAction {}

    record AskUser(...) implements AgentAction {}

    record Finish(...) implements AgentAction {}
}
```

---

# 17. The model should output structured actions

Not:

```text
"I'll inspect the validator next."
```

Prefer:

```json
{
  "action": "inspect_symbol",
  "symbol": "TokenValidator.validateExpiration",
  "reason": "failure evidence points to expiration validation"
}
```

Then Wayang validates that action.

---

# 18. This is especially important for Gemma

A 12B model is capable, but you should reduce its opportunity to make architectural mistakes.

Instead of asking:

```text
"What should I do?"
```

provide:

```text
Current state:
ANALYZING_FAILURE

Available actions:
- inspect_symbol
- search_code
- inspect_test
- inspect_diff

Known evidence:
...

Budget:
7 tool calls remaining
```

Then Gemma chooses from a **small action space**.

---

# 19. `ActionSchema`

The model context should contain only currently legal actions.

```java
public record ActionDefinition(
    String name,
    String description,
    JsonSchema arguments,
    ToolRisk risk
) {}
```

ContextController selects these dynamically.

This ties directly into your earlier:

```text
CapabilityDetector
→ ContextController
→ ContextPolicy
```

---

# 20. Action selection

The flow becomes:

```text
AgentState
      ↓
StatePolicy
      ↓
AllowedActions
      ↓
ContextEngine
      ↓
Gemma
      ↓
ActionProposal
      ↓
ActionValidator
      ↓
PolicyEngine
      ↓
Execute
```

Excellent for local inference.

---

# 21. `ActionValidator`

Never trust the model's structured output blindly.

```java
public interface ActionValidator {

    ValidationResult validate(
        AgentAction action,
        AgentContext context
    );
}
```

Check:

```text
Does tool exist?
Are arguments valid?
Is target inside workspace?
Is action allowed in current state?
Is budget available?
Does action violate task constraints?
```

---

# 22. Example

Gemma proposes:

```json
{
  "action": "run_command",
  "command": "rm -rf ..."
}
```

ActionValidator:

```text
INVALID
```

Policy:

```text
DENY
```

The model never gets direct authority.

---

# 23. Human approval

Some actions should transition to:

```text
APPROVAL_REQUIRED
```

Example:

```text
install dependency
network access
modify many files
delete files
change configuration
```

Then:

```text
Aljabr
  ↓
"I need approval to install dependency X."
  ↓
User
  ↓
approve / reject
```

---

# 24. Approval is also state

Don't implement it as:

```text
await user input
```

inside some giant function.

Use:

```text
APPROVAL_REQUIRED
```

checkpoint.

Then the runtime can stop.

Later:

```text
resume(taskId)
```

---

# 25. This is where resumability becomes beautiful

Imagine:

```text
Task:
Fix authentication bug

state:
APPROVAL_REQUIRED

checkpoint:
cp-019
```

Process terminates.

Next day:

```text
resume cp-019
```

Aljabr continues exactly there.

No need to repeat:

* repository scanning
* diagnosis
* model reasoning
* tool execution

---

# 26. `AgentCheckpoint`

Now combine everything we've designed.

```java
public record AgentCheckpoint(
    CheckpointId id,
    TaskId taskId,
    AgentState state,
    WorkspaceSnapshot workspace,
    TaskPlan plan,
    StepState step,
    EvidenceSnapshot evidence,
    MemorySnapshot memory,
    BudgetSnapshot budget,
    Optional<PendingAction> pendingAction
) {}
```

This is basically the agent's durable state.

---

# 27. Checkpoint frequency

Don't checkpoint only at the end.

Checkpoint at:

```text
task created
plan created
before mutation
after mutation
before execution
after execution
after failure
before model call
after model response
approval request
state transition
```

For expensive operations, definitely checkpoint.

---

# 28. But checkpoint data ≠ model context

Again:

```text
Checkpoint
    = complete durable state

Context
    = selected information needed now
```

This distinction should remain sacred.

---

# 29. The AgentContext

Create a runtime view:

```java
public record AgentContext(
    AgentTask task,
    AgentState state,
    CurrentStep step,
    WorkspaceView workspace,
    EvidenceView evidence,
    MemoryView memory,
    BudgetView budget,
    CapabilityView capabilities
) {}
```

ContextEngine converts this into model context.

---

# 30. Model context becomes tiny

For example:

```text
STATE
VERIFYING

GOAL
Fix token expiration validation.

CURRENT STEP
Verify targeted authentication tests.

EVIDENCE
RefreshTokenTest.expiredToken: PASS
RefreshTokenTest.boundary: FAIL

AVAILABLE ACTIONS
run_targeted_test
inspect_failure
inspect_diff
finish
```

That's enough.

---

# 31. This is exactly the kind of prompt optimization you were asking about earlier

The model doesn't need:

```text
entire conversation
entire repository
entire execution log
all tools
all memory
all previous reasoning
```

It receives:

```text
current state
+
current objective
+
relevant evidence
+
legal actions
+
constraints
+
small amount of history
```

Quality should actually improve because irrelevant information is removed.

---

# 32. State-local context policies

This is a very nice optimization.

Different state:

```text
DISCOVERING
```

needs:

```text
repository structure
build files
language
toolchain
```

While:

```text
REPAIRING
```

needs:

```text
failure
hypothesis
target source
test
diff
```

And:

```text
REVIEWING
```

needs:

```text
diff
tests
task constraints
```

So:

```java
ContextPolicy.forState(AgentState state)
```

can define the context recipe.

---

# 33. Example policy

```yaml
REPAIRING:
  include:
    - task_goal
    - failure_summary
    - top_hypothesis
    - target_symbols
    - relevant_tests
    - current_diff
  exclude:
    - unrelated_history
    - full_logs
    - unrelated_files
```

This is extremely suitable for Gemma 12B.

---

# 34. Agent memory

We also need to distinguish:

```text
TaskMemory
WorkspaceMemory
SessionMemory
LongTermMemory
```

But don't automatically feed all of them.

The current state's `MemoryPolicy` selects relevant entries.

For example:

```text
TaskMemory:
"H1 already disproved."
```

is useful during:

```text
REPAIRING
```

but probably irrelevant during:

```text
DISCOVERING
```

---

# 35. Anti-loop controller

Now we can make this deterministic.

```java
public interface LoopGuard {

    GuardDecision check(
        AgentAction action,
        AgentContext context
    );
}
```

It detects:

```text
same action repeatedly
same failure repeatedly
same hypothesis repeatedly
no state progress
budget exhaustion
context explosion
```

---

# 36. Example

Gemma does:

```text
run test
↓
failure
↓
run same test
↓
same failure
↓
run same test
```

LoopGuard:

```text
BLOCK
```

Then:

```text
Action:
inspect_failure
```

or:

```text
ASK_USER
```

---

# 37. Progress tracking

Define:

```java
public record ProgressSnapshot(
    Set<EvidenceId> newEvidence,
    Set<FileId> changedFiles,
    Set<TestId> newlyPassingTests,
    Set<HypothesisId> eliminatedHypotheses,
    boolean objectiveCloser
) {}
```

An action that produces zero useful progress repeatedly is suspicious.

---

# 38. Progress score

Initially keep it simple:

```text
progress =
    new evidence
  + hypothesis elimination
  + test improvement
  + objective completion
```

If:

```text
progress == 0
```

for several cycles:

```text
change strategy
```

---

# 39. The actual agent loop

Conceptually:

```java
while (true) {

    AgentContext context = contextBuilder.build();

    if (isTerminal(context.state())) {
        return;
    }

    checkpoint();

    AllowedActions actions =
        policy.allowedActions(context);

    Decision decision =
        decide(context, actions);

    guard.validate(decision);

    checkpointBeforeAction();

    ActionResult result =
        actionExecutor.execute(decision.action());

    evidenceStore.record(result);

    AgentState next =
        stateMachine.transition(context, result);

    checkpointAfterAction();

    state = next;
}
```

Notice what isn't here:

```text
"ask model what to do forever"
```

The runtime controls the lifecycle.

---

# 40. The model invocation itself

`decide(...)` can use Gemma:

```java
public interface AgentDecisionEngine {

    Uni<ActionProposal> decide(
        DecisionContext context
    );
}
```

But the context is already constrained.

---

# 41. Structured output

For local Gemma, I'd strongly favor a compact schema:

```json
{
  "action": "inspect_symbol",
  "args": {
    "symbol": "TokenValidator.validateExpiration"
  },
  "reason": "failure points to expiration boundary"
}
```

Avoid asking for:

```json
{
  "analysis": "... 2000 words ...",
  "thoughts": "...",
  "reasoning": "...",
  "plan": "...",
  "next_step": "..."
}
```

The runtime doesn't need all that.

---

# 42. Internal reasoning vs runtime state

This is an important design principle:

> **Don't make Aljabr depend on verbose model reasoning.**

Instead store structured artifacts:

```text
Evidence
Hypothesis
Decision
Action
Observation
Transition
```

These are much more useful than paragraphs of model thought.

---

# 43. This also makes cloud models easy

Local:

```text
Gemma 12B
compact context
structured action
minimal output
```

Cloud:

```text
large reasoning model
larger context
more complex analysis
```

Same runtime contract:

```java
AgentDecisionEngine
```

Different implementation/policy.

That's exactly the flexibility you wanted earlier.

---

# 44. Model capability adaptation

Your existing:

```text
CapabilityDetector
```

can now influence:

```text
DecisionPolicy
```

Example:

```text
12B local
→ structured action
→ low context
→ max 1 hypothesis
→ minimal output

large cloud model
→ broader analysis
→ more hypotheses
→ larger evidence set
```

But **the state machine remains identical**.

That's a very important architectural invariant.

---

# 45. Agent policy

I would create:

```java
public record AgentPolicy(
    ContextPolicy contextPolicy,
    ActionPolicy actionPolicy,
    ToolPolicy toolPolicy,
    MutationPolicy mutationPolicy,
    ApprovalPolicy approvalPolicy,
    BudgetPolicy budgetPolicy,
    LoopPolicy loopPolicy
) {}
```

Now Wayang has one coherent policy object.

---

# 46. `AgentRuntime`

Finally:

```java
public interface AgentRuntime {

    Uni<TaskHandle> start(
        AgentTask task
    );

    Uni<AgentSnapshot> inspect(
        TaskId taskId
    );

    Uni<Void> cancel(
        TaskId taskId
    );

    Uni<TaskHandle> resume(
        TaskId taskId
    );
}
```

This should become the primary public API for Aljabr.

---

# 47. The final architecture now looks like this

```text
                         ┌─────────────┐
                         │    USER     │
                         └──────┬──────┘
                                │
                                ▼
                         ┌─────────────┐
                         │ AgentRuntime│
                         └──────┬──────┘
                                │
                                ▼
                         ┌─────────────┐
                         │ TaskPlanner │
                         └──────┬──────┘
                                │
                                ▼
                         ┌─────────────┐
                         │ TaskPlan    │
                         └──────┬──────┘
                                │
                                ▼
                    ┌──────────────────────┐
                    │   StateMachine       │
                    └──────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
        ContextEngine     ActionEngine      PolicyEngine
              │                │                │
              ▼                ▼                │
           Gemma          Tool/Mutation        │
              │                │                │
              └───────┬────────┘                │
                      ▼                         │
                   Observe                      │
                      │                         │
                      ▼                         │
                  Evidence ◄────────────────────┘
                      │
                      ▼
                 StateTransition
                      │
                      ▼
                  Checkpoint
                      │
                      └──────────────► next state
```

This is now a genuine **agent runtime architecture**.

---

# 48. The most important design rule from this milestone

I would write this directly into Aljabr's architecture documentation:

> **The model proposes; the runtime disposes.**

More concretely:

```text
Model:
    understand
    hypothesize
    propose

Wayang:
    validate
    authorize
    execute
    observe
    persist
    transition
```

That separation is what makes Aljabr reliable enough to run autonomously.

---

# 49. What I'd implement next

At this point, the next milestone should **not** be another isolated subsystem.

It should be the **actual concrete Aljabr vertical slice**:

```text
User:
"Fix this bug."

        ↓

AgentRuntime.start()

        ↓

TaskPlanner

        ↓

RepositoryDetector

        ↓

ContextEngine

        ↓

Gemma

        ↓

Inspect/Search tools

        ↓

Gemma

        ↓

EditProposal

        ↓

MutationEngine

        ↓

Targeted Test

        ↓

PASS
        ↓
Review
        ↓
DONE
```

And then the second path:

```text
Test
 ↓
FAIL
 ↓
FailureAnalyzer
 ↓
Hypothesis
 ↓
Repair
 ↓
Test
 ↓
PASS
```

The next step should therefore be to define the **actual Java package/module structure and concrete interfaces/classes for this vertical slice**, including how `AgentRuntime`, `TaskPlanner`, `StateMachine`, `ContextEngine`, `ToolOrchestrator`, `MutationEngine`, and `VerificationEngine` connect without creating circular dependencies.

That will turn everything we've designed so far into something you can actually start implementing in the Wayang codebase.




Yes. The next milestone should be **the concrete Aljabr vertical slice**—turning the architecture into packages, interfaces, dependency direction, and one end-to-end implementation path.

# Milestone — Concrete Aljabr Runtime

The goal is to make this executable:

```text
aljabr fix "refresh token expiration is broken"
        │
        ▼
   AgentRuntime
        │
        ▼
    AgentTask
        │
        ▼
    TaskPlanner
        │
        ▼
    StateMachine
        │
        ├── ContextEngine ──► Gemma
        │
        ├── ToolOrchestrator
        │
        ├── MutationEngine
        │
        └── VerificationEngine
                │
                ▼
          Evidence / State
                │
                └────► next step
```

The important part is that **this should be implementable without introducing a giant `AljabrAgent.java` containing everything.**

---

# 1. Start with module boundaries

I would structure the code approximately like this:

```text
wayang/
└── aljabr/
    ├── api/
    ├── domain/
    ├── runtime/
    ├── planning/
    ├── reasoning/
    ├── context/
    ├── tools/
    ├── execution/
    ├── workspace/
    ├── mutation/
    ├── verification/
    ├── evidence/
    ├── memory/
    ├── policy/
    └── persistence/
```

But there is an important rule:

> These are architectural boundaries, not necessarily separate Gradle/Maven modules yet.

Initially they can be packages inside the same module.

Split them physically only when the boundaries prove useful.

---

# 2. Dependency direction

This is more important than the folder names.

```text
                 ┌──────────────┐
                 │     API      │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │   Runtime    │
                 └──────┬───────┘
                        │
       ┌────────────────┼─────────────────┐
       ▼                ▼                 ▼
   Planning         Reasoning          Policy
       │                │                 │
       └────────────────┼─────────────────┘
                        ▼
                    Context
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
            Tools              Mutation
              │                   │
              ▼                   ▼
          Execution          Verification
              │                   │
              └─────────┬─────────┘
                        ▼
                     Evidence
                        │
                        ▼
                   Persistence
```

Avoid:

```text
Tool → AgentRuntime
Mutation → TaskPlanner
GemmaAdapter → StateMachine
```

Those reverse dependencies will eventually create a mess.

---

# 3. `domain` should contain the vocabulary

This is the most dependency-light package.

```text
domain/
├── task/
├── agent/
├── plan/
├── action/
├── evidence/
├── execution/
├── workspace/
└── budget/
```

For example:

```java
public record TaskId(String value) {}
public record StepId(String value) {}
public record ExecutionId(String value) {}
public record EvidenceId(String value) {}
```

Use value objects early.

It prevents accidentally passing:

```java
String taskId
String executionId
String evidenceId
```

around the system.

---

# 4. Agent domain

```java
public enum AgentState {

    CREATED,
    PLANNING,
    READY,

    DISCOVERING,
    INSPECTING,
    REASONING,

    PROPOSING,
    APPROVAL_REQUIRED,

    MUTATING,
    VERIFYING,

    ANALYZING_FAILURE,
    REPAIRING,

    REVIEWING,

    COMPLETED,
    BLOCKED,
    CANCELLED,
    FAILED
}
```

Keep this enum dumb.

Don't put behavior like:

```java
state.repair(...)
```

inside it.

The state machine owns transitions.

---

# 5. Task domain

```java
public record AgentTask(
    TaskId id,
    String goal,
    WorkspaceId workspaceId,
    TaskConstraints constraints,
    TaskBudget budget
) {}
```

Then:

```java
public record TaskConstraints(
    ChangeScope changeScope,
    boolean requireTests,
    boolean allowNetwork,
    boolean allowDependencies,
    boolean allowGeneratedFiles
) {}
```

This becomes immutable task configuration.

---

# 6. Runtime package

The runtime should be thin.

```text
runtime/
├── AgentRuntime.java
├── DefaultAgentRuntime.java
├── AgentSession.java
├── AgentLoop.java
├── AgentContextBuilder.java
└── AgentCheckpointService.java
```

Public API:

```java
public interface AgentRuntime {

    Uni<TaskHandle> start(AgentTask task);

    Uni<AgentSnapshot> inspect(TaskId taskId);

    Uni<Void> cancel(TaskId taskId);

    Uni<TaskHandle> resume(TaskId taskId);
}
```

That's basically it.

Don't expose internal machinery here.

---

# 7. `DefaultAgentRuntime`

It coordinates infrastructure.

Conceptually:

```java
public final class DefaultAgentRuntime
        implements AgentRuntime {

    private final TaskRepository tasks;
    private final TaskPlanner planner;
    private final AgentLoop loop;
    private final CheckpointRepository checkpoints;

    @Override
    public Uni<TaskHandle> start(AgentTask task) {
        return tasks.save(task)
            .chain(saved -> planner.plan(saved))
            .chain(plan -> loop.start(task, plan));
    }
}
```

Keep this class boring.

That's a good thing.

---

# 8. `AgentLoop`

This is where orchestration happens.

```java
public interface AgentLoop {

    Uni<TaskHandle> start(
        AgentTask task,
        TaskPlan plan
    );

    Uni<TaskHandle> resume(
        AgentCheckpoint checkpoint
    );
}
```

Implementation:

```text
DefaultAgentLoop
```

but it should delegate almost everything.

---

# 9. Don't create this

Avoid:

```java
public class AljabrAgent {

    // 4,000 lines
    // planner
    // gemma
    // tools
    // edits
    // tests
    // memory
    // state
}
```

That's the exact architecture we're trying to prevent.

---

# 10. `AgentSession`

The loop needs a runtime state object.

```java
public final class AgentSession {

    private AgentTask task;
    private TaskPlan plan;
    private AgentState state;
    private StepState step;

    private EvidenceSet evidence;
    private BudgetState budget;

    public AgentContext context() {
        ...
    }
}
```

But don't make this the permanent database representation.

It's a runtime object.

---

# 11. `AgentContext`

This is the bridge between subsystems.

```java
public record AgentContext(
    AgentTask task,
    AgentState state,
    Optional<PlanStep> currentStep,
    WorkspaceView workspace,
    EvidenceView evidence,
    MemoryView memory,
    BudgetView budget,
    CapabilityView capabilities
) {}
```

This is intentionally a **view**.

It should not contain:

```text
raw filesystem
raw logs
all memory
all repository files
all execution history
```

---

# 12. `TaskPlanner`

```text
planning/
├── TaskPlanner.java
├── DefaultTaskPlanner.java
├── PlanningContext.java
├── TaskPlan.java
└── PlanStep.java
```

Interface:

```java
public interface TaskPlanner {

    Uni<TaskPlan> plan(
        AgentTask task,
        PlanningContext context
    );
}
```

---

# 13. Planning context should be small

```java
public record PlanningContext(
    TaskSummary task,
    RepositorySummary repository,
    CapabilityView capabilities,
    ConstraintView constraints
) {}
```

Notice:

**No giant repository dump.**

The planner can request discovery when necessary.

---

# 14. Initial plan should be shallow

For:

> Fix refresh token expiration bug.

Generate:

```text
1. DISCOVER authentication implementation
2. INSPECT expiration validation
3. MODIFY validation
4. VERIFY targeted tests
5. REVIEW changes
```

Don't generate:

```text
1.
  1.1
    1.1.1
      ...
```

Planning should be adaptive.

---

# 15. The state machine

Create:

```text
runtime/state/
├── AgentStateMachine.java
├── TransitionPolicy.java
├── StateTransition.java
└── DefaultAgentStateMachine.java
```

Interface:

```java
public interface AgentStateMachine {

    Uni<StateTransition> transition(
        AgentContext context,
        ActionResult result
    );
}
```

---

# 16. Explicit transition table

This is worth making data-driven.

```java
public final class TransitionRules {

    public static StateTransition next(
        AgentState state,
        ActionResult result
    ) {
        ...
    }
}
```

Conceptually:

```text
CREATED
  → PLANNING

PLANNING
  → READY

READY
  → DISCOVERING

DISCOVERING
  → INSPECTING

INSPECTING
  → REASONING

REASONING
  → PROPOSING

PROPOSING
  → MUTATING

MUTATING
  → VERIFYING

VERIFYING
  ├── PASS → REVIEWING
  └── FAIL → ANALYZING_FAILURE

ANALYZING_FAILURE
  ├── recoverable → REPAIRING
  └── unknown     → BLOCKED

REPAIRING
  → VERIFYING

REVIEWING
  → COMPLETED
```

This gives you a very inspectable system.

---

# 17. State transitions should be evidence-driven

Don't do:

```java
if (modelSaysDone) {
    state = COMPLETED;
}
```

Instead:

```java
if (verification.allRequiredChecksPassed()
        && review.approved()
        && constraintsSatisfied()) {

    transition(COMPLETED);
}
```

The model can recommend completion.

It cannot declare completion by itself.

---

# 18. `reasoning`

Keep model abstraction clean:

```text
reasoning/
├── ReasoningEngine.java
├── ModelProvider.java
├── ModelRequest.java
├── ModelResponse.java
├── ActionProposal.java
└── StructuredOutputParser.java
```

---

# 19. `ModelProvider`

```java
public interface ModelProvider {

    Uni<ModelResponse> generate(
        ModelRequest request
    );
}
```

Then your Gemma implementation:

```text
GemmaModelProvider
```

doesn't know anything about:

```text
MutationEngine
VerificationEngine
AgentState
```

It only knows inference.

---

# 20. This is critical for local/cloud portability

You can later have:

```text
GemmaLocalProvider
OllamaProvider
llama.cpp provider
OpenAIProvider
AnthropicProvider
```

while Aljabr still sees:

```java
ModelProvider
```

The runtime doesn't care.

---

# 21. `ReasoningEngine`

Don't let every subsystem call the model independently.

Use:

```java
public interface ReasoningEngine {

    Uni<ActionProposal> decide(
        DecisionContext context
    );
}
```

Then:

```text
ReasoningEngine
    ↓
ContextEngine
    ↓
ModelProvider
    ↓
StructuredOutputParser
```

---

# 22. `DecisionContext`

```java
public record DecisionContext(
    AgentState state,
    String objective,
    EvidenceView evidence,
    List<ActionDefinition> actions,
    ConstraintView constraints
) {}
```

This is the **model's world**.

Much smaller than the runtime's world.

---

# 23. ContextEngine

Now integrate the earlier architecture.

```text
context/
├── ContextEngine.java
├── ContextController.java
├── ContextPolicy.java
├── ContextBuilder.java
├── ContextBudget.java
├── ContextItem.java
└── ContextCompressor.java
```

The flow:

```text
AgentContext
     ↓
ContextController
     ↓
ContextPolicy
     ↓
ContextBuilder
     ↓
ContextCompressor
     ↓
ModelRequest
```

---

# 24. Context policy should depend on model capability

You already established this architecture:

```text
CapabilityDetector
       ↓
CapabilityProfile
       ↓
ContextController
       ↓
ContextPolicy
```

So for Gemma 12B:

```text
small active context
compact tool schemas
limited evidence
structured outputs
minimal repetition
```

For a large-context cloud model:

```text
broader evidence
larger history
more alternatives
```

Same underlying runtime.

---

# 25. Tool package

```text
tools/
├── ToolRegistry.java
├── ToolDefinition.java
├── ToolIntent.java
├── ToolResolver.java
├── ToolOrchestrator.java
├── ToolPolicy.java
└── adapters/
```

---

# 26. Important interface separation

Don't make:

```java
ToolOrchestrator
```

know how to execute every tool.

Instead:

```java
public interface ToolHandler {

    ToolKind kind();

    Uni<ToolResult> execute(
        ToolArguments arguments,
        ToolContext context
    );
}
```

Then:

```text
ReadFileHandler
SearchCodeHandler
RunTestHandler
GitDiffHandler
BuildHandler
```

---

# 27. Registry

```java
public interface ToolRegistry {

    Optional<ToolHandler> handler(
        ToolKind kind
    );
}
```

The orchestrator:

```java
handler = registry.handler(intent.kind());

return handler.execute(
    intent.arguments(),
    context
);
```

Simple.

---

# 28. Mutation package

Keep mutation completely separate.

```text
mutation/
├── MutationEngine.java
├── EditProposal.java
├── EditOperation.java
├── MutationValidator.java
├── MutationApplier.java
└── MutationResult.java
```

---

# 29. Model should propose edits

Example:

```java
public record EditProposal(
    List<EditOperation> operations,
    String rationale
) {}
```

But:

```text
Gemma
 ↓
EditProposal
```

does **not** mean:

```text
EditProposal
 ↓
immediately write files
```

---

# 30. Mutation pipeline

```text
EditProposal
     ↓
MutationValidator
     ↓
DiffGenerator
     ↓
Policy
     ↓
MutationApplier
     ↓
WorkspaceSnapshot
```

---

# 31. Validate before mutation

Check:

```text
file exists
range valid
expected content matches
scope allowed
generated files allowed
no unexpected files
```

This prevents stale model edits.

---

# 32. Use optimistic concurrency

Suppose Gemma inspected:

```text
AuthService.java
```

Then another process modifies it.

The edit proposal should contain:

```java
expectedContentHash
```

Before applying:

```text
currentHash == expectedHash
```

If not:

```text
STALE_EDIT
```

Don't blindly overwrite.

---

# 33. Verification package

```text
verification/
├── VerificationEngine.java
├── VerificationPlan.java
├── TestSelector.java
├── VerificationResult.java
└── VerificationPolicy.java
```

---

# 34. Verification should be hierarchical

First:

```text
changed test
```

Then:

```text
affected tests
```

Then:

```text
module tests
```

Then, if necessary:

```text
full suite
```

So:

```text
cheap → expensive
```

This is especially valuable on local hardware.

---

# 35. Verification policy

```java
public interface VerificationPolicy {

    VerificationPlan choose(
        WorkspaceSnapshot before,
        WorkspaceSnapshot after,
        TaskContext task
    );
}
```

Example:

```text
changed:
TokenValidator.java

affected:
4 tests

→ run 4 tests
```

If all pass:

```text
DONE
```

Maybe no full suite needed depending on policy.

---

# 36. Evidence package

This should be one of the most stable parts of the system.

```text
evidence/
├── Evidence.java
├── EvidenceType.java
├── EvidenceStore.java
├── EvidenceSet.java
├── EvidenceRelation.java
└── EvidenceView.java
```

---

# 37. Evidence types

```java
public enum EvidenceType {

    USER_REQUIREMENT,

    FILE_CONTENT,
    SEARCH_RESULT,
    SYMBOL_INFO,

    TEST_RESULT,
    BUILD_RESULT,

    COMPILER_DIAGNOSTIC,
    EXECUTION_RESULT,

    GIT_DIFF,
    GIT_STATUS,

    MODEL_ASSERTION
}
```

---

# 38. Don't treat model assertions as facts

This is important.

```text
TestResult:
"expected false, actual true"
```

is strong evidence.

But:

```text
Gemma:
"The expiration check is probably inverted."
```

is:

```text
MODEL_ASSERTION
```

It should have lower epistemic status.

That distinction will dramatically improve reliability later.

---

# 39. Hypothesis package

I'd add:

```text
reasoning/hypothesis/
├── Hypothesis.java
├── HypothesisStatus.java
├── HypothesisEngine.java
└── HypothesisEvidence.java
```

Example:

```java
public record Hypothesis(
    HypothesisId id,
    String statement,
    double confidence,
    HypothesisStatus status,
    Set<EvidenceId> supportingEvidence,
    Set<EvidenceId> contradictingEvidence
) {}
```

---

# 40. This gives Aljabr proper debugging

Instead of:

```text
FAIL
 ↓
ask model again
```

we get:

```text
Failure
 ↓
Hypothesis H1
 ↓
test H1
 ↓
Evidence
 ↓
H1 supported / rejected
 ↓
H2
```

That is a much stronger agent loop.

---

# 41. Persistence

Now make the whole thing resumable.

```text
persistence/
├── TaskRepository
├── CheckpointRepository
├── EvidenceRepository
├── ExecutionRepository
└── MemoryRepository
```

For your first implementation, even:

```text
SQLite
```

would be sufficient.

Don't build a distributed event-sourcing system yet.

---

# 42. Store immutable events

I'd nevertheless introduce a simple event model:

```java
public record AgentEvent(
    EventId id,
    TaskId taskId,
    Instant timestamp,
    AgentEventType type,
    JsonNode payload
) {}
```

Events:

```text
TASK_CREATED
PLAN_CREATED
STATE_CHANGED
MODEL_CALLED
ACTION_PROPOSED
TOOL_EXECUTED
FILES_CHANGED
VERIFICATION_COMPLETED
HYPOTHESIS_CREATED
CHECKPOINT_CREATED
APPROVAL_REQUESTED
TASK_COMPLETED
```

---

# 43. Why events are useful

If something goes wrong:

```text
"Why did Aljabr modify this file?"
```

You can reconstruct:

```text
user goal
 ↓
plan
 ↓
evidence
 ↓
model proposal
 ↓
policy decision
 ↓
mutation
 ↓
verification
```

That is enormously useful during development.

---

# 44. But don't over-engineer event sourcing

You don't need:

```text
Kafka
CQRS
distributed event bus
```

for the first Aljabr.

A local SQLite event table is enough.

---

# 45. Concrete vertical slice

Now let's actually walk through what happens.

User:

```text
Fix the refresh token expiration bug.
```

---

## Step 1 — Create task

```text
AgentRuntime
 ↓
AgentTask
 ↓
TASK_CREATED
```

State:

```text
CREATED
```

---

## Step 2 — Capability detection

```text
CapabilityDetector
```

discovers:

```text
model:
Gemma 12B local

workspace:
Git repository

language:
Java

build:
Gradle

tests:
JUnit

network:
disabled
```

---

## Step 3 — Planner

```text
TaskPlanner
```

produces:

```text
DISCOVER
INSPECT
MODIFY
VERIFY
REVIEW
```

---

## Step 4 — Discovery

State:

```text
DISCOVERING
```

Tool:

```text
repository_structure
```

Evidence:

```text
E1:
AuthService.java

E2:
RefreshTokenService.java

E3:
RefreshTokenTest.java
```

---

## Step 5 — Inspection

ContextEngine selects:

```text
RefreshTokenService
RefreshTokenTest
relevant symbols
```

Gemma receives compact context.

It proposes:

```text
inspect RefreshTokenService.validateExpiration
```

---

## Step 6 — Action validation

Wayang checks:

```text
action allowed?
target exists?
budget available?
```

Yes.

---

## Step 7 — Tool execution

```text
InspectSymbolHandler
```

produces:

```text
E4:
expiration check uses issuedAt instead of expiresAt
```

---

## Step 8 — Reasoning

Gemma sees:

```text
goal
E4
relevant code
test
constraints
```

proposes:

```text
change expiration comparison
add boundary test
```

---

# 46. Mutation

MutationEngine validates:

```text
expected file hash
```

creates:

```diff
- issuedAt.plus(...)
+ expiresAt
```

and applies it.

Evidence:

```text
E5:
workspace changed
```

---

# 47. Verification

VerificationPolicy chooses:

```text
RefreshTokenTest
```

not the entire repository.

Run.

Result:

```text
FAIL
```

Evidence:

```text
E6:
boundary test still fails
```

---

# 48. Failure analysis

FailureAnalyzer classifies:

```text
TEST_FAILURE
```

HypothesisEngine:

```text
H1:
boundary condition uses > instead of >=
```

Gemma gets:

```text
failure summary
+
relevant source
+
current diff
+
H1
```

Not the whole previous conversation.

---

# 49. Repair

Gemma proposes:

```text
change > to >=
```

MutationEngine applies.

---

# 50. Verification again

Targeted tests:

```text
PASS
PASS
PASS
```

Now:

```text
VerificationResult:
SUCCESS
```

---

# 51. Review

Review policy checks:

```text
required tests passed
diff within scope
no unexpected files
no dependency changes
no generated files
```

Everything okay.

---

# 52. Completed

```text
REVIEWING
    ↓
COMPLETED
```

Final output to user can be tiny:

```text
Fixed refresh-token expiration boundary handling.

Changed:
- RefreshTokenService
- RefreshTokenTest

Verification:
- 3 targeted tests passed
```

No need to expose 50 internal events.

---

# 53. The resulting package dependency graph

I'd aim for this:

```text
                    api
                     │
                     ▼
                  runtime
                     │
        ┌────────────┼─────────────┐
        ▼            ▼             ▼
     planning    reasoning       policy
        │            │             │
        └──────┬─────┴─────────────┘
               ▼
            context
               │
       ┌───────┼────────┐
       ▼       ▼        ▼
     tools  mutation verification
       │       │        │
       └───────┼────────┘
               ▼
           execution
               │
               ▼
            evidence
               │
               ▼
          persistence
```

`domain` sits underneath these packages as the common vocabulary.

---

# 54. One thing I would change from our earlier architecture

I would **not** make `MemoryEngine` central to every operation.

Instead:

```text
Memory
   ↓
Evidence / Context selection
```

Memory is supporting infrastructure.

The primary source of truth during an active coding task should be:

```text
Task
Evidence
Workspace
Execution
Plan
```

Memory should help the agent remember useful things—not become a second hidden database of facts that competes with current repository state.

That's particularly important for coding agents.

---

# 55. Likewise, don't make `ReasoningEngine` omniscient

It should not have direct access to:

```text
filesystem
shell
git
database
memory
```

It receives:

```text
DecisionContext
```

and produces:

```text
ActionProposal
```

That's all.

---

# 56. The first implementation target

I would now resist adding more abstractions until this path actually works:

```text
AgentRuntime
   ↓
TaskPlanner
   ↓
StateMachine
   ↓
ContextEngine
   ↓
GemmaModelProvider
   ↓
ActionProposal
   ↓
ToolOrchestrator
   ↓
MutationEngine
   ↓
VerificationEngine
   ↓
EvidenceStore
   ↓
StateMachine
```

Implement **one happy path** first:

```text
read → inspect → edit → test → pass
```

Then implement:

```text
test → fail → diagnose → repair → test → pass
```

Only after those work should we add sophisticated recovery, parallelism, multi-agent behavior, or elaborate long-term memory.

---

# 57. Next milestone after this

Once this vertical slice exists, the next architectural problem becomes much more interesting:

## `EvidenceGraph + HypothesisEngine + DebuggingLoop`

That is where Aljabr can move beyond:

> "LLM edits code and runs tests"

toward:

> **"Aljabr systematically investigates a software failure, maintains competing hypotheses, gathers evidence, eliminates wrong explanations, performs the smallest justified change, and verifies it."**

That will be the foundation for making a **12B local model surprisingly capable**, because Wayang can perform much of the deterministic investigation while Gemma focuses its limited reasoning capacity on the genuinely ambiguous parts.




Absolutely. Now we should go into the **EvidenceGraph + HypothesisEngine + DebuggingLoop** milestone.

This is one of the most important parts of Aljabr because it lets a relatively small local model avoid repeatedly "thinking from scratch."

# Milestone — Evidence-Driven Debugging

The core transformation is:

```text
OLD
failure
 ↓
ask Gemma
 ↓
guess
 ↓
edit
 ↓
test
 ↓
guess again
```

into:

```text
failure
   ↓
Evidence
   ↓
Hypotheses
   ↓
choose cheapest discriminating action
   ↓
new Evidence
   ↓
update hypotheses
   ↓
repair only when sufficiently supported
   ↓
verify
```

This is exactly the kind of work where Wayang can compensate for a smaller local model.

---

# 1. The central abstraction: Evidence

We already introduced `Evidence`.

Now make it more powerful.

```java
public record Evidence(
    EvidenceId id,
    EvidenceType type,
    String subject,
    EvidencePayload payload,
    EvidenceStrength strength,
    Instant observedAt,
    SourceRef source
) {}
```

For example:

```text
E1
type: TEST_RESULT
subject: RefreshTokenTest.expiredToken
payload:
  expected = false
  actual = true
strength:
  DIRECT
```

---

# 2. Evidence should have provenance

Every important fact should answer:

> Where did this come from?

```java
public sealed interface SourceRef {

    record User(...) implements SourceRef {}

    record File(...) implements SourceRef {}

    record ToolExecution(...) implements SourceRef {}

    record Test(...) implements SourceRef {}

    record Model(...) implements SourceRef {}
}
```

So Aljabr can distinguish:

```text
test says X
```

from:

```text
Gemma believes X
```

---

# 3. Evidence strength

Keep the first implementation simple.

```java
public enum EvidenceStrength {

    DIRECT,
    DERIVED,
    INFERRED,
    SPECULATIVE
}
```

Examples:

```text
compiler diagnostic
    → DIRECT

test result
    → DIRECT

"method X calls method Y"
    → DERIVED

Gemma hypothesis
    → INFERRED

"maybe dependency Z is broken"
    → SPECULATIVE
```

This gives your reasoning system an epistemic hierarchy.

---

# 4. Evidence should be immutable

Don't mutate:

```text
E5:
test failed
```

into:

```text
E5:
test passed
```

Instead:

```text
E5:
test failed

E8:
test passed
```

That history becomes incredibly useful.

---

# 5. Evidence relations

Now introduce:

```java
public record EvidenceRelation(
    EvidenceId from,
    EvidenceId to,
    RelationType type
) {}
```

```java
public enum RelationType {

    SUPPORTS,
    CONTRADICTS,
    DERIVED_FROM,
    EXPLAINS,
    INVALIDATES,
    SUPERSEDES
}
```

Now you have a graph.

---

# 6. Example evidence graph

```text
                 E1
          test failure
                │
                ▼
              H1
      expiration boundary
                │
        ┌───────┴────────┐
        ▼                ▼
       E2                E3
 source code        boundary test
        │                │
        └───────┬────────┘
                ▼
              H1
             supported
                │
                ▼
             Edit E4
                │
                ▼
             Test E5
                │
                ▼
          H1 contradicted
```

This is much more useful than a transcript.

---

# 7. `EvidenceGraph`

```java
public interface EvidenceGraph {

    Uni<EvidenceId> add(Evidence evidence);

    Uni<Void> relate(
        EvidenceId from,
        EvidenceId to,
        RelationType relation
    );

    Uni<EvidenceView> query(
        EvidenceQuery query
    );
}
```

The implementation can initially be SQLite tables.

Don't start with a graph database.

---

# 8. Evidence query

The ContextEngine should never ask:

```text
"give me all evidence"
```

Instead:

```java
public record EvidenceQuery(
    Set<EvidenceType> types,
    Set<String> subjects,
    Optional<HypothesisId> hypothesis,
    int maxItems
) {}
```

Then:

```text
repairing TokenValidator
```

can request:

```text
relevant failures
relevant code
supporting evidence
contradicting evidence
current diff
```

---

# 9. Hypothesis

Now the second major primitive.

```java
public record Hypothesis(
    HypothesisId id,
    String statement,
    HypothesisStatus status,
    Confidence confidence,
    Set<EvidenceId> supportingEvidence,
    Set<EvidenceId> contradictingEvidence,
    int attempts
) {}
```

Status:

```java
public enum HypothesisStatus {

    PROPOSED,
    TESTING,
    SUPPORTED,
    WEAKENED,
    REJECTED,
    CONFIRMED
}
```

---

# 10. Don't overtrust confidence numbers

You might be tempted to do:

```text
H1 = 0.93
H2 = 0.71
```

and pick H1.

I wouldn't initially.

A model's numerical confidence is not necessarily calibrated.

Instead use:

```text
evidence support
+
contradictions
+
test results
+
cost to investigate
```

and derive a rough priority.

---

# 11. `HypothesisScore`

Internally:

```java
public record HypothesisScore(
    double evidenceSupport,
    double contradictionPenalty,
    double investigationCost,
    double priority
) {}
```

Conceptually:

```text
priority =
    evidenceSupport
    - contradictionPenalty
    - costPenalty
```

The exact formula can evolve.

---

# 12. HypothesisEngine

```java
public interface HypothesisEngine {

    Uni<List<Hypothesis>> generate(
        FailureContext failure
    );

    Uni<HypothesisAssessment> assess(
        Hypothesis hypothesis,
        EvidenceView newEvidence
    );
}
```

Gemma can help generate hypotheses.

Wayang evaluates them.

Again:

```text
Gemma proposes
Wayang evaluates
```

---

# 13. Generate only a few hypotheses

For Gemma 12B:

```text
maximum:
3 hypotheses
```

Not:

```text
"generate 20 possible causes"
```

Example:

```text
H1:
expiration comparison uses wrong timestamp

H2:
boundary condition is incorrect

H3:
timezone conversion changes expiration
```

That's enough.

---

# 14. Why fewer hypotheses helps

Suppose there are 10 hypotheses.

The model now needs to maintain:

```text
H1
H2
H3
...
H10
```

That consumes context and reasoning capacity.

Instead:

```text
top 2–3
```

and actively eliminate them.

This is essentially **reasoning compression**.

---

# 15. Hypothesis testing

A hypothesis should define:

> What observation would distinguish this hypothesis from the others?

For example:

```text
H1:
wrong expiration timestamp

discriminating action:
inspect token creation timestamp
```

Another:

```text
H2:
boundary comparison wrong

discriminating action:
run exact expiration-boundary test
```

---

# 16. `InvestigationAction`

Introduce:

```java
public record InvestigationAction(
    ActionId id,
    HypothesisId hypothesis,
    ActionType type,
    ActionArguments arguments,
    CostEstimate cost,
    InformationGainEstimate informationGain
) {}
```

This connects the hypothesis system directly to ToolOrchestrator.

---

# 17. Information gain

This is one of the strongest concepts for local Aljabr.

Suppose:

```text
Action A
cost = 1
information = high

Action B
cost = 10
information = high
```

Pick A.

More formally:

```text
utility =
expected_information_gain / execution_cost
```

You don't need Bayesian mathematics initially.

A rough heuristic is enough.

---

# 18. Example

Hypotheses:

```text
H1 = wrong timestamp
H2 = wrong boundary
H3 = timezone conversion
```

Available actions:

```text
inspect token creation
cost 1
discriminates H1

run boundary test
cost 1
discriminates H2

run entire integration suite
cost 20
weakly informs all
```

Choose:

```text
run boundary test
```

because it is cheap and highly discriminating.

---

# 19. `InvestigationPlanner`

```java
public interface InvestigationPlanner {

    Uni<List<InvestigationAction>> propose(
        HypothesisSet hypotheses,
        InvestigationContext context
    );
}
```

This can initially be mostly deterministic.

Gemma doesn't have to decide everything.

---

# 20. This is where Wayang compensates for Gemma

Instead of:

```text
Gemma:
"What should I do next?"
```

Wayang can calculate:

```text
available evidence
+
candidate hypotheses
+
known tools
+
tool costs
+
current state
```

Then give Gemma only:

```text
Candidate A
Candidate B
Candidate C
```

Gemma chooses among them.

That's much easier for a 12B model.

---

# 21. DebuggingLoop

Now combine everything.

```java
public interface DebuggingLoop {

    Uni<DebuggingResult> investigate(
        FailureContext failure
    );
}
```

Implementation:

```text
DefaultDebuggingLoop
```

---

# 22. Actual loop

Conceptually:

```java
while (!resolved) {

    evidence = collectRelevantEvidence();

    hypotheses =
        hypothesisEngine.generateOrUpdate(evidence);

    if (hasConfirmedCause(hypotheses)) {
        return cause;
    }

    actions =
        investigationPlanner.propose(hypotheses);

    action =
        selectBestAction(actions);

    result =
        toolOrchestrator.execute(action);

    newEvidence =
        evidenceExtractor.extract(result);

    evidenceGraph.add(newEvidence);

    hypotheses =
        hypothesisEngine.assess(
            hypotheses,
            newEvidence
        );
}
```

But don't literally make this one giant method.

Break it into state transitions.

---

# 23. Better: make investigation a state machine

```text
ANALYZING_FAILURE
        ↓
GENERATING_HYPOTHESES
        ↓
SELECTING_INVESTIGATION
        ↓
INVESTIGATING
        ↓
ASSESSING_EVIDENCE
        │
        ├── cause sufficiently supported
        │        ↓
        │      REPAIRING
        │
        ├── hypotheses remain
        │        ↓
        │      SELECTING_INVESTIGATION
        │
        └── no useful path
                 ↓
              BLOCKED
```

This fits perfectly into the AgentStateMachine from the previous milestone.

---

# 24. `FailureContext`

```java
public record FailureContext(
    ExecutionResult execution,
    EvidenceView evidence,
    WorkspaceView workspace,
    TaskConstraints constraints,
    BudgetView budget
) {}
```

Don't include the entire task transcript.

---

# 25. Failure normalization

Before Gemma sees the failure, normalize it.

Raw:

```text
Gradle output:
...
...
...
```

becomes:

```text
Failure:

Type:
TEST_FAILURE

Test:
RefreshTokenTest.expiredToken

Expected:
false

Actual:
true

Location:
RefreshTokenTest.java:84

Relevant stack:
...
```

This is an enormous context saving.

---

# 26. `FailureAnalyzer`

```java
public interface FailureAnalyzer {

    Uni<FailureAnalysis> analyze(
        ExecutionResult result
    );
}
```

Result:

```java
public record FailureAnalysis(
    FailureType type,
    List<Diagnostic> diagnostics,
    List<SuspectedLocation> locations,
    FailureFingerprint fingerprint
) {}
```

---

# 27. Failure fingerprint

This is very useful.

```java
public record FailureFingerprint(
    FailureType type,
    String normalizedMessage,
    Set<String> files,
    Set<String> symbols,
    Set<String> tests
) {}
```

Suppose Aljabr gets:

```text
same failure
same file
same line
same message
```

three times.

The fingerprint tells LoopGuard:

```text
NO_PROGRESS
```

---

# 28. This enables smarter retry behavior

Instead of:

```text
failure
 ↓
repair
 ↓
same failure
 ↓
repair
 ↓
same failure
 ↓
repair
```

Aljabr can do:

```text
same fingerprint twice
        ↓
change investigation strategy
```

After three:

```text
same fingerprint
        ↓
BLOCKED / ASK USER
```

---

# 29. Hypothesis elimination

This is the key debugging capability.

Suppose:

```text
H1:
wrong timestamp

H2:
wrong boundary
```

Run test.

Result:

```text
boundary test passes
```

Then:

```text
H2 → REJECTED
```

Now Gemma doesn't need to reconsider H2 later.

That's a huge context reduction.

---

# 30. Store rejected hypotheses

This is important.

Don't just delete:

```text
H2
```

Store:

```text
H2:
REJECTED

reason:
E12 contradicted it
```

Then future reasoning gets:

```text
already eliminated:
H2
```

This prevents circular reasoning.

---

# 31. `HypothesisMemory`

For the active task:

```text
active hypotheses
rejected hypotheses
confirmed hypotheses
supporting evidence
contradicting evidence
```

This is **task memory**, not long-term memory.

---

# 32. Hypothesis compression

For Gemma:

Don't send:

```text
H1 entire history...
H2 entire history...
H3 entire history...
```

Send:

```text
H1 wrong timestamp
  support: E4
  contradictions: none

H2 wrong boundary
  rejected by: E8

H3 timezone conversion
  weak support
```

Tiny.

---

# 33. Repair should require evidence threshold

This is another important safety mechanism.

Don't transition:

```text
ANALYZING_FAILURE
 → REPAIRING
```

just because Gemma says:

> "I found the cause."

Require something like:

```text
minimum evidence:
1 direct failure
+
1 relevant source observation
+
cause consistent with both
```

Or allow policy configuration.

---

# 34. `RepairReadiness`

```java
public record RepairReadiness(
    boolean ready,
    List<EvidenceId> requiredEvidence,
    List<String> unresolvedQuestions
) {}
```

Then:

```java
RepairReadinessPolicy
```

decides.

---

# 35. Example

Weak:

```text
Test failed.
Gemma thinks AuthService is wrong.
```

Not enough.

Better:

```text
Test failed at expiration boundary.

Source inspection:
expiration compared against issuedAt.

Test expectation:
expiration should use expiresAt.

Hypothesis:
incorrect timestamp used.

Ready:
YES
```

Now mutation is justified.

---

# 36. Repair scope

The HypothesisEngine should also inform mutation scope.

Example:

```text
cause:
one comparison operator

expected repair:
one-line change
```

MutationPolicy can say:

```text
minimal patch preferred
```

If Gemma proposes:

```text
rewrite AuthService.java
```

Policy can reject it:

```text
SCOPE_EXCEEDED
```

---

# 37. `ChangeRiskAnalyzer`

I would add this now.

```java
public interface ChangeRiskAnalyzer {

    ChangeRisk assess(
        EditProposal proposal,
        TaskContext context
    );
}
```

Risk factors:

```text
number of files
number of lines
public API changes
dependency changes
configuration changes
generated files
security-sensitive files
```

---

# 38. Example risk

```text
1 file
3 lines
private method
no dependency changes
```

→ LOW

Whereas:

```text
12 files
420 lines
pom.xml changed
public API changed
```

→ HIGH

Then:

```text
HIGH
 ↓
approval
```

---

# 39. Review becomes evidence-based too

At the end:

```text
ReviewEngine
```

shouldn't simply ask Gemma:

> "Does this look good?"

Instead:

```text
ReviewChecklist
```

first verifies:

```text
task satisfied?
scope respected?
unexpected files?
tests pass?
API changed?
dependency changed?
formatting okay?
```

Then Gemma gets only the remaining ambiguous questions.

---

# 40. Review pipeline

```text
Workspace Diff
     ↓
Deterministic Review
     ↓
Policy Checks
     ↓
Risk Analysis
     ↓
Optional Gemma Review
     ↓
Final Decision
```

Again:

> deterministic first, model second.

---

# 41. The complete debugging architecture

Now we have:

```text
                 Verification Failure
                         │
                         ▼
                  FailureAnalyzer
                         │
                         ▼
                  FailureFingerprint
                         │
                         ▼
                  HypothesisEngine
                         │
                ┌────────┴────────┐
                ▼                 ▼
               H1                H2
                │                 │
                └────────┬────────┘
                         ▼
                InvestigationPlanner
                         │
                         ▼
                   ToolOrchestrator
                         │
                         ▼
                       Result
                         │
                         ▼
                    Evidence
                         │
                         ▼
                   EvidenceGraph
                         │
                         ▼
                HypothesisAssessment
                    │          │
             rejected│          │supported
                    ▼          ▼
                  next       Repair
                              │
                              ▼
                         MutationEngine
                              │
                              ▼
                         Verification
```

That's a real debugging system.

---

# 42. The local-model optimization becomes even stronger

Your Gemma 12B does **not** have to maintain the whole investigation.

Wayang maintains:

```text
EvidenceGraph
HypothesisSet
FailureFingerprint
ToolHistory
RejectedHypotheses
WorkspaceState
```

Gemma receives only:

```text
current failure
+
top hypotheses
+
relevant evidence
+
candidate investigation actions
```

This is exactly the architecture I would want for your M4 16GB use case.

---

# 43. Example actual prompt to Gemma

Instead of something huge like:

```text
You are an autonomous coding agent...
Here is the repository...
Here is the conversation...
Here are 20 tool definitions...
Here are all previous logs...
Think carefully...
```

Aljabr can construct something closer to:

```text
TASK
Fix refresh-token expiration bug.

STATE
ANALYZING_FAILURE

FAILURE
RefreshTokenTest.expiredToken
Expected false
Actual true
RefreshTokenTest.java:84

HYPOTHESES

H1
Expiration uses issuedAt instead of expiresAt.
Support: source inspection E12.

H2
Boundary comparison is incorrect.
Status: weak.

AVAILABLE INVESTIGATIONS

1. inspect_symbol(Token.create)
2. inspect_symbol(RefreshToken.expiresAt)
3. run_test(RefreshTokenTest.boundary)

CONSTRAINTS

- minimal changes
- no dependency changes
- tests required

Choose one investigation.
```

That's a very manageable prompt for a 12B model.

---

# 44. And the response can be tiny

```json
{
  "action": "run_test",
  "test": "RefreshTokenTest.boundary",
  "reason": "distinguishes H1 from H2"
}
```

That's ideal.

No giant generated reasoning.

---

# 45. One subtle but important improvement

I would introduce:

```text
ReasoningBudget
```

separate from:

```text
ToolBudget
```

For example:

```java
public record ReasoningBudget(
    int maxHypotheses,
    int maxModelCalls,
    int maxTokensPerCall,
    int maxRepairIterations
) {}
```

Why?

Because the agent might have:

```text
50 tool calls
```

available but only:

```text
10 model calls
```

That's perfectly reasonable for a local model.

Wayang can perform deterministic work cheaply and reserve Gemma calls for ambiguous decisions.

---

# 46. The ideal local Aljabr strategy

For your environment:

```text
                 User
                   │
                   ▼
              Gemma 12B
                   │
          small decision
                   │
                   ▼
                Wayang
                   │
       ┌───────────┼───────────┐
       ▼           ▼           ▼
   filesystem    compiler     tests
       │           │           │
       └───────────┼───────────┘
                   ▼
                Evidence
                   │
                   ▼
              Gemma 12B
```

So the model acts like a **high-value reasoning accelerator**, not the entire operating system.

That is the right architecture for a local coding agent.

---

# 47. Next implementation target

At this point, I would stop adding conceptual subsystems and build the first concrete debugging vertical slice:

```text
Test failure
   ↓
FailureAnalyzer
   ↓
FailureFingerprint
   ↓
HypothesisEngine
   ↓
2–3 hypotheses
   ↓
InvestigationPlanner
   ↓
ToolOrchestrator
   ↓
EvidenceGraph
   ↓
HypothesisAssessment
   ↓
RepairReadiness
   ↓
MutationEngine
   ↓
VerificationEngine
```

And the **next milestone after that** should be something very practical:

# `Aljabr Workspace Intelligence`

Specifically:

```text
RepositoryIndexer
SymbolIndex
DependencyGraph
CallGraph
TestIndex
ChangeImpactAnalyzer
```

because that is where Aljabr can dramatically reduce the amount of code it needs to give Gemma.

Instead of asking Gemma:

> "Which files might be relevant?"

Wayang can deterministically say:

```text
Changed symbol:
TokenValidator.validate()

Likely affected:
RefreshTokenService.java
RefreshTokenTest.java

Callers:
RefreshTokenService.refresh()

Tests:
RefreshTokenTest.expiredToken
RefreshTokenTest.boundary
```

Then Gemma reasons over **5 relevant artifacts instead of 5,000 repository files**.

That is probably the next highest-value investment for making **Aljabr + Gemma 12B on 16GB actually feel much stronger than the raw model would suggest**.


Yes. **Next milestone: Aljabr Workspace Intelligence.**

This is where the architecture starts becoming genuinely powerful for a 12B local model. The principle is:

> **Wayang should understand the repository structurally before asking Gemma to reason about it.**

The model should receive *relevant slices*, not raw repository contents.

---

# Milestone — Workspace Intelligence

The pipeline becomes:

```text
Repository
    │
    ├── File Index
    ├── Symbol Index
    ├── Import/Dependency Graph
    ├── Call Graph
    ├── Test Index
    └── Change Impact Graph
              │
              ▼
       Workspace Intelligence
              │
              ▼
       Relevant Context
              │
              ▼
           Gemma
```

The key distinction:

```text
Wayang = repository navigation + deterministic analysis
Gemma  = interpretation + ambiguous reasoning
```

---

# 1. New package

I'd introduce:

```text
workspace/
├── Workspace.java
├── WorkspaceSnapshot.java
│
├── index/
│   ├── RepositoryIndexer.java
│   ├── FileIndex.java
│   ├── SymbolIndex.java
│   ├── TestIndex.java
│   └── DependencyIndex.java
│
├── graph/
│   ├── DependencyGraph.java
│   ├── CallGraph.java
│   ├── TestGraph.java
│   └── ChangeImpactGraph.java
│
├── analysis/
│   ├── SymbolResolver.java
│   ├── ReferenceAnalyzer.java
│   ├── ImpactAnalyzer.java
│   └── RelevanceAnalyzer.java
│
└── query/
    ├── WorkspaceQuery.java
    ├── WorkspaceQueryEngine.java
    └── WorkspaceResult.java
```

Don't implement all of these at once.

The first useful slice is:

```text
FileIndex
SymbolIndex
DependencyIndex
TestIndex
ImpactAnalyzer
```

---

# 2. Repository snapshot

Everything starts with a consistent snapshot.

```java
public record WorkspaceSnapshot(
    WorkspaceId workspaceId,
    String revision,
    Instant createdAt
) {}
```

`revision` could initially be:

```text
git HEAD
```

or a calculated workspace hash.

This matters because your analysis must correspond to a specific repository state.

---

# 3. Why snapshot matters

Imagine:

```text
09:00
Aljabr indexes repository

09:02
user edits AuthService.java

09:03
Aljabr applies an edit based on old index
```

That's dangerous.

Every index should therefore know:

```text
index revision = abc123
workspace revision = abc123
```

Before using the index:

```java
if (!index.matches(snapshot)) {
    refresh();
}
```

---

# 4. FileIndex

Start simple.

```java
public interface FileIndex {

    Optional<FileInfo> find(String path);

    List<FileInfo> all();

    List<FileInfo> byExtension(String extension);

    boolean exists(String path);
}
```

`FileInfo`:

```java
public record FileInfo(
    String path,
    long size,
    String language,
    String contentHash
) {}
```

Don't store entire file contents in the index.

Store metadata and hashes.

---

# 5. Why hashes matter

Suppose:

```text
AuthService.java
hash = A
```

Gemma reads it.

Later:

```text
hash = B
```

Aljabr knows:

```text
previous context is stale
```

without rereading everything.

This integrates directly with the earlier optimistic mutation system.

---

# 6. SymbolIndex

Now the interesting part.

```java
public interface SymbolIndex {

    Optional<Symbol> find(SymbolId id);

    List<Symbol> findByName(String name);

    List<Symbol> symbolsIn(String file);

    List<Reference> referencesTo(SymbolId symbol);

    List<Reference> referencesFrom(SymbolId symbol);
}
```

A symbol:

```java
public record Symbol(
    SymbolId id,
    SymbolKind kind,
    String name,
    String file,
    SourceRange range,
    Optional<SymbolId> parent
) {}
```

---

# 7. Symbol kinds

```java
public enum SymbolKind {

    PACKAGE,
    MODULE,

    CLASS,
    INTERFACE,
    ENUM,
    RECORD,

    METHOD,
    FUNCTION,
    CONSTRUCTOR,

    FIELD,
    VARIABLE,

    TEST
}
```

This should be language-neutral.

---

# 8. Don't parse everything yourself

This is an important pragmatic decision.

Don't build:

```text
AljabrJavaParser
AljabrPythonParser
AljabrRustParser
...
```

unless necessary.

Use existing language parsing infrastructure where possible.

For Java, for example, an AST parser can provide:

```text
classes
methods
imports
references
annotations
```

The Wayang layer should normalize these into its own model.

---

# 9. Normalized representation

Different languages:

```text
Java
TypeScript
Python
Rust
Go
```

all become:

```text
Symbol
Reference
Dependency
Test
SourceRange
```

Then the rest of Aljabr doesn't care which parser produced them.

---

# 10. DependencyIndex

```java
public interface DependencyIndex {

    List<Dependency> dependenciesOf(String file);

    List<Dependency> dependentsOf(String file);

    List<Dependency> dependenciesOf(SymbolId symbol);

    List<Dependency> dependentsOf(SymbolId symbol);
}
```

Example:

```text
RefreshTokenService
    ↓
TokenValidator
    ↓
Token
```

Reverse:

```text
Token
  ↑
TokenValidator
  ↑
RefreshTokenService
```

Both directions are important.

---

# 11. Dependency graph

Represent it explicitly:

```text
A ──depends_on──► B
```

But also query:

```text
dependents(B)
```

which gives:

```text
A
```

This becomes the foundation for impact analysis.

---

# 12. TestIndex

This deserves its own index.

```java
public interface TestIndex {

    List<TestCase> testsFor(SymbolId symbol);

    List<TestCase> testsForFile(String file);

    List<TestCase> testsRelatedTo(String file);

    Optional<TestCase> find(String testId);
}
```

Example:

```text
TokenValidator.validate()
       │
       ├── TokenValidatorTest.valid
       ├── TokenValidatorTest.expired
       └── TokenValidatorTest.boundary
```

---

# 13. Test metadata

```java
public record TestCase(
    TestId id,
    String name,
    String file,
    Optional<SymbolId> target,
    Set<SymbolId> referencedSymbols
) {}
```

This allows Aljabr to choose tests intelligently.

---

# 14. ChangeImpactAnalyzer

Now combine everything.

```java
public interface ChangeImpactAnalyzer {

    ImpactReport analyze(
        ChangeSet changes,
        WorkspaceSnapshot snapshot
    );
}
```

Result:

```java
public record ImpactReport(
    Set<String> directlyChangedFiles,
    Set<SymbolId> changedSymbols,
    Set<SymbolId> affectedSymbols,
    Set<TestId> affectedTests,
    RiskLevel risk
) {}
```

---

# 15. Example

Suppose Gemma modifies:

```text
TokenValidator.java
```

Wayang determines:

```text
changed:
TokenValidator.validate()

callers:
RefreshTokenService.refresh()

tests:
RefreshTokenTest.expiredToken
RefreshTokenTest.boundary
LoginFlowTest.refresh
```

Now verification can run:

```text
3 tests
```

instead of:

```text
2,000 tests
```

---

# 16. Impact depth

Don't blindly traverse the entire graph.

Have configurable depth:

```java
public record ImpactPolicy(
    int callDepth,
    int dependencyDepth,
    boolean includeTests,
    boolean includeInterfaces
) {}
```

For local execution:

```text
callDepth = 2
dependencyDepth = 1
```

might be enough initially.

---

# 17. Relevance scoring

Now the really useful component:

```text
RelevanceAnalyzer
```

It answers:

> Which artifacts are most relevant to the current task?

```java
public interface RelevanceAnalyzer {

    List<RelevantArtifact> rank(
        WorkspaceQuery query
    );
}
```

---

# 18. Don't use only semantic search

A coding agent should combine several signals.

For example:

```text
relevance =
    lexical similarity
  + symbol relationship
  + dependency relationship
  + test relationship
  + recent change relationship
  + task relevance
```

Not just:

```text
embedding similarity
```

---

# 19. Example relevance

User:

> Fix refresh token expiration.

Possible files:

```text
RefreshTokenService.java
    score 0.98

RefreshTokenTest.java
    score 0.96

Token.java
    score 0.82

AuthController.java
    score 0.51

README.md
    score 0.03
```

Gemma gets the top relevant artifacts.

---

# 20. `WorkspaceQuery`

Make this structured.

```java
public record WorkspaceQuery(
    Optional<String> text,
    Set<SymbolId> symbols,
    Set<String> files,
    Set<TestId> tests,
    QueryPurpose purpose,
    int maxResults
) {}
```

Purpose:

```java
public enum QueryPurpose {

    DISCOVERY,
    DEBUGGING,
    IMPACT_ANALYSIS,
    TEST_SELECTION,
    REFACTORING,
    CODE_REVIEW
}
```

---

# 21. Query engine

```java
public interface WorkspaceQueryEngine {

    Uni<WorkspaceResult> query(
        WorkspaceQuery query
    );
}
```

Implementation combines:

```text
FileIndex
SymbolIndex
DependencyIndex
TestIndex
RelevanceAnalyzer
```

---

# 22. WorkspaceResult

Don't return giant files.

Return:

```java
public record WorkspaceResult(
    List<RelevantArtifact> artifacts,
    List<Relationship> relationships,
    QueryDiagnostics diagnostics
) {}
```

---

# 23. `RelevantArtifact`

```java
public record RelevantArtifact(
    ArtifactId id,
    ArtifactType type,
    String path,
    Optional<SymbolId> symbol,
    SourceRange range,
    double relevance
) {}
```

Then ContextEngine decides whether to actually read the source.

---

# 24. This creates a two-stage retrieval architecture

Very important:

```text
Stage 1
STRUCTURAL RETRIEVAL

repository
 ↓
indexes
 ↓
relevant files/symbols

Stage 2
CONTENT RETRIEVAL

relevant symbols
 ↓
source slices
 ↓
context compression
 ↓
Gemma
```

This is much cheaper than:

```text
repository
 ↓
embed everything
 ↓
retrieve huge chunks
 ↓
Gemma
```

---

# 25. Source slicing

Don't give Gemma:

```text
AuthService.java
1,200 lines
```

if it needs:

```text
validateExpiration()
```

Give:

```text
class TokenValidator {

    ...

    boolean validateExpiration(Token token) {
        ...
    }

    ...
}
```

plus perhaps:

```text
caller:
RefreshTokenService.refresh()
```

and relevant tests.

---

# 26. `SourceSlice`

```java
public record SourceSlice(
    String file,
    SourceRange range,
    String content,
    SliceReason reason
) {}
```

Reason:

```java
public enum SliceReason {

    TARGET_SYMBOL,
    CALLER,
    CALLEE,
    TEST,
    IMPORT,
    ERROR_LOCATION,
    CHANGE_CONTEXT
}
```

This is excellent metadata for ContextEngine.

---

# 27. ContextEngine now becomes much stronger

Earlier:

```text
ContextEngine
 ↓
choose useful context
```

Now:

```text
ContextEngine
       │
       ▼
WorkspaceQueryEngine
       │
       ▼
RelevantArtifacts
       │
       ▼
SourceSlicer
       │
       ▼
ContextPolicy
       │
       ▼
ContextBuilder
       │
       ▼
Gemma
```

---

# 28. The model gets structural breadcrumbs

Instead of just:

```text
source code
```

give:

```text
TARGET
TokenValidator.validateExpiration()

CALLERS
RefreshTokenService.refresh()

TESTS
RefreshTokenTest.expiredToken
RefreshTokenTest.boundary

DEPENDENCIES
Token
Clock

ERROR
RefreshTokenTest.boundary:84
```

Then source.

This makes the model's reasoning easier.

---

# 29. This is especially valuable for Gemma 12B

Because the model doesn't have to infer repository topology.

Wayang has already answered:

```text
what is related?
what changed?
what calls this?
what tests it?
what depends on it?
```

Gemma only needs to answer:

> What does this relationship imply for the bug?

That's a much smaller reasoning problem.

---

# 30. Incremental indexing

Do **not** re-index the repository after every change.

Maintain:

```text
WorkspaceIndex
```

with:

```text
revision
file hashes
symbol hashes
dependency hashes
```

After mutation:

```text
changed files
      ↓
incremental re-index
      ↓
update affected symbols
      ↓
update affected graph edges
```

---

# 31. `WorkspaceIndexer`

```java
public interface RepositoryIndexer {

    Uni<WorkspaceIndex> build(
        Workspace workspace
    );

    Uni<WorkspaceIndex> update(
        WorkspaceIndex current,
        ChangeSet changes
    );
}
```

This is important for local performance.

---

# 32. Background indexing

Eventually:

```text
Aljabr starts
     │
     ├── load existing index
     │
     ├── detect changes
     │
     └── incrementally update
```

The agent doesn't need to wait for a complete re-index every time.

---

# 33. Cache aggressively

Your earlier cache architecture becomes useful here.

Cache:

```text
file hash → parsed AST
file hash → symbols
file hash → imports
symbol → references
symbol → tests
query → ranked result
```

If:

```text
AuthService.java
hash unchanged
```

don't parse it again.

---

# 34. But cache invalidation must be explicit

The cache key should include relevant versions:

```text
language parser version
index schema version
file hash
workspace revision
```

For example:

```text
SHA256(
    fileHash
    + parserVersion
    + indexSchemaVersion
)
```

Otherwise parser upgrades can produce stale indexes.

---

# 35. Repository topology summary

I would create:

```java
public record RepositorySummary(
    int fileCount,
    int symbolCount,
    int testCount,
    Set<String> languages,
    Set<String> buildSystems,
    Set<String> frameworks,
    WorkspaceSnapshot snapshot
) {}
```

This is what Planner gets initially.

Not the whole repository.

---

# 36. Planner can now make better plans

Before:

```text
"Fix authentication bug"

Gemma guesses where to look.
```

Now:

```text
TaskPlanner
 ↓
RepositorySummary
 ↓
WorkspaceQuery
 ↓
top relevant symbols
 ↓
plan
```

For example:

```text
DISCOVER:
RefreshTokenService
TokenValidator
RefreshTokenTest

INSPECT:
expiration path

MODIFY:
TokenValidator

VERIFY:
3 affected tests
```

Much more deterministic.

---

# 37. Add `WorkspaceFacts`

One useful optimization:

```java
public record WorkspaceFacts(
    String buildSystem,
    String language,
    String testFramework,
    String packageRoot,
    String sourceRoot,
    String testRoot
) {}
```

Cache this.

Don't ask Gemma:

> "Is this a Gradle Java project?"

Wayang should know.

---

# 38. Add `ProjectDetector`

```java
public interface ProjectDetector {

    Uni<ProjectProfile> detect(
        Workspace workspace
    );
}
```

It can detect:

```text
Gradle
Maven
npm
pnpm
Cargo
Go
Python
```

and:

```text
JUnit
pytest
Jest
Vitest
Go test
```

Again, deterministic infrastructure.

---

# 39. Tool selection gets smarter

Instead of Gemma deciding:

```text
"maybe search files"
```

Wayang can expose appropriate tools based on structure.

For Java:

```text
find_symbol
find_references
find_implementations
find_tests
run_test
```

For Python:

```text
find_definition
find_references
pytest_target
```

Tool availability becomes capability-aware.

---

# 40. `ToolCapabilityResolver`

```java
public interface ToolCapabilityResolver {

    List<ToolDefinition> available(
        WorkspaceFacts facts,
        AgentState state
    );
}
```

So Gemma sees only:

```text
find_symbol
find_references
run_targeted_test
inspect_source
```

instead of 40 unrelated tools.

This is another major **input-token optimization**.

---

# 41. Important: tool schemas should be tiny

Instead of:

```json
{
  "name": "find_references",
  "description": "This tool is capable of..."
  "... 700 words ..."
}
```

use:

```json
{
  "name": "find_references",
  "description": "Find code references to a symbol.",
  "input": {
    "symbol": "string"
  }
}
```

For Gemma 12B, tool-description tokens matter.

---

# 42. Progressive tool disclosure

Don't expose every tool at once.

Start:

```text
discover
inspect
search
```

After discovery:

```text
edit
test
diff
```

During verification:

```text
test
build
diagnostics
```

This is **dynamic tool context**.

Very useful for small models.

---

# 43. Now connect everything

The complete local inference loop becomes:

```text
                 USER
                   │
                   ▼
              AgentRuntime
                   │
                   ▼
               TaskPlanner
                   │
                   ▼
          Workspace Intelligence
                   │
        ┌──────────┼───────────┐
        ▼          ▼           ▼
     Symbols    Tests      Dependencies
        │          │           │
        └──────────┼───────────┘
                   ▼
             Relevance Engine
                   │
                   ▼
              ContextEngine
                   │
                   ▼
                 Gemma
                   │
                   ▼
              ActionProposal
                   │
                   ▼
            ToolOrchestrator
                   │
                   ▼
                Evidence
                   │
                   ▼
            HypothesisEngine
                   │
                   ▼
             MutationEngine
                   │
                   ▼
           ChangeImpactAnalyzer
                   │
                   ▼
          VerificationEngine
                   │
                   ▼
                Review
```

---

# 44. The big architectural principle emerging

At this point Aljabr should have three distinct intelligence layers:

### Layer 1 — Deterministic intelligence

```text
filesystem
git
AST
symbols
dependencies
tests
compiler
build system
diff
hashes
```

### Layer 2 — Search / statistical intelligence

```text
relevance ranking
semantic retrieval
similarity
history retrieval
```

### Layer 3 — Generative reasoning

```text
Gemma
```

And the priority should be:

```text
Deterministic
    ↓
Search
    ↓
Generative
```

not:

```text
Gemma
 ↓
Gemma
 ↓
Gemma
 ↓
Gemma
```

---

# 45. This also gives you a very important cost hierarchy

For your M4 / 16 GB local environment:

```text
CHEAP
│
├── hash file
├── read metadata
├── AST lookup
├── symbol lookup
├── graph traversal
├── indexed search
├── targeted test
│
├── semantic retrieval
│
├── Gemma short decision
│
├── Gemma code reasoning
│
└── large Gemma context
EXPENSIVE
```

Aljabr should climb this ladder only when necessary.

---

# 46. `ReasoningEscalationPolicy`

I would introduce this now:

```java
public interface ReasoningEscalationPolicy {

    ReasoningLevel choose(
        DecisionContext context
    );
}
```

Levels:

```java
public enum ReasoningLevel {

    DETERMINISTIC,
    RETRIEVAL,
    SHORT_REASONING,
    DEEP_REASONING
}
```

For example:

```text
Can we answer from index?
    ↓ yes
DETERMINISTIC

No?
    ↓
Can retrieval resolve it?
    ↓ yes
RETRIEVAL

No?
    ↓
Gemma short decision

Still ambiguous?
    ↓
Gemma deeper reasoning
```

---

# 47. This becomes the heart of "Aljabr optimization"

The optimization isn't:

> "Always minimize context."

It's:

> **"Spend the minimum amount of inference required to resolve the uncertainty."**

That's much better.

Because sometimes a bigger context genuinely improves quality.

Aljabr should know when to spend it.

---

# 48. And cloud models can exploit the same architecture

Nothing here limits cloud models.

For a large model:

```text
Workspace Intelligence
       ↓
larger context
       ↓
more evidence
       ↓
more hypotheses
       ↓
deeper reasoning
```

For Gemma 12B:

```text
Workspace Intelligence
       ↓
tiny relevant context
       ↓
2 hypotheses
       ↓
short decision
```

Same runtime.

Different `CapabilityProfile`.

---

# 49. The next architectural milestone after Workspace Intelligence

Once this is implemented, I would move to:

# **Execution Intelligence**

Specifically:

```text
CommandPlanner
ProcessSandbox
ResourceBudget
TimeoutController
OutputNormalizer
FailureClassifier
IncrementalTestRunner
BuildCache
ProcessRecovery
```

Because then Aljabr doesn't merely understand code.

It becomes very good at **actually operating the development environment**.

And that is where the next major local-model optimization appears:

> **Don't spend Gemma tokens interpreting compiler output, test logs, stack traces, git diffs, or shell output that Wayang can normalize deterministically.**

Gemma should receive:

```text
TEST FAILED

test: RefreshTokenTest.boundary

failure:
expected false
actual true

location:
RefreshTokenTest.java:84

relevant source:
...

likely affected symbols:
...

candidate hypotheses:
H1
H2
```

—not 8,000 lines of Gradle output.

That will be the next major piece that makes **Aljabr feel substantially smarter than a raw Gemma 12B coding model.**
