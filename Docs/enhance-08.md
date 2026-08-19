Absolutely. The next milestone should be **Execution Intelligence**.

We now have:

```text
Milestone 1
CapabilityDetector
ContextController
ContextPolicy

Milestone 2
ContextEngine

Milestone 3
EvidenceGraph
HypothesisEngine
DebuggingLoop

Milestone 4
Workspace Intelligence
```

Now we make Aljabr excellent at the other half of coding-agent work:

> **running the repository safely, cheaply, incrementally, and understanding execution results without wasting Gemma tokens.**

---

# Milestone — Execution Intelligence

The architecture becomes:

```text
                 Aljabr
                   │
                   ▼
            ExecutionPlanner
                   │
          ┌────────┼─────────┐
          ▼        ▼         ▼
       Build      Test      Lint
          │        │         │
          └────────┼─────────┘
                   ▼
             ProcessRunner
                   │
                   ▼
            OutputNormalizer
                   │
                   ▼
             FailureClassifier
                   │
                   ▼
                Evidence
                   │
                   ▼
           EvidenceGraph
```

The important idea is:

**Raw process output should almost never go directly into Gemma.**

---

# 1. `ExecutionPlanner`

Don't let the model directly decide arbitrary shell commands whenever possible.

```java
public interface ExecutionPlanner {

    Uni<ExecutionPlan> plan(
        ExecutionRequest request,
        WorkspaceSnapshot workspace
    );
}
```

```java
public record ExecutionPlan(
    List<ExecutionStep> steps,
    ExecutionBudget budget,
    ExecutionPurpose purpose
) {}
```

For example:

```text
Task:
Fix TokenValidator

Execution plan:

1. compile affected module
2. run TokenValidatorTest
3. run RefreshTokenTest
4. if successful → run affected integration tests
```

Not:

```text
./gradlew test
```

every time.

---

# 2. `ExecutionPurpose`

```java
public enum ExecutionPurpose {

    DISCOVERY,
    COMPILE_CHECK,
    TARGETED_TEST,
    REGRESSION_TEST,
    FULL_TEST,
    LINT,
    FORMAT,
    BUILD,
    PACKAGE
}
```

This matters because the runtime can choose different budgets.

---

# 3. Execution cost

Introduce:

```java
public record ExecutionCost(
    Duration expectedDuration,
    long expectedOutputBytes,
    ResourceRequirements resources
) {}
```

Now Aljabr can reason:

```text
targeted test
    cost = low

module test
    cost = medium

full test suite
    cost = high
```

---

# 4. `ExecutionBudget`

```java
public record ExecutionBudget(
    Duration timeout,
    long maxOutputBytes,
    long maxMemoryBytes,
    int maxProcesses
) {}
```

This should be enforced outside Gemma.

The model must never be able to accidentally create an unbounded process.

---

# 5. ProcessRunner

The low-level primitive:

```java
public interface ProcessRunner {

    Uni<ProcessResult> execute(
        ProcessSpec process
    );
}
```

```java
public record ProcessSpec(
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    ExecutionBudget budget
) {}
```

---

# 6. Never use a single giant shell string

Avoid:

```java
execute("cd project && ./gradlew test && ...");
```

Prefer:

```java
List<String> command = List.of(
    "./gradlew",
    "test",
    "--tests",
    "RefreshTokenTest"
);
```

This gives you better control over:

```text
arguments
timeouts
environment
logging
cancellation
security
```

---

# 7. `ProcessResult`

```java
public record ProcessResult(
    int exitCode,
    Duration duration,
    ProcessStatus status,
    String stdout,
    String stderr
) {}
```

But don't let this object become the context object.

It's an execution artifact.

---

# 8. Process statuses

```java
public enum ProcessStatus {

    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED,
    START_FAILED,
    RESOURCE_LIMIT,
    UNKNOWN
}
```

This lets Wayang reason without parsing text.

For example:

```text
exitCode = 1
status = FAILED
```

already tells the runtime something.

---

# 9. OutputNormalizer

This is one of the highest-value components.

```java
public interface OutputNormalizer {

    NormalizedOutput normalize(
        ProcessResult result,
        ProjectProfile project
    );
}
```

Suppose Gradle produces:

```text
> Task :test FAILED

RefreshTokenTest > expiredToken FAILED
    java.lang.AssertionError:
    expected: <false>
    but was: <true>

... hundreds of lines ...
```

Normalize to:

```text
TEST_FAILURE

test:
RefreshTokenTest.expiredToken

expected:
false

actual:
true

location:
RefreshTokenTest.java:84
```

---

# 10. Normalization should be deterministic first

For common formats, don't ask Gemma.

Build parsers:

```text
JUnitParser
GradleParser
MavenParser
JestParser
PytestParser
CargoTestParser
GoTestParser
CompilerDiagnosticParser
```

Eventually:

```java
public interface ExecutionOutputParser {
    boolean supports(ProjectProfile project, ProcessResult result);
    List<Diagnostic> parse(ProcessResult result);
}
```

---

# 11. Parser chain

```java
public interface DiagnosticParserChain {

    List<Diagnostic> parse(
        ProcessResult result,
        ProjectProfile project
    );
}
```

Implementation:

```text
JUnitParser
     ↓
GradleParser
     ↓
CompilerParser
     ↓
GenericParser
```

The generic parser is the fallback.

---

# 12. `Diagnostic`

```java
public record Diagnostic(
    DiagnosticType type,
    Severity severity,
    String message,
    Optional<String> file,
    Optional<SourceRange> location,
    Optional<String> symbol
) {}
```

Example:

```text
ERROR
RefreshTokenTest.java:84
RefreshTokenTest.expiredToken
expected false, actual true
```

---

# 13. FailureClassifier

Now turn diagnostics into useful failure categories.

```java
public interface FailureClassifier {

    FailureAnalysis classify(
        NormalizedOutput output,
        WorkspaceSnapshot snapshot
    );
}
```

Categories:

```java
public enum FailureType {

    COMPILATION,
    TEST_ASSERTION,
    TEST_ERROR,
    TIMEOUT,
    DEPENDENCY,
    CONFIGURATION,
    ENVIRONMENT,
    RESOURCE,
    NETWORK,
    TOOLING,
    UNKNOWN
}
```

This becomes Evidence.

---

# 14. Why classification matters

Suppose:

```text
Gradle failed.
```

Raw information is weak.

But:

```text
FAILURE_TYPE:
COMPILATION

FILE:
TokenValidator.java

LINE:
91

ERROR:
cannot find symbol
```

is immediately actionable.

Gemma doesn't need to inspect Gradle's internal output.

---

# 15. Failure fingerprints

We already introduced this.

Now make Execution Intelligence produce them automatically.

```java
public interface FailureFingerprintBuilder {

    FailureFingerprint build(
        FailureAnalysis failure
    );
}
```

Example:

```text
COMPILATION
TokenValidator.java
cannot_find_symbol
Clock
```

If the same fingerprint appears again:

```text
NO_PROGRESS
```

---

# 16. Output compression

This is critical for your 12B model.

Imagine:

```text
stdout = 1.7 MB
stderr = 600 KB
```

Never send that directly to Gemma.

Instead:

```text
Execution:
FAILED

Type:
TEST_ASSERTION

Tests:
3

Failures:
1

Primary failure:
RefreshTokenTest.expiredToken

Location:
RefreshTokenTest.java:84

Expected:
false

Actual:
true
```

Potentially:

```text
Relevant stack:
first 5 meaningful frames
```

That's enough.

---

# 17. `OutputSummary`

```java
public record OutputSummary(
    ProcessStatus status,
    FailureType failureType,
    List<Diagnostic> diagnostics,
    List<String> relevantLines,
    FailureFingerprint fingerprint
) {}
```

Then ContextEngine consumes `OutputSummary`, not `ProcessResult`.

This is an important boundary.

---

# 18. Full output still exists

Don't throw away raw logs.

Store:

```text
ExecutionArtifact
```

in your artifact/cache system.

Gemma gets:

```text
summary
```

and can request:

```text
show raw output around line X
```

if necessary.

This gives you **progressive disclosure**.

---

# 19. Progressive execution context

First:

```text
TEST FAILED
RefreshTokenTest.expiredToken
expected false
actual true
```

If Gemma needs more:

```text
stack trace
```

If still unclear:

```text
raw compiler section
```

So:

```text
summary
 ↓
targeted detail
 ↓
raw output
```

rather than:

```text
raw output
 ↓
huge context
```

---

# 20. `ExecutionArtifactStore`

```java
public interface ExecutionArtifactStore {

    Uni<ArtifactId> store(
        ProcessResult result
    );

    Uni<String> read(
        ArtifactId artifact,
        ArtifactRange range
    );
}
```

This also integrates beautifully with your earlier cache architecture.

---

# 21. Incremental testing

Now use Workspace Intelligence.

Suppose:

```text
changed:
TokenValidator.java
```

Impact graph says:

```text
affected:
TokenValidatorTest
RefreshTokenTest
LoginFlowTest
```

ExecutionPlanner chooses:

```text
1. TokenValidatorTest
2. RefreshTokenTest
3. LoginFlowTest
```

instead of:

```text
all 2,000 tests
```

---

# 22. Test selection policy

```java
public interface TestSelectionPolicy {

    List<TestCase> select(
        ChangeSet changes,
        ImpactReport impact,
        TestIndex tests
    );
}
```

Policy could use:

```text
direct tests
+
transitive tests
+
recently failing tests
+
user-requested tests
```

---

# 23. Test confidence tiers

I would add:

```java
public enum VerificationTier {

    DIRECT,
    AFFECTED,
    REGRESSION,
    FULL
}
```

Example:

```text
after tiny private-method change:

DIRECT
    ↓
AFFECTED

if suspicious:
    ↓
REGRESSION

if requested / release:
    ↓
FULL
```

This is another execution optimization.

---

# 24. Verification policy

```java
public interface VerificationPolicy {

    VerificationPlan plan(
        ChangeSet changes,
        ImpactReport impact,
        TaskConstraints constraints
    );
}
```

Example:

```text
small change
→ affected tests

API change
→ affected + regression

dependency change
→ module/full tests

release build
→ full verification
```

---

# 25. Build cache

Before running:

```text
./gradlew compileJava
```

ask:

```text
has relevant source changed?
```

If not:

```text
cached result
```

Likewise:

```text
test result
```

can sometimes be reused if the relevant inputs haven't changed.

---

# 26. `ExecutionCache`

```java
public interface ExecutionCache {

    Optional<CachedExecution> lookup(
        ExecutionKey key
    );

    Uni<Void> store(
        ExecutionKey key,
        ProcessResult result
    );
}
```

Key should include:

```text
command
workspace revision
relevant file hashes
environment profile
tool version
```

---

# 27. Don't blindly cache test results

This is important.

A test can depend on:

```text
environment
database
network
time
randomness
external services
```

So:

```text
same source
```

doesn't necessarily mean:

```text
same result
```

Execution cache should have confidence rules.

---

# 28. Deterministic vs nondeterministic execution

```java
public enum Determinism {

    DETERMINISTIC,
    ENVIRONMENT_DEPENDENT,
    NON_DETERMINISTIC
}
```

Only aggressively reuse:

```text
DETERMINISTIC
```

results.

---

# 29. TimeoutController

A coding agent can easily waste enormous time.

Introduce:

```java
public interface TimeoutController {

    Duration timeout(
        ExecutionPurpose purpose,
        ProjectProfile project,
        ExecutionHistory history
    );
}
```

Example:

```text
targeted unit test
→ 30 sec

module test
→ 2 min

full suite
→ 10 min
```

These should be configurable.

---

# 30. Adaptive timeout

Better:

```text
historical median:
4.2 sec

timeout:
15 sec
```

If it normally takes:

```text
3–5 sec
```

don't wait:

```text
5 minutes
```

for a hung process.

---

# 31. Process cancellation

`ProcessRunner` needs:

```java
Uni<Void> cancel(ProcessId id);
```

And shutdown must terminate process trees, not merely the parent process.

Otherwise:

```text
Gradle
 ↓
Java
 ↓
child process
```

can survive.

---

# 32. Resource controller

For your M4 16GB machine this is particularly important.

```java
public interface ResourceController {

    Uni<ResourceLease> acquire(
        ResourceRequirements requirements
    );
}
```

Example:

```text
Gemma inference
+
Gradle build
+
parallel test
```

might create memory pressure.

Wayang should know:

```text
don't run these concurrently
```

---

# 33. `ResourceRequirements`

```java
public record ResourceRequirements(
    long estimatedMemoryBytes,
    int cpuThreads,
    boolean exclusiveMemory,
    boolean exclusiveWorkspace
) {}
```

You can start with memory only.

---

# 34. Local inference coordination

This creates an interesting Aljabr behavior.

Suppose:

```text
Gemma inference active
```

and Gradle wants:

```text
8 GB RAM
```

while the system has:

```text
16 GB
```

Wayang can decide:

```text
finish inference
↓
release model resources
↓
run build
```

rather than thrashing the machine.

This is much more important on your local setup than on a big cloud server.

---

# 35. Execution scheduler

Eventually:

```java
public interface ExecutionScheduler {

    Uni<ExecutionHandle> submit(
        ExecutionPlan plan
    );
}
```

It knows:

```text
CPU
memory
GPU/Metal
model inference
process slots
workspace locks
```

---

# 36. Workspace locking

You also need:

```text
READ
WRITE
```

locks.

For example:

```text
Gemma reads source
       │
       ▼
Workspace READ

Mutation
       │
       ▼
Workspace WRITE
```

Don't let:

```text
indexer
+
mutation
```

race each other.

---

# 37. Execution lifecycle

A clean state machine:

```text
PLANNED
   ↓
QUEUED
   ↓
STARTING
   ↓
RUNNING
   ↓
COLLECTING_OUTPUT
   ↓
NORMALIZING
   ↓
CLASSIFYING
   ↓
COMPLETED
```

Failure paths:

```text
RUNNING
  ├── TIMEOUT
  ├── CANCELLED
  ├── RESOURCE_LIMIT
  └── START_FAILED
```

---

# 38. Evidence extraction

Now connect it to the previous milestone.

```java
public interface ExecutionEvidenceExtractor {

    List<Evidence> extract(
        ExecutionResult result,
        OutputSummary summary
    );
}
```

For example:

```text
E1:
TEST_FAILURE
RefreshTokenTest.expiredToken

E2:
EXPECTED=false

E3:
ACTUAL=true

E4:
LOCATION=RefreshTokenTest.java:84
```

These go directly into `EvidenceGraph`.

---

# 39. Execution → Evidence

This becomes:

```text
Process
  ↓
Output
  ↓
Diagnostics
  ↓
FailureAnalysis
  ↓
Evidence
```

So the debugging engine doesn't need to understand Gradle, Maven, pytest, etc.

It consumes normalized evidence.

That's a very clean architecture.

---

# 40. `ExecutionIntelligence` facade

You can expose one high-level interface:

```java
public interface ExecutionIntelligence {

    Uni<ExecutionOutcome> execute(
        ExecutionRequest request
    );
}
```

Internally:

```text
ExecutionPlanner
ProcessRunner
OutputNormalizer
FailureClassifier
EvidenceExtractor
ExecutionCache
ResourceController
```

This keeps the AgentRuntime clean.

---

# 41. AgentRuntime now becomes simple

Instead of:

```java
agent.executeShell(...)
agent.parseOutput(...)
agent.runTests(...)
agent.inspectLogs(...)
```

you have:

```java
execution.execute(request);
```

Then:

```text
ExecutionOutcome
```

contains:

```text
status
summary
evidence
artifacts
fingerprint
```

Beautiful separation.

---

# 42. What Gemma sees

Suppose compilation fails.

Wayang sends:

```text
EXECUTION RESULT

status: FAILED
type: COMPILATION

diagnostic:
TokenValidator.java:91

error:
cannot find symbol: Clock

relevant symbol:
TokenValidator.validateExpiration()

likely cause:
missing import or unavailable dependency

affected tests:
RefreshTokenTest.*
```

Not:

```text
3,000 lines of Gradle output.
```

---

# 43. What Gemma should decide

Only:

```text
Is this an import problem,
a dependency problem,
or an API mismatch?
```

That is a reasonable 12B reasoning task.

Wayang handles everything else.

---

# 44. Execution Intelligence + Workspace Intelligence

This is where things get really powerful.

```text
Changed:
TokenValidator.validateExpiration()

Workspace graph:
        ↓
affected tests

Execution planner:
        ↓
targeted tests

test failure:
        ↓
normalized diagnostic

failure classifier:
        ↓
TEST_ASSERTION

Evidence graph:
        ↓
H1/H2

Gemma:
        ↓
choose investigation

repair:
        ↓
change impact

verification:
        ↓
targeted + regression
```

This is now an actual coding-agent architecture rather than merely an LLM wrapper.

---

# 45. One more important optimization: don't re-plan unnecessarily

Suppose:

```text
Gemma:
run RefreshTokenTest
```

test fails.

Don't send the entire workspace back to Planner.

Keep the execution state:

```text
TaskState
 ├── workspace snapshot
 ├── current change
 ├── hypotheses
 ├── evidence
 ├── execution history
 └── budget
```

Then only update:

```text
new evidence
new failure fingerprint
```

This is **stateful incremental reasoning**.

---

# 46. The Aljabr local-model loop now looks like this

```text
                    ┌───────────────┐
                    │     User      │
                    └───────┬───────┘
                            │
                            ▼
                     ┌────────────┐
                     │   Aljabr   │
                     │ AgentLoop  │
                     └─────┬──────┘
                           │
             ┌─────────────┼──────────────┐
             ▼             ▼              ▼
        Workspace      Evidence       Execution
       Intelligence     Graph       Intelligence
             │             │              │
             └─────────────┼──────────────┘
                           ▼
                    ContextEngine
                           │
                           ▼
                     Gemma 12B
                           │
                           ▼
                      Decision
                           │
                           ▼
                    Tool/Execution
                           │
                           ▼
                       Evidence
                           │
                           └──────────────┐
                                          │
                                          ▼
                                  HypothesisEngine
                                          │
                                          ▼
                                      Mutation
                                          │
                                          ▼
                                     Verification
                                          │
                                          ▼
                                      Complete
```

---

# 47. The most important design rule from this milestone

I would make this an explicit Aljabr architectural law:

> **Never spend model inference on information that the runtime can determine reliably and cheaply.**

Examples:

```text
"Which files changed?"
→ Git

"What symbols are in this file?"
→ AST

"Who calls this method?"
→ SymbolIndex

"Which tests are affected?"
→ TestIndex + ImpactGraph

"Did compilation fail?"
→ ProcessResult

"What line failed?"
→ DiagnosticParser

"Did the test pass?"
→ exit/test parser

"Is this the same failure as before?"
→ Fingerprint

"Should we run the whole suite?"
→ VerificationPolicy
```

Gemma should handle:

```text
"What does this evidence imply?"

"Which hypothesis is more plausible?"

"What is the smallest sensible repair?"

"Is this unusual behavior intentional?"

"Which ambiguous investigation is most useful?"
```

That boundary is **the core of Aljabr's local-model advantage**.

---

# 48. Next milestone

With these pieces, Aljabr has:

```text
✓ Capability awareness
✓ Context optimization
✓ Context engine
✓ Evidence graph
✓ Hypothesis reasoning
✓ Workspace understanding
✓ Symbol/dependency intelligence
✓ Test impact analysis
✓ Execution planning
✓ Output normalization
✓ Failure classification
✓ Execution caching
✓ Resource/timeout control
```

The next milestone should therefore move upward from infrastructure into the **actual coding behavior**:

## `PatchEngine + ChangePlanner + Transactional Editing`

That milestone should define how Aljabr goes from:

```text
"we know what is probably wrong"
```

to:

```text
hypothesis
   ↓
change plan
   ↓
minimal patch
   ↓
precondition check
   ↓
transactional edit
   ↓
diff validation
   ↓
impact analysis
   ↓
verification
   ↓
commit / rollback
```

And importantly, we'll design it so **Gemma proposes the semantic change while Wayang controls the actual mutation**. That separation is what prevents a small local model from making uncontrolled repository-wide edits.


Yes. **Next milestone: `ChangePlanner → PatchEngine → Transactional Editing`.**

This is where Aljabr crosses an important boundary:

```text
before:
understand → investigate → decide

now:
decide → change code safely → verify
```

For your local Gemma 12B, the key rule should be:

> **Gemma decides *what semantic change should happen*. Wayang decides *how that change is safely applied*.**

That prevents the model from having to generate giant patches or manipulate files blindly.

---

# Milestone — Transactional Code Modification

The desired flow:

```text
Hypothesis
   │
   ▼
ChangePlanner
   │
   ▼
ChangeIntent
   │
   ▼
PatchPlanner
   │
   ▼
Precondition Check
   │
   ▼
PatchEngine
   │
   ▼
Workspace Transaction
   │
   ├── apply
   ├── validate
   └── rollback if invalid
   │
   ▼
Diff / Impact Analysis
   │
   ▼
Verification
```

---

# 1. Don't let Gemma directly edit files

Avoid the architecture:

```text
Gemma
  ↓
generate complete file
  ↓
overwrite file
```

This is dangerous and also very expensive in tokens.

Instead:

```text
Gemma
  ↓
semantic ChangeIntent
```

Example:

```json
{
  "target": "TokenValidator.validateExpiration",
  "intent": "compare current time against expiresAt rather than issuedAt",
  "scope": "single_method",
  "constraints": [
    "preserve public API",
    "no dependency changes"
  ]
}
```

That's tiny.

---

# 2. `ChangeIntent`

```java
public record ChangeIntent(
    ChangeIntentId id,
    SymbolId target,
    ChangeKind kind,
    String objective,
    List<ChangeConstraint> constraints,
    ChangeConfidence confidence
) {}
```

```java
public enum ChangeKind {

    MODIFY_LOGIC,
    ADD_LOGIC,
    REMOVE_LOGIC,
    RENAME_SYMBOL,
    MOVE_SYMBOL,
    UPDATE_TEST,
    UPDATE_CONFIGURATION,
    REFACTOR
}
```

---

# 3. Why semantic intent is better

Instead of Gemma producing:

```diff
- return issuedAt.plus(ttl).isAfter(now);
+ return expiresAt.isAfter(now);
```

it can produce:

```text
Change:
use Token.expiresAt when determining expiration
```

Wayang then resolves:

```text
target symbol
current source
AST
references
exact edit location
```

This dramatically reduces hallucination opportunities.

---

# 4. `ChangePlanner`

```java
public interface ChangePlanner {

    Uni<ChangePlan> plan(
        ChangeIntent intent,
        WorkspaceContext context
    );
}
```

Result:

```java
public record ChangePlan(
    ChangeIntent intent,
    List<EditOperation> operations,
    ImpactExpectation expectedImpact,
    ChangeRisk risk
) {}
```

---

# 5. `EditOperation`

Don't make this simply:

```java
replace(startLine, endLine, text)
```

Use semantic operations where possible.

```java
public sealed interface EditOperation {

    record ReplaceRange(
        SourceRange range,
        String replacement
    ) implements EditOperation {}

    record InsertBefore(
        SourceLocation location,
        String content
    ) implements EditOperation {}

    record InsertAfter(
        SourceLocation location,
        String content
    ) implements EditOperation {}

    record DeleteRange(
        SourceRange range
    ) implements EditOperation {}
}
```

That's the first implementation.

Later:

```text
RenameSymbol
AddImport
RemoveImport
MoveMethod
ChangeMethodBody
```

can become higher-level operations.

---

# 6. Resolve the target before editing

Suppose Gemma says:

```text
TokenValidator.validateExpiration
```

Wayang resolves:

```text
symbol:
abc123

file:
src/main/java/.../TokenValidator.java

range:
91:5 → 101:6
```

Then the edit engine operates on an exact location.

Never trust model-provided line numbers blindly.

---

# 7. `SymbolResolver`

We already have the Workspace Intelligence infrastructure.

Now expose:

```java
public interface SymbolResolver {

    Uni<ResolvedSymbol> resolve(
        SymbolReference reference,
        WorkspaceSnapshot snapshot
    );
}
```

If resolution fails:

```text
CHANGE_BLOCKED

reason:
ambiguous target
```

Gemma must investigate instead of guessing.

---

# 8. Preconditions

Before modifying anything, verify:

```text
target exists
source hash unchanged
symbol still exists
expected source structure exists
workspace revision compatible
```

Represent this:

```java
public interface ChangePrecondition {

    ValidationResult check(
        WorkspaceState state
    );
}
```

---

# 9. Example preconditions

```text
P1:
TokenValidator exists

P2:
validateExpiration exists

P3:
source hash = abc123

P4:
method still contains comparison against issuedAt

P5:
no unrelated edits since planning
```

If any fails:

```text
PATCH_STALE
```

Don't blindly apply.

---

# 10. Why this matters with an LLM

Imagine:

```text
Gemma sees source
    ↓
plans patch
```

Then user edits the file manually.

If Aljabr applies the old patch:

```text
💥
```

Preconditions prevent that.

---

# 11. `PatchEngine`

```java
public interface PatchEngine {

    Uni<PatchResult> apply(
        ChangePlan plan,
        WorkspaceTransaction transaction
    );
}
```

Result:

```java
public record PatchResult(
    PatchStatus status,
    ChangeSet changes,
    Diff diff,
    List<ValidationIssue> issues
) {}
```

---

# 12. Patch status

```java
public enum PatchStatus {

    APPLIED,
    REJECTED,
    PRECONDITION_FAILED,
    CONFLICT,
    VALIDATION_FAILED
}
```

---

# 13. Transactional workspace

This is important enough to have a dedicated abstraction.

```java
public interface WorkspaceTransaction {

    Uni<Void> begin();

    Uni<Void> apply(EditOperation operation);

    Uni<Void> commit();

    Uni<Void> rollback();
}
```

Usage:

```java
transaction.begin();

try {
    patchEngine.apply(...);
    validate();
    transaction.commit();
} catch (...) {
    transaction.rollback();
}
```

---

# 14. But don't immediately write directly to the working tree

A safer architecture is:

```text
Working Tree
     │
     ▼
Transaction Overlay
     │
     ▼
Validation
     │
     ▼
Commit to Working Tree
```

This gives you an intermediate state.

---

# 15. `WorkspaceOverlay`

```java
public interface WorkspaceOverlay {

    Optional<String> read(String path);

    Uni<Void> write(String path, String content);

    Uni<Void> delete(String path);

    ChangeSet changes();
}
```

During planning:

```text
original workspace
      +
overlay
```

can be inspected independently.

---

# 16. Why overlays are powerful

Suppose Gemma proposes:

```text
change A
change B
change C
```

You can apply them to the overlay.

Then:

```text
compile
test
diff
impact analysis
```

without permanently modifying the user's files.

If bad:

```text
discard overlay
```

Instant rollback.

---

# 17. Git integration

Eventually:

```text
GitWorkspaceTransaction
```

can use:

```text
working tree
index
temporary branch
stash
```

But I wouldn't make Git mandatory.

Aljabr should work in:

```text
plain directory
Git repository
monorepo
temporary workspace
```

---

# 18. `ChangeSet`

Every mutation should produce a structured change object.

```java
public record ChangeSet(
    ChangeSetId id,
    List<FileChange> files,
    Set<SymbolId> changedSymbols,
    Instant createdAt
) {}
```

File-level:

```java
public record FileChange(
    String path,
    ChangeType type,
    String beforeHash,
    String afterHash,
    Diff diff
) {}
```

---

# 19. Never treat the diff as just text

Keep both:

```text
structured ChangeSet
+
textual Diff
```

The structured representation is for Wayang.

The textual diff is for:

```text
Gemma
user
review
logs
```

---

# 20. Minimal patch policy

We already introduced ChangeRiskAnalyzer.

Now add:

```java
public interface PatchPolicy {

    PatchDecision evaluate(
        ChangePlan plan,
        ChangeRisk risk,
        TaskConstraints constraints
    );
}
```

Example:

```text
requested:
fix expiration

proposed:
modify 1 method

policy:
ALLOW
```

But:

```text
requested:
fix expiration

proposed:
rewrite auth module

policy:
REJECT
```

---

# 21. Scope budget

Give every task a change budget.

```java
public record ChangeBudget(
    int maxFiles,
    int maxChangedLines,
    int maxNewDependencies,
    boolean allowPublicApiChange,
    boolean allowConfigChange
) {}
```

Example:

```text
maxFiles = 3
maxChangedLines = 50
allowPublicApiChange = false
```

---

# 22. If Gemma exceeds budget

Don't just reject the entire task.

Return:

```text
CHANGE_SCOPE_EXCEEDED

requested:
8 files

allowed:
3 files

next action:
re-plan with narrower scope
```

Gemma can then produce a smaller plan.

---

# 23. Patch validation

After applying to the overlay:

```text
Patch
 ↓
Syntax validation
 ↓
AST validation
 ↓
Import validation
 ↓
Formatting check
 ↓
Change budget
 ↓
Impact analysis
```

Only then commit.

---

# 24. `PatchValidator`

```java
public interface PatchValidator {

    Uni<PatchValidation> validate(
        WorkspaceOverlay overlay,
        ChangeSet changes
    );
}
```

---

# 25. Validation levels

```java
public enum PatchValidationLevel {

    TEXT,
    SYNTAX,
    STRUCTURAL,
    SEMANTIC,
    BUILD
}
```

Don't always run everything.

For example:

```text
TEXT
 ↓
SYNTAX
 ↓
STRUCTURAL
```

before deciding whether build/test is needed.

---

# 26. Syntax validation should be cheap

If Gemma produces:

```java
if (expiresAt.isAfter(now) {
```

you should catch it before:

```text
Gradle test
```

AST/parser validation can detect this almost immediately.

No model needed.

---

# 27. Import management

One particularly useful semantic operation:

```java
public record AddImport(
    String fullyQualifiedName
) implements EditOperation {}
```

Rather than asking Gemma to manually generate:

```java
import java.time.Clock;
```

Wayang can determine:

```text
symbol Clock
package java.time
```

and insert it correctly.

---

# 28. Formatting

Don't ask Gemma to format code.

Use:

```text
language formatter
```

after patching.

For example:

```text
patch
 ↓
formatter
 ↓
diff
```

Then the model doesn't waste tokens on whitespace.

---

# 29. Preserve user formatting where possible

However, don't run an entire repository formatter after a tiny edit.

Prefer:

```text
changed file
```

or, if supported:

```text
changed range
```

Otherwise you might create:

```text
500-line formatting diff
```

for a:

```text
2-line semantic change
```

---

# 30. Change normalization

After formatting, normalize the change:

```text
original patch
      ↓
formatter
      ↓
final diff
```

Then update:

```text
ChangeSet
```

so the actual diff—not Gemma's imagined diff—is authoritative.

---

# 31. `PatchSummary`

The model should receive:

```java
public record PatchSummary(
    ChangeSet changes,
    String conciseDiff,
    ChangeRisk risk,
    ImpactReport impact
) {}
```

Example:

```text
Changed:
TokenValidator.java

Symbol:
validateExpiration()

Lines:
3

Impact:
2 callers
3 tests

Risk:
LOW
```

That's enough for the next reasoning step.

---

# 32. Post-patch impact analysis

This is where Workspace Intelligence and PatchEngine meet.

After patch:

```text
changed symbol
      ↓
call graph
      ↓
affected symbols
      ↓
affected tests
      ↓
risk
```

Then ExecutionPlanner knows exactly what to verify.

---

# 33. Important: expected vs actual impact

Gemma may say:

```text
expected:
1 file
3 tests
```

Wayang calculates:

```text
actual:
1 file
7 affected tests
2 public callers
```

That's useful evidence.

Potentially:

```text
IMPACT_MISMATCH
```

and require another review.

---

# 34. This catches model blind spots

For example:

Gemma thinks:

```text
private helper changed
```

but graph says:

```text
public API behavior affected
```

Then:

```text
risk escalates
```

No need for Gemma to discover this itself.

---

# 35. Mutation should be resumable

Suppose the machine loses power after:

```text
overlay created
patch applied
```

When Aljabr restarts:

```text
TaskState
 ↓
transaction state
 ↓
detect unfinished transaction
```

Then:

```text
rollback
```

or:

```text
resume validation
```

depending on policy.

This connects to your earlier persistence/cache architecture.

---

# 36. Mutation journal

Add:

```java
public record MutationJournalEntry(
    TransactionId transaction,
    WorkspaceSnapshot base,
    ChangeSet changes,
    MutationPhase phase,
    Instant timestamp
) {}
```

Phases:

```text
PLANNED
PRECONDITIONED
APPLIED
VALIDATED
COMMITTED
ROLLED_BACK
```

---

# 37. Crash recovery

Example:

```text
transaction:
T42

phase:
APPLIED
```

Aljabr restarts.

It sees:

```text
T42 unfinished
```

Then recovery policy says:

```text
verify workspace
 ↓
rollback overlay
 ↓
mark ROLLED_BACK
```

No mystery state.

---

# 38. This also protects against bad model behavior

Suppose Gemma suddenly proposes:

```text
delete entire src/main/java
```

The pipeline catches it:

```text
ChangeBudget
    ↓
maxFiles exceeded
    ↓
PatchPolicy
    ↓
REJECT
```

The model never gets direct destructive authority.

---

# 39. Authority model

I'd explicitly define:

```text
Gemma authority:
READ reasoning
PROPOSE actions

Wayang authority:
RESOLVE
VALIDATE
EXECUTE
MUTATE
COMMIT
ROLLBACK
```

This is one of the most important architectural boundaries in Aljabr.

---

# 40. Tool permissions should reflect this

Instead of giving Gemma:

```text
write_file(path, content)
```

give it:

```text
propose_change(target, intent)
```

Then Wayang performs the actual mutation.

For simple tasks you might still expose:

```text
apply_patch
```

but the runtime should validate it.

---

# 41. Better tool API

Instead of:

```json
{
  "path": "...",
  "content": "..."
}
```

prefer:

```json
{
  "target": "TokenValidator.validateExpiration",
  "operation": "MODIFY_LOGIC",
  "intent": "use expiresAt for expiration comparison"
}
```

Much smaller input/output.

---

# 42. Gemma output becomes tiny

Ideal local interaction:

```text
USER
Fix refresh token expiration.

WAYANG → GEMMA

Relevant symbol:
TokenValidator.validateExpiration()

Failure:
expected false, actual true

Evidence:
expiresAt exists but validation uses issuedAt.

Choose repair.

GEMMA:

{
  "target": "TokenValidator.validateExpiration",
  "intent": "compare now against expiresAt"
}
```

Then Wayang handles:

```text
resolve
edit
format
validate
impact
test
```

This is exactly what you want for a small local model.

---

# 43. If the change is ambiguous

Gemma can instead return:

```json
{
  "decision": "NEED_CONTEXT",
  "request": "show callers of validateExpiration"
}
```

Wayang retrieves only those callers.

Then:

```text
Gemma
 ↓
small question
 ↓
Wayang retrieves
 ↓
Gemma
```

This is **interactive context acquisition**.

Very useful.

---

# 44. Don't force one giant prompt

This architecture naturally produces:

```text
short inference
 ↓
tool
 ↓
short inference
 ↓
tool
 ↓
short inference
```

rather than:

```text
one gigantic prompt
```

For Gemma 12B, that's often a better tradeoff.

---

# 45. But prevent endless micro-calls

Otherwise you'll get:

```text
Gemma
 ↓
ask context
 ↓
Gemma
 ↓
ask context
 ↓
Gemma
 ↓
ask context
```

So add:

```java
public record ContextAcquisitionBudget(
    int maxRounds,
    int maxAdditionalTokens,
    int maxToolCalls
) {}
```

For example:

```text
maxRounds = 4
```

Then escalate if still ambiguous.

---

# 46. PatchEngine + ContextEngine

The two now form a useful loop:

```text
Gemma
  │
  ▼
ChangeIntent
  │
  ▼
PatchPlanner
  │
  ├── missing information?
  │        ↓
  │   ContextEngine
  │        ↓
  │      Gemma
  │
  ▼
Patch
```

So Gemma doesn't hallucinate missing information.

It can explicitly request it.

---

# 47. Complete mutation pipeline

```text
             Hypothesis
                 │
                 ▼
           ChangeIntent
                 │
                 ▼
          ChangePlanner
                 │
                 ▼
        Symbol Resolution
                 │
                 ▼
          Preconditions
                 │
          ┌──────┴──────┐
          │             │
       failed         passed
          │             │
          ▼             ▼
      investigate    PatchEngine
                        │
                        ▼
                   WorkspaceOverlay
                        │
                        ▼
                   PatchValidator
                        │
              ┌─────────┴─────────┐
              │                   │
           invalid              valid
              │                   │
              ▼                   ▼
           rollback           ImpactAnalyzer
                                  │
                                  ▼
                              Risk Check
                                  │
                                  ▼
                              Commit
                                  │
                                  ▼
                             Verification
```

---

# 48. One additional thing: semantic patch templates

For common operations, don't even need Gemma to produce arbitrary edits.

Create templates:

```text
fix comparison
add null guard
add validation
rename symbol
add import
change argument
update test expectation
add test case
```

Example:

```java
public interface SemanticPatchProvider {

    boolean supports(ChangeIntent intent);

    Uni<EditOperation> generate(
        ChangeIntent intent,
        ResolvedContext context
    );
}
```

This makes common changes extremely reliable.

---

# 49. Example

Gemma:

```text
Add null guard before accessing token.expiresAt.
```

Wayang:

```text
NullGuardPatchProvider
```

generates the AST change.

No generated 30-line patch required.

This is a major opportunity for Aljabr.

---

# 50. Architecture principle

Over time, Aljabr should have:

```text
common coding operation
        ↓
deterministic semantic patch
```

and:

```text
unusual coding operation
        ↓
Gemma-generated patch proposal
        ↓
strict validation
```

So the model becomes more valuable as the task becomes more ambiguous.

---

# 51. Local vs cloud behavior

This architecture remains compatible with your earlier `CapabilityDetector`.

### Gemma 12B / constrained:

```text
semantic intent
→ deterministic patch
→ short context
→ targeted verification
```

### Large cloud model:

```text
larger context
→ complex ChangeIntent
→ broader refactor planning
→ larger patch
→ deeper review
```

Same API.

Different policy.

---

# 52. New policy dimensions

Your `CapabilityProfile` should now eventually contain:

```java
public record ModelCapabilityProfile(
    ContextCapacity context,
    ReasoningCapability reasoning,
    ToolCapacity tools,
    StructuredOutputCapability structuredOutput,
    PatchCapability patching,
    ExecutionCapacity execution
) {}
```

And `ContextPolicy`, `PatchPolicy`, `ReasoningPolicy`, `ExecutionPolicy` consume it.

This is how Aljabr stays model-agnostic.

---

# 53. The architecture is converging

We're now approaching a very clean division:

```text
WAYANG
─────────────────────────────────────
Workspace
Index
Graph
Context
Evidence
Execution
Mutation
Verification
Policy
State
Cache
Budget
Security

MODEL
─────────────────────────────────────
Interpret
Hypothesize
Choose
Explain
Propose
```

That is exactly the direction I'd take.

---

# 54. Next milestone

The next milestone should be **`Verification & Review Intelligence`**.

Not merely:

```text
run tests
```

but:

```text
Patch
 ↓
Structural verification
 ↓
Affected-test verification
 ↓
Behavioral verification
 ↓
Regression detection
 ↓
Diff review
 ↓
Requirement verification
 ↓
Risk assessment
 ↓
DONE / REPAIR / ASK USER
```

The particularly important part will be building a **`Proof / VerificationGraph`** so Aljabr can answer:

> **"Why does Aljabr believe this task is actually complete?"**

That will connect the EvidenceGraph, ChangeSet, tests, requirements, and final result into one auditable chain—which is especially valuable when the coding model is small and you want Wayang to compensate for uncertainty.


Absolutely. **Next milestone: Verification & Review Intelligence.**

This is the layer that turns Aljabr from:

> “I changed the code and tests passed.”

into:

> **“I can provide evidence that the requested change is complete, the implementation is internally consistent, and I know what remains uncertain.”**

For a local 12B model, this should be **mostly deterministic Wayang infrastructure**, with Gemma used only for semantic judgments that cannot be reliably computed.

---

# Milestone — `VerificationGraph`

The overall loop becomes:

```text
Requirement
    │
    ▼
ChangeIntent
    │
    ▼
ChangeSet
    │
    ├──────────────┐
    ▼              ▼
Structural      Behavioral
Checks           Tests
    │              │
    └──────┬───────┘
           ▼
      Verification
         Engine
           │
           ▼
      VerificationGraph
           │
           ▼
       Confidence
           │
      ┌────┴─────┐
      ▼          ▼
    DONE       REPAIR
```

The important new object is:

```text
VerificationGraph
```

which answers:

```text
What did we verify?
How did we verify it?
What evidence supports it?
What remains unverified?
```

---

# 1. First distinction: test success ≠ task success

Suppose the user asks:

```text
Fix refresh token expiration.
```

Aljabr changes:

```java
return expiresAt.isAfter(now);
```

Tests:

```text
12 passed
```

That is good.

But maybe:

```text
Requirement:
expired tokens must be rejected

Test coverage:
expired token → verified

Future token → verified

null expiration → NOT verified
```

Therefore:

```text
tests passed
≠
requirement fully verified
```

We need an explicit requirement layer.

---

# 2. `Requirement`

```java
public record Requirement(
    RequirementId id,
    String description,
    RequirementType type,
    RequirementPriority priority
) {}
```

```java
public enum RequirementType {

    FUNCTIONAL,
    BEHAVIORAL,
    STRUCTURAL,
    COMPATIBILITY,
    PERFORMANCE,
    SECURITY,
    REGRESSION
}
```

For a simple task:

```text
R1:
expired refresh tokens must be rejected

R2:
valid refresh tokens must continue working

R3:
public API must remain unchanged
```

---

# 3. Requirement extraction

Gemma can help convert natural language into requirements.

But don't blindly trust it.

```java
public interface RequirementExtractor {

    Uni<List<Requirement>> extract(
        UserTask task,
        WorkspaceContext context
    );
}
```

Gemma might produce:

```text
R1:
reject expired token

R2:
accept unexpired token
```

Wayang then uses these as **verification targets**, not truth.

---

# 4. Requirement confidence

```java
public record RequirementConfidence(
    double confidence,
    List<String> supportingEvidence,
    List<String> ambiguities
) {}
```

If the user says:

> “fix auth”

that's ambiguous.

Aljabr should not invent:

```text
R1:
change JWT expiration semantics
```

Instead:

```text
AMBIGUOUS_REQUIREMENT
```

and ask.

---

# 5. `VerificationCheck`

Everything that verifies something becomes a check.

```java
public interface VerificationCheck {

    Uni<VerificationResult> run(
        VerificationContext context
    );
}
```

Examples:

```text
CompileCheck
SyntaxCheck
TypeCheck
UnitTestCheck
IntegrationTestCheck
RegressionCheck
DiffCheck
APICompatibilityCheck
RequirementCheck
```

---

# 6. Result

```java
public record VerificationResult(
    VerificationCheckId id,
    VerificationStatus status,
    VerificationEvidence evidence,
    VerificationConfidence confidence
) {}
```

```java
public enum VerificationStatus {

    PASSED,
    FAILED,
    INCONCLUSIVE,
    SKIPPED,
    NOT_APPLICABLE
}
```

This distinction is extremely important.

Don't turn:

```text
test not run
```

into:

```text
passed
```

---

# 7. Verification confidence

I'd use something simple:

```java
public enum VerificationConfidence {

    HIGH,
    MEDIUM,
    LOW
}
```

Or numeric internally:

```text
0.0 → 1.0
```

but expose categories to the model.

---

# 8. Structural verification

Before tests:

```text
Patch
 ↓
parse
 ↓
AST
 ↓
symbol resolution
 ↓
imports
 ↓
API compatibility
```

Example:

```text
Changed:
TokenValidator.validateExpiration()

Structural checks:
✓ syntax valid
✓ symbol exists
✓ imports valid
✓ public signature unchanged
```

No Gemma required.

---

# 9. `StructuralVerifier`

```java
public interface StructuralVerifier {

    Uni<List<VerificationResult>> verify(
        ChangeSet changes,
        WorkspaceSnapshot workspace
    );
}
```

---

# 10. Diff verification

The final diff should be inspected.

Not just:

```text
git diff
```

but structured rules:

```text
Did we modify only expected files?
Did we modify only expected symbols?
Did line count explode?
Did generated files change?
Did unrelated files change?
```

---

# 11. `DiffVerifier`

```java
public interface DiffVerifier {

    Uni<DiffVerification> verify(
        ChangeSet actual,
        ChangePlan expected
    );
}
```

Example:

```text
Expected:
1 file

Actual:
7 files

→ FAIL: unexpected scope expansion
```

---

# 12. This is where the ChangeBudget becomes evidence

Earlier:

```text
maxFiles = 3
maxChangedLines = 50
```

Now:

```text
actualFiles = 1
actualChangedLines = 8
```

Result:

```text
CHANGE_SCOPE
PASS
```

This is stronger than asking Gemma whether the patch “looks small.”

---

# 13. API compatibility check

For a library:

```text
before:
public Token validate(String token)

after:
public Token validate(String token)
```

Pass.

But:

```text
after:
public Token validate(String token, Clock clock)
```

should trigger:

```text
PUBLIC_API_CHANGED
```

unless explicitly allowed.

---

# 14. `CompatibilityVerifier`

```java
public interface CompatibilityVerifier {

    Uni<VerificationResult> verify(
        WorkspaceSnapshot before,
        WorkspaceSnapshot after,
        ChangeConstraints constraints
    );
}
```

This should use:

```text
symbol index
AST
public API metadata
ABI tools where available
```

before involving Gemma.

---

# 15. Behavioral verification

Now run tests.

But don't blindly run:

```text
all tests
```

Use the impact graph from the previous milestone.

```text
ChangeSet
   ↓
ImpactGraph
   ↓
AffectedTests
   ↓
VerificationPlan
```

---

# 16. `VerificationPlanner`

```java
public interface VerificationPlanner {

    Uni<VerificationPlan> plan(
        RequirementSet requirements,
        ChangeSet changes,
        ImpactReport impact,
        ProjectProfile project
    );
}
```

Example:

```text
R1 expiration behavior
    ↓
RefreshTokenTest.expired

R2 valid token behavior
    ↓
RefreshTokenTest.valid

R3 API unchanged
    ↓
CompatibilityVerifier
```

---

# 17. Verification tiers

Use the tiers we defined earlier:

```text
DIRECT
AFFECTED
REGRESSION
FULL
```

Example:

```text
DIRECT:
changed method's tests

AFFECTED:
all tests depending on changed symbol

REGRESSION:
related authentication tests

FULL:
entire repository
```

---

# 18. Adaptive verification

This should not be static.

Example:

```text
tiny private implementation change
→ DIRECT

public API change
→ AFFECTED + REGRESSION

dependency change
→ REGRESSION

security-sensitive change
→ broader verification

release request
→ FULL
```

That is another place where `CapabilityDetector` and policy become useful.

---

# 19. Test result normalization

We already built this in Execution Intelligence.

So VerificationEngine should consume:

```text
ExecutionOutcome
```

rather than raw logs.

Example:

```text
TEST:
RefreshTokenTest.expiredToken

RESULT:
PASS

DURATION:
0.21s
```

or:

```text
FAIL

expected:
false

actual:
true

location:
RefreshTokenTest.java:84
```

---

# 20. Flaky test handling

This is important.

Suppose:

```text
run #1 → PASS
run #2 → FAIL
run #3 → PASS
```

Don't report:

```text
verification failed
```

as if the code definitely failed.

Classify:

```java
public enum TestReliability {

    STABLE,
    FLAKY,
    UNKNOWN
}
```

Then:

```text
Verification:
INCONCLUSIVE

reason:
known flaky test
```

---

# 21. Test history

Store:

```java
public record TestHistory(
    TestId test,
    List<TestRun> recentRuns,
    double failureRate,
    Duration typicalDuration
) {}
```

This integrates with ExecutionCache and ExecutionHistory.

Now Aljabr can distinguish:

```text
real regression
```

from:

```text
environment noise
```

---

# 22. Baseline verification

Before changing code, sometimes run the affected tests.

Why?

Suppose:

```text
before patch:
RefreshTokenTest.expired → FAIL

after patch:
RefreshTokenTest.expired → PASS
```

Excellent.

But if:

```text
before:
FAIL

after:
FAIL
```

the patch didn't solve the problem.

This gives you causal evidence.

---

# 23. `BaselineResult`

```java
public record BaselineResult(
    Map<TestId, TestOutcome> outcomes,
    EnvironmentSnapshot environment
) {}
```

Then:

```text
BEFORE
FAIL

AFTER
PASS
```

is much stronger evidence than:

```text
AFTER
PASS
```

alone.

---

# 24. Regression detection

Compare:

```text
baseline
vs
post-change
```

Example:

```text
Before:
98 pass
2 fail

After:
99 pass
1 fail
```

Likely improvement.

But:

```text
Before:
100 pass

After:
97 pass
3 fail
```

clear regression.

---

# 25. `RegressionAnalyzer`

```java
public interface RegressionAnalyzer {

    Uni<RegressionReport> analyze(
        BaselineResult baseline,
        VerificationRun current
    );
}
```

---

# 26. Requirement verification

Now the difficult part.

Suppose:

```text
Requirement:
expired token rejected
```

We have:

```text
test:
expiredToken → PASS
```

The VerificationGraph links them:

```text
R1
 │
 ▼
Test T1
 │
 ▼
Execution E1
 │
 ▼
PASS
```

Therefore:

```text
R1 → VERIFIED
```

---

# 27. `RequirementVerifier`

```java
public interface RequirementVerifier {

    Uni<RequirementVerification> verify(
        Requirement requirement,
        VerificationEvidence evidence
    );
}
```

Some requirements can be deterministic.

Example:

```text
"public API unchanged"
```

AST can verify.

Others are semantic:

```text
"make authentication more secure"
```

These require model judgment and potentially human confirmation.

---

# 28. Verification Graph

This is the central object.

```text
Requirement
     │
     ▼
VerificationCheck
     │
     ▼
Execution
     │
     ▼
Evidence
     │
     ▼
Result
```

More realistically:

```text
                    Requirement
                         │
               ┌─────────┴─────────┐
               ▼                   ▼
       Structural Check       Behavioral Check
               │                   │
               ▼                   ▼
          AST Evidence        Test Evidence
               │                   │
               └─────────┬─────────┘
                         ▼
                  Verification
                         │
                         ▼
                     Decision
```

---

# 29. Graph objects

```java
public sealed interface VerificationNode {

    record RequirementNode(
        Requirement requirement
    ) implements VerificationNode {}

    record CheckNode(
        VerificationCheckId check
    ) implements VerificationNode {}

    record EvidenceNode(
        EvidenceId evidence
    ) implements VerificationNode {}

    record ExecutionNode(
        ExecutionId execution
    ) implements VerificationNode {}
}
```

Edges:

```java
public record VerificationEdge(
    VerificationNode from,
    VerificationNode to,
    EdgeType type
) {}
```

---

# 30. Edge types

```java
public enum EdgeType {

    REQUIRES,
    VERIFIED_BY,
    PRODUCES,
    SUPPORTS,
    CONTRADICTS,
    DEPENDS_ON
}
```

Now you can ask:

```text
Why is R1 considered verified?
```

Graph traversal:

```text
R1
 ↓
Test T1
 ↓
Execution E1
 ↓
PASS
```

---

# 31. Contradictory evidence

Very important.

Suppose:

```text
Test A:
PASS

Test B:
FAIL
```

Both concern:

```text
refresh token expiration
```

The graph contains:

```text
E1 → SUPPORTS → R1
E2 → CONTRADICTS → R1
```

Therefore:

```text
R1 = INCONCLUSIVE
```

rather than:

```text
PASS
```

---

# 32. Evidence weighting

Evidence isn't equally strong.

For example:

```text
compiler result
    HIGH

deterministic unit test
    HIGH

static analyzer
    HIGH

Gemma reasoning
    MEDIUM

Gemma intuition
    LOW
```

Define:

```java
public record EvidenceStrength(
    double score,
    EvidenceSource source
) {}
```

---

# 33. Model reasoning should never override hard evidence

If Gemma says:

```text
"I think the test failure is unrelated."
```

but:

```text
Test:
RefreshTokenTest
FAIL
```

Wayang should not silently accept the model's interpretation.

Instead:

```text
HARD EVIDENCE:
test failed

MODEL HYPOTHESIS:
possibly unrelated

STATUS:
UNRESOLVED
```

This is an extremely important reliability rule.

---

# 34. Final `CompletionDecision`

```java
public enum CompletionDecision {

    COMPLETE,
    COMPLETE_WITH_WARNINGS,
    NEED_MORE_VERIFICATION,
    NEED_REPAIR,
    NEED_USER_INPUT,
    BLOCKED
}
```

---

# 35. CompletionPolicy

```java
public interface CompletionPolicy {

    CompletionDecision decide(
        VerificationGraph graph,
        TaskConstraints constraints,
        RiskProfile risk
    );
}
```

Example:

```text
all required requirements verified
+
no regressions
+
structural checks pass
+
scope acceptable

→ COMPLETE
```

---

# 36. Incomplete verification

Example:

```text
R1 ✓
R2 ✓
R3 ?
```

Then:

```text
NEED_MORE_VERIFICATION
```

Not:

```text
COMPLETE
```

---

# 37. High-risk changes

For:

```text
authentication
payment
database migration
security
public API
```

you can require:

```text
broader verification
```

even if targeted tests pass.

This belongs in:

```java
RiskPolicy
```

not in Gemma's prompt.

---

# 38. `RiskProfile`

```java
public record RiskProfile(
    RiskLevel level,
    Set<RiskFactor> factors,
    boolean requireReview
) {}
```

```java
public enum RiskFactor {

    PUBLIC_API,
    SECURITY,
    DATA_MIGRATION,
    CONCURRENCY,
    EXTERNAL_INTEGRATION,
    LARGE_CHANGE,
    UNKNOWN_IMPACT
}
```

---

# 39. Local model optimization

Here's the nice part.

Gemma doesn't need to see the entire VerificationGraph.

Wayang summarizes it:

```text
VERIFICATION SUMMARY

Requirements:
3

Verified:
3

Structural:
PASS

Targeted tests:
12/12 PASS

Regression:
PASS

Public API:
UNCHANGED

Unexpected changes:
NONE

Risk:
LOW

Decision:
COMPLETE
```

That's tiny.

---

# 40. Only invoke Gemma for ambiguous review

For example:

```text
Verification:
all tests pass

but:

Requirement:
"preserve backward compatibility"

API analysis:
unchanged

Gemma:
semantic compatibility review requested
```

Then provide only:

```text
public API before
public API after
changed behavior
relevant tests
```

Not the repository.

---

# 41. Review prompt

A local model could receive:

```text
TASK:
Fix refresh token expiration.

REQUIREMENTS:
R1 reject expired tokens
R2 accept valid tokens
R3 preserve public API

CHANGES:
TokenValidator.validateExpiration()
8 changed lines

EVIDENCE:
R1 test PASS
R2 test PASS
API unchanged
12 affected tests PASS

QUESTION:
Is there any requirement not adequately verified?
```

That's a manageable reasoning task.

---

# 42. Model output

Prefer structured:

```json
{
  "decision": "COMPLETE",
  "unverified": [],
  "concerns": []
}
```

Or:

```json
{
  "decision": "NEED_MORE_VERIFICATION",
  "unverified": [
    "null expiration behavior"
  ],
  "recommended_check": "add null expiration test"
}
```

Again:

**Gemma recommends. Wayang decides according to policy.**

---

# 43. Review loop

If Gemma says:

```text
null expiration behavior is unverified
```

Wayang can determine:

```text
there is already a test
```

or:

```text
there is no test
```

Then:

```text
NO TEST
 ↓
ChangePlanner
 ↓
add test
```

This is a powerful autonomous loop.

---

# 44. Verification-driven repair

Now the entire Aljabr loop becomes:

```text
Task
 ↓
Requirements
 ↓
Evidence
 ↓
Hypothesis
 ↓
ChangeIntent
 ↓
Patch
 ↓
Structural verification
 ↓
Behavioral verification
 ↓
Requirement verification
 ↓
 ┌─────────────┐
 │             │
PASS         FAIL
 │             │
 ▼             ▼
DONE       FailureAnalysis
               │
               ▼
           New Hypothesis
               │
               └──────────────→ Patch
```

That is the actual autonomous coding loop.

---

# 45. But add a repair budget

Never allow:

```text
patch
test
patch
test
patch
test
...
```

forever.

```java
public record RepairBudget(
    int maxIterations,
    Duration maxTaskDuration,
    int maxPatchAttempts,
    int maxRepeatedFailures
) {}
```

Example:

```text
maxIterations = 6
maxPatchAttempts = 4
```

---

# 46. Failure stagnation

Earlier we had failure fingerprints.

Now combine them:

```text
Attempt 1:
FP-A

Attempt 2:
FP-A

Attempt 3:
FP-A
```

This is:

```text
STAGNATION
```

Stop asking Gemma to repeat itself.

Instead:

```text
change strategy
```

or:

```text
ask user
```

---

# 47. Progress score

We can make this more intelligent.

```java
public record RepairProgress(
    int requirementsResolved,
    int failuresResolved,
    int newFailures,
    int changedFiles,
    double score
) {}
```

Example:

```text
Attempt 1:
1 failure → 0 failures

score +1.0

Attempt 2:
0 failures → 2 failures

score -1.5
```

If progress becomes negative:

```text
rollback
```

or:

```text
reconsider hypothesis
```

---

# 48. Don't only optimize for "tests pass"

A patch could make tests pass by:

```text
disabling functionality
weakening assertion
skipping test
catching exception
```

So add **test integrity verification**.

Check:

```text
were tests removed?
were assertions weakened?
were tests skipped?
was coverage materially reduced?
```

This is critical for autonomous coding.

---

# 49. `TestIntegrityVerifier`

```java
public interface TestIntegrityVerifier {

    Uni<VerificationResult> verify(
        ChangeSet changes,
        TestBaseline baseline
    );
}
```

Example:

```text
Before:
120 tests

After:
119 tests

Changed:
assertEquals → assertTrue

→ WARNING / FAIL
```

---

# 50. Test manipulation detection

Some patterns should trigger high risk:

```text
@Disabled
.skip
ignore
expected exception
catch (Exception) {}
assertTrue(true)
```

Don't necessarily reject automatically, but flag:

```text
TEST_INTEGRITY_RISK
```

---

# 51. This is where Aljabr becomes substantially safer than a simple coding agent

A basic agent:

```text
LLM
 ↓
edit
 ↓
test
 ↓
done
```

Aljabr:

```text
Requirement
 ↓
Evidence
 ↓
Hypothesis
 ↓
Minimal Change
 ↓
Precondition
 ↓
Transactional Patch
 ↓
Structural Verification
 ↓
Impact Verification
 ↓
Behavior Verification
 ↓
Regression Verification
 ↓
Requirement Verification
 ↓
Integrity Verification
 ↓
Completion Decision
```

The intelligence isn't only in Gemma.

**The runtime itself provides intelligence.**

---

# 52. Final architecture so far

We're approaching:

```text
                           ALJABR
                              │
               ┌──────────────┼──────────────┐
               │              │              │
          Intelligence      Control       Execution
               │              │              │
               ▼              ▼              ▼
        ┌─────────────┐  ┌────────────┐  ┌──────────────┐
        │ Context     │  │ Policies   │  │ Execution    │
        │ Evidence    │  │ Budgets    │  │ Runner       │
        │ Hypothesis  │  │ Capability │  │ Normalizer   │
        │ Reasoning   │  │ Risk       │  │ Classifier   │
        └──────┬──────┘  └────────────┘  └──────┬───────┘
               │                                │
               ▼                                ▼
        ┌─────────────┐                  ┌──────────────┐
        │ Workspace   │                  │ Verification │
        │ Intelligence│                  │ Engine       │
        │ Symbol      │                  │              │
        │ AST         │                  │ Verification │
        │ Impact      │                  │ Graph        │
        └──────┬──────┘                  └──────┬───────┘
               │                                │
               └────────────┬───────────────────┘
                            ▼
                     ┌─────────────┐
                     │   Patch     │
                     │   Engine    │
                     │             │
                     │ Transaction │
                     │ Overlay     │
                     │ Validation  │
                     └──────┬──────┘
                            │
                            ▼
                       Working Tree
```

And Gemma sits above this infrastructure as a **reasoning component**, not as the infrastructure itself.

---

# 53. The next milestone should be different

At this point we have most of the **coding-agent mechanics**.

The next major piece I would build is:

## `TaskState + Durable AgentLoop`

Because now Aljabr needs to survive:

```text
model failure
tool failure
connection loss
process crash
Mac sleep
partial patch
partial verification
context compaction
model switching
```

without starting over.

The architecture would become:

```text
Task
 ↓
Durable TaskState
 ↓
AgentLoop
 ↓
Checkpoint
 ↓
Reason
 ↓
Checkpoint
 ↓
Execute
 ↓
Checkpoint
 ↓
Patch
 ↓
Checkpoint
 ↓
Verify
 ↓
Checkpoint
 ↓
Complete
```

And this is where your earlier idea about **research/tool-output caching, thought/reasoning state, memory scopes, and avoiding repeated work after connection loss** can finally converge into one concrete implementation: **`DurableTaskState + CheckpointStore + EventLog + ResumeEngine`**.


