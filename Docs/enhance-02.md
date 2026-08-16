Absolutely. **Milestone 2 is the first real mutation path**: make Aljabr capable of safely changing a workspace through Wayang, then prove the change with verification.

The important thing is that we should **not jump straight to a generic "edit file" tool**. Build a controlled pipeline:

```text
CodingIntent
    │
    ▼
PatchOperation
    │
    ▼
PatchValidator
    │
    ▼
WorkspaceGuard
    │
    ▼
Wayang AgentDecision
    │
    ▼
Wayang ToolExecutor
    │
    ▼
Workspace mutation
    │
    ▼
ChangeRecord
    │
    ▼
Checkpoint
    │
    ▼
Verification
```

This is the first place where Aljabr can actually modify code.

---

# 1. Define the mutation contract

Start with a semantic operation:

```java
public record PatchOperation(
    String path,
    List<PatchEdit> edits,
    String reason
) {
    public PatchOperation {
        edits = List.copyOf(edits);
    }
}
```

And:

```java
public record PatchEdit(
    String expectedText,
    String replacementText
) {}
```

For example:

```json
{
  "path": "src/auth/TokenValidator.java",
  "edits": [
    {
      "expectedText": "expiresAt > now",
      "replacementText": "expiresAt >= now"
    }
  ],
  "reason": "Expiration at the exact boundary should be rejected"
}
```

This is much safer than asking the model for:

```json
{
  "shell": "sed -i ..."
}
```

---

# 2. Give every mutation an ID

Don't let a patch be anonymous.

```java
public record PatchOperation(
    String id,
    String path,
    List<PatchEdit> edits,
    String reason
) {}
```

Generate:

```text
patch-01
patch-02
...
```

or preferably a stable UUID.

Why?

Because later we want:

```text
plan step
    ↓
patch
    ↓
tool execution
    ↓
verification
```

to be traceable.

---

# 3. Add `ChangeSet`

A patch is the **requested mutation**.

A change set is what **actually happened**.

```java
public record ChangeSet(
    List<ChangeRecord> changes
) {
    public ChangeSet {
        changes = List.copyOf(changes);
    }
}
```

And:

```java
public record ChangeRecord(
    String path,
    ChangeType type,
    String beforeHash,
    String afterHash,
    String patchId,
    String stepId
) {}
```

```java
public enum ChangeType {
    CREATED,
    MODIFIED,
    DELETED
}
```

Now Aljabr can distinguish:

```text
requested patch
        ≠
actual workspace change
```

That distinction becomes very useful.

---

# 4. `PatchValidator`

This should run before anything reaches the workspace.

```java
public interface PatchValidator {

    PatchValidationResult validate(
        PatchOperation patch,
        CodingContext context
    );
}
```

Result:

```java
public record PatchValidationResult(
    boolean valid,
    List<String> errors
) {
    public static PatchValidationResult valid() {
        return new PatchValidationResult(
            true,
            List.of()
        );
    }

    public static PatchValidationResult invalid(
            String error) {

        return new PatchValidationResult(
            false,
            List.of(error)
        );
    }
}
```

---

# 5. First validation: path

```java
private void validatePath(
        PatchOperation patch,
        CodingContext context,
        List<String> errors) {

    Path path = Path.of(patch.path());

    if (path.isAbsolute()) {
        errors.add("Absolute paths are not allowed");
    }

    if (path.normalize()
            .startsWith("..")) {

        errors.add(
            "Path escapes workspace"
        );
    }

    if (!context.scope()
            .canWrite(path)) {

        errors.add(
            "Path is outside writable scope"
        );
    }
}
```

But importantly:

**This is not your security boundary.**

Wayang's sandbox/policy remains the final enforcement.

Aljabr is adding semantic protection.

---

# 6. Second validation: file state

Before patching:

```java
Path path = workspace.resolve(
    patch.path()
);
```

Read the current file.

Then each edit must satisfy:

```text
expectedText exists
expectedText occurs exactly once
```

For example:

```java
int matches =
    countOccurrences(
        currentContent,
        edit.expectedText()
    );

if (matches != 1) {
    errors.add(
        "Expected exactly one match, found "
        + matches
    );
}
```

This prevents accidental broad replacements.

---

# 7. Why this is essential

Imagine the model says:

```text
replace:
return true;
```

But there are 27 occurrences.

Never silently do:

```java
content.replace(
    "return true;",
    "return false;"
);
```

Instead:

```text
expected = 1
actual   = 27
```

→ reject.

The model must make the patch more precise.

---

# 8. Third validation: workspace freshness

Before applying:

```java
WorkspaceSnapshot snapshot =
    context.workspaceSnapshot();

WorkspaceCheck check =
    workspaceGuard.check(snapshot);
```

If:

```text
unchanged = false
```

then:

```java
return PatchValidationResult.invalid(
    "Workspace changed since inspection"
);
```

This prevents stale reasoning from becoming destructive mutation.

---

# 9. But don't immediately fail every workspace change

This needs nuance.

Suppose Aljabr inspected:

```text
Foo.java
Bar.java
```

and the user edits:

```text
README.md
```

A global workspace fingerprint changed, but the patch might still be safe.

So eventually the guard should distinguish:

```java
public record WorkspaceConflict(
    String path,
    ConflictType type
) {}
```

Then:

```text
user changed README.md
Aljabr modifies Foo.java
```

may be safe.

But:

```text
user changed Foo.java
Aljabr patches Foo.java
```

requires re-inspection or user intervention.

This is better than a simplistic global lock.

---

# 10. Introduce `WorkspaceGuard` at path granularity

```java
public interface WorkspaceGuard {

    WorkspaceCheck check(
        WorkspaceSnapshot baseline,
        Set<Path> affectedPaths
    );
}
```

Then:

```java
WorkspaceCheck check(
    baseline,
    Set.of(
        Path.of("src/auth/TokenValidator.java")
    )
);
```

Now you can detect:

```text
baseline Foo.java = A
current Foo.java  = B

→ conflict
```

while ignoring unrelated changes.

---

# 11. Patch application should be deterministic

Create a semantic service:

```java
public interface PatchApplier {

    Uni<AppliedPatch> apply(
        PatchOperation patch,
        CodingContext context
    );
}
```

Result:

```java
public record AppliedPatch(
    String patchId,
    ChangeSet changes,
    WorkspaceSnapshot resultingSnapshot
) {}
```

Notice:

**It doesn't return just `boolean`.**

We want evidence.

---

# 12. `PatchApplier` should not execute arbitrary commands

Implementation conceptually:

```java
public Uni<AppliedPatch> apply(
        PatchOperation patch,
        CodingContext context) {

    return Uni.createFrom().item(() -> {

        PatchValidationResult validation =
            validator.validate(
                patch,
                context
            );

        if (!validation.valid()) {
            throw new InvalidPatchException(
                validation.errors()
            );
        }

        return applyValidatedPatch(
            patch,
            context
        );
    });
}
```

But the actual filesystem operation should ideally be represented through Wayang's governed workspace tool.

So eventually:

```text
PatchApplier
   ↓
PatchOperation
   ↓
AgentDecision.ExecuteTool
   ↓
Wayang
   ↓
WorkspacePatchTool
```

rather than:

```text
PatchApplier
   ↓
Files.write(...)
```

inside Aljabr.

---

# 13. The tool itself can be generic

This is an important architectural distinction.

Wayang can expose:

```text
workspace.read
workspace.write
workspace.patch
workspace.diff
```

Those aren't Aljabr-specific.

Aljabr says:

```text
"I need this semantic patch."
```

Wayang says:

```text
"I know how to safely execute workspace.patch."
```

Then another agent could use the exact same tool.

That's good framework design.

---

# 14. `workspace.patch`

Conceptually:

```java
public record WorkspacePatchRequest(
    String path,
    List<PatchEdit> edits
) {}
```

Tool:

```java
@AgentTool(
    name = "workspace.patch"
)
public ToolResult patch(
        WorkspacePatchRequest request,
        ToolContext context) {

    // policy
    // sandbox
    // filesystem operation
    // audit
    // result
}
```

The actual implementation belongs to Wayang.

Aljabr should never need to know whether the workspace is:

```text
local filesystem
container
remote workspace
Git worktree
IDE workspace
```

---

# 15. Tool result needs structured evidence

Don't return:

```text
"Patch successful"
```

Return something like:

```java
public record PatchToolResult(
    String path,
    String beforeHash,
    String afterHash,
    int editsApplied,
    List<String> affectedLines
) {}
```

Then Aljabr can create:

```java
ChangeRecord
```

from actual evidence.

---

# 16. Make patch execution idempotent

This is extremely important for checkpoint/resume.

Imagine:

```text
patch applied
    ↓
process crashes
    ↓
checkpoint wasn't recorded
    ↓
resume
```

If Aljabr blindly applies the same patch again, it may fail or corrupt state.

Every mutation needs an idempotency key:

```java
public record MutationRequest(
    String idempotencyKey,
    ...
) {}
```

For example:

```text
executionId + patchId
```

The Wayang tool layer can remember:

```text
mutation-abc123
→ already applied
→ return previous result
```

This is much better than trying to infer whether the patch was previously applied.

---

# 17. This connects directly to your earlier cache work

This is **not ordinary research cache**.

It's an execution/mutation idempotency record:

```text
Research cache
    → "I already searched this"

Tool cache
    → "I already executed this deterministic operation"

Mutation idempotency
    → "I already applied this side effect"
```

Don't mix those concepts.

---

# 18. Update coding state after patch

Once the tool succeeds:

```java
CodingState next =
    state.recordChange(
        appliedPatch.changes()
    );
```

I would extend the state:

```java
public record CodingState(
    CodingPhase phase,
    CodingPlan plan,
    int currentStep,
    int repairAttempts,
    VerificationReport lastVerification,
    List<ChangeRecord> changes
) {}
```

Again:

```java
changes = List.copyOf(changes);
```

No mutable collection.

---

# 19. Then checkpoint immediately

The mutation sequence should be:

```text
validate patch
     ↓
execute patch
     ↓
obtain actual result
     ↓
update CodingState
     ↓
checkpoint
     ↓
continue
```

Not:

```text
execute patch
     ↓
continue
     ↓
checkpoint later
```

Because if the process dies after the mutation but before the checkpoint, resume becomes ambiguous.

---

# 20. This gives us a transaction-like boundary

Not a full database transaction.

Rather:

```text
SIDE EFFECT
    +
RECORDED RESULT
    +
CHECKPOINT
```

as close together as possible.

That's the pattern Aljabr should use for all important mutations.

---

# 21. Now implement targeted verification

Once patch succeeds:

```java
private AgentDecision afterPatch(
        CodingContext context,
        AppliedPatch patch) {

    VerificationRequirements requirements =
        currentStep(context)
            .verificationRequirements();

    return AgentDecision.executeTool(
        "verification.run",
        VerificationRequest.from(
            requirements,
            patch.changes()
        )
    );
}
```

Again:

**Wayang executes the verification tool.**

---

# 22. Verification should understand changed files

Suppose only:

```text
src/auth/TokenValidator.java
```

changed.

Don't immediately run the entire universe.

Build:

```java
VerificationRequest(
    changedPaths,
    requiredChecks,
    relatedTests
)
```

Example:

```text
changed:
TokenValidator.java

likely tests:
TokenValidatorTest.java

verification:
compile
TokenValidatorTest
```

This makes Aljabr much faster.

---

# 23. But don't let "related tests" become hallucinated

The model can suggest:

```text
TokenValidatorTest
```

but Wayang should verify that the test exists.

Use repository evidence:

```text
search
references
test discovery
build metadata
```

Then the verification engine determines what can actually run.

---

# 24. Verification report

Make it detailed enough for repair:

```java
public record VerificationReport(
    VerificationStatus status,
    List<VerificationCheck> checks,
    List<VerificationFailure> failures,
    Duration duration
) {}
```

```java
public record VerificationCheck(
    String name,
    VerificationStatus status,
    String output
) {}
```

```java
public record VerificationFailure(
    String check,
    String type,
    String message,
    List<String> locations
) {}
```

Now the repair model gets useful information.

---

# 25. Normalize failures

Raw compiler output is often noisy.

Create:

```java
public interface FailureNormalizer {

    List<NormalizedFailure> normalize(
        VerificationReport report
    );
}
```

Example:

```text
raw:
TokenValidator.java:84:
bad operand types for binary operator '>'

normalized:
type = COMPILATION
file = TokenValidator.java
line = 84
message = incompatible comparison
```

This is the foundation for the repair fingerprint we discussed earlier.

---

# 26. Then the first real repair decision

```java
private AgentDecision handleVerification(
        CodingContext context,
        VerificationReport report) {

    if (report.passed()) {

        updatePhase(
            context,
            CodingPhase.REVIEWING
        );

        return requestReview(context);
    }

    if (repairBudgetExceeded(context)) {
        return fail(
            "Verification failed and repair budget exceeded"
        );
    }

    if (noProgressDetected(context, report)) {
        return replan(context);
    }

    updatePhase(
        context,
        CodingPhase.REPAIRING
    );

    return requestRepair(
        context,
        report
    );
}
```

This is already a real control loop.

---

# 27. Review should be independent of the implementation model

This is another important design decision.

Don't use:

```text
same LLM
same context
"Did I do a good job?"
```

Instead provide the reviewer:

```text
original request
+
approved plan
+
actual diff
+
verification report
```

Then ask:

```text
Does the resulting change satisfy the requested objective?
```

The reviewer shouldn't simply trust the implementation model's narrative.

---

# 28. Final review gate

```java
public record ReviewReport(
    ReviewStatus status,
    List<ReviewFinding> findings
) {}
```

Potential findings:

```text
UNRELATED_CHANGE
MISSING_TEST
SCOPE_VIOLATION
INCOMPLETE_IMPLEMENTATION
REGRESSION_RISK
STYLE_ISSUE
```

Not all findings need to block completion.

Give them severity:

```java
public enum FindingSeverity {
    INFO,
    WARNING,
    BLOCKING
}
```

Completion requires:

```text
no BLOCKING findings
+
verification passed
+
workspace safe
```

---

# 29. Now the entire first coding milestone is real

```text
USER REQUEST
     │
     ▼
INSPECT
     │
     ▼
PLAN
     │
     ▼
EXECUTE
     │
     ▼
CodingIntent
     │
     ▼
PatchOperation
     │
     ▼
PatchValidator
     │
     ▼
WorkspaceGuard
     │
     ▼
Wayang workspace.patch
     │
     ▼
ChangeRecord
     │
     ▼
CHECKPOINT
     │
     ▼
VERIFY
     │
 ┌───┴────┐
 │        │
PASS     FAIL
 │        │
 ▼        ▼
REVIEW   REPAIR
 │        │
 ▼        ▼
DONE    PATCH
           │
           └────→ VERIFY
```

That's the milestone I'd consider the **first serious Aljabr implementation**.

---

# 30. What to actually code next

I would now create these concrete classes/interfaces:

```text
aljabr/
├── coding/
│   ├── CodingIntent.java
│   ├── CodingAction.java
│   └── CodingContext.java
│
├── patch/
│   ├── PatchOperation.java
│   ├── PatchEdit.java
│   ├── PatchValidator.java
│   ├── PatchValidationResult.java
│   └── PatchApplier.java
│
├── workspace/
│   ├── WorkspaceSnapshot.java
│   ├── WorkspaceGuard.java
│   ├── WorkspaceCheck.java
│   └── WorkspaceConflict.java
│
├── change/
│   ├── ChangeSet.java
│   ├── ChangeRecord.java
│   └── ChangeType.java
│
├── verification/
│   ├── VerificationEngine.java
│   ├── VerificationRequest.java
│   ├── VerificationReport.java
│   ├── VerificationCheck.java
│   └── VerificationFailure.java
│
└── review/
    ├── ReviewEngine.java
    ├── ReviewReport.java
    └── ReviewFinding.java
```

But **only some of these should actually live in Aljabr**.

The boundary should ultimately be:

```text
WAYANG
──────────────────────────────
workspace.patch
workspace.read
workspace.diff
verification.run
sandbox
tool execution
policy
approval
checkpoint
idempotency
audit

ALJABR
──────────────────────────────
CodingIntent
CodingPlan
Patch semantics
Workspace reasoning
CodingState
Repair strategy
Review strategy
Completion policy
```

That distinction is more important than the exact package names.

---

## The next milestone after this

Once this mutation/verification path is implemented, I would **not immediately add more coding features**.

The next milestone should be **Aljabr's "recovery brain"**:

```text
failure
   ↓
normalize
   ↓
fingerprint
   ↓
classify
   ↓
choose:
   ├── retry
   ├── repair
   ├── re-inspect
   ├── re-plan
   ├── ask user
   └── fail
```

That is where Aljabr starts becoming substantially more capable than a simple ReAct coding agent—and it can be built entirely on top of the Wayang runtime we've now established.


Absolutely. **Milestone 2 is the first real mutation path**: make Aljabr capable of safely changing a workspace through Wayang, then prove the change with verification.

The important thing is that we should **not jump straight to a generic "edit file" tool**. Build a controlled pipeline:

```text
CodingIntent
    │
    ▼
PatchOperation
    │
    ▼
PatchValidator
    │
    ▼
WorkspaceGuard
    │
    ▼
Wayang AgentDecision
    │
    ▼
Wayang ToolExecutor
    │
    ▼
Workspace mutation
    │
    ▼
ChangeRecord
    │
    ▼
Checkpoint
    │
    ▼
Verification
```

This is the first place where Aljabr can actually modify code.

---

# 1. Define the mutation contract

Start with a semantic operation:

```java
public record PatchOperation(
    String path,
    List<PatchEdit> edits,
    String reason
) {
    public PatchOperation {
        edits = List.copyOf(edits);
    }
}
```

And:

```java
public record PatchEdit(
    String expectedText,
    String replacementText
) {}
```

For example:

```json
{
  "path": "src/auth/TokenValidator.java",
  "edits": [
    {
      "expectedText": "expiresAt > now",
      "replacementText": "expiresAt >= now"
    }
  ],
  "reason": "Expiration at the exact boundary should be rejected"
}
```

This is much safer than asking the model for:

```json
{
  "shell": "sed -i ..."
}
```

---

# 2. Give every mutation an ID

Don't let a patch be anonymous.

```java
public record PatchOperation(
    String id,
    String path,
    List<PatchEdit> edits,
    String reason
) {}
```

Generate:

```text
patch-01
patch-02
...
```

or preferably a stable UUID.

Why?

Because later we want:

```text
plan step
    ↓
patch
    ↓
tool execution
    ↓
verification
```

to be traceable.

---

# 3. Add `ChangeSet`

A patch is the **requested mutation**.

A change set is what **actually happened**.

```java
public record ChangeSet(
    List<ChangeRecord> changes
) {
    public ChangeSet {
        changes = List.copyOf(changes);
    }
}
```

And:

```java
public record ChangeRecord(
    String path,
    ChangeType type,
    String beforeHash,
    String afterHash,
    String patchId,
    String stepId
) {}
```

```java
public enum ChangeType {
    CREATED,
    MODIFIED,
    DELETED
}
```

Now Aljabr can distinguish:

```text
requested patch
        ≠
actual workspace change
```

That distinction becomes very useful.

---

# 4. `PatchValidator`

This should run before anything reaches the workspace.

```java
public interface PatchValidator {

    PatchValidationResult validate(
        PatchOperation patch,
        CodingContext context
    );
}
```

Result:

```java
public record PatchValidationResult(
    boolean valid,
    List<String> errors
) {
    public static PatchValidationResult valid() {
        return new PatchValidationResult(
            true,
            List.of()
        );
    }

    public static PatchValidationResult invalid(
            String error) {

        return new PatchValidationResult(
            false,
            List.of(error)
        );
    }
}
```

---

# 5. First validation: path

```java
private void validatePath(
        PatchOperation patch,
        CodingContext context,
        List<String> errors) {

    Path path = Path.of(patch.path());

    if (path.isAbsolute()) {
        errors.add("Absolute paths are not allowed");
    }

    if (path.normalize()
            .startsWith("..")) {

        errors.add(
            "Path escapes workspace"
        );
    }

    if (!context.scope()
            .canWrite(path)) {

        errors.add(
            "Path is outside writable scope"
        );
    }
}
```

But importantly:

**This is not your security boundary.**

Wayang's sandbox/policy remains the final enforcement.

Aljabr is adding semantic protection.

---

# 6. Second validation: file state

Before patching:

```java
Path path = workspace.resolve(
    patch.path()
);
```

Read the current file.

Then each edit must satisfy:

```text
expectedText exists
expectedText occurs exactly once
```

For example:

```java
int matches =
    countOccurrences(
        currentContent,
        edit.expectedText()
    );

if (matches != 1) {
    errors.add(
        "Expected exactly one match, found "
        + matches
    );
}
```

This prevents accidental broad replacements.

---

# 7. Why this is essential

Imagine the model says:

```text
replace:
return true;
```

But there are 27 occurrences.

Never silently do:

```java
content.replace(
    "return true;",
    "return false;"
);
```

Instead:

```text
expected = 1
actual   = 27
```

→ reject.

The model must make the patch more precise.

---

# 8. Third validation: workspace freshness

Before applying:

```java
WorkspaceSnapshot snapshot =
    context.workspaceSnapshot();

WorkspaceCheck check =
    workspaceGuard.check(snapshot);
```

If:

```text
unchanged = false
```

then:

```java
return PatchValidationResult.invalid(
    "Workspace changed since inspection"
);
```

This prevents stale reasoning from becoming destructive mutation.

---

# 9. But don't immediately fail every workspace change

This needs nuance.

Suppose Aljabr inspected:

```text
Foo.java
Bar.java
```

and the user edits:

```text
README.md
```

A global workspace fingerprint changed, but the patch might still be safe.

So eventually the guard should distinguish:

```java
public record WorkspaceConflict(
    String path,
    ConflictType type
) {}
```

Then:

```text
user changed README.md
Aljabr modifies Foo.java
```

may be safe.

But:

```text
user changed Foo.java
Aljabr patches Foo.java
```

requires re-inspection or user intervention.

This is better than a simplistic global lock.

---

# 10. Introduce `WorkspaceGuard` at path granularity

```java
public interface WorkspaceGuard {

    WorkspaceCheck check(
        WorkspaceSnapshot baseline,
        Set<Path> affectedPaths
    );
}
```

Then:

```java
WorkspaceCheck check(
    baseline,
    Set.of(
        Path.of("src/auth/TokenValidator.java")
    )
);
```

Now you can detect:

```text
baseline Foo.java = A
current Foo.java  = B

→ conflict
```

while ignoring unrelated changes.

---

# 11. Patch application should be deterministic

Create a semantic service:

```java
public interface PatchApplier {

    Uni<AppliedPatch> apply(
        PatchOperation patch,
        CodingContext context
    );
}
```

Result:

```java
public record AppliedPatch(
    String patchId,
    ChangeSet changes,
    WorkspaceSnapshot resultingSnapshot
) {}
```

Notice:

**It doesn't return just `boolean`.**

We want evidence.

---

# 12. `PatchApplier` should not execute arbitrary commands

Implementation conceptually:

```java
public Uni<AppliedPatch> apply(
        PatchOperation patch,
        CodingContext context) {

    return Uni.createFrom().item(() -> {

        PatchValidationResult validation =
            validator.validate(
                patch,
                context
            );

        if (!validation.valid()) {
            throw new InvalidPatchException(
                validation.errors()
            );
        }

        return applyValidatedPatch(
            patch,
            context
        );
    });
}
```

But the actual filesystem operation should ideally be represented through Wayang's governed workspace tool.

So eventually:

```text
PatchApplier
   ↓
PatchOperation
   ↓
AgentDecision.ExecuteTool
   ↓
Wayang
   ↓
WorkspacePatchTool
```

rather than:

```text
PatchApplier
   ↓
Files.write(...)
```

inside Aljabr.

---

# 13. The tool itself can be generic

This is an important architectural distinction.

Wayang can expose:

```text
workspace.read
workspace.write
workspace.patch
workspace.diff
```

Those aren't Aljabr-specific.

Aljabr says:

```text
"I need this semantic patch."
```

Wayang says:

```text
"I know how to safely execute workspace.patch."
```

Then another agent could use the exact same tool.

That's good framework design.

---

# 14. `workspace.patch`

Conceptually:

```java
public record WorkspacePatchRequest(
    String path,
    List<PatchEdit> edits
) {}
```

Tool:

```java
@AgentTool(
    name = "workspace.patch"
)
public ToolResult patch(
        WorkspacePatchRequest request,
        ToolContext context) {

    // policy
    // sandbox
    // filesystem operation
    // audit
    // result
}
```

The actual implementation belongs to Wayang.

Aljabr should never need to know whether the workspace is:

```text
local filesystem
container
remote workspace
Git worktree
IDE workspace
```

---

# 15. Tool result needs structured evidence

Don't return:

```text
"Patch successful"
```

Return something like:

```java
public record PatchToolResult(
    String path,
    String beforeHash,
    String afterHash,
    int editsApplied,
    List<String> affectedLines
) {}
```

Then Aljabr can create:

```java
ChangeRecord
```

from actual evidence.

---

# 16. Make patch execution idempotent

This is extremely important for checkpoint/resume.

Imagine:

```text
patch applied
    ↓
process crashes
    ↓
checkpoint wasn't recorded
    ↓
resume
```

If Aljabr blindly applies the same patch again, it may fail or corrupt state.

Every mutation needs an idempotency key:

```java
public record MutationRequest(
    String idempotencyKey,
    ...
) {}
```

For example:

```text
executionId + patchId
```

The Wayang tool layer can remember:

```text
mutation-abc123
→ already applied
→ return previous result
```

This is much better than trying to infer whether the patch was previously applied.

---

# 17. This connects directly to your earlier cache work

This is **not ordinary research cache**.

It's an execution/mutation idempotency record:

```text
Research cache
    → "I already searched this"

Tool cache
    → "I already executed this deterministic operation"

Mutation idempotency
    → "I already applied this side effect"
```

Don't mix those concepts.

---

# 18. Update coding state after patch

Once the tool succeeds:

```java
CodingState next =
    state.recordChange(
        appliedPatch.changes()
    );
```

I would extend the state:

```java
public record CodingState(
    CodingPhase phase,
    CodingPlan plan,
    int currentStep,
    int repairAttempts,
    VerificationReport lastVerification,
    List<ChangeRecord> changes
) {}
```

Again:

```java
changes = List.copyOf(changes);
```

No mutable collection.

---

# 19. Then checkpoint immediately

The mutation sequence should be:

```text
validate patch
     ↓
execute patch
     ↓
obtain actual result
     ↓
update CodingState
     ↓
checkpoint
     ↓
continue
```

Not:

```text
execute patch
     ↓
continue
     ↓
checkpoint later
```

Because if the process dies after the mutation but before the checkpoint, resume becomes ambiguous.

---

# 20. This gives us a transaction-like boundary

Not a full database transaction.

Rather:

```text
SIDE EFFECT
    +
RECORDED RESULT
    +
CHECKPOINT
```

as close together as possible.

That's the pattern Aljabr should use for all important mutations.

---

# 21. Now implement targeted verification

Once patch succeeds:

```java
private AgentDecision afterPatch(
        CodingContext context,
        AppliedPatch patch) {

    VerificationRequirements requirements =
        currentStep(context)
            .verificationRequirements();

    return AgentDecision.executeTool(
        "verification.run",
        VerificationRequest.from(
            requirements,
            patch.changes()
        )
    );
}
```

Again:

**Wayang executes the verification tool.**

---

# 22. Verification should understand changed files

Suppose only:

```text
src/auth/TokenValidator.java
```

changed.

Don't immediately run the entire universe.

Build:

```java
VerificationRequest(
    changedPaths,
    requiredChecks,
    relatedTests
)
```

Example:

```text
changed:
TokenValidator.java

likely tests:
TokenValidatorTest.java

verification:
compile
TokenValidatorTest
```

This makes Aljabr much faster.

---

# 23. But don't let "related tests" become hallucinated

The model can suggest:

```text
TokenValidatorTest
```

but Wayang should verify that the test exists.

Use repository evidence:

```text
search
references
test discovery
build metadata
```

Then the verification engine determines what can actually run.

---

# 24. Verification report

Make it detailed enough for repair:

```java
public record VerificationReport(
    VerificationStatus status,
    List<VerificationCheck> checks,
    List<VerificationFailure> failures,
    Duration duration
) {}
```

```java
public record VerificationCheck(
    String name,
    VerificationStatus status,
    String output
) {}
```

```java
public record VerificationFailure(
    String check,
    String type,
    String message,
    List<String> locations
) {}
```

Now the repair model gets useful information.

---

# 25. Normalize failures

Raw compiler output is often noisy.

Create:

```java
public interface FailureNormalizer {

    List<NormalizedFailure> normalize(
        VerificationReport report
    );
}
```

Example:

```text
raw:
TokenValidator.java:84:
bad operand types for binary operator '>'

normalized:
type = COMPILATION
file = TokenValidator.java
line = 84
message = incompatible comparison
```

This is the foundation for the repair fingerprint we discussed earlier.

---

# 26. Then the first real repair decision

```java
private AgentDecision handleVerification(
        CodingContext context,
        VerificationReport report) {

    if (report.passed()) {

        updatePhase(
            context,
            CodingPhase.REVIEWING
        );

        return requestReview(context);
    }

    if (repairBudgetExceeded(context)) {
        return fail(
            "Verification failed and repair budget exceeded"
        );
    }

    if (noProgressDetected(context, report)) {
        return replan(context);
    }

    updatePhase(
        context,
        CodingPhase.REPAIRING
    );

    return requestRepair(
        context,
        report
    );
}
```

This is already a real control loop.

---

# 27. Review should be independent of the implementation model

This is another important design decision.

Don't use:

```text
same LLM
same context
"Did I do a good job?"
```

Instead provide the reviewer:

```text
original request
+
approved plan
+
actual diff
+
verification report
```

Then ask:

```text
Does the resulting change satisfy the requested objective?
```

The reviewer shouldn't simply trust the implementation model's narrative.

---

# 28. Final review gate

```java
public record ReviewReport(
    ReviewStatus status,
    List<ReviewFinding> findings
) {}
```

Potential findings:

```text
UNRELATED_CHANGE
MISSING_TEST
SCOPE_VIOLATION
INCOMPLETE_IMPLEMENTATION
REGRESSION_RISK
STYLE_ISSUE
```

Not all findings need to block completion.

Give them severity:

```java
public enum FindingSeverity {
    INFO,
    WARNING,
    BLOCKING
}
```

Completion requires:

```text
no BLOCKING findings
+
verification passed
+
workspace safe
```

---

# 29. Now the entire first coding milestone is real

```text
USER REQUEST
     │
     ▼
INSPECT
     │
     ▼
PLAN
     │
     ▼
EXECUTE
     │
     ▼
CodingIntent
     │
     ▼
PatchOperation
     │
     ▼
PatchValidator
     │
     ▼
WorkspaceGuard
     │
     ▼
Wayang workspace.patch
     │
     ▼
ChangeRecord
     │
     ▼
CHECKPOINT
     │
     ▼
VERIFY
     │
 ┌───┴────┐
 │        │
PASS     FAIL
 │        │
 ▼        ▼
REVIEW   REPAIR
 │        │
 ▼        ▼
DONE    PATCH
           │
           └────→ VERIFY
```

That's the milestone I'd consider the **first serious Aljabr implementation**.

---

# 30. What to actually code next

I would now create these concrete classes/interfaces:

```text
aljabr/
├── coding/
│   ├── CodingIntent.java
│   ├── CodingAction.java
│   └── CodingContext.java
│
├── patch/
│   ├── PatchOperation.java
│   ├── PatchEdit.java
│   ├── PatchValidator.java
│   ├── PatchValidationResult.java
│   └── PatchApplier.java
│
├── workspace/
│   ├── WorkspaceSnapshot.java
│   ├── WorkspaceGuard.java
│   ├── WorkspaceCheck.java
│   └── WorkspaceConflict.java
│
├── change/
│   ├── ChangeSet.java
│   ├── ChangeRecord.java
│   └── ChangeType.java
│
├── verification/
│   ├── VerificationEngine.java
│   ├── VerificationRequest.java
│   ├── VerificationReport.java
│   ├── VerificationCheck.java
│   └── VerificationFailure.java
│
└── review/
    ├── ReviewEngine.java
    ├── ReviewReport.java
    └── ReviewFinding.java
```

But **only some of these should actually live in Aljabr**.

The boundary should ultimately be:

```text
WAYANG
──────────────────────────────
workspace.patch
workspace.read
workspace.diff
verification.run
sandbox
tool execution
policy
approval
checkpoint
idempotency
audit

ALJABR
──────────────────────────────
CodingIntent
CodingPlan
Patch semantics
Workspace reasoning
CodingState
Repair strategy
Review strategy
Completion policy
```

That distinction is more important than the exact package names.

---

## The next milestone after this

Once this mutation/verification path is implemented, I would **not immediately add more coding features**.

The next milestone should be **Aljabr's "recovery brain"**:

```text
failure
   ↓
normalize
   ↓
fingerprint
   ↓
classify
   ↓
choose:
   ├── retry
   ├── repair
   ├── re-inspect
   ├── re-plan
   ├── ask user
   └── fail
```

That is where Aljabr starts becoming substantially more capable than a simple ReAct coding agent—and it can be built entirely on top of the Wayang runtime we've now established.


Yes. The next milestone should be **Aljabr Recovery & Repair**.

Up to now we have:

```text
inspect → plan → patch → verify → review
```

Now we make the agent capable of answering the harder question:

> **"Verification failed. What should I do next?"**

The important design is to avoid the classic:

```text
test fails
  ↓
ask LLM to fix
  ↓
test fails
  ↓
ask LLM again
```

Instead:

```text
failure
  ↓
normalize
  ↓
fingerprint
  ↓
classify
  ↓
measure progress
  ↓
choose recovery action
  ├── retry
  ├── repair
  ├── re-inspect
  ├── re-plan
  ├── ask user
  └── fail
```

---

# 1. Start with `Failure`

Don't let the repair system consume raw compiler/test output.

Create a normalized representation:

```java
public record CodingFailure(
    String id,
    FailureCategory category,
    String message,
    List<FailureLocation> locations,
    FailureFingerprint fingerprint,
    FailureEvidence evidence
) {}
```

Location:

```java
public record FailureLocation(
    String path,
    Integer line,
    Integer column
) {}
```

Evidence:

```java
public record FailureEvidence(
    String source,
    String rawOutput,
    String command,
    Integer exitCode
) {}
```

The important thing is that repair works from **structured failure**, not giant logs.

---

# 2. Failure categories

Keep the first taxonomy small:

```java
public enum FailureCategory {

    COMPILATION,

    TEST_FAILURE,

    STATIC_ANALYSIS,

    LINT,

    PATCH_CONFLICT,

    WORKSPACE_CONFLICT,

    SCOPE_VIOLATION,

    TOOL_FAILURE,

    TIMEOUT,

    ENVIRONMENT_FAILURE,

    UNKNOWN
}
```

Don't create 50 categories yet.

Classification can become richer later.

---

# 3. `FailureFingerprint`

This is the key to detecting loops.

```java
public record FailureFingerprint(
    FailureCategory category,
    String normalizedMessage,
    List<String> locations
) {}
```

Then normalize:

```text
TokenValidator.java:84:
bad operand types for binary operator '>'

```

into something approximately like:

```text
category:
COMPILATION

message:
bad operand types for binary operator

location:
TokenValidator.java:84
```

So dynamic details don't create a different fingerprint every time.

---

# 4. Don't hash the entire error output

This is a common mistake.

Bad:

```java
hash(rawCompilerOutput)
```

because timestamps, paths, IDs, and surrounding logs can change.

Instead:

```text
raw output
   ↓
strip volatile information
   ↓
normalize whitespace
   ↓
normalize paths
   ↓
extract message/location
   ↓
fingerprint
```

Then hash the normalized structure if you want a compact identifier.

---

# 5. Add `FailureHistory`

Now persist repair history in `CodingState`.

```java
public record FailureHistory(
    List<FailureObservation> observations
) {
    public FailureHistory {
        observations = List.copyOf(observations);
    }
}
```

And:

```java
public record FailureObservation(
    FailureFingerprint fingerprint,
    String workspaceFingerprint,
    String patchId,
    int attempt
) {}
```

Now we can answer:

> Have I actually made progress?

---

# 6. Progress is not simply "different error"

Suppose:

```text
attempt 1:
10 failing tests

attempt 2:
8 failing tests
```

That's progress.

But:

```text
attempt 1:
TokenValidatorTest fails

attempt 2:
TokenValidatorTest fails
```

probably isn't.

Create:

```java
public record ProgressMeasurement(
    boolean improved,
    boolean unchanged,
    boolean regressed,
    String explanation
) {}
```

---

# 7. First simple progress algorithm

```java
public ProgressMeasurement compare(
        VerificationReport previous,
        VerificationReport current) {

    int oldFailures =
        previous.failures().size();

    int newFailures =
        current.failures().size();

    if (newFailures < oldFailures) {
        return new ProgressMeasurement(
            true,
            false,
            false,
            "Number of failures decreased"
        );
    }

    if (newFailures > oldFailures) {
        return new ProgressMeasurement(
            false,
            false,
            true,
            "Number of failures increased"
        );
    }

    if (sameFingerprints(previous, current)) {
        return new ProgressMeasurement(
            false,
            true,
            false,
            "Same failures remain"
        );
    }

    return new ProgressMeasurement(
        false,
        false,
        false,
        "Failure changed"
    );
}
```

This is intentionally conservative.

Later we can add weighted scoring.

---

# 8. `RecoveryAction`

Now the actual decision vocabulary:

```java
public enum RecoveryAction {

    RETRY,

    REPAIR,

    REINSPECT,

    REPLAN,

    ASK_USER,

    FAIL
}
```

This is one of the most important Aljabr enums.

---

# 9. `RecoveryDecision`

```java
public record RecoveryDecision(
    RecoveryAction action,
    String reason,
    Optional<CodingFailure> failure
) {}
```

Now the strategy doesn't directly decide from raw error strings.

It asks:

```java
RecoveryDecision decision =
    recoveryController.decide(
        context,
        failure
    );
```

---

# 10. `RecoveryController`

```java
@ApplicationScoped
public final class RecoveryController {

    private final FailureClassifier classifier;
    private final ProgressAnalyzer progress;
    private final RecoveryPolicy policy;

    public RecoveryDecision decide(
            CodingContext context,
            VerificationReport report) {

        List<CodingFailure> failures =
            classifier.classify(report);

        ProgressMeasurement measurement =
            progress.measure(
                context,
                report
            );

        return policy.choose(
            context,
            failures,
            measurement
        );
    }
}
```

This is much cleaner than putting everything inside `AljabrStrategy`.

---

# 11. The policy should be deterministic first

Start with explicit rules.

```java
public RecoveryDecision choose(
        CodingContext context,
        List<CodingFailure> failures,
        ProgressMeasurement progress) {

    if (failures.isEmpty()) {
        return new RecoveryDecision(
            RecoveryAction.RETRY,
            "No classified failure",
            Optional.empty()
        );
    }

    CodingFailure failure =
        failures.getFirst();

    if (failure.category()
            == FailureCategory.WORKSPACE_CONFLICT) {

        return decision(
            RecoveryAction.ASK_USER,
            "Workspace changed externally"
        );
    }

    if (failure.category()
            == FailureCategory.SCOPE_VIOLATION) {

        return decision(
            RecoveryAction.FAIL,
            "Coding scope violation"
        );
    }

    if (progress.unchanged()) {
        return decision(
            RecoveryAction.REPLAN,
            "No progress after repair"
        );
    }

    if (failure.category()
            == FailureCategory.COMPILATION) {

        return decision(
            RecoveryAction.REPAIR,
            "Compilation failure is locally repairable"
        );
    }

    if (failure.category()
            == FailureCategory.TEST_FAILURE) {

        return decision(
            RecoveryAction.REPAIR,
            "Test failure may be repairable"
        );
    }

    return decision(
        RecoveryAction.REINSPECT,
        "Failure requires additional repository evidence"
    );
}
```

This is an excellent place to **not use an LLM** initially.

---

# 12. Why deterministic recovery is valuable

Suppose the LLM sees:

```text
workspace conflict
```

and decides:

> "I'll overwrite the user's file."

No.

The runtime says:

```text
WORKSPACE_CONFLICT
→ ASK_USER
```

Likewise:

```text
scope violation
→ FAIL
```

The model never gets to override these policies.

---

# 13. Repair is where the LLM comes back

If recovery chooses:

```text
REPAIR
```

then:

```text
RecoveryController
      ↓
REPAIR
      ↓
build repair context
      ↓
model
      ↓
CodingIntent
      ↓
PatchValidator
      ↓
WorkspaceGuard
      ↓
patch
      ↓
verify
```

The repair model gets:

```text
original objective
+
current plan step
+
actual diff
+
verification report
+
normalized failures
+
failure history
```

Not the entire conversation.

---

# 14. Build a `RepairContext`

```java
public record RepairContext(
    String objective,
    PlanStep step,
    List<ChangeRecord> changes,
    VerificationReport verification,
    List<CodingFailure> failures,
    FailureHistory history
) {}
```

This becomes the model's repair input.

---

# 15. Repair prompt should explicitly constrain behavior

Conceptually:

```text
You are repairing an implementation.

Original objective:
...

Current plan step:
...

Actual changes:
...

Verification failures:
...

Previous repair attempts:
...

Rules:

1. Do not undo unrelated user changes.
2. Do not modify files outside the current scope.
3. Do not assume tests pass.
4. Make the smallest change that addresses the failure.
5. If the current approach is fundamentally wrong, request REPLAN.
6. Return a structured coding intent.
```

Notice the final rule:

> If the approach is fundamentally wrong, request REPLAN.

This prevents endless patching.

---

# 16. Add `REPLAN` as a first-class operation

This is important.

Suppose the plan says:

```text
modify TokenValidator
```

but inspection reveals the actual behavior comes from:

```text
AuthPolicy
```

The agent shouldn't keep patching `TokenValidator`.

Instead:

```text
REPAIR
   ↓
evidence contradicts plan
   ↓
REPLAN
```

---

# 17. `ReplanReason`

```java
public enum ReplanReason {

    PLAN_ASSUMPTION_INVALID,

    REQUIRED_FILE_NOT_FOUND,

    DEPENDENCY_DIFFERENT_THAN_EXPECTED,

    TEST_EXPECTATION_CHANGED,

    ARCHITECTURE_MISMATCH,

    REPEATED_FAILURE,

    USER_CHANGE_CONFLICT
}
```

Then:

```java
public record ReplanRequest(
    ReplanReason reason,
    String explanation,
    List<String> affectedAreas
) {}
```

---

# 18. Replanning should not restart from zero

This is a major design point.

Bad:

```text
REPLAN
 ↓
forget everything
 ↓
inspect repository from scratch
```

Instead:

```text
existing research
existing observations
existing valid plan steps
existing changes
existing failures
        ↓
    invalidate
        ↓
new plan
```

So the plan should have versioning.

---

# 19. Version the plan

```java
public record CodingPlan(
    String id,
    int version,
    String objective,
    List<PlanStep> steps
) {}
```

Example:

```text
plan v1
  step 1 ✓
  step 2 ✗

REPLAN

plan v2
  step 1 ✓
  step 2 revised
  step 3 added
```

Now history remains understandable.

---

# 20. Don't throw away the old plan

Record:

```java
public record PlanRevision(
    int version,
    String reason,
    CodingPlan plan
) {}
```

Then:

```text
Plan v1
   ↓
failed assumption
   ↓
Plan v2
```

This will later make debugging the agent much easier.

---

# 21. Repair budget

You need multiple budgets.

Not just:

```text
maxRetries = 5
```

Use:

```java
public record CodingBudget(
    int maxRepairAttempts,
    int maxReplans,
    Duration maxExecutionTime,
    int maxToolCalls,
    long maxModelTokens
) {}
```

And track actual usage:

```java
public record BudgetUsage(
    int repairAttempts,
    int replans,
    Duration executionTime,
    int toolCalls,
    long modelTokens
) {}
```

---

# 22. Budget decisions should be runtime-owned

For example:

```java
if (usage.repairAttempts()
        >= budget.maxRepairAttempts()) {

    return FAIL;
}
```

Don't ask the model:

> "Do you think we have enough budget?"

The runtime knows.

---

# 23. Add escalation levels

A nice improvement is:

```java
public enum RecoveryLevel {

    LOCAL_REPAIR,

    BROADER_REPAIR,

    REINSPECTION,

    REPLAN,

    USER_ESCALATION
}
```

Then failures can escalate.

Example:

```text
test fails
 ↓
LOCAL_REPAIR
 ↓
same failure
 ↓
BROADER_REPAIR
 ↓
still fails
 ↓
REINSPECTION
 ↓
still invalid
 ↓
REPLAN
 ↓
still impossible
 ↓
ASK_USER
```

This creates controlled escalation instead of chaos.

---

# 24. Local repair vs broader repair

This distinction is useful.

### Local repair

Only touch:

```text
current file
current method
current test
```

### Broader repair

May inspect:

```text
related classes
interfaces
callers
tests
configuration
```

The runtime can tell the model:

```text
repair scope = local
```

before escalating.

---

# 25. `RepairScope`

```java
public enum RepairScope {

    CURRENT_CHANGE,

    CURRENT_FILE,

    RELATED_FILES,

    PROJECT
}
```

And:

```java
public record RecoveryBudget(
    RepairScope scope,
    int remainingAttempts
) {}
```

This helps prevent the classic agent behavior:

> Fix a one-line bug → rewrite half the repository.

---

# 26. Detect scope expansion

Suppose the original plan touched:

```text
TokenValidator.java
TokenValidatorTest.java
```

but the model suddenly proposes:

```text
Database.java
SecurityConfig.java
UserRepository.java
AuthController.java
```

That should trigger:

```text
scope expansion detected
```

Then either:

```text
REPLAN
```

or:

```text
ASK_USER
```

depending on policy.

---

# 27. Add `ChangeImpactAnalyzer`

```java
public interface ChangeImpactAnalyzer {

    ImpactReport analyze(
        ChangeSet changes,
        CodingPlan plan
    );
}
```

Result:

```java
public record ImpactReport(
    Set<String> plannedFiles,
    Set<String> changedFiles,
    Set<String> unexpectedFiles,
    boolean scopeExpanded
) {}
```

Now the reviewer doesn't have to infer this from prose.

---

# 28. This gives us another strong invariant

At completion:

```text
actual changes
    ⊆
approved scope
```

unless the user explicitly approved expansion.

That's a very strong coding-agent guarantee.

---

# 29. Add user escalation as a structured state

Don't just return:

```text
"Please help."
```

Create:

```java
public record UserApprovalRequest(
    String reason,
    List<String> options,
    List<String> affectedFiles,
    Optional<CodingFailure> failure
) {}
```

Example:

```text
Aljabr needs your decision.

The repository changed while I was working.

Affected file:
src/auth/TokenValidator.java

Options:

1. Re-inspect and continue
2. Preserve the current user change and adapt the patch
3. Stop
```

Then:

```text
CodingPhase
    ↓
WAITING_USER
```

The execution can checkpoint there.

---

# 30. This is where Wayang's pause/resume becomes valuable

The flow becomes:

```text
Aljabr
 ↓
WAITING_USER
 ↓
checkpoint
 ↓
execution paused
 ↓
user responds
 ↓
resume
 ↓
recovery continues
```

No special Aljabr persistence mechanism is necessary.

That's exactly the benefit of keeping state inside Wayang's execution model.

---

# 31. Recovery event history

At this point I would introduce events we discussed earlier:

```java
sealed interface CodingEvent
```

with:

```java
record VerificationFailed(...) implements CodingEvent {}

record RepairStarted(...) implements CodingEvent {}

record RepairCompleted(...) implements CodingEvent {}

record ReplanStarted(...) implements CodingEvent {}

record UserApprovalRequested(...) implements CodingEvent {}

record UserApprovalReceived(...) implements CodingEvent {}
```

This gives us an excellent debugging trail.

---

# 32. Example real execution

Imagine:

```text
User:
"Fix the token expiration bug."
```

Aljabr:

```text
INSPECTING
 ↓
find TokenValidator
 ↓
read expiration logic
 ↓
PLANNING
 ↓
plan v1
 ↓
EXECUTING
 ↓
patch
 ↓
VERIFYING
 ↓
TokenValidatorTest FAIL
```

Failure:

```text
expected:
401

actual:
200
```

Recovery:

```text
classify:
TEST_FAILURE

fingerprint:
TokenValidatorTest / expiration boundary

progress:
first failure

decision:
REPAIR
```

Model proposes patch.

```text
PATCH
 ↓
VERIFY
 ↓
same failure
```

Now:

```text
fingerprint unchanged
```

Recovery:

```text
NO_PROGRESS
 ↓
REPLAN
```

New inspection discovers:

```text
expiration is actually handled
in AuthPolicy
```

Plan v2:

```text
modify AuthPolicy
add regression test
```

Then:

```text
PATCH
 ↓
VERIFY
 ↓
PASS
 ↓
REVIEW
 ↓
DONE
```

**That's the behavior we want.**

---

# 33. Notice what the LLM did NOT control

It didn't control:

```text
scope
checkpoint
budget
workspace safety
verification truth
retry count
completion
```

It controlled:

```text
interpretation
search strategy
implementation reasoning
repair proposal
replanning proposal
```

That's the right distribution of authority.

---

# 34. The next code package

At this milestone, I'd add:

```text
aljabr/
├── recovery/
│   ├── RecoveryAction.java
│   ├── RecoveryDecision.java
│   ├── RecoveryController.java
│   ├── RecoveryPolicy.java
│   └── RecoveryLevel.java
│
├── failure/
│   ├── CodingFailure.java
│   ├── FailureCategory.java
│   ├── FailureFingerprint.java
│   ├── FailureClassifier.java
│   ├── FailureNormalizer.java
│   └── FailureHistory.java
│
├── progress/
│   ├── ProgressAnalyzer.java
│   └── ProgressMeasurement.java
│
├── repair/
│   ├── RepairContext.java
│   └── RepairController.java
│
└── replan/
    ├── ReplanReason.java
    ├── ReplanRequest.java
    └── PlanRevision.java
```

And I would **not** add another executor.

Everything still flows through Wayang.

---

# 35. The architecture now has three intelligence layers

We're reaching an important shape:

```text
                    ALJABR
                       │
          ┌────────────┼────────────┐
          │            │            │
       Strategy     Recovery      Review
          │            │            │
          └────────────┼────────────┘
                       │
                  Coding State
                       │
                       ▼
                    WAYANG
                       │
       ┌───────────────┼────────────────┐
       │               │                │
     Model           Tools            State
       │               │                │
       ▼               ▼                ▼
   providers       sandbox          checkpoint
                  policy             resume
                  approval           events
                  cache              memory
```

And that is a very healthy boundary.

---

## The next milestone after Recovery

Once this is working, I'd move to something even more important for a serious coding agent:

### **Repository Intelligence**

Not "search files."

A persistent, incremental understanding of:

```text
repository
   ↓
modules
   ↓
packages
   ↓
symbols
   ↓
references
   ↓
tests
   ↓
dependencies
   ↓
architecture
```

Then Aljabr can stop repeatedly doing:

```text
grep → read → grep → read → grep → read
```

and instead use a **repository knowledge layer** with freshness/version tracking.

That is where your earlier Wayang **research cache + memory scope + invalidation** work can become extremely powerful for Aljabr.


Absolutely. **Milestone 4 should be Repository Intelligence**.

This is where Aljabr moves from:

> “I can search the repository.”

to:

> **“I maintain a structured, incrementally refreshed understanding of the repository.”**

The key is **not to build a giant AI knowledge graph first**. Start with a deterministic repository index, then let the LLM reason over the index.

---

# 1. The architecture

We want:

```text
                    Repository
                        │
                        ▼
                 RepositoryIndexer
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
       Files          Symbols      Dependencies
          │             │             │
          └─────────────┼─────────────┘
                        ▼
                RepositoryIndex
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
        Query / Search       Impact Analysis
              │                   │
              └─────────┬─────────┘
                        ▼
                    Aljabr
```

The critical distinction:

```text
RepositoryIndex
    = deterministic facts

Aljabr reasoning
    = interpretation of those facts
```

Don't store model guesses as repository truth.

---

# 2. Start with `RepositoryIndex`

```java
public record RepositoryIndex(
    String repositoryId,
    String revision,
    IndexVersion version,
    List<RepositoryFile> files,
    List<RepositorySymbol> symbols,
    List<DependencyEdge> dependencies
) {}
```

But I wouldn't actually keep all of this as one giant in-memory object.

Treat this as the **logical model**.

The physical implementation can use separate stores.

---

# 3. File model

```java
public record RepositoryFile(
    String path,
    FileKind kind,
    String contentHash,
    long size,
    long modifiedAt,
    boolean generated
) {}
```

```java
public enum FileKind {
    SOURCE,
    TEST,
    CONFIGURATION,
    DOCUMENTATION,
    BUILD,
    RESOURCE,
    UNKNOWN
}
```

This already gives Aljabr useful information.

For example:

```text
src/auth/TokenValidator.java
    SOURCE

src/test/auth/TokenValidatorTest.java
    TEST

build.gradle
    BUILD
```

---

# 4. Don't index every file identically

The first implementation should have specialized extractors.

```java
public interface RepositoryExtractor {

    boolean supports(
        RepositoryFile file
    );

    ExtractionResult extract(
        Path path,
        ExtractionContext context
    );
}
```

Then:

```text
JavaExtractor
KotlinExtractor
TypeScriptExtractor
PythonExtractor
GradleExtractor
MavenExtractor
DockerExtractor
ConfigExtractor
```

But don't implement every language immediately.

Start with the languages Aljabr actually targets.

---

# 5. Symbols

The first really useful abstraction:

```java
public record RepositorySymbol(
    SymbolId id,
    SymbolKind kind,
    String name,
    String path,
    Range range,
    Optional<SymbolId> parent
) {}
```

```java
public enum SymbolKind {
    CLASS,
    INTERFACE,
    ENUM,
    METHOD,
    FUNCTION,
    FIELD,
    PROPERTY,
    CONSTRUCTOR,
    MODULE,
    PACKAGE
}
```

For:

```java
class TokenValidator {
    boolean validate(Token token) {
        ...
    }
}
```

we want:

```text
TokenValidator
    CLASS

validate
    METHOD
```

---

# 6. Give symbols stable IDs

Don't use:

```text
class name + line number
```

because lines change.

Something closer to:

```text
repository
+
language
+
qualified symbol name
```

For example:

```text
java:
com.example.auth.TokenValidator#validate(Token)
```

Then:

```java
public record SymbolId(
    String value
) {}
```

This gives us stable references across index refreshes.

---

# 7. Symbol relationships

This is where the index becomes much more powerful.

```java
public record SymbolEdge(
    SymbolId from,
    SymbolId to,
    SymbolRelation relation
) {}
```

```java
public enum SymbolRelation {
    CALLS,
    REFERENCES,
    EXTENDS,
    IMPLEMENTS,
    OVERRIDES,
    IMPORTS,
    READS,
    WRITES,
    CREATES
}
```

Now we can answer:

```text
Who calls TokenValidator.validate()?
```

without repeatedly searching text.

---

# 8. Dependency edges

Keep build dependencies separate from symbol relationships.

```java
public record DependencyEdge(
    String from,
    String to,
    DependencyType type
) {}
```

```java
public enum DependencyType {
    MODULE,
    PACKAGE,
    LIBRARY,
    BUILD,
    RUNTIME
}
```

For example:

```text
auth-module
   ↓
security-core
```

This matters later for impact analysis.

---

# 9. The index should be incremental

This is one of the most important parts.

Do **not** do:

```text
every agent step
    ↓
scan entire repository
```

Instead:

```text
repository revision
       ↓
compare file fingerprints
       ↓
changed files
       ↓
re-index only changed files
       ↓
update affected relationships
```

---

# 10. `FileFingerprint`

```java
public record FileFingerprint(
    String path,
    String contentHash,
    long size,
    long modifiedAt
) {}
```

Then:

```java
public interface RepositoryChangeDetector {

    Uni<RepositoryChanges> detect(
        RepositorySnapshot previous,
        RepositorySnapshot current
    );
}
```

---

# 11. `RepositoryChanges`

```java
public record RepositoryChanges(
    Set<String> added,
    Set<String> modified,
    Set<String> deleted
) {}
```

Then:

```text
100,000 files

changed:
    3

index:
    3
```

instead of:

```text
index:
    100,000
```

This becomes essential for a real coding agent.

---

# 12. But indexes themselves need freshness

This connects directly to your earlier cache architecture.

Every indexed fact needs provenance:

```java
public record IndexFactMetadata(
    String sourceRevision,
    String contentHash,
    Instant indexedAt,
    String extractorVersion
) {}
```

So Aljabr knows:

```text
Symbol X
source revision = abc123
```

while current repository is:

```text
def456
```

Therefore:

```text
STALE
```

---

# 13. Don't confuse cache with memory

This distinction should remain strict.

### Repository index

```text
deterministic
derived from repository
rebuildable
```

### Research cache

```text
tool/query result
potentially reusable
TTL/invalidation
```

### Agent memory

```text
knowledge learned by agent
scope-dependent
may be user-controlled
```

### Coding state

```text
current execution
```

These should **not collapse into one "memory" system**.

---

# 14. Add `IndexFreshness`

```java
public enum Freshness {
    CURRENT,
    STALE,
    UNKNOWN
}
```

Then:

```java
public record IndexedFact<T>(
    T value,
    IndexFactMetadata metadata,
    Freshness freshness
) {}
```

This gives Aljabr an important rule:

> **Never silently treat stale repository facts as current.**

---

# 15. Query API

Now expose a semantic query interface:

```java
public interface RepositoryQuery {

    Uni<List<RepositoryFile>> findFiles(
        FileQuery query
    );

    Uni<List<RepositorySymbol>> findSymbols(
        SymbolQuery query
    );

    Uni<List<SymbolEdge>> findRelations(
        RelationQuery query
    );

    Uni<ImpactReport> analyzeImpact(
        ImpactQuery query
    );
}
```

This becomes Aljabr's primary repository intelligence API.

---

# 16. `SymbolQuery`

```java
public record SymbolQuery(
    Optional<String> name,
    Optional<SymbolKind> kind,
    Optional<String> path,
    Optional<String> qualifiedName
) {}
```

So the model can request:

```text
find method:
TokenValidator.validate
```

instead of:

```text
grep "validate"
```

---

# 17. `ImpactQuery`

This is where things get interesting.

```java
public record ImpactQuery(
    Set<SymbolId> changedSymbols,
    int maxDepth
) {}
```

Result:

```java
public record ImpactReport(
    Set<RepositorySymbol> directDependents,
    Set<RepositorySymbol> transitiveDependents,
    Set<RepositoryFile> relatedTests,
    Set<DependencyEdge> affectedDependencies
) {}
```

Now Aljabr can reason:

```text
I changed TokenValidator.validate()

Who depends on it?
```

---

# 18. Impact analysis should be deterministic

Do not ask the LLM:

> "What files might be affected?"

Instead:

```text
TokenValidator.validate
        │
        ├── AuthService
        ├── SessionService
        └── LoginController
```

The model then decides:

```text
Which of these need inspection?
```

Again:

```text
runtime/index → facts
LLM → reasoning
```

---

# 19. Test discovery

Add:

```java
public interface TestDiscovery {

    Uni<List<TestTarget>> findTests(
        Set<SymbolId> changedSymbols
    );
}
```

Result:

```java
public record TestTarget(
    String path,
    String name,
    TestRelation relation
) {}
```

```java
public enum TestRelation {
    DIRECT,
    RELATED,
    MODULE,
    UNKNOWN
}
```

---

# 20. Example

Aljabr changes:

```text
TokenValidator.validate()
```

Index finds:

```text
TokenValidatorTest.validateExpiredToken
TokenValidatorTest.validateBoundary
AuthIntegrationTest.login
```

Then verification policy can say:

```text
direct:
    TokenValidatorTest

related:
    AuthIntegrationTest
```

Start with direct tests.

Escalate if necessary.

---

# 21. Repository context builder

Now we need to connect this to the LLM.

Don't dump the entire index into the prompt.

Create:

```java
public interface CodingContextBuilder {

    Uni<RepositoryContext> build(
        CodingTask task,
        CodingState state
    );
}
```

Result:

```java
public record RepositoryContext(
    List<RepositoryFile> relevantFiles,
    List<RepositorySymbol> relevantSymbols,
    List<SymbolEdge> relationships,
    ImpactReport impact,
    List<TestTarget> tests
) {}
```

---

# 22. Context should be query-driven

For example, task:

```text
"Fix token expiration"
```

The context builder might retrieve:

```text
1. TokenValidator
2. TokenValidator.validate
3. callers
4. tests
5. configuration
6. recent changes
```

Not:

```text
entire repository
```

This reduces model context enormously.

---

# 23. Add relevance scoring

At first, deterministic scoring is enough.

```java
public record RepositoryRelevance(
    String path,
    double score,
    List<RelevanceReason> reasons
) {}
```

Reasons:

```java
public enum RelevanceReason {
    DIRECT_SYMBOL_MATCH,
    CALLER,
    CALLEE,
    TEST,
    SAME_MODULE,
    IMPORT,
    PATH_MATCH,
    RECENTLY_CHANGED
}
```

Now the model sees:

```text
TokenValidator.java
score: 1.00
reason: DIRECT_SYMBOL_MATCH

TokenValidatorTest.java
score: 0.94
reason: TEST

AuthService.java
score: 0.71
reason: CALLER
```

Much better than arbitrary file search.

---

# 24. Don't make semantic embeddings mandatory

This is important.

You **can** add vector search later.

But the first repository intelligence system should work with:

```text
filesystem
+
parser
+
symbol index
+
dependency graph
+
text search
```

Then optionally:

```text
embeddings
```

for fuzzy discovery.

Otherwise you introduce:

```text
embedding model
vector database
embedding invalidation
chunking
semantic drift
```

before the basic system is even reliable.

---

# 25. Layer search instead

Use a search cascade:

```text
1. exact symbol
       ↓
2. structural relation
       ↓
3. filename/path
       ↓
4. text search
       ↓
5. semantic search
```

Only escalate when necessary.

This makes repository research cheap and predictable.

---

# 26. Add `ResearchSession`

This connects beautifully with your earlier research/cache work.

```java
public record ResearchSession(
    String id,
    String repositoryRevision,
    List<ResearchObservation> observations
) {}
```

Observation:

```java
public record ResearchObservation(
    String query,
    List<String> sources,
    String summary,
    String evidenceHash
) {}
```

Then:

```text
Aljabr asks:
"Where is token expiration determined?"
```

It searches.

The result is recorded.

Later:

```text
"Where is token expiration determined?"
```

can reuse the observation if the relevant repository state is still current.

---

# 27. But invalidate selectively

Suppose:

```text
Research:
Token expiration → TokenValidator.java
```

Then:

```text
TokenValidator.java changed
```

The observation becomes:

```text
STALE
```

But:

```text
Research:
Database configuration → DatabaseConfig.java
```

can remain:

```text
CURRENT
```

This is exactly the kind of **fine-grained invalidation** your earlier Wayang architecture needs.

---

# 28. Research provenance

Every important conclusion should eventually be traceable to evidence:

```java
public record ResearchEvidence(
    EvidenceType type,
    String source,
    String sourceHash,
    String repositoryRevision,
    String excerpt
) {}
```

Types:

```java
public enum EvidenceType {
    FILE,
    SYMBOL,
    SEARCH_RESULT,
    TEST,
    BUILD_METADATA,
    TOOL_RESULT
}
```

So the model can reason:

```text
Conclusion:
expiration is enforced by TokenValidator

Evidence:
TokenValidator.java
symbol TokenValidator.validate
revision abc123
```

---

# 29. This is extremely useful during repair

Suppose the model says:

```text
"I believe AuthPolicy controls expiration."
```

The repository index can answer:

```text
No relevant symbol relation found.

Current evidence:
TokenValidator.validate()
    ↓
called by AuthService
```

The model then has to reconsider.

This is much better than letting model memory become repository truth.

---

# 30. Add repository architecture facts later

Eventually:

```java
public record ArchitectureFact(
    String subject,
    String relation,
    String object,
    FactConfidence confidence,
    List<ResearchEvidence> evidence
) {}
```

Example:

```text
AuthService
    DEPENDS_ON
TokenValidator

AuthModule
    OWNS
TokenValidator

TokenValidatorTest
    TESTS
TokenValidator
```

But I would **not allow the LLM to freely write these facts into the canonical index**.

They should be either:

```text
derived deterministically
```

or stored separately as:

```text
inferred knowledge
```

with confidence/provenance.

---

# 31. This gives us three repository knowledge layers

This is an important architectural boundary:

```text
┌────────────────────────────────────┐
│ Canonical Repository Facts         │
│                                    │
│ files                              │
│ symbols                            │
│ references                         │
│ dependencies                       │
│ tests                              │
└────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────┐
│ Derived Research                   │
│                                    │
│ relevant files                     │
│ impact analysis                    │
│ research observations              │
│ summaries                          │
└────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────┐
│ Agent Reasoning                    │
│                                    │
│ hypotheses                         │
│ plans                              │
│ decisions                          │
│ proposed changes                   │
└────────────────────────────────────┘
```

Do not merge them.

---

# 32. The index update lifecycle

Now the complete process becomes:

```text
workspace
   ↓
snapshot
   ↓
detect changes
   ↓
┌─────────────────────┐
│ added / modified    │
│ deleted             │
└──────────┬──────────┘
           ↓
    incremental parse
           ↓
    update symbols
           ↓
    update relations
           ↓
    update dependencies
           ↓
    update tests
           ↓
    commit index revision
```

The index itself should be checkpointable/versioned.

---

# 33. Critical invariant

Never expose half-updated index state.

Bad:

```text
update Foo
↓
index now partially changed
↓
update Bar crashes
↓
Aljabr queries broken index
```

Instead:

```text
old index
    +
changes
    ↓
build new index state
    ↓
validate
    ↓
atomic publish
```

Conceptually:

```java
public interface RepositoryIndexStore {

    Uni<RepositoryIndexSnapshot> current();

    Uni<RepositoryIndexTransaction> begin(
        String baseRevision
    );
}
```

Then:

```text
build
 ↓
validate
 ↓
commit
```

---

# 34. Repository index versioning

Use:

```java
public record IndexVersion(
    long sequence,
    String repositoryRevision,
    String schemaVersion
) {}
```

Example:

```text
index:
  sequence = 184
  repositoryRevision = abc123
  schema = 2
```

Now every Aljabr observation can say exactly what repository knowledge it was based on.

---

# 35. This unlocks a major Aljabr feature: impact-aware planning

Instead of:

```text
PLAN:
change Foo.java
run tests
```

Aljabr can generate:

```text
PLAN:

1. Modify TokenValidator.validate()
2. Update TokenValidatorTest boundary case
3. Inspect AuthService callers
4. Run direct tests
5. Run auth integration tests if impact analysis indicates
6. Review affected symbols
```

The plan becomes **evidence-driven**.

---

# 36. And the recovery system gets stronger

Previously:

```text
test fails
→ repair
```

Now:

```text
test fails
 ↓
failure classification
 ↓
impact analysis
 ↓
inspect affected dependency graph
 ↓
repair / replan
```

So recovery becomes much more intelligent without making the model infinitely larger.

---

# 37. Wayang integration

Again, keep the boundary clean.

Wayang should provide generic capabilities such as:

```text
workspace.snapshot
workspace.read
workspace.search
workspace.diff

repository.index
repository.query
repository.symbols
repository.references
repository.tests
```

Aljabr orchestrates:

```text
"Which query should I perform?"
"Which evidence matters?"
"What does it mean?"
"What should I change?"
```

The repository index itself should not become an Aljabr-only feature if other Wayang agents can benefit from it.

---

# 38. First practical implementation scope

Don't implement everything above at once.

### Phase A

Build:

```text
RepositoryFile
RepositorySymbol
SymbolEdge
RepositoryIndex
RepositoryIndexStore
```

### Phase B

Implement one language:

```text
JavaExtractor
```

### Phase C

Implement:

```text
symbol lookup
reference lookup
file lookup
```

### Phase D

Implement incremental indexing:

```text
hash
diff
re-index changed files
atomic publish
```

### Phase E

Implement:

```text
impact analysis
test discovery
```

### Phase F

Connect to Aljabr:

```text
CodingContextBuilder
ResearchSession
```

That's enough for a serious first version.

---

# 39. The resulting Aljabr loop

We're now approaching something substantially more capable:

```text
                         USER
                           │
                           ▼
                      OBJECTIVE
                           │
                           ▼
                    ┌────────────┐
                    │  ALJABR    │
                    └─────┬──────┘
                          │
                    RepositoryQuery
                          │
                          ▼
                 ┌─────────────────┐
                 │ RepositoryIndex │
                 └────────┬────────┘
                          │
              facts / symbols / impact
                          │
                          ▼
                       MODEL
                          │
                    plan / intent
                          │
                          ▼
                     VALIDATION
                          │
                          ▼
                       WAYANG
                          │
                    tools / sandbox
                          │
                          ▼
                     WORKSPACE
                          │
                          ▼
                    VERIFICATION
                          │
                    ┌─────┴─────┐
                    │           │
                  PASS        FAIL
                    │           │
                    ▼           ▼
                  REVIEW     RECOVERY
                                │
                    ┌───────────┼───────────┐
                    ▼           ▼           ▼
                  REPAIR     REINSPECT    REPLAN
                    │           │           │
                    └───────────┴───────────┘
                                │
                                ▼
                         RepositoryQuery
```

This is the point where **Aljabr starts behaving like an engineering system rather than an LLM wrapped around shell commands.**

---

## What I'd build immediately after this

The next milestone should be **Execution Memory + Research Memory integration**.

Not generic long-term "AI memory."

Specifically:

```text
Repository Index
      +
Research Cache
      +
Coding Observations
      +
Failure History
      +
Plan History
      +
Tool Results
      ↓
Evidence Store
```

with explicit:

```text
scope
freshness
provenance
invalidation
compression
retention
forget
```

That would finally connect the repository intelligence we've just built to the **memory architecture you were developing earlier**, without turning everything into one giant memory blob.


Yes. **Next milestone: Evidence & Memory Architecture for Aljabr.**

This is where we connect the repository intelligence, research cache, execution state, failures, and plans **without collapsing them into one giant memory system**.

The core rule:

> **Store evidence separately from conclusions, and make every conclusion traceable to evidence.**

---

# 1. The architecture

I'd make the next layer:

```text
                    ALJABR
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
      Research      Coding       Recovery
      Session       State        History
          │            │            │
          └────────────┼────────────┘
                       ▼
                 Evidence Store
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
        Evidence              Knowledge
        Records                Records
             │                   │
             └─────────┬─────────┘
                       ▼
                 Freshness /
                 Provenance
                       │
                       ▼
                Repository Index
```

The crucial distinction:

```text
Evidence
   ≠
Knowledge
   ≠
Execution state
   ≠
Cache
```

---

# 2. Start with `EvidenceRecord`

```java
public record EvidenceRecord(
    String id,
    EvidenceType type,
    EvidenceSource source,
    String contentHash,
    String repositoryRevision,
    Instant capturedAt,
    Map<String, String> metadata
) {}
```

Types:

```java
public enum EvidenceType {

    FILE_CONTENT,
    SYMBOL,
    REFERENCE,
    SEARCH_RESULT,
    TEST_RESULT,
    BUILD_RESULT,
    TOOL_RESULT,
    DIFF,
    USER_INPUT,
    EXTERNAL_DOCUMENT
}
```

This becomes the foundation.

---

# 3. Evidence should be immutable

Once captured:

```text
Evidence E1
revision abc123
TokenValidator.java
```

don't mutate it into:

```text
TokenValidator.java
revision def456
```

Instead:

```text
E1 → old evidence
E2 → new evidence
```

That makes history reproducible.

---

# 4. Add provenance

Every important fact needs:

```java
public record EvidenceProvenance(
    String sourceId,
    String sourceRevision,
    String tool,
    String toolVersion,
    Instant observedAt
) {}
```

For example:

```text
Fact:
"TokenValidator.validate checks expiration"

Evidence:
file: src/auth/TokenValidator.java
revision: abc123
extractor: JavaExtractor 2.1
```

Now if the repository changes, we know exactly what the conclusion was based on.

---

# 5. Knowledge records

Now create a separate abstraction:

```java
public record KnowledgeRecord(
    String id,
    String subject,
    String predicate,
    String object,
    KnowledgeScope scope,
    Confidence confidence,
    List<String> evidenceIds
) {}
```

Example:

```text
subject:
TokenValidator.validate

predicate:
ENFORCES

object:
token expiration
```

Evidence:

```text
[E123, E124]
```

---

# 6. Knowledge is not automatically truth

This is extremely important.

Add:

```java
public enum KnowledgeOrigin {

    DETERMINISTIC,
    OBSERVED,
    INFERRED,
    USER_PROVIDED,
    EXTERNAL
}
```

Then:

```java
public record KnowledgeRecord(
    ...
    KnowledgeOrigin origin,
    ...
) {}
```

So:

```text
Java parser:
TokenValidator.validate calls AuthService
```

is:

```text
DETERMINISTIC
```

while:

```text
"AuthService is the central authentication boundary"
```

might be:

```text
INFERRED
```

The latter must never silently become canonical architecture truth.

---

# 7. Confidence

Don't use fake precision like:

```text
confidence = 0.87342
```

unless you have a meaningful statistical basis.

For now:

```java
public enum Confidence {
    VERIFIED,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
```

And define clear semantics.

For example:

```text
VERIFIED
    direct deterministic evidence

HIGH
    multiple independent observations

MEDIUM
    strong inference

LOW
    weak inference
```

---

# 8. Knowledge scopes

This connects directly to your earlier memory architecture.

```java
public enum KnowledgeScope {

    EXECUTION,

    TASK,

    PROJECT,

    WORKSPACE,

    USER,

    GLOBAL
}
```

Example:

```text
"User prefers tabs"
    → USER

"Repository uses Gradle"
    → PROJECT

"This task requires Java 21"
    → TASK

"Patch 7 has been applied"
    → EXECUTION
```

Don't put these all into one bucket.

---

# 9. Execution memory

For an individual Aljabr run:

```java
public record ExecutionMemory(
    String executionId,
    List<String> evidenceIds,
    List<String> knowledgeIds,
    List<String> observationIds
) {}
```

This is disposable.

When the execution ends, some knowledge can be promoted.

---

# 10. Promotion should be explicit

For example:

```text
EXECUTION
    │
    │ observed repeatedly
    ▼
TASK
    │
    │ validated
    ▼
PROJECT
```

Never:

```text
LLM said something
    ↓
GLOBAL MEMORY
```

That is how coding agents accumulate garbage.

---

# 11. `KnowledgePromotionPolicy`

```java
public interface KnowledgePromotionPolicy {

    PromotionDecision evaluate(
        KnowledgeRecord knowledge,
        PromotionContext context
    );
}
```

Example:

```java
public enum PromotionDecision {
    KEEP_EXECUTION,
    PROMOTE_TASK,
    PROMOTE_PROJECT,
    PROMOTE_WORKSPACE,
    REJECT
}
```

---

# 12. Example

Aljabr discovers:

```text
"All repository tests use JUnit 5."
```

Evidence:

```text
build.gradle
20 test files
test dependencies
```

Potentially:

```text
PROJECT
HIGH
```

But:

```text
"Developer prefers concise tests."
```

might come from one user request.

That's:

```text
USER
MEDIUM
```

Different scope.

---

# 13. Research cache should remain separate

We already discussed cache.

Make the distinction explicit:

```java
public record ResearchCacheEntry(
    String key,
    String queryFingerprint,
    String repositoryRevision,
    List<String> evidenceIds,
    Instant expiresAt
) {}
```

The cache answers:

> "Can I reuse the result of this research operation?"

It does **not** answer:

> "Is this a permanent fact about the project?"

---

# 14. Cache lookup

```java
public interface ResearchCache {

    Uni<Optional<ResearchCacheEntry>> get(
        ResearchQuery query,
        ResearchContext context
    );

    Uni<Void> put(
        ResearchQuery query,
        ResearchResult result
    );

    Uni<Void> invalidate(
        InvalidationEvent event
    );
}
```

Now research becomes:

```text
query
 ↓
cache lookup
 ↓
HIT
 ├── verify freshness
 └── reuse
```

or:

```text
MISS
 ↓
repository query
 ↓
capture evidence
 ↓
store result
 ↓
return
```

---

# 15. Freshness should be dependency-based

This is the really useful part.

Suppose:

```text
Research R1:
"Where is expiration handled?"
```

depends on:

```text
TokenValidator.java
AuthPolicy.java
```

Record:

```java
public record ResearchDependency(
    String researchId,
    String dependencyId,
    DependencyKind kind
) {}
```

Then:

```text
TokenValidator.java changed
        ↓
invalidate R1
```

but unrelated research stays valid.

---

# 16. `DependencyKind`

```java
public enum DependencyKind {

    FILE,

    SYMBOL,

    BUILD_CONFIGURATION,

    TEST,

    EXTERNAL_SOURCE
}
```

This makes invalidation precise.

---

# 17. Evidence graph

We're now approaching a very useful structure:

```text
                   Evidence
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
       Research    Knowledge    Verification
          │           │           │
          └───────────┼───────────┘
                      ▼
                Dependencies
```

Example:

```text
Knowledge K1:
TokenValidator enforces expiration

supported by:
  E101 TokenValidator.java
  E102 TokenValidator.validate()
  E103 TokenValidatorTest
```

Then:

```text
TokenValidator.java changes
```

automatically marks:

```text
K1 → STALE
R1 → STALE
```

---

# 18. Staleness propagation

Don't delete stale information immediately.

Mark it:

```java
public enum KnowledgeStatus {
    CURRENT,
    STALE,
    INVALIDATED,
    SUPERSEDED
}
```

Why?

Because historical reasoning is valuable.

You may want to know:

> Why did Aljabr make this patch?

Answer:

```text
It relied on K1,
which was valid at revision abc123.
```

---

# 19. This solves a huge debugging problem

Without provenance:

```text
Why did the agent change AuthPolicy?
```

Possible answer:

> "The model thought it was right."

Useless.

With evidence:

```text
Decision D42
 ↓
Plan P7
 ↓
Knowledge K12
 ↓
Evidence E51
 ↓
TokenValidator.java @ abc123
```

Now the execution is explainable.

---

# 20. Add `DecisionRecord`

I strongly recommend this.

```java
public record DecisionRecord(
    String id,
    String executionId,
    DecisionType type,
    String rationale,
    List<String> evidenceIds,
    List<String> knowledgeIds,
    Instant createdAt
) {}
```

Types:

```java
public enum DecisionType {
    PLAN,
    PATCH,
    REPAIR,
    REPLAN,
    VERIFY,
    COMPLETE,
    ASK_USER
}
```

This gives you a proper decision trail.

---

# 21. Don't store chain-of-thought

Important architectural boundary:

```text
DecisionRecord
```

should store:

```text
decision
+
short rationale
+
evidence
+
inputs
```

not hidden/private chain-of-thought.

For example:

```text
Decision:
REPLAN

Rationale:
Current implementation does not affect the failing execution path.

Evidence:
E21
E22
E23
```

That's enough for observability and reproducibility.

---

# 22. Add `Observation`

An observation is slightly different from knowledge.

```java
public record Observation(
    String id,
    String statement,
    List<String> evidenceIds,
    ObservationScope scope,
    ObservationStatus status
) {}
```

Example:

```text
Observation:
TokenValidator.validate() is called by AuthService.

Evidence:
symbol graph E41
```

It doesn't necessarily mean:

```text
"TokenValidator is architecturally responsible for authentication."
```

That second statement is interpretation.

---

# 23. The hierarchy becomes:

```text
Evidence
   ↓
Observation
   ↓
Knowledge
   ↓
Decision
   ↓
Action
   ↓
Result
   ↓
Evidence
```

This is a fantastic loop:

```text
EVIDENCE
   ↓
OBSERVE
   ↓
UNDERSTAND
   ↓
DECIDE
   ↓
ACT
   ↓
VERIFY
   ↓
NEW EVIDENCE
```

That should become a fundamental Aljabr mental model.

---

# 24. Research should therefore be an evidence-producing process

Instead of:

```java
ResearchResult research(...)
```

conceptually:

```java
public record ResearchResult(
    List<Observation> observations,
    List<EvidenceRecord> evidence,
    List<KnowledgeRecord> derivedKnowledge
) {}
```

The model can then consume the structured result.

---

# 25. Example

User:

```text
"Fix authentication timeout."
```

Research:

```text
E1:
TokenValidator.java

E2:
TokenValidator.validate()

E3:
AuthService.java

E4:
TokenValidatorTest.java
```

Observation:

```text
O1:
TokenValidator.validate checks token expiration.
```

Knowledge:

```text
K1:
TokenValidator is part of the token validation path.
```

Plan:

```text
P1:
Modify expiration comparison.
```

Decision:

```text
D1:
PATCH TokenValidator.java.
```

Result:

```text
E5:
diff
```

Verification:

```text
E6:
TokenValidatorTest failed.
```

Recovery:

```text
D2:
REPLAN
```

Everything is connected.

---

# 26. This also makes memory "forget" possible

You previously wanted:

> user wants the agent to forget some knowledge.

Now it's straightforward.

Don't physically erase evidence automatically.

Instead:

```java
public interface KnowledgeLifecycle {

    Uni<Void> forget(
        String knowledgeId,
        ForgetReason reason
    );
}
```

Status:

```text
K1
 ↓
FORGOTTEN
```

But preserve the audit relationship if policy allows.

---

# 27. More importantly: forget can cascade

Suppose:

```text
K1
 ↓
K2
 ↓
K3
```

and K1 is forgotten.

You shouldn't blindly delete K2/K3.

Instead:

```text
K2:
supported by K1 + E7
```

If E7 independently supports K2:

```text
K2 remains
```

If K1 was its only support:

```text
K2 → NEEDS_REVALIDATION
```

That's much safer.

---

# 28. `KnowledgeSupport`

```java
public record KnowledgeSupport(
    String knowledgeId,
    String evidenceId,
    SupportType type
) {}
```

```java
public enum SupportType {
    PRIMARY,
    SECONDARY,
    CORROBORATING
}
```

Then forgetting becomes a graph operation.

---

# 29. User-controlled memory boundaries

For example:

```text
USER MEMORY
    "Use Gradle for this project."
```

versus:

```text
PROJECT KNOWLEDGE
    "Build system is Gradle."
```

These are not identical.

The user can say:

> "Forget that I prefer Gradle."

That should remove:

```text
USER preference
```

but not necessarily:

```text
PROJECT fact:
build.gradle exists
```

This distinction prevents destructive memory operations.

---

# 30. Memory retrieval should be scoped

Create:

```java
public record MemoryQuery(
    String query,
    Set<KnowledgeScope> scopes,
    Optional<String> projectId,
    Optional<String> executionId,
    int limit
) {}
```

For an Aljabr execution:

```text
EXECUTION
TASK
PROJECT
WORKSPACE
USER
```

might be available.

But:

```text
GLOBAL
```

should require explicit policy.

---

# 31. Retrieval priority

I would use:

```text
CURRENT EXECUTION
       ↓
TASK
       ↓
PROJECT
       ↓
WORKSPACE
       ↓
USER
       ↓
GLOBAL
```

with stronger weight toward current/relevant scope.

Not:

```text
search everything
```

---

# 32. Contradictions need first-class handling

Suppose memory says:

```text
K1:
Project uses Maven.
```

Repository index says:

```text
pom.xml absent
build.gradle present
```

Don't silently choose one.

Create:

```java
public record KnowledgeConflict(
    List<String> conflictingKnowledgeIds,
    List<String> evidenceIds,
    ConflictSeverity severity
) {}
```

Then:

```text
CONFLICT
 ↓
revalidate
 ↓
prefer current deterministic evidence
```

---

# 33. This gives us an evidence hierarchy

I'd establish:

```text
1. Current deterministic repository/tool evidence
2. Current verification result
3. User-provided explicit instruction
4. Recent verified project knowledge
5. Historical project knowledge
6. Agent inference
7. External/general assumptions
```

Higher evidence should generally dominate lower evidence.

This should be a policy, not merely a prompt instruction.

---

# 34. Now connect it to the model context

The model should receive something like:

```text
Repository facts
────────────────
TokenValidator.validate()
calls:
  AuthService

Tests:
  TokenValidatorTest

Current changes:
  TokenValidator.java

Relevant observations
────────────────
O1:
TokenValidator.validate checks expiration.

Relevant knowledge
────────────────
K1:
TokenValidator is part of token validation.

Evidence
────────────────
E101:
TokenValidator.java @ abc123

Current verification
────────────────
TokenValidatorTest:
FAILED
```

That's dramatically better than:

```text
Here are 30 files and 8,000 lines.
```

---

# 35. Add context budgets

```java
public record ContextBudget(
    int maxEvidenceItems,
    int maxKnowledgeItems,
    int maxFiles,
    int maxTokens
) {}
```

Context builder then ranks:

```text
relevance
+
freshness
+
confidence
+
scope
```

and selects the best evidence.

---

# 36. The selection algorithm

Conceptually:

```java
score =
    relevance
    * freshnessWeight
    * confidenceWeight
    * scopeWeight;
```

But don't over-engineer the mathematics initially.

Start with explicit ranking:

```text
CURRENT + DIRECT + VERIFIED
        >
CURRENT + RELATED + HIGH
        >
STALE + DIRECT
        >
LOW CONFIDENCE INFERENCE
```

That will be easier to debug.

---

# 37. The resulting memory architecture

Now Wayang/Aljabr has:

```text
                         MEMORY
                           │
       ┌───────────────────┼──────────────────┐
       ▼                   ▼                  ▼
   Evidence             Research           Knowledge
       │                 Cache                │
       │                   │                  │
       │                   │                  ▼
       │                   │              Observations
       │                   │                  │
       └───────────────────┴──────────────────┘
                           │
                           ▼
                       Decisions
                           │
                           ▼
                     Coding State
```

And separately:

```text
Repository Index
```

provides deterministic repository facts.

---

# 38. The most important invariant

I'd write this directly into the architecture documentation:

> **Aljabr may reason from stale or uncertain knowledge only when explicitly permitted by policy; it must never represent stale inferred knowledge as current deterministic repository fact.**

That's the kind of invariant that will prevent enormous classes of bugs later.

---

# 39. Practical implementation order

Don't implement the whole graph at once.

### Step 1

```text
EvidenceRecord
EvidenceStore
```

### Step 2

```text
Observation
ObservationStore
```

### Step 3

```text
KnowledgeRecord
KnowledgeStore
```

### Step 4

```text
provenance
freshness
scope
```

### Step 5

```text
ResearchCache
    →
EvidenceStore
```

### Step 6

```text
DecisionRecord
```

### Step 7

```text
dependency/invalidation
```

### Step 8

```text
MemoryQuery
ContextBuilder
```

### Step 9

```text
forget / promotion / conflict
```

That gives you a usable system at every step.

---

# 40. And now Aljabr has a very strong core loop

```text
                  ┌───────────────┐
                  │    OBJECTIVE  │
                  └───────┬───────┘
                          ▼
                     RESEARCH
                          │
                          ▼
                      EVIDENCE
                          │
                          ▼
                    OBSERVATIONS
                          │
                          ▼
                     KNOWLEDGE
                          │
                          ▼
                       PLAN
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
                     VERIFICATION
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
             SUCCESS              FAILURE
                │                   │
                ▼                   ▼
             REVIEW              RECOVERY
                                    │
                          ┌─────────┼─────────┐
                          ▼         ▼         ▼
                       REPAIR   RESEARCH   REPLAN
                          │         │         │
                          └─────────┴─────────┘
                                    │
                                    ▼
                                 EVIDENCE
```

That is the **real Aljabr architecture** emerging.

And importantly, Wayang remains the runtime underneath it rather than becoming polluted with coding-agent-specific reasoning.

---

## Next milestone

The next thing I'd tackle is **Aljabr's Planner + Task Graph**.

Not just:

```text
Plan = List<Step>
```

but an executable dependency graph:

```text
             ┌──────────────┐
             │ Understand   │
             └──────┬───────┘
                    ▼
             ┌──────────────┐
             │ Locate code  │
             └──────┬───────┘
                    ▼
          ┌─────────┴─────────┐
          ▼                   ▼
    Modify source        Add regression test
          │                   │
          └─────────┬─────────┘
                    ▼
                Verify
                    │
                    ▼
                 Review
```

That would let Aljabr support **parallel research, dependency-aware execution, partial completion, replanning, resumability, and much better task decomposition**—which is the natural next step after the evidence/memory layer.


Absolutely. **Next milestone: Aljabr Planner + Executable Task Graph.**

This is the natural progression from the evidence layer.

The important shift is:

```text
Old:
Plan = ordered list of instructions

New:
Plan = dependency-aware execution graph
```

That gives Aljabr the ability to understand **what can run in parallel, what depends on what, what became invalid, and what needs replanning**.

---

# 1. Core model

Start with:

```java
public record CodingPlan(
    String id,
    int version,
    String objective,
    TaskGraph graph,
    PlanMetadata metadata
) {}
```

And:

```java
public record TaskGraph(
    Map<TaskId, CodingTask> tasks,
    Set<TaskDependency> dependencies
) {}
```

The graph is:

```text
Task A ──────→ Task C
   │
   └──────────→ Task D

Task B ──────→ Task C
```

Meaning:

```text
A and B can run independently.

C requires:
    A
    B
```

---

# 2. `CodingTask`

Don't make the task merely a string.

```java
public record CodingTask(
    TaskId id,
    TaskType type,
    String objective,
    TaskScope scope,
    TaskStatus status,
    TaskConstraints constraints
) {}
```

For example:

```text
T1
type = RESEARCH
objective = locate token expiration logic

T2
type = INSPECT
objective = inspect TokenValidator

T3
type = MODIFY
objective = change expiration comparison

T4
type = TEST
objective = add boundary regression test

T5
type = VERIFY
objective = run authentication tests

T6
type = REVIEW
objective = inspect final diff
```

---

# 3. Task types

Start small.

```java
public enum TaskType {

    RESEARCH,

    INSPECT,

    PLAN,

    MODIFY,

    TEST,

    VERIFY,

    REVIEW,

    VALIDATE,

    USER_APPROVAL
}
```

Don't create separate types for every imaginable action.

---

# 4. Task IDs must be stable

```java
public record TaskId(String value) {}
```

Avoid:

```text
task-1
task-2
task-3
```

if those are regenerated arbitrarily.

Better:

```text
research.token-expiration
inspect.token-validator
modify.token-validator.expiration
test.token-validator.boundary
verify.auth
review.final
```

Stable identity makes resume/replan much easier.

---

# 5. Dependencies

```java
public record TaskDependency(
    TaskId from,
    TaskId to,
    DependencyCondition condition
) {}
```

For example:

```text
T1 → T2
T2 → T3
T2 → T4
T3 → T5
T4 → T5
T5 → T6
```

Graph:

```text
          Research
             │
             ▼
           Inspect
          /       \
         ▼         ▼
      Modify      Test
         \         /
          ▼       ▼
            Verify
               │
               ▼
             Review
```

---

# 6. Dependency conditions

This becomes useful quickly.

```java
public enum DependencyCondition {

    COMPLETED,

    SUCCEEDED,

    VERIFIED,

    APPROVED
}
```

Example:

```text
Test
  requires Inspect
  condition = COMPLETED
```

but:

```text
Deploy
  requires Verify
  condition = SUCCEEDED
```

That distinction matters.

---

# 7. Task lifecycle

Use explicit states.

```java
public enum TaskStatus {

    PENDING,

    READY,

    RUNNING,

    WAITING,

    SUCCEEDED,

    FAILED,

    BLOCKED,

    CANCELLED,

    INVALIDATED
}
```

The runtime should own these transitions.

The model should **not** arbitrarily set:

```text
status = SUCCEEDED
```

---

# 8. Task state machine

Conceptually:

```text
              ┌──────────┐
              │ PENDING  │
              └────┬─────┘
                   │ dependencies satisfied
                   ▼
              ┌──────────┐
              │  READY   │
              └────┬─────┘
                   │
                   ▼
              ┌──────────┐
              │ RUNNING  │
              └────┬─────┘
                ┌──┴───────┐
                ▼          ▼
           SUCCEEDED     FAILED
                │          │
                ▼          ▼
             NEXT       RECOVERY
```

And:

```text
RUNNING
   ↓
WAITING
   ↓
resume
   ↓
RUNNING
```

---

# 9. Task executor

Now create one generic interface:

```java
public interface CodingTaskExecutor {

    boolean supports(TaskType type);

    Uni<TaskExecutionResult> execute(
        CodingTask task,
        CodingExecutionContext context
    );
}
```

Implementations:

```text
ResearchTaskExecutor
InspectTaskExecutor
ModifyTaskExecutor
TestTaskExecutor
VerifyTaskExecutor
ReviewTaskExecutor
```

This is cleaner than making one enormous Aljabr executor.

---

# 10. But don't duplicate Wayang

For example:

```java
ModifyTaskExecutor
```

should NOT implement:

```text
filesystem
patching
shell
git
```

It should call Wayang capabilities:

```text
ModifyTaskExecutor
       │
       ▼
Wayang Workspace
       │
       ├── read
       ├── patch
       ├── diff
       └── snapshot
```

Aljabr decides **what**.

Wayang performs **how**.

---

# 11. Task execution context

```java
public record CodingExecutionContext(
    String executionId,
    CodingState state,
    RepositoryContext repository,
    EvidenceContext evidence,
    CodingBudget budget
) {}
```

This means every task has controlled access to:

```text
repository
evidence
memory
workspace
budget
execution state
```

instead of reaching into global services.

---

# 12. `TaskExecutionResult`

```java
public record TaskExecutionResult(
    TaskStatus status,
    List<String> evidenceIds,
    List<String> knowledgeIds,
    Optional<TaskFailure> failure,
    TaskEffects effects
) {}
```

Effects:

```java
public record TaskEffects(
    Set<String> changedFiles,
    Set<String> changedSymbols,
    Set<String> invalidatedTasks
) {}
```

This is particularly important.

A task should report **what it changed**.

---

# 13. Why effects matter

Suppose:

```text
T3:
modify TokenValidator.java
```

The result says:

```text
changedFiles:
    TokenValidator.java

changedSymbols:
    TokenValidator.validate()
```

The planner can then automatically invalidate:

```text
T5
verification
```

if its assumptions are now stale.

---

# 14. Task invalidation

Add:

```java
public record TaskInvalidation(
    TaskId taskId,
    String reason,
    List<String> evidenceIds
) {}
```

Example:

```text
T2 inspect TokenValidator
       ↓
T3 modifies TokenValidator
       ↓
old T2 result may still be useful
but:
T5 verification context becomes invalid
```

So:

```text
T5
SUCCEEDED
```

might become:

```text
T5
INVALIDATED
```

if the verification was based on a previous workspace.

---

# 15. This prevents a nasty bug

Without invalidation:

```text
modify
 ↓
verify → PASS
 ↓
another modification
 ↓
agent thinks verification still passes
```

Wrong.

The task graph needs dependency-aware invalidation.

---

# 16. Workspace revision

Every meaningful mutation should produce a workspace revision.

```java
public record WorkspaceRevision(
    String id,
    String parentId,
    String contentFingerprint
) {}
```

Now verification records:

```text
Verify T5
workspace = W17
result = PASS
```

If workspace becomes:

```text
W18
```

then T5's result may no longer apply.

---

# 17. Verification validity

```java
public record VerificationValidity(
    String workspaceRevision,
    boolean valid
) {}
```

Simple rule:

```text
verification workspace == current workspace
        ↓
potentially valid

otherwise
        ↓
stale
```

You can later make it more selective based on affected files.

---

# 18. Selective verification invalidation

Suppose:

```text
T5:
unit test TokenValidator
```

and later:

```text
README.md changed
```

No need to invalidate T5.

But:

```text
TokenValidator.java changed
```

should invalidate it.

So connect verification to dependencies:

```java
public record VerificationDependency(
    String verificationId,
    Set<String> files,
    Set<String> symbols
) {}
```

Then invalidation becomes precise.

---

# 19. Planner output

The LLM shouldn't directly create executable objects.

Have it return a structured proposal:

```java
public record PlanProposal(
    String objective,
    List<ProposedTask> tasks,
    List<ProposedDependency> dependencies
) {}
```

Then:

```text
LLM
 ↓
PlanProposal
 ↓
PlanValidator
 ↓
CodingPlan
```

---

# 20. `PlanValidator`

This is critical.

```java
public interface PlanValidator {

    Uni<ValidatedPlan> validate(
        PlanProposal proposal,
        PlanningContext context
    );
}
```

Validate:

```text
- duplicate task IDs
- missing dependencies
- cycles
- invalid task types
- scope violations
- impossible ordering
- missing verification
- missing evidence
```

---

# 21. Cycle detection

For example, model proposes:

```text
A → B
B → C
C → A
```

The planner should reject this immediately.

Use ordinary graph cycle detection.

Don't ask the LLM:

> "Does this graph contain a cycle?"

The runtime can know.

---

# 22. Plan invariants

I'd define these explicitly.

### Invariant 1

Every `MODIFY` task must have a scope.

```text
modify
    ↓
files/symbols required
```

### Invariant 2

Every modification plan must eventually reach verification.

```text
MODIFY
   ↓
VERIFY
```

### Invariant 3

Every verification task must specify its target.

```text
VERIFY
    ↓
tests/build/static analysis/etc.
```

### Invariant 4

No task can depend on itself.

### Invariant 5

Graph must be acyclic.

### Invariant 6

User approval tasks must pause execution.

---

# 23. Plan validation should be partly deterministic

For example:

```java
if (task.type() == TaskType.MODIFY
        && task.scope().isEmpty()) {
    reject("Modification has no scope");
}
```

Again:

```text
LLM proposes
runtime validates
```

This is one of the central Aljabr principles.

---

# 24. Ready-task scheduler

Now we can actually execute the graph.

```java
public interface TaskScheduler {

    Uni<List<CodingTask>> readyTasks(
        TaskGraph graph,
        CodingState state
    );
}
```

For:

```text
A → C
B → C
D independent
```

initial ready set:

```text
A
B
D
```

Then:

```text
A ✓
B ✓
D ✓
 ↓
C READY
```

---

# 25. Parallel execution

This is where the graph pays off.

Suppose:

```text
T1 research API
T2 inspect tests
T3 inspect configuration
```

are independent.

The scheduler can execute:

```text
T1 ─────┐
T2 ─────┼──→ T4
T3 ─────┘
```

Potentially concurrently.

But only if their resource policies permit it.

---

# 26. Don't blindly parallelize modifications

This is important.

Safe:

```text
research A
research B
inspect C
```

Potentially safe:

```text
modify Foo.java
modify Bar.java
```

if scopes are disjoint.

Dangerous:

```text
modify Foo.java
modify Foo.java
```

So add:

```java
public interface TaskConflictDetector {

    ConflictReport detect(
        List<CodingTask> tasks
    );
}
```

---

# 27. Conflict report

```java
public record ConflictReport(
    boolean conflict,
    List<TaskConflict> conflicts
) {}
```

```java
public record TaskConflict(
    TaskId first,
    TaskId second,
    ConflictType type
) {}
```

```java
public enum ConflictType {
    SAME_FILE,
    SAME_SYMBOL,
    OVERLAPPING_SCOPE,
    SHARED_RESOURCE,
    WORKSPACE_MUTATION
}
```

---

# 28. Resource locks

Wayang can potentially provide the generic mechanism.

Aljabr declares:

```text
T3
requires:
    workspace.write(TokenValidator.java)
```

Then Wayang can serialize conflicting operations.

This is exactly the kind of capability that belongs underneath Aljabr.

---

# 29. Planner should reason in capabilities

A task could say:

```java
public record TaskRequirement(
    ResourceType resource,
    String identifier,
    AccessMode mode
) {}
```

Example:

```text
WORKSPACE
TokenValidator.java
WRITE
```

or:

```text
REPOSITORY_INDEX
READ
```

or:

```text
TEST_RUNNER
auth-tests
EXECUTE
```

Then Wayang's scheduler/locking machinery can enforce it.

---

# 30. This is the bridge to generic Wayang orchestration

Wayang eventually has:

```text
Execution
 ├── Tasks
 ├── Dependencies
 ├── Resources
 ├── Checkpoints
 ├── Events
 └── Policies
```

Aljabr supplies:

```text
coding tasks
coding dependencies
coding resource requirements
```

Wayang executes them.

That's exactly the separation we want.

---

# 31. Partial completion

Suppose:

```text
T1 ✓
T2 ✓
T3 ✓
T4 FAILED
T5 BLOCKED
T6 BLOCKED
```

The plan should not be thrown away.

Persist:

```text
plan v1
completed:
  T1
  T2
  T3

failed:
  T4

blocked:
  T5
  T6
```

Then recovery can resume from T4.

---

# 32. Replanning should preserve successful work

This connects to the previous milestone.

Bad:

```text
REPLAN
 ↓
new plan
 ↓
everything starts again
```

Better:

```text
Plan v1
 T1 ✓
 T2 ✓
 T3 ✓
 T4 ✗

REPLAN

Plan v2
 T1 IMPORTED ✓
 T2 IMPORTED ✓
 T3 IMPORTED ✓
 T4 REPLACED
 T5 NEW
```

This is **plan reconciliation**.

---

# 33. `PlanReconciler`

```java
public interface PlanReconciler {

    Uni<CodingPlan> reconcile(
        CodingPlan oldPlan,
        PlanProposal newProposal,
        CodingState state
    );
}
```

It determines:

```text
KEEP
REPLACE
REMOVE
ADD
INVALIDATE
```

for each task.

---

# 34. Task identity matters here

Suppose v1:

```text
inspect.token-validator
```

and v2 contains the same conceptual task.

Keep the same ID.

Then:

```text
old result
   ↓
still applicable
   ↓
reuse
```

This is why stable task identity is so important.

---

# 35. Plan checkpoints

After meaningful graph progress:

```text
task started
task completed
task failed
plan revised
workspace mutated
user approval
```

persist a checkpoint.

```java
public record PlanCheckpoint(
    String planId,
    String executionId,
    String graphFingerprint,
    String workspaceRevision,
    Instant createdAt
) {}
```

Now process crashes are recoverable.

---

# 36. Crash recovery

Suppose:

```text
Aljabr process
 ↓
T3 MODIFY
 ↓
process crashes
```

On restart:

```text
load checkpoint
 ↓
inspect workspace
 ↓
compare workspace revision
 ↓
determine T3 state
 ↓
resume / rollback / recover
```

Don't blindly rerun T3.

---

# 37. Idempotency

Every mutating task should declare whether it is safe to retry.

```java
public enum RetrySafety {
    IDEMPOTENT,
    CONDITIONALLY_IDEMPOTENT,
    NON_IDEMPOTENT
}
```

Example:

```text
format file
    IDEMPOTENT

apply patch
    CONDITIONALLY_IDEMPOTENT

database migration
    NON_IDEMPOTENT
```

This will become important once Aljabr operates beyond local source files.

---

# 38. Task retry policy

```java
public record RetryPolicy(
    int maxAttempts,
    RetrySafety safety,
    Duration backoff
) {}
```

But coding-agent retry should also consult the recovery layer:

```text
task failed
 ↓
retry?
 ↓
FailureClassifier
 ↓
RecoveryPolicy
```

So scheduler and recovery don't become duplicated systems.

---

# 39. The full planner architecture

We're now at:

```text
                       USER OBJECTIVE
                              │
                              ▼
                        PLAN PROPOSAL
                              │
                              ▼
                       PLAN VALIDATOR
                              │
                              ▼
                     PLAN RECONCILER
                              │
                              ▼
                    EXECUTABLE TASK GRAPH
                              │
                              ▼
                         SCHEDULER
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
              TASK          TASK          TASK
                │             │             │
                └─────────────┼─────────────┘
                              ▼
                          WAYANG
                              │
                       resources/tools
                              │
                              ▼
                           RESULT
                              │
                 ┌────────────┴────────────┐
                 ▼                         ▼
              SUCCESS                    FAILURE
                 │                         │
                 ▼                         ▼
             NEXT TASK                  RECOVERY
                                           │
                                ┌──────────┼─────────┐
                                ▼          ▼         ▼
                              REPAIR    RESEARCH   REPLAN
```

---

# 40. The practical package structure

I'd now have:

```text
aljabr/
├── planning/
│   ├── CodingPlan.java
│   ├── PlanProposal.java
│   ├── PlanValidator.java
│   ├── PlanReconciler.java
│   └── PlanRevision.java
│
├── task/
│   ├── CodingTask.java
│   ├── TaskId.java
│   ├── TaskType.java
│   ├── TaskStatus.java
│   ├── TaskDependency.java
│   ├── TaskScheduler.java
│   ├── TaskExecutor.java
│   └── TaskConflictDetector.java
│
├── execution/
│   ├── CodingExecutionContext.java
│   ├── TaskExecutionResult.java
│   ├── PlanCheckpoint.java
│   └── RetryPolicy.java
│
├── invalidation/
│   ├── TaskInvalidation.java
│   ├── VerificationDependency.java
│   └── InvalidationEngine.java
│
└── recovery/
    └── ...
```

---

# 41. One architectural improvement I'd make now

At this stage, **don't let `AljabrStrategy` orchestrate all of this directly**.

If you end up with:

```java
class AljabrStrategy {

    inspect();
    research();
    plan();
    execute();
    repair();
    replan();
    verify();
    review();
    remember();
    ...
}
```

stop.

Instead:

```text
AljabrStrategy
      │
      ▼
CodingOrchestrator
      │
 ┌────┼────────┬─────────┐
 ▼    ▼        ▼         ▼
Plan Task   Recovery  Evidence
```

The strategy should primarily translate the user objective into the **coding orchestration request**.

---

# 42. `CodingOrchestrator`

```java
public interface CodingOrchestrator {

    Uni<CodingResult> execute(
        CodingRequest request
    );
}
```

Implementation:

```java
public final class DefaultCodingOrchestrator
        implements CodingOrchestrator {

    private final Planner planner;
    private final TaskScheduler scheduler;
    private final RecoveryController recovery;
    private final EvidenceStore evidence;

    ...
}
```

Now the system has a clean center.

---

# 43. End-to-end example

User:

```text
"Fix token expiration and add a regression test."
```

Planner produces:

```text
T1 RESEARCH
   locate expiration logic

T2 INSPECT
   inspect TokenValidator
   depends T1

T3 MODIFY
   fix expiration comparison
   depends T2

T4 TEST
   add regression test
   depends T2

T5 VERIFY
   run affected tests
   depends T3 + T4

T6 REVIEW
   inspect final diff
   depends T5
```

Scheduler:

```text
T1
 ↓
T2
 ├── T3
 └── T4
      ↓
     T5
      ↓
     T6
```

T3 and T4 can potentially run in parallel **if their scopes don't conflict**.

That is a meaningful improvement over a linear agent loop.

---

# 44. And now the really powerful part

Because every task has:

```text
identity
dependencies
scope
requirements
status
effects
evidence
workspace revision
```

you can ask Wayang:

> "Continue this coding task."

and it doesn't need to reconstruct everything.

It can load:

```text
execution
 ↓
plan
 ↓
task graph
 ↓
current task statuses
 ↓
workspace revision
 ↓
evidence
 ↓
memory
 ↓
resume
```

That is **true resumability**, rather than "ask the model to remember what it was doing."

---

# 45. Next milestone after this

Once the task graph exists, I'd tackle the **Aljabr Coding Tool Contract**.

That means defining the actual tools the model sees:

```text
inspect_symbol
find_references
find_tests
read_file
search_code
apply_patch
run_tests
run_build
get_diff
check_scope
request_replan
request_user_approval
```

but with a crucial design:

> **The model should see high-level, safe coding tools—not raw unrestricted Wayang internals.**

Then we'll define each tool's:

```text
input schema
output schema
preconditions
permissions
side effects
evidence generated
workspace effects
failure semantics
retry semantics
```

That milestone is where Aljabr becomes **actually implementable as an agent**, rather than just an architecture.


Absolutely. **Next milestone: Aljabr Coding Tool Contract.**

Now we turn the planner/task graph into something the model can actually operate. The key is to make tools **small, typed, observable, permissioned, and reversible where possible**.

The architecture should be:

```text
LLM
 │
 ▼
Aljabr Tool Contract
 │
 ├── inspect
 ├── search
 ├── modify
 ├── verify
 ├── git/diff
 └── orchestration
 │
 ▼
Wayang Capability Layer
 │
 ▼
Workspace / Repository / Runtime
```

The LLM should **never directly manipulate Wayang internals**.

---

# 1. First principle: tools are contracts, not methods

Don't expose something like:

```java
wayang.execute(...)
```

That is too powerful.

Instead:

```java
interface AljabrTool<I, O> {

    ToolDescriptor descriptor();

    Uni<ToolResult<O>> execute(
        I input,
        ToolExecutionContext context
    );
}
```

Every tool therefore has:

```text
identity
input
output
permissions
preconditions
side effects
evidence
failure semantics
```

---

# 2. Tool descriptor

```java
public record ToolDescriptor(
    String name,
    String description,
    ToolCategory category,
    JsonSchema inputSchema,
    JsonSchema outputSchema,
    ToolCapabilities capabilities
) {}
```

For example:

```text
name:
aljabr.read_file

category:
INSPECTION

capabilities:
READ_WORKSPACE
```

---

# 3. Tool categories

Keep them small:

```java
public enum ToolCategory {

    INSPECTION,

    RESEARCH,

    MODIFICATION,

    VERIFICATION,

    VERSION_CONTROL,

    ORCHESTRATION
}
```

---

# 4. The first tool set

I'd start with roughly this:

```text
INSPECTION
────────────────────────
read_file
search_code
inspect_symbol
find_references
find_tests

RESEARCH
────────────────────────
research_repository
research_external

MODIFICATION
────────────────────────
apply_patch
create_file
delete_file
rename_symbol

VERIFICATION
────────────────────────
run_tests
run_build
run_static_analysis

VERSION_CONTROL
────────────────────────
get_diff
get_status

ORCHESTRATION
────────────────────────
request_replan
request_user_approval
```

Don't build 40 tools immediately.

These are enough to make a serious coding agent.

---

# 5. `read_file`

Input:

```java
public record ReadFileRequest(
    String path,
    Optional<Integer> startLine,
    Optional<Integer> endLine
) {}
```

Output:

```java
public record ReadFileResult(
    String path,
    String content,
    int startLine,
    int endLine,
    String contentHash,
    String workspaceRevision
) {}
```

Notice:

```text
contentHash
workspaceRevision
```

Those are important.

The agent knows **exactly which version it read**.

---

# 6. Don't allow unrestricted file reads

The tool should check:

```text
workspace boundary
path validity
file size
binary/text classification
permissions
```

For example:

```java
Path safePath = workspace.resolve(request.path()).normalize();

if (!safePath.startsWith(workspace)) {
    throw new ToolSecurityException("Path escapes workspace");
}
```

This should be enforced by Wayang's workspace capability, not just by prompting.

---

# 7. `search_code`

```java
public record SearchCodeRequest(
    String query,
    SearchMode mode,
    Optional<String> path,
    int maxResults
) {}
```

Modes:

```java
public enum SearchMode {
    TEXT,
    REGEX,
    SYMBOL,
    SEMANTIC
}
```

Result:

```java
public record SearchCodeResult(
    List<CodeMatch> matches,
    String workspaceRevision
) {}
```

---

# 8. `inspect_symbol`

This should be one of Aljabr's most valuable tools.

```java
public record InspectSymbolRequest(
    String symbol
) {}
```

Output:

```java
public record InspectSymbolResult(
    SymbolDescriptor symbol,
    List<SymbolRelation> relations,
    List<String> evidenceIds
) {}
```

Relations:

```text
CALLS
CALLED_BY
IMPLEMENTS
EXTENDS
USES
DECLARES
REFERENCES
TESTED_BY
```

Now the agent doesn't need to read an entire repository.

---

# 9. `find_references`

```java
public record FindReferencesRequest(
    String symbol,
    ReferenceKind kind,
    int maxResults
) {}
```

Example:

```text
TokenValidator.validate()
```

returns:

```text
AuthService.java:83
LoginHandler.java:51
TokenValidatorTest.java:27
```

Each result becomes evidence.

---

# 10. `find_tests`

This deserves a dedicated tool.

```java
public record FindTestsRequest(
    String target,
    TestSearchStrategy strategy
) {}
```

Strategies:

```java
public enum TestSearchStrategy {
    DIRECT,
    SYMBOL,
    FILE,
    MODULE,
    AFFECTED
}
```

The agent can then ask:

```text
Which tests are relevant to this symbol?
```

rather than guessing.

---

# 11. `apply_patch`

This is the most sensitive coding tool.

Don't expose:

```java
apply(String arbitraryShellCommand)
```

Instead:

```java
public record ApplyPatchRequest(
    String patch,
    List<ExpectedFileState> expectedStates,
    PatchOptions options
) {}
```

Expected state:

```java
public record ExpectedFileState(
    String path,
    String contentHash
) {}
```

This is important because it prevents:

```text
agent read Foo.java
someone changes Foo.java
agent applies old patch
```

without detecting the race.

---

# 12. Optimistic concurrency

The patch operation should check:

```text
expected hash
       ==
current hash
```

If not:

```text
PATCH_CONFLICT
```

Then:

```text
PATCH_CONFLICT
       ↓
research/inspect
       ↓
recalculate patch
```

rather than blindly overwriting.

---

# 13. Patch result

```java
public record ApplyPatchResult(
    String workspaceRevisionBefore,
    String workspaceRevisionAfter,
    List<String> changedFiles,
    List<String> changedSymbols,
    String diff,
    List<String> evidenceIds
) {}
```

Now the modification automatically feeds the evidence graph.

---

# 14. `create_file`

Separate this from patching.

```java
public record CreateFileRequest(
    String path,
    String content,
    Optional<String> expectedParentDirectory
) {}
```

And:

```text
file already exists
    ↓
FAIL
```

unless an explicit overwrite operation is requested.

This prevents accidental destruction.

---

# 15. `delete_file`

Make it intentionally harder.

```java
public record DeleteFileRequest(
    String path,
    String expectedContentHash,
    DeleteReason reason
) {}
```

The tool should require the current hash.

So the model cannot accidentally delete a file that changed since inspection.

---

# 16. Rename

Likewise:

```java
public record RenameFileRequest(
    String source,
    String destination,
    String expectedSourceHash
) {}
```

Eventually Wayang can provide semantic rename support for languages where this is available.

---

# 17. Verification tools

Now:

```java
public record RunTestsRequest(
    TestTarget target,
    TestOptions options
) {}
```

Output:

```java
public record TestResult(
    TestStatus status,
    List<TestCaseResult> tests,
    Duration duration,
    String workspaceRevision,
    List<String> evidenceIds
) {}
```

Statuses:

```java
public enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
    NOT_FOUND
}
```

---

# 18. Don't make `run_tests` just return text

Bad:

```json
{
  "output": "BUILD SUCCESSFUL..."
}
```

Good:

```json
{
  "status": "PASSED",
  "tests": [
    {
      "name": "TokenValidatorTest.expiredToken",
      "status": "PASSED"
    }
  ],
  "workspaceRevision": "W17"
}
```

The model can still receive the human-readable output when useful.

But the runtime should have structured facts.

---

# 19. `run_build`

```java
public record RunBuildRequest(
    BuildTarget target,
    BuildOptions options
) {}
```

Result:

```java
public record BuildResult(
    BuildStatus status,
    List<BuildDiagnostic> diagnostics,
    Duration duration,
    String workspaceRevision,
    List<String> evidenceIds
) {}
```

Diagnostics:

```text
file
line
column
severity
message
code
```

This makes compiler errors directly actionable.

---

# 20. Static analysis

```java
run_static_analysis
```

should similarly produce:

```text
rule
severity
file
location
message
```

Not merely raw console output.

---

# 21. `get_diff`

This should be extremely cheap and deterministic.

```java
public record GetDiffRequest(
    DiffScope scope
) {}
```

Result:

```java
public record DiffResult(
    String diff,
    List<ChangedFile> files,
    String workspaceRevision,
    List<String> evidenceIds
) {}
```

Aljabr should call this before declaring a modification complete.

---

# 22. `get_status`

```java
public record WorkspaceStatus(
    String workspaceRevision,
    List<ChangedFile> changedFiles,
    boolean clean
) {}
```

Useful for detecting:

```text
unexpected external modification
```

---

# 23. Tool permissions

Now we need an actual security model.

```java
public enum ToolPermission {

    READ_WORKSPACE,

    SEARCH_WORKSPACE,

    WRITE_WORKSPACE,

    EXECUTE_TESTS,

    EXECUTE_BUILD,

    VERSION_CONTROL_READ,

    VERSION_CONTROL_WRITE,

    EXTERNAL_NETWORK,

    USER_INTERACTION
}
```

A tool declares what it needs.

---

# 24. Tool policy

```java
public interface ToolPolicy {

    PolicyDecision authorize(
        ToolDescriptor tool,
        ToolExecutionContext context
    );
}
```

Example:

```text
read_file
    READ_WORKSPACE

apply_patch
    WRITE_WORKSPACE

run_tests
    EXECUTE_TESTS

research_external
    EXTERNAL_NETWORK
```

This is much stronger than telling the model:

> "Don't access the internet."

The runtime actually prevents it.

---

# 25. Tool execution context

```java
public record ToolExecutionContext(
    String executionId,
    String taskId,
    WorkspaceContext workspace,
    ToolPolicy policy,
    EvidenceRecorder evidence,
    CancellationToken cancellation
) {}
```

Every invocation is tied to:

```text
execution
task
workspace
policy
evidence
cancellation
```

---

# 26. Every tool call should generate an event

This is very useful.

```java
public record ToolInvocation(
    String id,
    String executionId,
    String taskId,
    String toolName,
    Instant startedAt,
    Instant completedAt,
    ToolInvocationStatus status
) {}
```

Statuses:

```java
SUCCESS
FAILED
DENIED
CANCELLED
TIMEOUT
```

Now observability becomes natural.

---

# 27. Tool events

Emit:

```text
ToolStarted
ToolCompleted
ToolFailed
ToolDenied
```

into Wayang's event system.

This means the UI can eventually show:

```text
Aljabr
 ├─ Searching TokenValidator...
 ├─ Inspecting references...
 ├─ Applying patch...
 ├─ Running tests...
 └─ Reviewing diff...
```

without the model having to generate fake progress messages.

---

# 28. Tool failure taxonomy

Don't just throw:

```java
RuntimeException
```

Define:

```java
public enum ToolFailureType {

    INVALID_INPUT,

    NOT_FOUND,

    PERMISSION_DENIED,

    CONFLICT,

    PRECONDITION_FAILED,

    TIMEOUT,

    EXECUTION_ERROR,

    ENVIRONMENT_ERROR,

    RESOURCE_LIMIT,

    CANCELLED,

    UNKNOWN
}
```

---

# 29. Structured failure

```java
public record ToolFailure(
    ToolFailureType type,
    String message,
    boolean retryable,
    Optional<String> recoveryHint,
    List<String> evidenceIds
) {}
```

Now recovery can make decisions.

For example:

```text
CONFLICT
retryable = false
recovery = RESEARCH

TIMEOUT
retryable = true

PERMISSION_DENIED
retryable = false
recovery = ASK_USER

TEST_FAILURE
retryable = false
recovery = ANALYZE_FAILURE
```

---

# 30. Tool result envelope

Make every tool consistent:

```java
public sealed interface ToolResult<T>
    permits ToolSuccess, ToolFailureResult {}
```

Success:

```java
public record ToolSuccess<T>(
    T value,
    List<String> evidenceIds,
    ToolEffects effects
) implements ToolResult<T> {}
```

Failure:

```java
public record ToolFailureResult<T>(
    ToolFailure failure
) implements ToolResult<T> {}
```

Now the orchestrator doesn't need tool-specific error handling everywhere.

---

# 31. Preconditions

A tool should be able to declare:

```java
public interface ToolPrecondition {

    ValidationResult check(
        ToolExecutionContext context,
        Object input
    );
}
```

Example `apply_patch`:

```text
workspace writable
patch valid
expected hashes current
task owns mutation scope
```

---

# 32. Side effects

Declare them.

```java
public enum SideEffect {

    NONE,

    READS_WORKSPACE,

    WRITES_WORKSPACE,

    EXECUTES_PROCESS,

    NETWORK_ACCESS,

    MODIFIES_GIT_STATE
}
```

This becomes useful for:

* permissions
* scheduling
* audit
* retries
* parallelism

---

# 33. Tool risk level

I'd also introduce:

```java
public enum ToolRisk {

    SAFE,

    LOW,

    MODERATE,

    HIGH
}
```

Example:

```text
read_file       SAFE
search_code     SAFE
run_tests       LOW
apply_patch     MODERATE
delete_file     HIGH
git_push        HIGH
```

This can feed approval policies.

---

# 34. User approval becomes policy-driven

Instead of:

```text
LLM: "Can I do this?"
```

Wayang determines:

```text
tool risk
+
project policy
+
user policy
+
task scope
```

Then:

```text
ALLOW
DENY
ASK
```

Example:

```text
apply_patch inside workspace
→ ALLOW

delete 50 files
→ ASK

git push
→ ASK

network request to external API
→ ASK/DENY
```

Much cleaner.

---

# 35. Tool scope

A tool invocation should know:

```java
public record ToolScope(
    Set<String> allowedPaths,
    Set<String> allowedSymbols,
    Set<String> allowedCommands
) {}
```

So a task saying:

```text
modify TokenValidator.java
```

cannot suddenly modify:

```text
production-secrets.env
```

because the tool policy rejects it.

---

# 36. Command execution

I would **not** expose arbitrary shell execution as the primary coding tool.

Prefer:

```text
run_tests
run_build
run_static_analysis
```

These are semantic.

If you eventually need:

```text
run_command
```

make it a restricted lower-level capability:

```java
public record CommandPolicy(
    Set<String> allowedExecutables,
    Set<String> allowedWorkingDirectories,
    Duration timeout,
    long maxOutputBytes
) {}
```

And let Wayang enforce it.

---

# 37. Why semantic tools matter

Compare:

```text
run_command:
"grep -R TokenValidator ."
```

with:

```text
search_code:
query = TokenValidator
mode = SYMBOL
```

The latter gives Wayang enough structure to:

```text
index
cache
audit
optimize
limit
parallelize
```

That's much more powerful.

---

# 38. Tool result → evidence

Every meaningful tool should produce evidence automatically.

For example:

```text
read_file
    ↓
EvidenceType.FILE_CONTENT

search_code
    ↓
EvidenceType.REFERENCE

apply_patch
    ↓
EvidenceType.DIFF

run_tests
    ↓
EvidenceType.TEST_RESULT

run_build
    ↓
EvidenceType.BUILD_RESULT
```

The model doesn't need to manually "remember" this.

---

# 39. Tool result → task effects

Likewise:

```text
apply_patch
    ↓
changedFiles
changedSymbols
workspaceRevision
```

and:

```text
run_tests
    ↓
verification dependencies
```

This directly feeds the task graph.

---

# 40. The complete execution path

Now we have:

```text
LLM
 │
 │ tool request
 ▼
Tool Gateway
 │
 ├── schema validation
 ├── permission check
 ├── scope check
 ├── precondition check
 ├── resource check
 │
 ▼
Aljabr Tool
 │
 ▼
Wayang Capability
 │
 ▼
Workspace / Runtime
 │
 ▼
Tool Result
 │
 ├── Evidence
 ├── Effects
 ├── Workspace Revision
 └── Failure
 │
 ▼
Task Graph
 │
 ├── advance
 ├── invalidate
 ├── recover
 └── replan
```

This is now a **real agent runtime**, not simply an LLM loop.

---

# 41. Tool registry

Finally:

```java
public interface AljabrToolRegistry {

    void register(AljabrTool<?, ?> tool);

    Optional<AljabrTool<?, ?>> find(String name);

    List<ToolDescriptor> descriptors(
        ToolVisibilityContext context
    );
}
```

The model only receives descriptors for tools it is actually allowed to use.

---

# 42. Don't expose every tool to the model

This is another important optimization.

For example, during research:

```text
read_file
search_code
inspect_symbol
find_references
find_tests
```

During modification:

```text
read_file
apply_patch
create_file
get_diff
```

During verification:

```text
run_tests
run_build
get_diff
```

The planner can dynamically construct the tool set.

Less tool surface → less confusion → fewer bad calls.

---

# 43. Dynamic tool availability

```java
public interface ToolAvailabilityPolicy {

    List<ToolDescriptor> toolsFor(
        CodingTask task,
        CodingExecutionContext context
    );
}
```

For:

```text
RESEARCH
```

don't expose:

```text
delete_file
```

at all.

This is better than exposing everything and hoping the model doesn't use it.

---

# 44. Tool contracts become the "hands" of Aljabr

At this point the conceptual split is beautifully clean:

```text
Wayang
────────────────────────
runtime
resources
permissions
workspace
processes
events
persistence
execution

Aljabr
────────────────────────
coding objective
planning
task graph
research strategy
repair strategy
coding policies

LLM
────────────────────────
interpret
reason
propose
choose
explain
```

And:

```text
LLM ≠ runtime
LLM ≠ memory
LLM ≠ filesystem
LLM ≠ scheduler
LLM ≠ authority
```

That's a very strong foundation.

---

# 45. One more thing I'd add now: tool budgets

Every task should have a budget:

```java
public record ToolBudget(
    int maxCalls,
    Duration maxDuration,
    long maxOutputBytes,
    long maxFilesRead,
    long maxFilesChanged
) {}
```

Then:

```text
research task
    max 30 searches

modify task
    max 10 patches

verification
    max 5 test runs
```

This prevents loops such as:

```text
search
→ search
→ search
→ search
→ search
→ ...
```

---

# 46. Budget exhaustion should be a normal state

Not:

```text
Exception!
```

Instead:

```text
TASK
 ↓
RESOURCE_LIMIT
 ↓
RecoveryPolicy
 ↓
compress / replan / ask user
```

This will become particularly useful when Aljabr runs autonomously.

---

# 47. The milestone implementation order

I would implement this milestone in this exact sequence:

```text
1. AljabrTool<I,O>
2. ToolDescriptor
3. ToolResult
4. ToolFailure
5. ToolRegistry
6. ToolPolicy
7. ToolExecutionContext
8. read_file
9. search_code
10. inspect_symbol
11. find_references
12. find_tests
13. apply_patch
14. get_diff
15. run_tests
16. run_build
17. tool events
18. evidence integration
19. task effects
20. budgets
```

Then add:

```text
create_file
delete_file
rename
static analysis
git operations
external research
```

after the foundation is stable.

---

# 48. Where we are now

The Aljabr architecture is becoming:

```text
                    ALJABR
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
     Planner         Memory        Recovery
        │              │              │
        ▼              ▼              ▼
   Task Graph       Evidence       Failure
        │              │              │
        └──────────────┼──────────────┘
                       ▼
                  Tool Gateway
                       │
                       ▼
                     WAYANG
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Workspace    Processes    Resources
```


