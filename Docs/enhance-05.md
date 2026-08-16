Yes. **Next milestone: Repository Intelligence / Code Index.**

This is especially important for Aljabr on a **12B local model**. The runtime should do repository navigation and structural reasoning cheaply, so Gemma receives only the evidence it needs.

The principle:

> **Don't make the model search the repository. Make the runtime search the repository and let the model reason over the results.**

---

# 1. The architecture

Add a dedicated layer:

```text
                 Aljabr
                    │
             ContextEngine
                    │
            RepositoryIntel
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
     Index       Search      Graph
        │           │           │
        ▼           ▼           ▼
    Symbols      Matches    Relations
        │           │           │
        └───────────┼───────────┘
                    ▼
                 Evidence
                    │
                    ▼
                  Gemma
```

This should **not** become another LLM.

Most of it should be deterministic.

---

# 2. `RepositoryIntelligence`

I'd define:

```java
public interface RepositoryIntelligence {

    Uni<RepositorySnapshot> snapshot();

    Uni<SearchResult> search(SearchRequest request);

    Uni<SymbolInfo> symbol(SymbolRequest request);

    Uni<List<Reference>> references(ReferenceRequest request);

    Uni<List<Dependency>> dependencies(DependencyRequest request);

    Uni<List<RelatedFile>> related(RelatedFileRequest request);
}
```

The important thing is that this is a **query layer**, not an agent.

---

# 3. Repository snapshot

```java
public record RepositorySnapshot(
    String workspaceId,
    String revision,
    String fingerprint,

    List<SourceRoot> sourceRoots,
    List<ProjectModule> modules,

    RepositoryStats stats
) {}
```

Example:

```text
revision: 9c72af1

languages:
Java 82%
SQL 10%
YAML 8%

modules:
core
api
web
tests
```

Gemma shouldn't need to discover these repeatedly.

---

# 4. Workspace fingerprint

This becomes very important.

```java
public record WorkspaceFingerprint(
    String repositoryHash,
    String indexVersion,
    String configurationHash
) {}
```

Whenever the workspace changes:

```text
old fingerprint
      ↓
new fingerprint
      ↓
invalidate affected index entries
```

Not necessarily the entire index.

---

# 5. Don't rebuild the entire index

Suppose:

```text
src/AuthService.java
```

changes.

You shouldn't do:

```text
delete index
re-index 100,000 files
```

Instead:

```text
AuthService.java changed
        ↓
re-index file
        ↓
update symbols
        ↓
update references
        ↓
invalidate affected evidence
```

---

# 6. Start with a file index

The first index doesn't need AST intelligence.

```java
public record FileRecord(
    Path path,
    String contentHash,
    long size,
    String language,
    boolean generated,
    boolean test
) {}
```

This alone gives you useful capabilities.

---

# 7. File classification

During discovery:

```text
src/
test/
build/
generated/
vendor/
node_modules/
.git/
```

classify them.

```java
public enum FileRole {

    SOURCE,
    TEST,
    CONFIG,
    DOCUMENTATION,
    GENERATED,
    VENDOR,
    BUILD_ARTIFACT,
    UNKNOWN
}
```

This prevents stupid searches.

For example:

> "Find implementation of TokenValidator"

shouldn't return:

```text
README.md
build/classes/...
node_modules/...
generated/...
```

---

# 8. Search index

At minimum:

```text
path
filename
tokens
language
symbols
file role
```

Then:

```java
public record SearchRequest(
    String query,
    SearchScope scope,
    int limit
) {}
```

---

# 9. Search should be multi-stage

Don't immediately use expensive semantic search.

Use:

```text
Stage 1
filename/path match

Stage 2
exact text match

Stage 3
symbol match

Stage 4
reference match

Stage 5
semantic retrieval
```

Only go deeper when necessary.

---

# 10. Why this is perfect for local inference

Imagine Gemma asks:

> "Where is token expiration handled?"

Instead of:

```text
LLM reads 300 files
```

runtime does:

```text
search:
expiration
expired
expires
expiry
token
```

and returns:

```text
TokenValidator.java:71
JwtService.java:103
TokenClaims.java:44
ExpirationPolicy.java:19
```

Gemma then reasons over **four locations**.

---

# 11. `SearchResult`

```java
public record SearchResult(
    List<SearchMatch> matches,
    SearchMetadata metadata
) {}
```

Metadata:

```java
public record SearchMetadata(
    int totalMatches,
    boolean truncated,
    SearchStrategy strategy
) {}
```

---

# 12. Search strategy should be visible

```java
public enum SearchStrategy {

    PATH,
    TEXT,
    SYMBOL,
    REFERENCE,
    STRUCTURAL,
    SEMANTIC,
    HYBRID
}
```

This is useful for evidence provenance.

---

# 13. Symbol index

Now move up one level.

```java
public record SymbolInfo(
    SymbolId id,
    String name,
    SymbolKind kind,
    Path file,
    SourceRange range,
    Optional<SymbolId> parent
) {}
```

---

# 14. `SymbolKind`

```java
public enum SymbolKind {

    CLASS,
    INTERFACE,
    ENUM,
    METHOD,
    FUNCTION,
    FIELD,
    VARIABLE,
    PROPERTY,
    CONSTRUCTOR,
    MODULE
}
```

---

# 15. Symbol hierarchy

Suppose:

```java
class TokenValidator {
    boolean validateExpiration(Token token) {
    }
}
```

Index:

```text
TokenValidator
└── validateExpiration
```

Now Gemma can request:

```text
symbol("validateExpiration")
```

rather than reading the entire file.

---

# 16. Symbol identity

Don't identify symbols only by name.

Bad:

```text
validate()
```

Better:

```text
com.example.auth.TokenValidator#validateExpiration(Token)
```

Create:

```java
public record SymbolId(
    String qualifiedName,
    String signature,
    String contentHash
) {}
```

---

# 17. References

Now build a lightweight graph:

```text
TokenValidator.validateExpiration
          │
     ┌────┼─────┐
     ▼    ▼     ▼
AuthService
LoginService
RefreshService
```

API:

```java
public record Reference(
    SymbolId source,
    SymbolId target,
    ReferenceKind kind,
    SourceRange location
) {}
```

---

# 18. Reference kinds

```java
public enum ReferenceKind {

    CALL,
    IMPORT,
    EXTENDS,
    IMPLEMENTS,
    READ,
    WRITE,
    INSTANTIATE,
    OVERRIDE
}
```

This lets Aljabr reason about impact.

---

# 19. Dependency graph

Separate symbol references from project dependencies.

```text
Project
 ├── module
 │    ├── dependency
 │    └── source
```

Example:

```text
web
 ↓
api
 ↓
core
```

Then if Gemma wants to change:

```text
core/AuthService
```

the runtime can identify potentially affected modules.

---

# 20. `Dependency`

```java
public record Dependency(
    String source,
    String target,
    DependencyKind kind
) {}
```

---

# 21. Dependency kinds

```java
public enum DependencyKind {

    MODULE,
    PACKAGE,
    LIBRARY,
    FILE,
    SYMBOL
}
```

---

# 22. Related files

This is where things get really useful.

Suppose:

```text
AuthService.java
```

is being modified.

`related()` can return:

```text
AuthServiceTest.java
AuthController.java
TokenValidator.java
AuthRepository.java
```

based on:

```text
references
imports
tests
same module
recent changes
dependency graph
```

---

# 23. `RelatedFile`

```java
public record RelatedFile(
    Path path,
    double score,
    List<RelationReason> reasons
) {}
```

Example:

```text
AuthServiceTest.java
score: 0.94
reasons:
- tests AuthService
- references validate()
```

This is much more useful than a raw search result.

---

# 24. `RelationReason`

```java
public enum RelationReason {

    DIRECT_REFERENCE,
    TEST_TARGET,
    IMPORT,
    DEPENDENCY,
    SAME_MODULE,
    SAME_SYMBOL,
    RECENT_CHANGE,
    CONFIGURATION,
    TEXT_SIMILARITY
}
```

---

# 25. Impact analysis

Now add:

```java
public interface ImpactAnalyzer {

    Uni<ImpactReport> analyze(
        ChangeSet changeSet
    );
}
```

Example:

```text
Changing:
TokenValidator.validateExpiration()

Potential impact:

HIGH
AuthService
LoginController

MEDIUM
RefreshService

TESTS
TokenValidatorTest
AuthServiceTest
```

Gemma receives this instead of rediscovering it.

---

# 26. Impact report

```java
public record ImpactReport(
    List<ImpactNode> affectedSymbols,
    List<ImpactNode> affectedFiles,
    List<Path> likelyTests,
    ImpactConfidence confidence
) {}
```

---

# 27. Test discovery

This deserves its own intelligence.

Given:

```text
src/AuthService.java
```

find:

```text
AuthServiceTest.java
AuthServiceIT.java
```

and perhaps:

```text
TokenValidatorTest.java
```

through references.

API:

```java
public interface TestDiscovery {

    Uni<List<TestCandidate>> findTests(
        ChangeSet changeSet
    );
}
```

---

# 28. `TestCandidate`

```java
public record TestCandidate(
    String selector,
    Path file,
    double relevance,
    List<String> reasons
) {}
```

Then the verifier can choose:

```text
targeted test
```

before:

```text
whole project
```

---

# 29. This connects directly to our verification milestone

The loop becomes:

```text
PATCH
 ↓
ImpactAnalyzer
 ↓
TestDiscovery
 ↓
Targeted tests
 ↓
PASS?
```

This can dramatically reduce test cost.

---

# 30. Change-aware indexing

Every mutation produces:

```text
old hash
new hash
changed ranges
changed symbols
```

Create:

```java
public record ChangeSet(
    List<FileChange> files,
    List<SymbolChange> symbols
) {}
```

---

# 31. `FileChange`

```java
public record FileChange(
    Path path,
    ChangeType type,
    String oldHash,
    String newHash,
    List<SourceRange> changedRanges
) {}
```

---

# 32. `ChangeType`

```java
public enum ChangeType {

    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
}
```

---

# 33. Don't invalidate everything

When:

```text
AuthService.java
```

changes:

invalidate:

```text
AuthService symbol evidence
AuthService references
related context
affected test discovery
```

but keep:

```text
TokenValidator.java
```

if untouched.

This is how the system remains fast.

---

# 34. Index tiers

I'd actually use **three tiers**.

### Tier 1 — always available

```text
file index
path
hash
language
role
```

### Tier 2 — cheap structural

```text
symbols
imports
references
dependencies
tests
```

### Tier 3 — expensive

```text
embeddings
semantic summaries
deep architecture analysis
```

For your local setup:

> Tier 1 + Tier 2 should do most of the work.

---

# 35. Don't start with embeddings

This is important.

You don't need:

```text
vector database
+
embeddings
+
semantic retrieval
```

to build a good coding agent.

First build:

```text
exact search
symbol search
reference graph
dependency graph
test mapping
```

These are much more deterministic.

---

# 36. Semantic retrieval becomes a fallback

For example:

```text
User:
"Where do we handle invalid sessions?"
```

Exact search may find nothing.

Then:

```text
semantic search
```

can find:

```text
SessionGuard.java
AuthenticationFilter.java
SessionManager.java
```

Now Gemma reasons over those.

---

# 37. Repository summaries

One more useful optimization:

Store **small generated summaries**, but don't regenerate them constantly.

```java
public record FileSummary(
    Path file,
    String summary,
    List<String> responsibilities,
    List<SymbolId> keySymbols,
    String sourceHash
) {}
```

Important:

```text
sourceHash
```

means the summary is tied to the exact source state.

---

# 38. Summary invalidation

If:

```text
sourceHash != currentHash
```

then:

```text
summary = stale
```

Don't let Gemma accidentally reason from outdated summaries.

---

# 39. Architecture summary

Eventually:

```text
Repository
│
├── FileIndex
│
├── SymbolIndex
│
├── ReferenceGraph
│
├── DependencyGraph
│
├── TestIndex
│
├── ChangeIndex
│
└── SummaryStore
```

with:

```text
RepositoryIntelligence
        │
        ├── search()
        ├── symbol()
        ├── references()
        ├── dependencies()
        ├── related()
        └── impact()
```

---

# 40. The key optimization: Evidence packets

Don't pass raw index results directly to Gemma.

Create:

```java
public record RepositoryEvidencePacket(
    List<FileEvidence> files,
    List<SymbolEvidence> symbols,
    List<RelationEvidence> relations,
    List<TestEvidence> tests,
    String summary
) {}
```

Then ContextEngine decides what subset is actually needed.

---

# 41. Example

Instead of giving Gemma:

```text
repository:
4,827 files
```

give:

```text
RELEVANT FILES

1. TokenValidator.java
   - contains validateExpiration()
   - modified recently

2. AuthService.java
   - calls validateExpiration()

3. TokenValidatorTest.java
   - tests expiration behavior

RELEVANT RELATIONS

AuthService
 → TokenValidator.validateExpiration

TESTS

TokenValidatorTest.expiredToken()
```

That's a **small-model-friendly context packet**.

---

# 42. The model's cognitive workload becomes tiny

Gemma only needs to answer:

```text
Given:
- these 3 files
- this symbol
- these 2 references
- this failing test

What should I inspect next?
```

That's dramatically easier than:

```text
Understand this 500,000-token repository.
```

---

# 43. Repository intelligence should also answer "why"

This is important for debugging.

Instead of:

```text
AuthService.java
```

return:

```text
AuthService.java

Why relevant:
- directly calls changed symbol
- changed symbol is on current execution path
- AuthServiceTest covers this path
```

That lets Gemma understand relevance without doing additional reasoning.

---

# 44. `EvidenceGraph`

At this point your evidence graph becomes extremely valuable.

```text
E104
TokenValidator.validateExpiration
       │
       ├── referenced by → E120 AuthService
       ├── tested by → E133 TokenValidatorTest
       └── changed in → E141 ChangeSet
```

So context retrieval becomes graph traversal rather than blind search.

---

# 45. Context retrieval example

Current active step:

```text
Fix failing expiration test
```

ContextEngine asks:

```text
What evidence is connected to:
TokenValidator.validateExpiration?
```

Graph gives:

```text
symbol
 ↓
implementation
 ↓
callers
 ↓
tests
 ↓
failure
 ↓
recent changes
```

Then rank them.

---

# 46. Evidence ranking

```java
public record EvidenceScore(
    EvidenceId evidence,
    double relevance,
    double freshness,
    double confidence,
    double cost
) {}
```

Then:

```text
score =
    relevance
    + freshness
    + confidence
    - cost
```

Again, exact formula belongs in policy.

---

# 47. Context budget integration

This is where the earlier `ContextController` finally becomes useful.

Suppose:

```text
budget = 6,000 tokens
```

ContextEngine might choose:

```text
task                    150
active step             100
failure                 250
implementation          800
caller                  400
test                    500
diff                    300
tool definitions        500
----------------------------
total                  3,000
```

It still has room.

Then maybe add:

```text
related configuration
```

if needed.

---

# 48. With a cloud model

Same system.

If budget:

```text
40,000 tokens
```

ContextController can include:

```text
more callers
more tests
more architecture
more history
more implementation
```

No architecture change.

This is exactly why your earlier **CapabilityDetector → ContextController → ContextPolicy** abstraction was the right decision.

---

# 49. Repository intelligence should be model-independent

Very important:

```text
RepositoryIntelligence
```

should not know:

```text
Gemma
GPT
Claude
etc.
```

It produces facts.

Then:

```text
ContextPolicy
```

decides how much to expose.

---

# 50. Recommended implementation order

I would build this milestone in this order:

### R1 — File index

```text
FileIndex
WorkspaceFingerprint
FileClassifier
```

### R2 — Search

```text
PathSearch
TextSearch
SearchRanking
```

### R3 — Symbols

```text
SymbolIndex
SymbolResolver
```

### R4 — References

```text
ReferenceGraph
ReferenceResolver
```

### R5 — Tests

```text
TestIndex
TestDiscovery
```

### R6 — Impact

```text
ChangeSet
ImpactAnalyzer
```

### R7 — Semantic fallback

```text
EmbeddingIndex
SemanticSearch
```

Only after the first six are working well.

---

# 51. First practical vertical slice

Make this work before anything sophisticated:

```text
repository opened
       ↓
scan files
       ↓
classify files
       ↓
index text
       ↓
user asks coding task
       ↓
search relevant files
       ↓
return top 10
       ↓
Gemma chooses one
       ↓
read bounded region
```

Then:

```text
symbol index
       ↓
reference graph
       ↓
test discovery
```

can be added incrementally.

---

# 52. The deeper architectural result

At this point Aljabr isn't:

```text
LLM + terminal
```

It's becoming:

```text
                ┌──────────────┐
                │    Gemma     │
                │   Reasoner   │
                └──────┬───────┘
                       │
                 small context
                       │
                ┌──────▼───────┐
                │ ContextEngine│
                └──────┬───────┘
                       │
              relevant evidence
                       │
        ┌──────────────▼──────────────┐
        │ Repository Intelligence     │
        │                             │
        │ files / symbols / refs      │
        │ dependencies / tests / diff │
        └──────────────┬──────────────┘
                       │
                 Workspace
```

**This is the direction I'd strongly recommend for Aljabr.**

The next milestone after this should be **`Change Intelligence → Patch Planning → Safe Mutation`**: taking repository intelligence and turning it into *minimal, dependency-aware patches*, including patch validation, conflict detection, atomic application, rollback, and eventually multi-file change planning. That will connect this repository layer directly to the `apply_patch → verify → replan` loop we just built.


Absolutely. **Next milestone: `Change Intelligence → Patch Planning → Safe Mutation`.**

This is the bridge between the repository intelligence we just built and actual code modification.

The core principle:

> **Aljabr should never treat “edit code” as a raw text-generation problem. It should treat it as a controlled state transition from one known workspace state to another.**

For a 12B local model, this is especially valuable because the runtime can enforce correctness instead of expecting Gemma to reason about every edge case.

---

# 1. The complete mutation pipeline

We now want:

```text
Repository Intelligence
        │
        ▼
Impact Analysis
        │
        ▼
Change Plan
        │
        ▼
Patch Proposal
        │
        ▼
Patch Validator
        │
        ▼
Mutation Gate
        │
        ▼
Atomic Apply
        │
        ▼
Diff / ChangeSet
        │
        ▼
Verification
        │
   ┌────┴────┐
 PASS       FAIL
   │          │
 DONE      Diagnose
              │
           Replan
```

The important addition is:

**Gemma proposes the change; Wayang validates and applies it.**

---

# 2. `ChangePlan`

Don't immediately ask Gemma for a patch.

First create a small change plan:

```java
public record ChangePlan(
    ChangePlanId id,
    String objective,
    List<PlannedChange> changes,
    List<EvidenceId> evidence,
    ChangeRisk risk
) {}
```

Example:

```text
Objective:
Fix expired-token acceptance.

Changes:
1. TokenValidator.java
   modify validateExpiration()

2. TokenValidatorTest.java
   add expired-token regression test
```

This is easier for a small model to reason about than a giant patch.

---

# 3. `PlannedChange`

```java
public record PlannedChange(
    Path path,
    ChangeIntent intent,
    List<SymbolId> affectedSymbols,
    List<EvidenceId> evidence
) {}
```

---

# 4. `ChangeIntent`

Keep the vocabulary small:

```java
public enum ChangeIntent {

    MODIFY_LOGIC,
    ADD_TEST,
    FIX_TYPE,
    RENAME,
    ADD_SYMBOL,
    REMOVE_SYMBOL,
    UPDATE_CONFIGURATION,
    UPDATE_DOCUMENTATION
}
```

Don't let the model invent arbitrary change categories.

---

# 5. Why separate intent from patch?

Because this:

```text
"modify validateExpiration"
```

is semantic.

While this:

```diff
- if (expiration > now)
+ if (expiration >= now)
```

is textual.

The runtime can verify whether the textual patch actually corresponds to the intended semantic change.

---

# 6. `PatchProposal`

```java
public record PatchProposal(
    ChangePlanId planId,
    List<FilePatch> patches,
    List<EvidenceId> supportingEvidence
) {}
```

---

# 7. File patch

```java
public record FilePatch(
    Path path,

    String expectedContentHash,

    List<PatchHunk> hunks
) {}
```

This expected hash is critical.

---

# 8. Patch hunk

```java
public record PatchHunk(
    SourceRange target,
    String expectedOldText,
    String replacementText
) {}
```

So the model doesn't merely say:

```text
"replace lines 70-75"
```

It says:

```text
Expected:
if (expiration > now)

Replace:
if (expiration >= now)
```

That gives the runtime something concrete to validate.

---

# 9. Why this matters

Suppose another process changed the file.

Gemma's patch expects:

```text
if (expiration > now)
```

but the file now contains:

```text
if (expiration >= now)
```

Runtime says:

```text
PRECONDITION_FAILED
STALE_TARGET
```

and **does not modify the file**.

---

# 10. Patch validation

Create:

```java
public interface PatchValidator {

    PatchValidationResult validate(
        PatchProposal proposal,
        WorkspaceSnapshot workspace
    );
}
```

---

# 11. Validation stages

I would use:

```text
1. Schema validation
2. Path validation
3. File existence
4. Content freshness
5. Hunk matching
6. Syntax validation
7. Change-size validation
8. Policy validation
9. Conflict validation
```

Only then:

```text
APPLY
```

---

# 12. Schema validation

Catch:

```text
missing path
missing replacement
invalid range
malformed patch
```

before touching the filesystem.

This should be deterministic.

---

# 13. Path validation

Never allow:

```text
../../something
```

or arbitrary filesystem paths.

Normalize:

```java
Path normalized =
    workspaceRoot.resolve(path).normalize();
```

Then ensure:

```java
normalized.startsWith(workspaceRoot)
```

---

# 14. File policy

Also reject:

```text
.git/
node_modules/
build/
target/
generated/
```

unless explicitly allowed.

Your repository classifier can provide this information.

---

# 15. Content freshness

For every file:

```text
expectedHash
      │
      ▼
currentHash
```

If:

```text
expectedHash != currentHash
```

stop.

This is essentially optimistic concurrency control for code editing.

---

# 16. Hunk matching

Even if the hash matches, validate the actual expected text.

```java
boolean matches =
    currentText.contains(expectedOldText);
```

For more robust matching, use:

```text
exact
normalized whitespace
AST-aware
```

in progressively more permissive modes.

---

# 17. Never silently fuzzy-match

This is dangerous:

```text
"close enough"
```

For mutation, ambiguity should result in:

```text
PATCH_AMBIGUOUS
```

not:

```text
guess where to put it
```

---

# 18. Patch validation result

```java
public record PatchValidationResult(
    ValidationStatus status,
    List<PatchIssue> issues,
    List<PatchWarning> warnings
) {}
```

Statuses:

```java
public enum ValidationStatus {

    VALID,
    INVALID,
    STALE,
    CONFLICT,
    POLICY_BLOCKED
}
```

---

# 19. Change risk

Now use the repository intelligence to estimate impact.

```java
public record ChangeRisk(
    RiskLevel level,
    List<String> reasons
) {}
```

Example:

```text
MEDIUM

Reasons:
- public API changed
- 4 callers affected
- 2 modules affected
```

---

# 20. Risk should influence the workflow

Example policy:

```text
LOW
→ automatic

MEDIUM
→ automatic + targeted verification

HIGH
→ stronger verification

CRITICAL
→ user confirmation
```

The model doesn't decide this.

**Policy does.**

---

# 21. `MutationGate`

This becomes the final checkpoint.

```java
public interface MutationGate {

    Uni<MutationAuthorization> authorize(
        PatchProposal proposal,
        ChangeRisk risk,
        AgentState state
    );
}
```

Possible result:

```text
ALLOW
DENY
REQUIRE_USER
```

---

# 22. This is where Wayang's policy system shines

You can have:

```java
MutationPolicy
```

containing:

```text
allowed paths
maximum files
maximum diff size
allowed operations
risk thresholds
confirmation rules
```

Then different agents can have different policies.

---

# 23. Example Aljabr local policy

For normal coding:

```text
allow:
  src/**
  test/**
  docs/**

deny:
  .git/**
  secrets/**
  build/**
```

And:

```text
max files:
5

max added lines:
300

max deleted lines:
150
```

Again, these should be configuration rather than hard-coded constants.

---

# 24. Atomic application

This is another important piece.

Don't:

```text
write file A
write file B
write file C
```

and then discover:

```text
file C failed
```

Now the workspace is partially modified.

Instead:

```text
prepare
 ↓
validate all
 ↓
stage all
 ↓
commit atomically
```

---

# 25. `MutationTransaction`

```java
public interface MutationTransaction {

    Uni<MutationTransaction> prepare(
        PatchProposal proposal
    );

    Uni<MutationResult> commit();

    Uni<Void> rollback();
}
```

---

# 26. Filesystem implementation

For a multi-file change:

```text
workspace
├── original A
├── original B
│
└── staging/
    ├── A
    └── B
```

Apply changes into staging.

Then replace the affected files.

If anything fails:

```text
rollback
```

---

# 27. Better: use snapshots

Before mutation:

```text
WorkspaceSnapshot
```

contains:

```text
path
hash
size
mtime
```

After mutation:

```text
WorkspaceSnapshot'
```

Then calculate:

```text
ChangeSet
```

from the two snapshots.

---

# 28. `MutationResult`

```java
public record MutationResult(
    MutationId id,
    ChangeSet changes,
    List<EvidenceId> evidence,
    WorkspaceFingerprint newFingerprint
) {}
```

Now the mutation itself produces evidence.

---

# 29. Git integration

If Git exists, leverage it.

Before:

```text
git status
```

After:

```text
git diff
git status
```

But don't make Git mandatory.

Aljabr should work in:

```text
Git repository
```

and:

```text
plain workspace
```

---

# 30. Git should be an additional safety layer

For a Git workspace:

```text
before mutation
→ capture HEAD/status

after mutation
→ capture diff

if rollback:
→ restore only agent-owned changes
```

Be careful not to destroy the user's pre-existing modifications.

---

# 31. Existing user changes are sacred

Suppose before Aljabr starts:

```text
M AuthService.java
M README.md
```

Aljabr should record:

```text
USER_BASELINE
```

Then if it changes:

```text
AuthService.java
```

it must distinguish:

```text
user change
+
agent change
```

This is crucial.

---

# 32. `WorkspaceBaseline`

```java
public record WorkspaceBaseline(
    WorkspaceFingerprint fingerprint,
    Map<Path, String> contentHashes,
    GitState gitState
) {}
```

Then the runtime knows:

```text
what existed before me?
what did I change?
```

---

# 33. Never use `git reset --hard` as generic rollback

That could destroy user work.

Instead:

```text
agent-owned changes
```

must be tracked individually.

Rollback:

```text
restore agent-owned files
```

while preserving unrelated user changes.

---

# 34. Mutation ownership

Add:

```java
public enum ChangeOwner {

    USER,
    AGENT,
    EXTERNAL,
    UNKNOWN
}
```

Then:

```java
public record FileOwnership(
    Path path,
    ChangeOwner owner
) {}
```

---

# 35. This also solves concurrent edits

Imagine:

```text
Aljabr running
     ↓
user edits AuthService.java
     ↓
Aljabr tries patch
```

Workspace hash changes.

Runtime:

```text
STALE_WORKSPACE
```

Then:

```text
re-observe
→ recompute impact
→ regenerate patch
```

Never blindly overwrite.

---

# 36. Patch regeneration

When stale:

```text
old patch
   ↓
invalid
   ↓
new workspace evidence
   ↓
Gemma
   ↓
new patch
```

This is much safer than trying to automatically merge everything.

---

# 37. Semantic patch validation

Eventually, add:

```java
public interface SemanticPatchValidator {

    ValidationResult validate(
        ChangePlan plan,
        PatchProposal patch
    );
}
```

Example:

Change intent:

```text
"modify expiration comparison"
```

Patch changes:

```text
database configuration
```

Runtime should detect that the patch doesn't match the intent.

---

# 38. Start simple

You don't need a full semantic verifier immediately.

Initially validate:

```text
affected files
affected symbols
diff size
expected text
syntax
```

Then later:

```text
AST
symbol semantics
dependency impact
```

---

# 39. Syntax validation before tests

After applying a patch:

```text
changed Java file
      ↓
parse / compile
```

If syntax is broken:

```text
don't run 5-minute integration tests
```

Return immediately:

```text
COMPILATION_FAILURE
```

This saves time and tokens.

---

# 40. Progressive verification

The mutation loop should be:

```text
PATCH
 ↓
FORMAT/PARSE
 ↓
TARGETED TEST
 ↓
MODULE TEST
 ↓
FULL BUILD
```

Only escalate when needed.

---

# 41. Change size should influence verification

For:

```text
1-line change
```

probably:

```text
targeted test
```

For:

```text
20-file refactor
```

probably:

```text
module tests
+
build
+
possibly broader tests
```

This can be deterministic.

---

# 42. `VerificationPlanner`

```java
public interface VerificationPlanner {

    VerificationPlan plan(
        ChangeSet changes,
        ImpactReport impact
    );
}
```

This connects repository intelligence with the verifier.

---

# 43. Verification plan

```java
public record VerificationPlan(
    List<VerificationAction> actions,
    VerificationLevel level,
    String rationale
) {}
```

Example:

```text
1. compile core
2. run TokenValidatorTest
3. run AuthServiceTest
```

No need for the model.

---

# 44. This is a major local-model optimization

Instead of Gemma generating:

```text
"Maybe run these tests..."
```

the runtime computes:

```text
changed symbols
+
affected modules
+
test graph
```

and determines:

```text
exact tests
```

Gemma can then focus on diagnosis if something fails.

---

# 45. Mutation evidence

Every mutation should produce:

```text
M001
├── old workspace fingerprint
├── new workspace fingerprint
├── files changed
├── symbols changed
├── diff
├── patch proposal
├── validation
└── verification
```

Now you have a complete causal chain:

```text
Task
 ↓
Plan
 ↓
Evidence
 ↓
Patch
 ↓
Mutation
 ↓
Verification
```

---

# 46. This becomes incredibly useful for memory

Don't store:

```text
"Gemma changed AuthService."
```

Store structured knowledge:

```text
Mutation M001

Task:
Fix expired-token validation.

Changed:
TokenValidator.validateExpiration

Evidence:
E104
E107

Verification:
TokenValidatorTest.expiredToken

Result:
PASS
```

That can later become durable project knowledge.

---

# 47. But don't immediately put it into long-term memory

This is important.

Mutation evidence:

```text
short-lived operational state
```

Project knowledge:

```text
durable memory
```

Only promote if useful.

For example:

```text
"Expiration validation is centralized in TokenValidator"
```

might be worth promoting.

---

# 48. `KnowledgePromotion`

Eventually:

```java
public interface KnowledgePromoter {

    Uni<List<KnowledgeCandidate>> extract(
        TaskResult result
    );
}
```

But only promote:

```text
stable
reusable
high-confidence
```

facts.

---

# 49. Now the full mutation architecture

```text
                  GEMMA
                    │
              Change Intent
                    │
                    ▼
             ChangePlanner
                    │
                    ▼
               ChangePlan
                    │
            RepositoryIntel
                    │
             ImpactAnalyzer
                    │
                    ▼
             PatchProposal
                    │
              PatchValidator
                    │
              MutationGate
                    │
             ┌──────┴──────┐
             │             │
          DENY          ALLOW
                           │
                           ▼
                  MutationTransaction
                           │
                     Atomic Apply
                           │
                           ▼
                       ChangeSet
                           │
                VerificationPlanner
                           │
                           ▼
                      Verifier
```

---

# 50. One thing I would **not** build yet

Don't jump into:

```text
multi-agent coding
```

or:

```text
planner agent
coder agent
reviewer agent
tester agent
```

You don't need that yet.

For your local model, I'd strongly prefer:

```text
one reasoning model
+
many deterministic runtime capabilities
```

First.

You can later add specialized model roles if measurements justify them.

---

# 51. The Aljabr philosophy is becoming clear

The architecture is essentially:

```text
LLM:
    ambiguity resolution
    reasoning
    intent interpretation
    diagnosis
    strategy

Runtime:
    state
    search
    indexing
    validation
    mutation
    verification
    recovery
    policy
    caching
    safety
```

This is **exactly the division of labor I'd want for a local 12B coding agent**.

---

# 52. The next milestone

After this, I'd move into **`Execution Sandbox + Process Manager`**.

That layer handles the dangerous/expensive part of coding-agent operation:

```text
run command
run build
run test
install dependency
start server
inspect process
collect logs
kill process
timeout
resource limits
network policy
environment isolation
```

And importantly:

```text
Tool
  ↓
ProcessManager
  ↓
Sandbox
  ↓
OS
```

rather than letting the LLM directly interact with the operating system.

That will complete the **safe execution foundation** before we start adding higher-level features like autonomous debugging, multi-file refactoring, code review, and eventually Aljabr's more advanced coding strategies.


Absolutely. **Next milestone: `Execution Sandbox + ProcessManager`.**

This is the layer that makes Aljabr capable of actually running builds, tests, scripts, servers, and diagnostics while keeping the LLM away from direct OS control.

The architecture should become:

```text
Gemma
  │
  │ intent
  ▼
Tool
  │
  ▼
ExecutionPolicy
  │
  ▼
ProcessManager
  │
  ▼
Sandbox / OS Adapter
  │
  ├── stdout
  ├── stderr
  ├── exit code
  ├── files
  ├── processes
  └── resource usage
```

The key principle:

> **Gemma requests an operation. Wayang decides exactly how that operation is allowed to execute.**

---

# 1. Don't make `run_command()` the architecture

A naïve coding agent does:

```java
String output = shell.execute(command);
```

That becomes problematic very quickly.

Instead:

```java
ExecutionRequest
        ↓
ExecutionPolicy
        ↓
ProcessManager
        ↓
ExecutionEnvironment
        ↓
Process
        ↓
ExecutionResult
```

Each layer has a distinct responsibility.

---

# 2. `ExecutionRequest`

```java
public record ExecutionRequest(
    String executable,
    List<String> arguments,
    Path workingDirectory,
    ExecutionProfile profile,
    Duration timeout,
    OutputPolicy outputPolicy
) {}
```

Notice that the command is structured.

Prefer:

```text
executable = "./gradlew"
arguments = ["test", "--tests", "TokenValidatorTest"]
```

over:

```text
command = "./gradlew test --tests TokenValidatorTest"
```

---

# 3. Why structured commands matter

If Gemma outputs:

```text
./gradlew test && rm -rf something
```

a string-based shell executor may interpret both commands.

Structured execution instead sees:

```text
executable:
./gradlew

arguments:
test
--tests
TokenValidatorTest
```

There is no accidental shell interpretation.

---

# 4. `ExecutionProfile`

Create predefined execution profiles.

```java
public enum ExecutionProfile {

    TEST,
    BUILD,
    LINT,
    FORMAT,
    DEV_SERVER,
    SCRIPT,
    CUSTOM
}
```

Then policy can say:

```text
TEST
→ network disabled
→ workspace read/write limited
→ 5 min timeout
```

while:

```text
DEV_SERVER
→ long running
→ port access
→ explicit lifecycle management
```

---

# 5. `ExecutionPolicy`

```java
public record ExecutionPolicy(
    Set<String> allowedExecutables,
    Set<String> deniedExecutables,
    Set<ExecutionProfile> allowedProfiles,

    boolean networkAllowed,
    boolean filesystemWriteAllowed,

    Duration maxTimeout,
    long maxOutputBytes,

    int maxProcesses
) {}
```

This belongs to Wayang.

Not Gemma.

---

# 6. Never allow arbitrary environment inheritance

This is easy to overlook.

Don't blindly do:

```java
ProcessBuilder.environment().putAll(System.getenv());
```

because the environment may contain:

```text
API keys
cloud credentials
tokens
private configuration
```

Instead construct a controlled environment.

```java
public interface EnvironmentPolicy {

    Map<String, String> build(
        ExecutionContext context
    );
}
```

---

# 7. Environment modes

For example:

```java
public enum EnvironmentMode {

    MINIMAL,
    PROJECT,
    INHERITED
}
```

Default Aljabr:

```text
MINIMAL
```

Only add project-required variables.

---

# 8. Working directory

Never let the model choose an arbitrary directory.

Resolve:

```java
Path workingDirectory =
    workspace.resolve(requestedRelativePath).normalize();
```

Then:

```java
if (!workingDirectory.startsWith(workspace)) {
    deny();
}
```

---

# 9. Process manager

Now the core:

```java
public interface ProcessManager {

    Uni<ProcessHandleInfo> start(
        ExecutionRequest request
    );

    Uni<ExecutionResult> waitFor(
        ProcessId processId
    );

    Uni<Void> terminate(
        ProcessId processId
    );

    Uni<List<ProcessHandleInfo>> list();

    Uni<ProcessOutput> output(
        ProcessId processId
    );
}
```

---

# 10. `ProcessId`

Never identify processes only by PID.

Use:

```java
public record ProcessId(
    String executionId,
    long osPid
) {}
```

The `executionId` lets Wayang track ownership.

---

# 11. Process lifecycle

```text
CREATED
   ↓
STARTING
   ↓
RUNNING
   ↓
EXITED
```

or:

```text
RUNNING
   ↓
TIMED_OUT
   ↓
TERMINATING
   ↓
TERMINATED
```

or:

```text
RUNNING
   ↓
FAILED_TO_START
```

---

# 12. `ProcessHandleInfo`

```java
public record ProcessHandleInfo(
    ProcessId id,
    String executable,
    List<String> arguments,

    ProcessState state,

    Instant startedAt,
    Optional<Instant> endedAt,

    Optional<Integer> exitCode
) {}
```

This gives the runtime deterministic process state.

---

# 13. Process ownership

Every process started by Aljabr gets:

```text
executionId
agentId
workspaceId
taskId
```

Then you can answer:

> Which process belongs to this task?

without guessing.

---

# 14. This matters for recovery

Suppose:

```text
Aljabr task
   ↓
starts dev server
   ↓
connection lost
```

When the runtime restarts:

```text
recover task
     ↓
inspect process registry
     ↓
find existing server
```

You don't accidentally start another one.

---

# 15. `ProcessRegistry`

Persist:

```java
public interface ProcessRegistry {

    Uni<Void> register(ProcessRecord process);

    Uni<Optional<ProcessRecord>> find(ProcessId id);

    Uni<List<ProcessRecord>> findByTask(TaskId taskId);

    Uni<Void> markExited(ProcessId id);
}
```

This ties directly into the checkpoint/recovery architecture.

---

# 16. Output handling is extremely important

Don't simply do:

```java
process.getInputStream().readAllBytes();
```

A build can generate enormous output.

Instead:

```text
process
 ├── stdout stream
 └── stderr stream
       ↓
   OutputCollector
       ↓
 ┌─────┴─────┐
 ▼           ▼
artifact    summary
```

---

# 17. `OutputPolicy`

```java
public record OutputPolicy(
    long maxBytes,
    int maxLines,
    boolean preserveFullArtifact,
    boolean streamLive
) {}
```

For Gemma:

```text
small summarized output
```

For storage:

```text
full output artifact
```

---

# 18. This follows our context architecture

Suppose Gradle emits:

```text
250,000 lines
```

Store all 250,000 lines.

But Gemma gets:

```text
BUILD FAILED

3 errors

TokenValidator.java:84
incompatible types

AuthService.java:112
method not found

Full output:
artifact://execution/123/stdout
```

Huge token savings.

---

# 19. `OutputNormalizer`

Raw process output:

```text
> Task :core:compileJava FAILED
...
/project/src/AuthService.java:112: error:
...
```

Normalize into:

```java
public record Diagnostic(
    DiagnosticSeverity severity,
    Optional<Path> file,
    Optional<SourceRange> range,
    String message,
    String tool
) {}
```

---

# 20. Diagnostic extraction

Support patterns from:

```text
Java
TypeScript
Python
Rust
Go
Gradle
Maven
npm
pytest
```

But keep the abstraction generic.

```java
public interface DiagnosticParser {

    boolean supports(String tool);

    List<Diagnostic> parse(
        String output
    );
}
```

---

# 21. Diagnostics become evidence

This is the same pattern we established earlier:

```text
Process
 ↓
Raw Output
 ↓
DiagnosticParser
 ↓
Diagnostics
 ↓
Evidence
 ↓
ContextEngine
 ↓
Gemma
```

The model sees:

```text
TokenValidator.java:84
expected Duration
received long
```

rather than thousands of build lines.

---

# 22. Timeout management

Timeout should be layered.

```text
task timeout
    ↓
execution timeout
    ↓
process timeout
```

Example:

```text
Task:
10 minutes

Build:
5 minutes

Individual command:
3 minutes
```

The shortest applicable limit wins.

---

# 23. Graceful termination

Don't immediately:

```text
kill -9
```

Use:

```text
SIGTERM / graceful stop
       ↓
wait
       ↓
SIGKILL if necessary
```

On platforms where the equivalent mechanism differs, use the OS adapter.

---

# 24. Process trees

A build can spawn children:

```text
gradle
 ├── java
 ├── javac
 └── test JVM
```

Killing only the parent can leave children alive.

Therefore:

```java
public interface ProcessTreeManager {

    Uni<Void> terminateTree(
        ProcessId root
    );
}
```

---

# 25. This becomes important for dev servers

Example:

```text
npm run dev
   ↓
node
   ↓
vite
   ↓
child process
```

Aljabr must be able to terminate the whole owned tree.

---

# 26. Long-running processes need a different abstraction

A test:

```text
run
→ finish
```

A dev server:

```text
start
→ remain running
→ inspect
→ interact
→ stop
```

So introduce:

```java
public interface ManagedService {

    Uni<ServiceHandle> start(
        ServiceRequest request
    );

    Uni<ServiceStatus> status(
        ServiceId id
    );

    Uni<Void> stop(
        ServiceId id
    );
}
```

---

# 27. `ServiceRequest`

```java
public record ServiceRequest(
    String executable,
    List<String> arguments,
    Path workingDirectory,
    List<Integer> expectedPorts
) {}
```

---

# 28. Service readiness

Don't assume:

```text
process started = server ready
```

Instead:

```text
process started
      ↓
readiness check
      ↓
HTTP / TCP / log signal
      ↓
READY
```

---

# 29. `ReadinessProbe`

```java
public interface ReadinessProbe {

    Uni<ReadinessResult> check(
        ServiceHandle service
    );
}
```

Implement:

```text
HTTP probe
TCP probe
log probe
process probe
```

---

# 30. This helps coding workflows enormously

Example:

```text
start application
     ↓
wait for port 8080
     ↓
READY
     ↓
run integration test
```

Gemma shouldn't have to figure out whether the server is ready.

---

# 31. Network policy

For local coding, default:

```text
network = OFF
```

for most commands.

Allow explicitly:

```text
TEST
BUILD
DEV_SERVER
```

only when required.

---

# 32. Why?

Build tools may unexpectedly attempt:

```text
dependency download
telemetry
remote services
```

The execution policy should make that explicit.

---

# 33. Dependency installation

Treat this differently from a normal command.

```text
install dependency
```

can change:

```text
lock files
dependency graph
workspace state
network state
```

So:

```java
public enum ExecutionRisk {

    SAFE,
    MODERATE,
    HIGH
}
```

and dependency installation should normally be:

```text
MODERATE/HIGH
```

depending on policy.

---

# 34. Don't make sandboxing synonymous with Docker

For your Mac M4 local use case, you don't necessarily want:

```text
every test
→ Docker container
```

That can introduce huge overhead.

Instead make:

```java
public interface ExecutionEnvironment {

    Uni<ExecutionResult> execute(
        ExecutionRequest request
    );
}
```

with implementations:

```text
DirectProcessEnvironment
SandboxEnvironment
ContainerEnvironment
RemoteEnvironment
```

---

# 35. Capability-driven execution

Then:

```text
local Mac
→ DirectProcessEnvironment

high-risk task
→ SandboxEnvironment

strong isolation needed
→ ContainerEnvironment

cloud worker
→ RemoteEnvironment
```

Same Aljabr API.

---

# 36. This fits your CapabilityDetector

Your earlier architecture:

```text
CapabilityDetector
```

can determine:

```text
process isolation available?
container runtime?
network namespace?
resource limits?
sandbox?
```

Then:

```text
ContextPolicy
```

and:

```text
ExecutionPolicy
```

can adapt.

---

# 37. `ExecutionCapabilities`

```java
public record ExecutionCapabilities(
    boolean processIsolation,
    boolean containerSupport,
    boolean networkIsolation,
    boolean resourceLimits,
    boolean filesystemIsolation
) {}
```

---

# 38. Local Mac profile

Your M4 setup might reasonably use:

```text
DirectProcessEnvironment
+
strict workspace boundary
+
command allowlist
+
output limits
+
timeouts
+
process ownership
```

rather than introducing heavyweight infrastructure everywhere.

---

# 39. Cloud profile

A cloud deployment might use:

```text
ContainerEnvironment
+
network isolation
+
CPU limit
+
memory limit
+
ephemeral filesystem
```

Same Aljabr agent.

---

# 40. Resource policy

You also need:

```java
public record ResourcePolicy(
    int maxCpu,
    long maxMemoryBytes,
    long maxDiskBytes,
    Duration maxRuntime
) {}
```

Not every environment can enforce every field.

That's why capabilities matter.

---

# 41. If resource control isn't available

Don't pretend it is.

Capability says:

```text
memoryLimit = false
```

Then policy can compensate with:

```text
smaller command scope
shorter timeout
lower concurrency
```

This is another place where Wayang should be honest about its runtime capabilities.

---

# 42. Concurrency control

Aljabr should not accidentally do:

```text
10 builds
+
5 test suites
+
3 dev servers
```

on a 16 GB machine.

Define:

```java
public record ExecutionBudget(
    int maxConcurrentProcesses,
    int maxConcurrentBuilds,
    int maxConcurrentTests
) {}
```

For local inference, resource competition matters.

---

# 43. CPU contention also affects inference

This is easy to miss.

Gemma is running locally.

If Aljabr launches:

```text
full Gradle build
```

while Gemma is generating tokens, both compete for CPU/memory.

So the runtime should understand:

```text
InferenceBudget
+
ExecutionBudget
```

together.

---

# 44. `ResourceCoordinator`

```java
public interface ResourceCoordinator {

    Uni<ExecutionLease> acquire(
        ExecutionCost cost
    );

    Uni<Void> release(
        ExecutionLease lease
    );
}
```

---

# 45. Example

Gemma generation is active:

```text
inference:
HIGH
```

Then:

```text
run full build
```

could be deferred until:

```text
model generation complete
```

or run at lower concurrency.

---

# 46. This is particularly valuable on 16 GB

Avoid:

```text
Gemma KV cache
+
Gradle daemon
+
Java compiler
+
browser/dev server
+
indexer
```

all exploding memory simultaneously.

Aljabr should know:

> **Inference is itself a resource consumer.**

---

# 47. Execution scheduling

Eventually:

```text
ExecutionScheduler
```

can prioritize:

```text
targeted test
    >
module test
    >
build
    >
indexing
```

and:

```text
user-requested task
    >
background indexing
```

---

# 48. Background indexing should yield

If Gemma is actively working:

```text
repository indexer
→ low priority
```

When idle:

```text
indexer
→ high priority
```

This makes the whole local agent feel much faster.

---

# 49. Execution cache

Some executions can also be cached.

For example:

```text
run:
./gradlew test --tests TokenValidatorTest
```

with identical workspace fingerprint.

Potential key:

```text
workspaceFingerprint
+
command
+
environmentFingerprint
```

Then:

```text
CACHE HIT
```

But be conservative.

Tests can depend on external state.

---

# 50. Cache policy

```java
public enum Cacheability {

    NEVER,
    SAFE,
    CONDITIONAL
}
```

Examples:

```text
compiler diagnostics
→ CONDITIONAL

pure formatter
→ SAFE

network integration test
→ NEVER
```

---

# 51. Execution evidence

Every execution produces:

```java
public record ExecutionEvidence(
    ExecutionId executionId,
    String commandSummary,
    ExecutionStatus status,
    List<Diagnostic> diagnostics,
    Optional<OutputArtifact> output,
    Duration duration
) {}
```

Again:

```text
execution
→ evidence
→ context
```

---

# 52. Don't feed command output directly back to Gemma

Instead:

```text
Raw output
 ↓
Normalizer
 ↓
Diagnostics
 ↓
Summary
 ↓
Relevant excerpts
```

Then:

```text
Gemma context:
```

```text
TEST FAILURE

Test:
TokenValidatorTest.expiredToken

Failure:
expected false
received true

Relevant source:
TokenValidator.java:71-82

Recent change:
expiration comparison changed in M001
```

That is vastly better.

---

# 53. The debugging loop becomes very clean

```text
Gemma
  │
  ▼
run_test
  │
  ▼
ProcessManager
  │
  ▼
TestResult
  │
  ▼
DiagnosticNormalizer
  │
  ▼
EvidenceGraph
  │
  ▼
ContextEngine
  │
  ▼
Gemma
```

No giant terminal dump.

---

# 54. The complete execution stack

At this point:

```text
Tool
 │
 ▼
ExecutionPolicy
 │
 ▼
ExecutionScheduler
 │
 ▼
ResourceCoordinator
 │
 ▼
ProcessManager
 │
 ├── ProcessRegistry
 ├── OutputCollector
 ├── DiagnosticParser
 ├── ProcessTreeManager
 └── ReadinessProbe
 │
 ▼
ExecutionEnvironment
 │
 ├── Direct
 ├── Sandbox
 ├── Container
 └── Remote
 │
 ▼
ExecutionEvidence
```

This is a strong foundation.

---

# 55. Most important implementation rule

Do **not** build all of this at once.

For Aljabr v1 on your M4:

### Implement first

```text
ExecutionRequest
ExecutionPolicy
ProcessManager
ProcessRegistry
OutputCollector
DiagnosticParser
Timeout
Process tree termination
ExecutionEvidence
```

That gives you a practical execution engine.

Then:

```text
ManagedService
ReadinessProbe
ResourceCoordinator
ExecutionScheduler
```

Then:

```text
SandboxEnvironment
ContainerEnvironment
RemoteEnvironment
```

---

# 56. The first real coding workflow

With the previous milestones, Aljabr can now do:

```text
User:
"Fix the failing expiration test."

        │
        ▼
RepositoryIntel
        │
        ▼
find relevant code
        │
        ▼
ImpactAnalyzer
        │
        ▼
Gemma
        │
        ▼
ChangePlan
        │
        ▼
PatchProposal
        │
        ▼
PatchValidator
        │
        ▼
MutationGate
        │
        ▼
Atomic Apply
        │
        ▼
run targeted test
        │
        ▼
ProcessManager
        │
        ▼
DiagnosticParser
        │
        ├── PASS → DONE
        │
        └── FAIL
              │
              ▼
         EvidenceGraph
              │
              ▼
            Gemma
              │
              ▼
           Replan
```

That is already a **real coding agent architecture**, not merely an LLM wrapper.

---

# 57. Next milestone

After execution, the next thing I'd build is **`Autonomous Debugging / Diagnose → Hypothesis → Experiment → Repair`**.

That is where Aljabr starts becoming genuinely powerful:

```text
TEST FAIL
   ↓
extract diagnostics
   ↓
identify suspicious symbols
   ↓
construct hypotheses
   ↓
rank hypotheses
   ↓
perform cheap experiment
   ↓
observe result
   ↓
update hypothesis
   ↓
patch
   ↓
verify
```

And for your **Gemma 12B**, we should design that loop specifically so the model doesn't have to generate enormous reasoning traces. The runtime can maintain the hypothesis state, evidence, experiments, confidence, and failure history, while Gemma only handles the genuinely ambiguous reasoning steps.



Great. **Next milestone: Autonomous Debugging — `Diagnose → Hypothesis → Experiment → Repair`.**

This is where Aljabr moves from:

> “I can execute code.”

to:

> **“I can investigate why the code is broken and iteratively converge on a fix.”**

For your local Gemma 12B, the important design choice is:

> **Keep the debugging state and evidence deterministic in Wayang; let the model perform only the ambiguous reasoning.**

---

# 1. The debugging architecture

```text
                    Failure
                       │
                       ▼
                DiagnosticEngine
                       │
                       ▼
                EvidenceBuilder
                       │
                       ▼
                SuspiciousArea
                       │
                       ▼
              HypothesisEngine
                       │
             ┌─────────┼─────────┐
             ▼         ▼         ▼
          H1          H2        H3
             │         │         │
             └─────────┼─────────┘
                       ▼
                HypothesisRanker
                       │
                       ▼
                 Experiment
                       │
                       ▼
                  Observation
                       │
                       ▼
               HypothesisUpdate
                    │      │
                  reject  increase
                    │      │
                    └──┬───┘
                       ▼
                    Repair
                       │
                       ▼
                   Verify
```

---

# 2. Don't let Gemma "just think harder"

A naïve implementation:

```text
TEST FAILED

Gemma:
"Let me reason about what happened..."
```

and then huge reasoning.

For a 12B model this is inefficient.

Instead Wayang maintains:

```java
public record DebugSession(
    DebugSessionId id,
    TaskId taskId,
    FailureId failure,
    List<Hypothesis> hypotheses,
    List<Experiment> experiments,
    List<EvidenceId> evidence,
    DebugState state
) {}
```

Gemma sees a compact representation.

---

# 3. `DebugState`

```java
public enum DebugState {

    FAILURE_DETECTED,
    TRIAGING,
    HYPOTHESIS_GENERATION,
    EXPERIMENT_SELECTION,
    EXPERIMENT_RUNNING,
    ANALYZING,
    REPAIRING,
    VERIFYING,
    RESOLVED,
    BLOCKED
}
```

This gives the runtime an explicit state machine.

---

# 4. First: classify the failure

Don't immediately ask the LLM for a diagnosis.

Create:

```java
public interface FailureClassifier {

    FailureClassification classify(
        ExecutionEvidence evidence
    );
}
```

---

# 5. Failure categories

Start small:

```java
public enum FailureKind {

    COMPILATION,
    TEST_ASSERTION,
    RUNTIME_EXCEPTION,
    TIMEOUT,
    PROCESS_CRASH,
    CONFIGURATION,
    DEPENDENCY,
    ENVIRONMENT,
    UNKNOWN
}
```

---

# 6. Example

Raw output:

```text
java.lang.NullPointerException
    at AuthService.java:112
```

Runtime derives:

```text
kind:
RUNTIME_EXCEPTION

file:
AuthService.java

line:
112

exception:
NullPointerException
```

Gemma doesn't need to extract this.

---

# 7. Failure normalization

Create:

```java
public record Failure(
    FailureId id,
    FailureKind kind,

    Optional<Path> file,
    Optional<SourceRange> location,

    String message,

    List<StackFrame> stack,

    List<Diagnostic> diagnostics
) {}
```

Now all failures have a common representation.

---

# 8. Stack traces become repository queries

Suppose:

```text
AuthService.java:112
TokenValidator.java:71
LoginController.java:44
```

RepositoryIntel can immediately calculate:

```text
implementation
callers
dependencies
tests
recent changes
```

So the debugging context is generated automatically.

---

# 9. `DebugContext`

```java
public record DebugContext(
    Failure failure,
    List<RelevantFile> files,
    List<RelevantSymbol> symbols,
    List<RecentChange> changes,
    List<TestCandidate> tests,
    List<Evidence> evidence
) {}
```

This is what gets passed to Gemma.

---

# 10. Example context

Instead of 20,000 tokens:

```text
FAILURE

TokenValidatorTest.expiredToken

Expected:
false

Actual:
true

FAILURE LOCATION

TokenValidator.java:78

RELEVANT SYMBOL

TokenValidator.validateExpiration()

CALLERS

AuthService.validate()
RefreshService.validate()

RECENT CHANGE

M001 changed expiration comparison.

RELEVANT TEST

TokenValidatorTest.expiredToken()
```

That is an excellent context packet for a 12B model.

---

# 11. Hypothesis

Now we introduce the central debugging object.

```java
public record Hypothesis(
    HypothesisId id,

    String statement,

    List<EvidenceId> supportingEvidence,
    List<EvidenceId> contradictingEvidence,

    double confidence,

    HypothesisStatus status
) {}
```

---

# 12. Example hypotheses

Gemma might generate:

```text
H1:
Expiration boundary comparison is incorrect.

H2:
Token expiration timestamp is calculated incorrectly.

H3:
Test fixture creates an already-valid token.
```

Notice that these are **hypotheses**, not actions.

---

# 13. This distinction is very important

Don't let Gemma jump directly from:

```text
test failed
```

to:

```text
change line 78
```

Instead:

```text
Failure
 ↓
Hypothesis
 ↓
Evidence
 ↓
Experiment
 ↓
Repair
```

That reduces hallucinated patches.

---

# 14. Hypothesis status

```java
public enum HypothesisStatus {

    PROPOSED,
    ACTIVE,
    SUPPORTED,
    WEAKENED,
    REJECTED,
    CONFIRMED
}
```

---

# 15. Confidence should not be pure LLM confidence

Don't blindly accept:

```text
Gemma says confidence = 0.95
```

Instead calculate runtime confidence from evidence.

For example:

```text
+ direct stack trace
+ changed line
+ failing test
+ relevant caller
- contradictory evidence
```

Then:

```java
public interface HypothesisScorer {

    HypothesisScore score(
        Hypothesis hypothesis,
        DebugContext context
    );
}
```

---

# 16. Evidence-based scoring

Something conceptually like:

```text
score =
    directEvidence
  + recentChange
  + structuralRelevance
  + testCorrelation
  - contradiction
  - uncertainty
```

The exact formula should live in policy.

---

# 17. The experiment is the key

A hypothesis isn't useful until we can test it.

```java
public record Experiment(
    ExperimentId id,
    HypothesisId hypothesis,

    ExperimentType type,

    String objective,

    List<EvidenceId> requiredEvidence
) {}
```

---

# 18. Experiment types

Start with:

```java
public enum ExperimentType {

    READ_SOURCE,
    SEARCH_CODE,
    INSPECT_SYMBOL,
    FIND_REFERENCES,
    RUN_TARGETED_TEST,
    RUN_VARIANT_TEST,
    ADD_TEMPORARY_LOG,
    COMPILE,
    REPRODUCE
}
```

Notice:

**many experiments don't modify code.**

That is intentional.

---

# 19. Cheap experiments first

Suppose H1 is:

```text
comparison operator is wrong
```

Before patching, possible experiment:

```text
read exact function
```

Cost:

```text
very low
```

Another:

```text
run targeted test
```

Cost:

```text
low
```

Another:

```text
run entire test suite
```

Cost:

```text
high
```

Aljabr should prefer cheap information first.

---

# 20. `ExperimentCost`

```java
public record ExperimentCost(
    int tokenCost,
    int executionCost,
    int latencyCost,
    int mutationRisk
) {}
```

Then:

```text
read_file
→ extremely cheap

targeted test
→ cheap

module build
→ moderate

full suite
→ expensive

large mutation
→ risky
```

---

# 21. Information gain

This is a powerful improvement.

Don't optimize only for:

```text
lowest cost
```

Optimize for:

```text
information gained / cost
```

Conceptually:

```text
experimentScore =
    expectedInformationGain
    / executionCost
```

---

# 22. Example

Suppose:

```text
H1 confidence = 0.55
H2 confidence = 0.35
H3 confidence = 0.10
```

Experiment A:

```text
read TokenValidator
cost = 1
information = 0.4
```

Experiment B:

```text
full test suite
cost = 20
information = 0.7
```

Choose A.

```text
0.4 / 1 > 0.7 / 20
```

This is exactly the kind of optimization that helps local agents.

---

# 23. `ExperimentPlanner`

```java
public interface ExperimentPlanner {

    ExperimentPlan plan(
        DebugContext context,
        List<Hypothesis> hypotheses
    );
}
```

---

# 24. Experiment plan

```java
public record ExperimentPlan(
    Experiment selected,
    String rationale,
    ExperimentCost cost
) {}
```

---

# 25. Gemma doesn't need to select from 50 experiments

Wayang filters them.

Current situation:

```text
failure = compilation
```

Candidate experiments:

```text
read_source
inspect_symbol
compile
```

No reason to expose:

```text
database_probe
network_test
dev_server
```

This is the same ToolRouter strategy from earlier.

---

# 26. Hypothesis graph

Now make hypotheses connected.

```text
H1
 │
 ├── supported by E104
 ├── supported by E109
 └── tested by X12

H2
 │
 └── contradicted by E110
```

So debugging becomes a graph:

```text
Evidence
   ↕
Hypothesis
   ↕
Experiment
   ↕
Observation
```

---

# 27. `HypothesisGraph`

```java
public interface HypothesisGraph {

    void add(Hypothesis hypothesis);

    void support(
        HypothesisId hypothesis,
        EvidenceId evidence
    );

    void contradict(
        HypothesisId hypothesis,
        EvidenceId evidence
    );

    List<Hypothesis> ranked();
}
```

---

# 28. This prevents repeated investigation

Suppose Gemma already tested:

```text
H1:
expiration comparison is wrong
```

and experiment showed:

```text
H1 rejected
```

Store that.

If Gemma later suggests the same hypothesis:

```text
duplicate
```

runtime can respond:

```text
Already tested:
H1
Result:
REJECTED
```

No repeated work.

---

# 29. This connects directly to your earlier research cache

It's the same architecture.

You wanted:

> if the connection breaks, don't repeat research.

Here:

> if debugging is interrupted, don't repeat experiments.

Checkpoint:

```text
DebugSession
+
HypothesisGraph
+
EvidenceGraph
+
ExperimentHistory
```

---

# 30. Recovery

Suppose:

```text
Experiment X17
running
```

connection dies.

On restart:

```text
recover DebugSession
        ↓
inspect ProcessRegistry
        ↓
determine X17 status
        ↓
continue
```

If process already finished:

```text
don't rerun
```

---

# 31. This is one of the biggest advantages of Wayang

The model can disappear.

The **agent state does not**.

```text
Gemma
  ↓
temporary reasoning

Wayang
  ↓
durable state
```

This is exactly the right division.

---

# 32. Repair should happen only after sufficient evidence

Suppose:

```text
H1 confidence = 0.91
```

and:

```text
direct failure location
+
recent change
+
targeted experiment
```

confirm it.

Then:

```text
REPAIR_ALLOWED
```

Otherwise:

```text
MORE_INVESTIGATION
```

---

# 33. `RepairDecision`

```java
public record RepairDecision(
    HypothesisId hypothesis,
    RepairConfidence confidence,
    List<EvidenceId> evidence,
    RepairAuthorization authorization
) {}
```

---

# 34. Repair confidence

```java
public enum RepairConfidence {

    LOW,
    MEDIUM,
    HIGH
}
```

And policy can say:

```text
LOW
→ investigate

MEDIUM
→ maybe patch

HIGH
→ patch automatically
```

---

# 35. The repair still goes through our previous pipeline

Never bypass:

```text
Hypothesis
 ↓
ChangePlan
 ↓
PatchProposal
 ↓
PatchValidator
 ↓
MutationGate
 ↓
AtomicApply
```

Debugging doesn't get special mutation privileges.

---

# 36. After repair, verify the hypothesis

This is important.

Suppose:

```text
H1:
comparison operator is wrong
```

Patch applied.

Test:

```text
PASS
```

Now:

```text
H1 = CONFIRMED
```

If test:

```text
FAIL
```

then:

```text
H1 = WEAKENED / REJECTED
```

and create another hypothesis.

---

# 37. The debugging loop

The real loop becomes:

```text
              ┌──────────────┐
              │   FAILURE    │
              └──────┬───────┘
                     ▼
                CLASSIFY
                     ▼
                 TRIAGE
                     ▼
               HYPOTHESES
                     ▼
                 RANK
                     ▼
               EXPERIMENT
                     ▼
                OBSERVE
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
      CONFIRMED             REJECTED
          │                     │
          ▼                     ▼
        REPAIR              NEXT HYPOTHESIS
          │
          ▼
       VERIFY
          │
     ┌────┴────┐
     ▼         ▼
   PASS       FAIL
     │         │
     ▼         └──────→ HYPOTHESIS UPDATE
   DONE
```

---

# 38. One subtle improvement: don't always create a hypothesis

Some failures are deterministic.

Example:

```text
cannot find symbol FooBar
```

Runtime knows:

```text
missing symbol
```

There may be no need for expensive hypothesis generation.

So use:

```java
public enum DiagnosisMode {

    DETERMINISTIC,
    MODEL_ASSISTED,
    OPEN_ENDED
}
```

---

# 39. Deterministic diagnosis

Example:

```text
compile error:
method foo(int) cannot be applied to (String)
```

Repository intelligence can inspect:

```text
method signature
call site
```

and directly derive:

```text
argument type mismatch
```

Gemma may only need to decide:

```text
Which intended type is correct?
```

---

# 40. This reduces model usage

Instead of:

```text
Gemma diagnoses everything
```

do:

```text
runtime:
known facts

Gemma:
ambiguous interpretation
```

That's exactly what you want for local inference.

---

# 41. Debugging context compression

When Gemma gets invoked, provide:

```text
DEBUG TASK

Failure:
...

Current hypothesis:
...

Evidence:
...

Already tested:
...

Contradictions:
...

Available experiments:
...

Constraints:
...
```

Very compact.

---

# 42. Example Gemma prompt

Conceptually:

```text
You are diagnosing a coding failure.

FAILURE:
TokenValidatorTest.expiredToken
expected false, got true

HYPOTHESIS:
Expiration comparison is incorrect.

SUPPORTING EVIDENCE:
- failure occurs at TokenValidator.validateExpiration
- method changed in M001

CONTRADICTING EVIDENCE:
none

ALREADY TESTED:
none

AVAILABLE EXPERIMENTS:
1. inspect implementation
2. inspect callers
3. run targeted test

Choose the next experiment.
```

That's far better for Gemma than dumping the whole project.

---

# 43. Don't expose chain-of-thought requirements

The model doesn't need to generate:

```text
"First I think..."
"Then I reason..."
"Then perhaps..."
```

Instead require a structured decision:

```json
{
  "hypothesis": "...",
  "next_action": "...",
  "evidence": ["E104"],
  "confidence": "medium"
}
```

Wayang stores the reasoning state.

---

# 44. Structured output

Define:

```java
public record DebugDecision(
    DecisionType type,
    Optional<HypothesisDraft> hypothesis,
    Optional<ExperimentRequest> experiment,
    Optional<RepairIntent> repair
) {}
```

Then validate it.

---

# 45. `DecisionType`

```java
public enum DecisionType {

    INVESTIGATE,
    EXPERIMENT,
    REPAIR,
    ASK_USER,
    DONE
}
```

This dramatically reduces parsing ambiguity.

---

# 46. If Gemma produces invalid output

Don't restart the whole task.

Return:

```text
INVALID_DECISION_SCHEMA
```

and retry with a tiny corrective prompt:

```text
Your previous response was invalid.

Required:
decision = one of
INVESTIGATE | EXPERIMENT | REPAIR | ASK_USER | DONE
```

This is much cheaper.

---

# 47. Debugging budget

You also need a hard budget.

```java
public record DebugBudget(
    int maxExperiments,
    int maxRepairs,
    Duration maxDuration,
    int maxModelCalls
) {}
```

For local usage:

```text
max experiments = configurable
max repairs = 3
max model calls = configurable
```

This prevents:

```text
failure
→ infinite debugging
```

---

# 48. Stagnation detector

Add:

```java
public interface StagnationDetector {

    boolean isStagnating(
        DebugSession session
    );
}
```

Detect:

```text
same hypothesis repeated
same file repeatedly edited
same test repeatedly failing
no evidence gained
```

Then stop or change strategy.

---

# 49. Example

```text
Attempt 1:
patch AuthService
→ fail

Attempt 2:
patch AuthService again
→ same failure

Attempt 3:
patch AuthService again
→ same failure
```

Runtime should say:

```text
STAGNATION_DETECTED
```

not allow a fourth nearly identical attempt.

---

# 50. Oscillation detector

Even worse:

```text
Patch A
→ test fails

Patch B
→ test fails

Patch A
→ test fails

Patch B
→ test fails
```

Detect:

```text
PATCH_OSCILLATION
```

Then force a higher-level diagnosis.

---

# 51. This is extremely useful for weaker models

A 12B model may occasionally fall into local repair loops.

Wayang can protect it.

The runtime effectively says:

> "You've tried this strategy twice. It isn't producing new information. Investigate the root cause instead."

---

# 52. Debugging memory

At the end:

```text
DebugSession
```

contains:

```text
Failure
Hypotheses
Experiments
Observations
Patches
Verification
```

But only promote stable knowledge.

For example:

```text
Project fact:
"Token expiration logic is centralized in TokenValidator."
```

Potential durable knowledge.

But:

```text
"H2 was rejected."
```

probably remains task-local.

---

# 53. This gives you memory scopes naturally

```text
TASK MEMORY
    │
    ├── failure
    ├── hypotheses
    ├── experiments
    └── temporary evidence
         │
         ▼
PROJECT MEMORY
    │
    └── stable architectural discoveries
```

Exactly aligned with the memory architecture you've been developing.

---

# 54. The full Aljabr debugging subsystem

```text
DebugEngine
│
├── FailureClassifier
├── FailureNormalizer
│
├── DebugContextBuilder
│
├── HypothesisEngine
├── HypothesisScorer
├── HypothesisGraph
│
├── ExperimentPlanner
├── ExperimentExecutor
├── ExperimentHistory
│
├── RepairPlanner
├── StagnationDetector
├── OscillationDetector
│
└── DebugBudget
```

And underneath:

```text
RepositoryIntelligence
ToolRouter
ProcessManager
ContextEngine
EvidenceStore
Memory
```

---

# 55. The first practical implementation

Don't implement all hypothesis intelligence first.

Build this vertical slice:

```text
TEST FAIL
   ↓
parse failure
   ↓
locate file/line
   ↓
find symbol
   ↓
find relevant recent changes
   ↓
Gemma generates 2-3 hypotheses
   ↓
runtime ranks them
   ↓
Gemma selects experiment
   ↓
execute experiment
   ↓
record evidence
   ↓
Gemma decides repair
   ↓
PatchValidator
   ↓
apply
   ↓
targeted test
```

If that works reliably, you already have a genuinely autonomous debugger.

---

# 56. The bigger picture now

Aljabr is evolving into this:

```text
                         ALJABR
                            │
       ┌────────────────────┼────────────────────┐
       │                    │                    │
       ▼                    ▼                    ▼
   REASONING            KNOWLEDGE            EXECUTION
       │                    │                    │
    Gemma 12B        RepositoryIntel       ProcessManager
       │                    │                    │
       │              EvidenceGraph        Sandbox
       │                    │                    │
       └──────────────┬─────┴────────────────────┘
                      ▼
                 ContextEngine
                      │
                      ▼
                DecisionEngine
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
       Plan        Debug        Repair
          │           │           │
          └───────────┼───────────┘
                      ▼
                 Verification
                      │
                      ▼
                    Memory
```

The crucial thing is that **Gemma is no longer the agent itself**.

Gemma is the **reasoning component inside the Wayang agent runtime**.

That distinction is going to make Aljabr much more reliable, recoverable, cacheable, and efficient on your M4.

---

## Next milestone I'd take

**`Task Planning + Goal Decomposition + Adaptive Control Loop`**

That would sit above everything we've built:

```text
User Goal
   ↓
Goal Interpreter
   ↓
Task Planner
   ↓
Task Graph
   ↓
Active Step
   ↓
Observe → Decide → Act → Verify
   ↓
Progress Evaluation
   ↓
Replan
```

The important part will be designing the planner so **Gemma 12B doesn't have to generate a giant plan upfront**.

Instead, Aljabr should use **hierarchical, incremental planning**:

```text
high-level goal
      ↓
small plan
      ↓
execute
      ↓
observe
      ↓
expand next step
      ↓
execute
      ↓
replan
```

That will be the layer that turns all these individual capabilities into a coherent autonomous coding agent.



Yes. **Next milestone: `Task Planning + Goal Decomposition + Adaptive Control Loop`.**

This is the layer that finally connects everything we've built into one coherent Aljabr agent.

The biggest design decision for your **Gemma 4 12B on M4/16 GB** is:

> **Do not ask the model to generate a giant plan and then blindly execute it.**
>
> Build a small hierarchical plan, execute one meaningful step, observe the real workspace, and expand/replan only when necessary.

That gives you better reliability *and* lower context/token consumption.

---

# 1. The architecture

We want:

```text
User Goal
   │
   ▼
Goal Interpreter
   │
   ▼
Task Definition
   │
   ▼
Task Planner
   │
   ▼
Task Graph
   │
   ▼
┌─────────────────────────────┐
│      Adaptive Control Loop  │
│                             │
│  Observe                    │
│     ↓                       │
│  Decide                     │
│     ↓                       │
│  Act                        │
│     ↓                       │
│  Verify                     │
│     ↓                       │
│  Update State               │
│     ↓                       │
│  Replan if necessary        │
└──────────────┬──────────────┘
               │
               ▼
             DONE
```

This is the actual **agent loop**.

Everything we've built so far becomes a capability inside this loop.

---

# 2. First principle: Goal ≠ Plan

User says:

> "Add OAuth login to the application."

That's a **goal**.

It should not immediately become:

```text
1. modify User.java
2. add OAuthService.java
3. modify AuthController.java
4. add dependency
5. modify configuration
6. add tests
7. run tests
```

because Aljabr doesn't yet know enough.

Instead:

```text
Goal
 ↓
inspect repository
 ↓
understand architecture
 ↓
identify authentication subsystem
 ↓
then create a plan
```

The plan is therefore **evidence-dependent**.

---

# 3. `Task`

```java
public record Task(
    TaskId id,
    String objective,
    TaskScope scope,
    TaskPriority priority,
    TaskConstraints constraints
) {}
```

Example:

```text
objective:
Add OAuth login.

scope:
authentication subsystem

constraints:
- preserve existing login
- add tests
- don't modify unrelated modules
```

---

# 4. `TaskScope`

```java
public record TaskScope(
    List<Path> allowedPaths,
    List<ModuleId> allowedModules,
    List<SymbolId> relevantSymbols
) {}
```

Initially it can be mostly empty.

Repository intelligence progressively fills it.

---

# 5. Don't make planning one giant LLM call

Bad:

```text
User request
   ↓
Gemma
   ↓
100-step plan
```

Better:

```text
User request
   ↓
Gemma
   ↓
high-level objectives
   ↓
runtime validates
   ↓
execute discovery
   ↓
Gemma expands next objective
```

---

# 6. Hierarchical planning

Use three levels:

```text
Goal
 │
 ├── Objective
 │      │
 │      └── Step
 │
 ├── Objective
 │      │
 │      └── Step
 │
 └── Objective
```

For example:

```text
GOAL
Add OAuth login

OBJECTIVE A
Understand authentication architecture

OBJECTIVE B
Implement OAuth flow

OBJECTIVE C
Integrate with existing session system

OBJECTIVE D
Verify behavior
```

Only expand A initially.

---

# 7. `PlanNode`

```java
public record PlanNode(
    PlanNodeId id,
    PlanNodeType type,
    String objective,
    PlanNodeStatus status,
    List<PlanNodeId> dependencies
) {}
```

---

# 8. `PlanNodeType`

```java
public enum PlanNodeType {

    GOAL,
    OBJECTIVE,
    STEP,
    VERIFICATION
}
```

---

# 9. `PlanNodeStatus`

```java
public enum PlanNodeStatus {

    PENDING,
    READY,
    RUNNING,
    BLOCKED,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
```

Simple is good here.

---

# 10. Task graph

```java
public interface TaskGraph {

    void add(PlanNode node);

    void addDependency(
        PlanNodeId node,
        PlanNodeId dependency
    );

    List<PlanNode> readyNodes();

    void markRunning(PlanNodeId id);

    void markSucceeded(PlanNodeId id);

    void markFailed(PlanNodeId id);
}
```

Now the runtime knows what can execute next.

---

# 11. Example

```text
A Understand architecture
        │
        ▼
B Implement OAuth
        │
        ▼
C Integrate session
        │
        ▼
D Run tests
```

But perhaps:

```text
A
├── A1 inspect AuthController
├── A2 inspect User model
└── A3 inspect session mechanism

A1 + A2 + A3
        │
        ▼
        B
```

This is much more useful than a linear todo list.

---

# 12. The model should not manually manage dependencies

Gemma can propose:

```text
B depends on A
```

but Wayang validates the graph.

Otherwise a weak model could accidentally produce:

```text
Run tests
  ↓
Implement feature
```

which is nonsense.

---

# 13. `PlanValidator`

```java
public interface PlanValidator {

    PlanValidationResult validate(
        TaskGraph graph
    );
}
```

Checks:

```text
cycles
missing dependencies
invalid nodes
duplicate objectives
scope violations
impossible actions
```

---

# 14. Detect cycles

This:

```text
A → B → C → A
```

must never enter execution.

The runtime should reject it before doing anything.

---

# 15. But here's the important optimization

Don't require the whole graph to be known.

Use:

```text
Partial Task Graph
```

instead.

Example:

```text
GOAL
 │
 ▼
A Understand architecture
 │
 ▼
? next
```

After A completes:

```text
A
│
▼
B Implement OAuth
│
├── B1 provider config
├── B2 callback
└── B3 token handling
```

Only now do we expand B.

---

# 16. `PlanExpansion`

```java
public interface PlanExpansionEngine {

    Uni<List<PlanNode>> expand(
        PlanNode node,
        TaskContext context
    );
}
```

This is where Gemma is used.

---

# 17. Context passed to planner

Do **not** send the entire repository.

Give:

```text
TASK
Add OAuth login.

CURRENT OBJECTIVE
Understand authentication architecture.

KNOWN FACTS
- AuthController handles login.
- SessionService stores sessions.
- UserRepository stores users.

RELEVANT FILES
AuthController.java
SessionService.java
User.java

NEXT DECISION
What should be investigated next?
```

Small context.

---

# 18. The planner should produce structured output

```java
public record PlanProposal(
    String objective,
    List<ProposedStep> steps
) {}
```

Each:

```java
public record ProposedStep(
    String description,
    StepKind kind,
    List<String> dependencies
) {}
```

---

# 19. `StepKind`

```java
public enum StepKind {

    OBSERVE,
    SEARCH,
    IMPLEMENT,
    VERIFY,
    ASK_USER
}
```

This is useful because the runtime can route each kind automatically.

---

# 20. Planning should be capability-aware

Suppose Gemma proposes:

```text
"Use AST transformation tool."
```

but the current environment doesn't have one.

CapabilityDetector says:

```text
AST_TRANSFORM = false
```

Planner should instead receive:

```text
AVAILABLE CAPABILITIES
- source search
- symbol index
- compiler
- test runner
- patching
```

Then generate an executable plan.

---

# 21. This is another reason Wayang should own capability detection

The model should not need to know:

```text
what tools exist?
what sandbox exists?
what model context is available?
what repository index exists?
```

Runtime tells it.

---

# 22. `TaskContext`

```java
public record TaskContext(
    Task task,
    TaskState state,
    RepositoryContext repository,
    List<EvidenceId> evidence,
    List<Capability> capabilities,
    ResourceBudget budget
) {}
```

This becomes the compact planning context.

---

# 23. Task state

```java
public record TaskState(
    PlanNodeId activeNode,
    List<PlanNodeId> completedNodes,
    List<PlanNodeId> failedNodes,
    List<EvidenceId> recentEvidence,
    ProgressState progress
) {}
```

Again:

**runtime state, not model memory.**

---

# 24. Adaptive control loop

Now the central component:

```java
public interface AgentController {

    Uni<AgentResult> run(
        Task task
    );
}
```

Internally:

```text
while !terminal:

    observe()

    decide()

    act()

    verify()

    update()

    if needed:
        replan()
```

---

# 25. `Observe`

Observe isn't necessarily an LLM call.

It can gather:

```text
workspace state
git state
test state
process state
task graph
new diagnostics
recent changes
```

Then:

```java
Observation observation =
    observer.observe(context);
```

---

# 26. `Decide`

Decision can be deterministic or model-assisted.

```java
Decision decision =
    decisionEngine.decide(observation);
```

Possible:

```text
CONTINUE
EXPAND_PLAN
EXECUTE
VERIFY
REPLAN
ASK_USER
DONE
```

---

# 27. Deterministic decisions first

This is important.

If:

```text
node status = READY
```

then:

```text
EXECUTE
```

No need for Gemma.

If:

```text
test failed
```

then:

```text
DEBUG
```

may be deterministic.

Only ambiguous choices invoke Gemma.

---

# 28. `DecisionEngine`

```java
public interface DecisionEngine {

    Uni<Decision> decide(
        Observation observation
    );
}
```

Internally:

```text
rules
 ↓
if unresolved
 ↓
model
```

This should be the default.

---

# 29. Decision ladder

I recommend:

```text
1. deterministic rule
2. cached decision
3. heuristic
4. small model reasoning
5. larger model reasoning
6. user
```

That hierarchy is extremely useful for your local-first design.

---

# 30. Example

Question:

> Which test should I run?

Runtime sees:

```text
changed symbol:
TokenValidator.validateExpiration()

test references:
TokenValidatorTest.expiredToken()
```

Deterministically:

```text
run TokenValidatorTest.expiredToken
```

No Gemma.

---

# 31. Another example

Question:

> Should this be a new authentication provider abstraction or integrated into the existing service?

That's genuinely ambiguous.

Invoke Gemma.

---

# 32. This is where local vs cloud strategy becomes powerful

Your `CapabilityDetector → ContextController → ContextPolicy` architecture can extend to reasoning.

For example:

```text
ReasoningPolicy
```

could choose:

```text
LOCAL_SMALL
LOCAL_DEEP
CLOUD_LARGE
HYBRID
```

based on:

```text
task complexity
context size
model capabilities
latency budget
user policy
```

---

# 33. Example reasoning policy

```text
Simple:
"Which test covers this method?"
→ deterministic

Moderate:
"Why does this test fail?"
→ Gemma 12B

Complex:
"Design migration strategy across 12 modules."
→ cloud model if available
```

This is better than permanently forcing every task through the biggest model.

---

# 34. But don't automatically send everything to cloud

Respect user policy:

```java
public enum InferenceMode {

    LOCAL_ONLY,
    LOCAL_PREFERRED,
    CLOUD_ALLOWED,
    CLOUD_ONLY
}
```

For Aljabr:

```text
default:
LOCAL_PREFERRED
```

---

# 35. Planning budget

Introduce:

```java
public record PlanningBudget(
    int maxNodes,
    int maxDepth,
    int maxModelCalls,
    Duration maxPlanningTime
) {}
```

For your local model:

```text
maxDepth:
small

maxNodes:
small

expand incrementally
```

This prevents plan explosion.

---

# 36. Plan compression

Completed nodes shouldn't remain in active context forever.

Instead:

```text
Task Graph
    │
    ├── active
    ├── recent
    └── archived
```

Gemma gets:

```text
ACTIVE:
B2 implement callback

RECENT:
B1 provider config completed

SUMMARY:
Authentication integration uses SessionService.
```

not 100 historical plan nodes.

---

# 37. This is ContextEngine integration

Your context system should expose something like:

```java
ContextPacket buildPlanningContext(
    TaskContext context
);
```

and:

```java
ContextPacket buildExecutionContext(
    PlanNode node
);
```

and:

```java
ContextPacket buildDebugContext(
    DebugSession session
);
```

Different context types.

---

# 38. Don't use one universal prompt

This is another major improvement.

Instead of:

```text
SYSTEM PROMPT
+
EVERYTHING
```

have specialized context contracts:

```text
PlanningContext
CodingContext
DebugContext
ReviewContext
VerificationContext
```

Each contains only what that operation needs.

---

# 39. This is much better for Gemma 12B

For example:

### Planning

```text
goal
current objective
architecture facts
constraints
```

### Coding

```text
specific symbols
relevant files
change plan
tests
```

### Debugging

```text
failure
hypotheses
evidence
experiment history
```

### Review

```text
diff
impact
policy
tests
```

No unnecessary context crossover.

---

# 40. `Act`

Once decision is known:

```java
Action action =
    actionRouter.route(decision);
```

Possible actions:

```text
SEARCH
READ
PATCH
RUN
DEBUG
VERIFY
WAIT
ASK
```

---

# 41. `ActionRouter`

```java
public interface ActionRouter {

    Uni<ActionResult> execute(
        Action action
    );
}
```

Internally maps to:

```text
SEARCH → RepositoryTool
READ   → FileTool
PATCH  → MutationEngine
RUN    → ProcessManager
DEBUG  → DebugEngine
```

This is your existing capability architecture coming together.

---

# 42. `Verify`

Every meaningful action should have a verification contract.

For example:

```text
PATCH
→ syntax valid

ADD DEPENDENCY
→ dependency resolves

REFACTOR
→ compilation passes

BUG FIX
→ regression test passes
```

Don't let "action completed" equal "task succeeded."

---

# 43. `VerificationContract`

```java
public record VerificationContract(
    List<VerificationRequirement> requirements
) {}
```

Example:

```text
required:
compile
+
targeted test
```

---

# 44. Progress evaluation

After each step:

```java
ProgressReport progress =
    progressEvaluator.evaluate(context);
```

Possible:

```text
ON_TRACK
PARTIAL
BLOCKED
REGRESSED
COMPLETED
```

---

# 45. Why "REGRESSED" matters

Imagine:

```text
tests:
42 passing
```

After a change:

```text
41 passing
```

Even if the requested test passes:

```text
task ≠ success
```

The runtime should notice:

```text
REGRESSION_DETECTED
```

and replan.

---

# 46. `ProgressEvaluator`

```java
public interface ProgressEvaluator {

    ProgressReport evaluate(
        TaskContext context,
        VerificationResult verification
    );
}
```

---

# 47. Replanning

Replanning is not failure.

It should be normal.

Example:

```text
Plan:
A → B → C
```

A discovers:

```text
existing OAuth provider abstraction
```

Then B becomes unnecessary.

New plan:

```text
A → B' → C
```

Runtime removes obsolete work.

---

# 48. `ReplanReason`

```java
public enum ReplanReason {

    NEW_EVIDENCE,
    FAILURE,
    REGRESSION,
    PLAN_INVALID,
    ENVIRONMENT_CHANGE,
    USER_CHANGE,
    SCOPE_CHANGE,
    STAGNATION
}
```

---

# 49. This is why plans must be mutable

Don't make:

```text
final immutable 100-step plan
```

Instead:

```text
versioned task graph
```

Example:

```text
Plan v1
 ↓
evidence
 ↓
Plan v2
 ↓
evidence
 ↓
Plan v3
```

---

# 50. `PlanVersion`

```java
public record PlanVersion(
    int version,
    Instant createdAt,
    ReplanReason reason,
    List<PlanNode> nodes
) {}
```

Now you can explain:

> "Why did Aljabr change its plan?"

Because:

```text
Plan v2:
new evidence showed OAuth support already exists.
```

---

# 51. This is excellent for debugging the agent itself

You'll eventually have:

```text
Agent trace
```

like:

```text
Task T42

Plan v1
  A inspect auth
  B implement provider
  C test

A completed

Evidence E102:
existing OAuth abstraction found

Plan v2
  A completed
  B integrate existing abstraction
  C test
```

You can inspect exactly why Aljabr behaved as it did.

---

# 52. Agent trace should be structured

```java
public record AgentEvent(
    Instant timestamp,
    TaskId task,
    EventType type,
    Map<String, Object> data
) {}
```

Types:

```java
public enum EventType {

    TASK_STARTED,
    PLAN_CREATED,
    PLAN_EXPANDED,
    OBSERVATION,
    DECISION,
    ACTION_STARTED,
    ACTION_COMPLETED,
    VERIFICATION,
    REPLAN,
    MODEL_CALL,
    TASK_COMPLETED,
    TASK_FAILED
}
```

---

# 53. This gives you observability

You can later answer:

```text
Why did Aljabr run this command?

Why did it modify this file?

Why did it call Gemma?

Why did it replan?

Why did it stop?

Why did it ask the user?
```

Without relying on model-generated explanations.

---

# 54. Important: separate agent trace from model reasoning

Don't make:

```text
Gemma explanation
```

the authoritative record.

The authoritative record is:

```text
AgentEvent
```

plus:

```text
Evidence
Action
Verification
```

Model output is just one input into the decision.

---

# 55. Failure recovery

If the runtime crashes:

```text
Task T42
Plan v3
Node B2 RUNNING
Process X19
```

After restart:

```text
restore task
restore graph
restore process state
restore evidence
continue
```

This gives you true resumability.

---

# 56. User interruption

User says:

> Stop.

Runtime should transition:

```text
RUNNING
   ↓
PAUSING
   ↓
PAUSED
```

not simply kill everything.

Then:

```text
resume
```

can continue from the current state.

---

# 57. Cancellation

Differentiate:

```text
PAUSE
CANCEL
ABORT
```

### Pause

Preserve state.

### Cancel

Stop task intentionally.

### Abort

Emergency termination.

These should have different cleanup semantics.

---

# 58. User changes the workspace

This is another major event.

Suppose Aljabr is planning:

```text
modify AuthService
```

User edits AuthService.

Workspace fingerprint changes.

Controller receives:

```text
WORKSPACE_CHANGED
```

Then:

```text
invalidate stale plan
```

and re-observe.

---

# 59. This is the correct autonomous loop

Not:

```text
think → execute → think → execute
```

but:

```text
observe
  ↓
state
  ↓
decide
  ↓
action
  ↓
observe result
  ↓
update state
  ↓
evaluate progress
  ↓
continue/replan
```

The runtime is effectively a **state machine with model-assisted decisions**.

---

# 60. Core implementation

I'd make one central class:

```java
public final class AljabrController
        implements AgentController {

    private final TaskStore tasks;
    private final TaskGraphStore plans;
    private final ObservationEngine observer;
    private final DecisionEngine decisionEngine;
    private final ActionRouter actionRouter;
    private final VerificationEngine verifier;
    private final ProgressEvaluator progress;
    private final ReplanningEngine replanner;

    @Override
    public Uni<AgentResult> run(Task task) {
        // control loop
    }
}
```

The important thing is that this class **orchestrates**.

It should not contain:

```text
LLM prompting
filesystem manipulation
process execution
Git logic
patching
test parsing
```

Those remain separate services.

---

# 61. The loop pseudocode

Conceptually:

```java
while (!state.isTerminal()) {

    Observation observation =
        observer.observe(state);

    Decision decision =
        decisionEngine.decide(observation);

    ActionResult result =
        actionRouter.execute(decision.action());

    VerificationResult verification =
        verifier.verify(
            decision,
            result
        );

    ProgressReport progress =
        evaluator.evaluate(
            state,
            verification
        );

    stateStore.update(
        state,
        result,
        verification,
        progress
    );

    if (progress.requiresReplan()) {
        replanner.replan(state);
    }
}
```

That's the heart of Aljabr.

---

# 62. But add a crucial optimization

Don't call `observe()` by rescanning the entire repository every loop.

Use:

```text
incremental observation
```

For example:

```text
previous fingerprint
+
filesystem changes
+
process changes
+
new evidence
```

Only recompute what changed.

This ties directly into the caching architecture we've discussed.

---

# 63. Event-driven observation

Eventually:

```text
FileWatcher
ProcessWatcher
GitWatcher
TaskWatcher
```

emit:

```text
WorkspaceChanged
ProcessExited
TestCompleted
UserChangedFile
```

Then the controller reacts.

This is much more efficient than polling everything.

---

# 64. This also enables reactive Aljabr

For example:

```text
Aljabr starts server
        ↓
server crashes
        ↓
ProcessWatcher
        ↓
ExecutionEvidence
        ↓
DebugEngine
        ↓
Gemma
        ↓
repair
```

The agent doesn't need the user to say:

> "Hey, the server crashed."

---

# 65. Local model optimization becomes very clear

At this point, your local Gemma should mostly receive **decision packets**, not application state.

Something like:

```text
┌─────────────────────────────┐
│ ALJABR DECISION CONTEXT     │
├─────────────────────────────┤
│ Goal                        │
│ Current objective           │
│ Current state               │
│ Relevant evidence           │
│ Available actions           │
│ Constraints                 │
│ Recent changes              │
│ Previous decision outcome   │
└─────────────────────────────┘
```

Potentially only a few thousand tokens.

---

# 66. And the model output is tiny

Instead of:

```text
Write a 5,000-token explanation...
```

ask for:

```json
{
  "decision": "EXPERIMENT",
  "experiment": "RUN_TARGETED_TEST",
  "target": "TokenValidatorTest.expiredToken",
  "reason": "Directly tests the failing behavior"
}
```

That's exactly the kind of workload a 12B local model can handle well.

---

# 67. This is the fundamental Aljabr architecture

At this point I'd formalize the distinction:

```text
WAYANG
│
├── State
├── Memory
├── Context
├── Tools
├── Policy
├── Capability detection
├── Execution
├── Evidence
├── Planning infrastructure
├── Verification
├── Recovery
└── Agent lifecycle
        │
        ▼
     ALJABR
        │
        ├── Coding strategy
        ├── Planning
        ├── Debugging
        ├── Refactoring
        ├── Review
        └── Model interaction
```

This is a very healthy separation.

---

# 68. And importantly: Aljabr should NOT become another giant framework

Wayang should remain the **generic runtime**.

Aljabr should provide:

```text
skills
coding-specific policies
coding-specific planners
coding-specific prompts
coding-specific tools
coding-specific evaluators
```

rather than duplicating Wayang internals.

For example:

```text
Wayang:
ExecutionEngine

Aljabr:
JavaTestExecutionSkill
```

and:

```text
Wayang:
MutationEngine

Aljabr:
RefactoringStrategy
```

---

# 69. The next architectural layer

Now that we have:

```text
CapabilityDetector
ContextController
ContextPolicy
ContextEngine

Repository Intelligence
Evidence

MutationEngine
ProcessManager

DebugEngine
TaskPlanner
AgentController
```

the next major milestone should be:

# **`Skill System + Coding Skill Contracts`**

Not generic skills anymore.

We should define the actual Aljabr skill boundaries:

```text
CodeSearch
CodeReading
CodePlanning
CodeEditing
Refactoring
Testing
Debugging
DependencyManagement
GitOperations
CodeReview
Documentation
ProjectScaffolding
```

and, more importantly, define **how a skill declares its context requirements, tools, verification requirements, risk, and model strategy**.

That will let Aljabr remain small-model-friendly while still being extensible to a much larger cloud model when available.



Absolutely. **Next milestone: `Skill System + Coding Skill Contracts`.**

Now we turn the architecture above into something you can actually implement in Wayang/Aljabr without creating another giant framework.

The key idea:

> A Skill is not just a prompt and not just a tool wrapper.
> A Skill is a **bounded capability contract** that tells Aljabr *what it can do, what context it needs, what it is allowed to change, and how success is verified.*

---

# 1. The Skill architecture

I'd make the relationship:

```text
                 Aljabr Agent
                      │
                SkillRouter
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
    CodeSearch     CodeEdit      Testing
        │             │             │
        ▼             ▼             ▼
      Skill         Skill          Skill
      Contract      Contract       Contract
        │             │             │
        ├─────────────┼─────────────┤
        ▼             ▼             ▼
      Tools         Tools          Tools
```

But the skill also connects to:

```text
ContextEngine
PolicyEngine
VerificationEngine
RiskEngine
CapabilityDetector
```

---

# 2. The actual Skill contract

I'd start with:

```java
public interface Skill {

    SkillDescriptor descriptor();

    Uni<SkillResult> execute(
        SkillRequest request,
        SkillContext context
    );
}
```

Very deliberately:

**Skill doesn't directly own the agent loop.**

It performs one bounded capability.

---

# 3. `SkillDescriptor`

```java
public record SkillDescriptor(

    SkillId id,

    String name,

    String description,

    SkillCategory category,

    Set<Capability> requiredCapabilities,

    ContextRequirement contextRequirement,

    RiskProfile riskProfile,

    VerificationContract verification,

    SkillPolicy policy
) {}
```

This descriptor is extremely important.

It lets Wayang inspect a skill **before executing it**.

---

# 4. Skill categories

Keep this intentionally boring:

```java
public enum SkillCategory {

    DISCOVERY,
    ANALYSIS,
    CODING,
    TESTING,
    DEBUGGING,
    REFACTORING,
    VERSION_CONTROL,
    DOCUMENTATION
}
```

Don't create 50 categories.

---

# 5. First Aljabr skill set

I'd start with these:

```text
Discovery
├── repository.inspect
├── code.search
├── symbol.inspect
└── references.find

Analysis
├── architecture.analyze
├── impact.analyze
└── dependency.analyze

Coding
├── code.plan
├── code.edit
└── code.create

Testing
├── test.discover
├── test.run
└── test.analyze

Debugging
├── failure.diagnose
├── hypothesis.test
└── bug.repair

Refactoring
├── refactor.plan
└── refactor.apply

Version Control
├── git.diff
├── git.status
└── git.commit

Review
└── code.review
```

Don't implement all of them yet.

---

# 6. First actual vertical slice

Implement these first:

```text
code.search
symbol.inspect
code.edit
test.run
failure.diagnose
code.review
```

Together they already form a useful coding agent.

---

# 7. Skill should declare its tools

For example:

```java
public record SkillDescriptor(
    SkillId id,
    String name,
    String description,
    SkillCategory category,

    Set<ToolId> requiredTools,

    Set<Capability> requiredCapabilities,

    ContextRequirement contextRequirement,

    RiskProfile riskProfile,

    VerificationContract verification,

    SkillPolicy policy
) {}
```

Example:

```text
code.search

tools:
repository.search
repository.read

capabilities:
SOURCE_INDEX

risk:
READ_ONLY
```

---

# 8. This lets SkillRouter reject impossible skills

Suppose:

```text
code.edit
```

requires:

```text
MutationEngine
```

but environment has:

```text
MutationEngine = unavailable
```

Don't call Gemma.

Don't let Gemma discover this.

Runtime simply says:

```text
SKILL_UNAVAILABLE
```

---

# 9. Skill availability

Add:

```java
public record SkillAvailability(
    SkillId skill,
    boolean available,
    List<String> missingRequirements
) {}
```

Then:

```java
SkillAvailability check(
    SkillDescriptor skill
);
```

---

# 10. This is where your CapabilityDetector pays off

You already have the concept:

```text
CapabilityDetector
        │
        ▼
Capabilities
        │
        ▼
SkillRegistry
        │
        ▼
Available Skills
```

So:

```text
Capability:
TEST_RUNNER

enables:

test.run
failure.diagnose
code.verify
```

---

# 11. Skill dependencies

Skills can depend on other skills.

Example:

```text
bug.repair
   │
   ├── code.search
   ├── failure.diagnose
   ├── code.edit
   └── test.run
```

Represent this:

```java
public record SkillDependency(
    SkillId skill,
    DependencyRequirement requirement
) {}
```

---

# 12. But don't create recursive skill chaos

A skill should preferably depend on **capabilities**, not arbitrary skills.

For example:

```text
failure.diagnose
```

should depend on:

```text
failure evidence
repository access
test output
```

rather than:

```text
failure.diagnose depends on bug.repair
```

Keep dependencies mostly directional:

```text
Discovery
   ↓
Analysis
   ↓
Coding
   ↓
Verification
```

---

# 13. Skill input/output contracts

This is another critical piece.

Don't make:

```java
Uni<Object> execute(Object input)
```

Use explicit contracts.

Example:

```java
public record CodeSearchRequest(
    String query,
    SearchScope scope,
    int maxResults
) {}
```

Output:

```java
public record CodeSearchResult(
    List<SearchMatch> matches
) {}
```

Then:

```java
public interface CodeSearchSkill
        extends Skill {

    Uni<CodeSearchResult> execute(
        CodeSearchRequest request,
        SkillContext context
    );
}
```

Much easier to validate.

---

# 14. `SkillContext`

The skill should not receive the entire agent state.

```java
public record SkillContext(

    TaskId taskId,

    WorkspaceContext workspace,

    ContextPacket context,

    CapabilitySet capabilities,

    SkillBudget budget,

    CancellationToken cancellation
) {}
```

This is deliberately constrained.

---

# 15. Context is skill-specific

This is where your ContextEngine becomes powerful.

For:

```text
code.search
```

context may be almost nothing.

For:

```text
code.edit
```

context might require:

```text
target symbols
current implementation
callers
tests
change constraints
```

For:

```text
failure.diagnose
```

context:

```text
failure
stack
recent changes
hypotheses
test output
```

---

# 16. `ContextRequirement`

```java
public record ContextRequirement(

    Set<ContextSource> required,

    Set<ContextSource> optional,

    int preferredTokenBudget,

    int maximumTokenBudget
) {}
```

For example:

```text
code.edit

required:
TARGET_SOURCE
SYMBOL_CONTEXT
CHANGE_PLAN

optional:
CALLERS
RECENT_CHANGES
TESTS

preferred:
3000 tokens

maximum:
6000
```

---

# 17. This directly integrates your ContextPolicy

The flow becomes:

```text
Skill
 ↓
ContextRequirement
 ↓
ContextController
 ↓
ContextPolicy
 ↓
ContextEngine
 ↓
ContextPacket
```

So a skill **declares what it needs**.

It doesn't manually construct giant prompts.

---

# 18. Local model optimization becomes automatic

Suppose Gemma has limited context.

ContextController sees:

```text
requested:
8,000

available:
3,500
```

It applies:

```text
ContextPolicy.LOCAL_COMPACT
```

and produces:

```text
3,200 tokens
```

But importantly:

**it doesn't randomly truncate.**

It preserves:

```text
failure
target
constraints
relevant symbols
required evidence
```

and removes lower-priority material.

---

# 19. Cloud model

If available:

```text
requested:
8,000

available:
100,000
```

ContextController can select:

```text
ContextPolicy.FULL
```

and provide more:

```text
architecture
call graph
historical changes
related implementations
additional tests
```

Same skill.

Different context policy.

That's exactly the flexibility you wanted earlier.

---

# 20. Skill doesn't care which model is used

This is important.

Bad:

```java
CodeEditSkill {
    GemmaPrompt...
}
```

Better:

```java
CodeEditSkill {
    Skill contract
}
```

Then:

```text
ReasoningEngine
     │
     ├── local Gemma
     ├── local other model
     └── cloud model
```

The skill asks for reasoning.

The runtime chooses the model.

---

# 21. `ReasoningRequirement`

I'd add:

```java
public record ReasoningRequirement(
    ReasoningComplexity complexity,
    OutputConstraint output,
    boolean requiresModel
) {}
```

Complexity:

```java
public enum ReasoningComplexity {

    NONE,
    LOW,
    MEDIUM,
    HIGH
}
```

---

# 22. Example

### Search

```text
complexity:
NONE
```

No model.

### Code edit

```text
complexity:
MEDIUM
```

Gemma.

### Large architectural migration

```text
complexity:
HIGH
```

Potentially cloud model.

---

# 23. Skill descriptor becomes

```java
public record SkillDescriptor(

    SkillId id,
    String name,
    String description,

    SkillCategory category,

    Set<ToolId> requiredTools,
    Set<Capability> requiredCapabilities,

    ContextRequirement contextRequirement,
    ReasoningRequirement reasoningRequirement,

    RiskProfile riskProfile,
    VerificationContract verification,

    SkillPolicy policy
) {}
```

That's a strong foundation.

---

# 24. Risk profile

Coding skills should declare risk.

```java
public enum RiskLevel {

    READ_ONLY,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}
```

Then:

```java
public record RiskProfile(
    RiskLevel level,
    boolean mutatesWorkspace,
    boolean mutatesDependencies,
    boolean mutatesGit
) {}
```

---

# 25. Example

```text
code.search
→ READ_ONLY

code.edit
→ MODERATE

dependency.add
→ HIGH

git.commit
→ HIGH
```

This feeds MutationGate / PolicyEngine.

---

# 26. Skill policy

```java
public record SkillPolicy(

    boolean allowAutonomousExecution,

    boolean requiresApproval,

    boolean allowRetry,

    int maxRetries,

    boolean allowReplanning
) {}
```

Example:

```text
code.search
autonomous = true

code.edit
autonomous = true
approval = false

git.push
autonomous = false
approval = true
```

---

# 27. This means "permission" is declarative

Instead of scattered:

```java
if (userAllowed...)
```

you have:

```text
Skill
 ↓
RiskProfile
 ↓
PolicyEngine
 ↓
AuthorizationDecision
```

Much cleaner.

---

# 28. Skill lifecycle

A skill execution should have:

```text
PREPARE
   ↓
VALIDATE
   ↓
CONTEXT_BUILD
   ↓
REASON
   ↓
EXECUTE
   ↓
VERIFY
   ↓
RECORD
```

Don't just:

```text
skill.execute()
```

---

# 29. `SkillExecutor`

```java
public interface SkillExecutor {

    Uni<SkillResult> execute(
        Skill skill,
        SkillRequest request,
        SkillContext context
    );
}
```

Internally:

```text
validate capability
validate policy
build context
invoke model if needed
invoke tool
verify
record evidence
```

---

# 30. This centralizes safety

Every skill automatically gets:

```text
authorization
budget
context policy
timeouts
cancellation
verification
telemetry
recovery
```

You don't implement those separately in every skill.

---

# 31. Example: `CodeSearchSkill`

Implementation can be tiny:

```java
public final class CodeSearchSkill
        implements CodeSearchSkillContract {

    private final RepositorySearch repository;

    @Override
    public Uni<CodeSearchResult> execute(
        CodeSearchRequest request,
        SkillContext context
    ) {
        return repository.search(
            request.query(),
            request.scope(),
            request.maxResults()
        );
    }
}
```

No model.

No prompt.

No giant framework.

---

# 32. Example: `CodeEditSkill`

This one is more interesting:

```text
CodeEditSkill
   ↓
ContextEngine
   ↓
Gemma
   ↓
PatchProposal
   ↓
PatchValidator
   ↓
MutationGate
   ↓
AtomicApply
   ↓
Verification
```

So the skill orchestrates the coding capability, but Wayang still owns mutation safety.

---

# 33. Never allow model output to directly become a filesystem write

Bad:

```text
Gemma
 ↓
file contents
 ↓
write()
```

Good:

```text
Gemma
 ↓
EditProposal
 ↓
validate
 ↓
policy
 ↓
patch
 ↓
verify
```

---

# 34. `EditProposal`

```java
public record EditProposal(

    Path file,

    SourceRange target,

    String replacement,

    String rationale,

    List<String> expectedEffects
) {}
```

Then:

```text
PatchValidator
```

checks whether the proposal is actually valid.

---

# 35. Multiple edits

Use:

```java
public record ChangeSet(
    List<EditProposal> edits
) {}
```

Then validate the **whole set**.

This avoids:

```text
edit 1 succeeds
edit 2 fails
edit 3 never runs
```

Use atomic application where possible.

---

# 36. Verification belongs to the skill contract

For `code.edit`:

```text
required verification:
compile affected module
```

For bug repair:

```text
required:
failing test passes
```

For refactoring:

```text
required:
compile
+
existing tests
```

---

# 37. Verification should be configurable

Don't always run the entire test suite.

Use:

```java
VerificationPolicy {
    TARGETED,
    AFFECTED_MODULE,
    FULL_SUITE
}
```

The planner can escalate:

```text
targeted
 ↓
affected module
 ↓
full suite
```

only when required.

Again: excellent for local-first execution.

---

# 38. Skill result should contain evidence

```java
public record SkillResult(

    SkillExecutionId executionId,

    SkillStatus status,

    Object output,

    List<Evidence> evidence,

    VerificationResult verification,

    ResourceUsage usage
) {}
```

This is important because skills don't merely produce output.

They produce **knowledge**.

---

# 39. Example

`code.search` returns:

```text
output:
5 matching symbols

evidence:
E123
E124
E125
```

Then later:

```text
planner
```

can use those facts without repeating the search.

---

# 40. This creates a beautiful loop

```text
Skill
 ↓
Action
 ↓
Evidence
 ↓
Task State
 ↓
ContextEngine
 ↓
Next Skill
```

So Aljabr progressively builds knowledge instead of repeatedly searching.

---

# 41. Skill cache

Some skills can cache safely.

Example:

```text
code.search
symbol.inspect
dependency.inspect
```

Potential cache key:

```java
record SkillCacheKey(
    SkillId skill,
    WorkspaceFingerprint workspace,
    String normalizedRequest
) {}
```

If workspace hasn't changed:

```text
same request
→ cached result
```

---

# 42. Don't cache mutation results blindly

For:

```text
code.edit
```

cache should generally not be:

```text
"this patch is valid forever"
```

because the workspace may change.

Instead cache:

```text
reasoning
analysis
search
evidence
```

more aggressively than mutations.

---

# 43. Skill invalidation

Use workspace fingerprints.

```text
Workspace v41
   ↓
search result cached

user edits AuthService
   ↓
Workspace v42

cache:
AuthService-related evidence invalid
```

But unrelated:

```text
README search
```

might remain valid.

So invalidation should be dependency-aware.

---

# 44. `EvidenceDependency`

```java
public record EvidenceDependency(
    Set<Path> files,
    Set<SymbolId> symbols,
    Set<ModuleId> modules
) {}
```

Then:

```text
evidence E123
depends on:
AuthService.java
TokenValidator.java
```

If neither changes:

```text
E123 remains valid
```

---

# 45. This is much better than clearing the entire cache

Otherwise every small edit causes:

```text
invalidate everything
```

which destroys your local inference optimization.

---

# 46. Skill registry

```java
public interface SkillRegistry {

    void register(Skill skill);

    Optional<Skill> find(SkillId id);

    List<SkillDescriptor> available(
        CapabilitySet capabilities
    );
}
```

At startup:

```text
Wayang runtime
 ↓
load Aljabr skills
 ↓
detect capabilities
 ↓
register available skills
```

---

# 47. Skill discovery

The planner doesn't need every skill implementation.

Give it a compact catalog:

```text
AVAILABLE SKILLS

code.search
Search source code.

symbol.inspect
Inspect implementation and metadata.

code.edit
Modify source through validated patches.

test.run
Run targeted tests.

failure.diagnose
Analyze execution failures.
```

That's enough.

---

# 48. Skill descriptions should be operational

Bad:

```text
"Helps with coding."
```

Good:

```text
"Search repository source for symbols, text, or structural references.
Read-only. Does not modify workspace."
```

The model can choose accurately from that.

---

# 49. Skill selection

Now create:

```java
public interface SkillSelector {

    Uni<SkillSelection> select(
        TaskContext context,
        List<SkillDescriptor> candidates
    );
}
```

But again:

**don't call Gemma if rules can decide.**

---

# 50. Candidate filtering first

Given:

```text
30 skills
```

runtime filters:

```text
capabilities
scope
risk
task type
current state
dependencies
```

Maybe only:

```text
code.search
symbol.inspect
code.edit
test.run
```

reach Gemma.

This dramatically reduces reasoning complexity.

---

# 51. The local model now solves a tiny classification problem

Instead of:

> "Figure out how to code this project."

Gemma receives:

```text
CURRENT OBJECTIVE:
Locate authentication implementation.

AVAILABLE SKILLS:
1. code.search
2. symbol.inspect
3. dependency.inspect

Choose the next skill.
```

Much easier.

---

# 52. This is probably the single most important pattern for Aljabr

**Reduce model problem complexity before invoking the model.**

```text
Raw world
   ↓
Wayang
   ↓
filter
   ↓
normalize
   ↓
compress
   ↓
candidate set
   ↓
Gemma
```

Gemma receives the **decision boundary**, not the whole world.

---

# 53. Skill orchestration

A coding task might become:

```text
code.search
   ↓
symbol.inspect
   ↓
architecture.analyze
   ↓
code.plan
   ↓
code.edit
   ↓
test.run
   ↓
failure.diagnose
   ↓
code.edit
   ↓
test.run
   ↓
code.review
```

This is the actual Aljabr behavior we want.

---

# 54. And this is where your previous DebugEngine fits

Debugging isn't a completely separate agent anymore.

It becomes a skill/subsystem:

```text
failure.diagnose
        ↓
DebugEngine
```

Likewise:

```text
code.edit
        ↓
MutationEngine
```

and:

```text
test.run
        ↓
ProcessManager
```

So the architecture becomes compositional.

---

# 55. Recommended package structure

Something like:

```text
wayang/
  runtime/
  context/
  memory/
  capability/
  tools/
  execution/
  policy/
  verification/

aljabr/
  agent/
  planning/
  skills/

    discovery/
      CodeSearchSkill
      SymbolInspectSkill

    analysis/
      ArchitectureAnalyzeSkill
      ImpactAnalyzeSkill

    coding/
      CodePlanSkill
      CodeEditSkill

    testing/
      TestDiscoverSkill
      TestRunSkill

    debugging/
      FailureDiagnoseSkill
      HypothesisTestSkill

    refactoring/
      RefactorPlanSkill
      RefactorApplySkill

    review/
      CodeReviewSkill

  prompts/
  policies/
```

---

# 56. Don't put skills inside Wayang core

This is important architecturally.

Wayang should not know:

```text
Java
Git
JUnit
TypeScript
OAuth
Gradle
Maven
```

Those belong to Aljabr or domain-specific skills.

Wayang knows:

```text
Tool
Skill
Context
Policy
Capability
Execution
Evidence
Memory
```

---

# 57. Language-specific skills

Eventually Aljabr can have:

```text
JavaSkillPack
PythonSkillPack
RustSkillPack
TypeScriptSkillPack
GoSkillPack
```

Each can register:

```text
language parser
compiler
test conventions
package manager
formatter
lint rules
```

Then generic skills reuse them.

---

# 58. Example

Generic:

```text
code.edit
```

Language adapter:

```text
JavaSourceAdapter
```

Tool:

```text
javac / Gradle / Maven
```

Verification:

```text
JUnit
```

Wayang itself doesn't care.

---

# 59. This is the next major abstraction

```text
Skill
   │
   ├── Contract
   ├── Context requirements
   ├── Capability requirements
   ├── Risk
   ├── Reasoning requirement
   ├── Verification
   └── Policy
```

That is much stronger than simply:

```text
Skill.md + tools
```

You can still **describe the skill in `Skill.md`**, but the runtime should have a structured contract alongside it.

---

# 60. Practical implementation order

Don't build the entire skill ecosystem.

Do this:

### Phase A

```text
Skill
SkillDescriptor
SkillRegistry
SkillExecutor
SkillContext
```

### Phase B

```text
ContextRequirement
RiskProfile
VerificationContract
ReasoningRequirement
```

### Phase C

```text
code.search
symbol.inspect
test.run
```

### Phase D

```text
code.edit
failure.diagnose
```

### Phase E

```text
SkillSelector
Skill caching
Skill evidence
```

### Phase F

```text
language-specific skill packs
```

---

# 61. The final execution path

For a user request:

> "Fix the failing login test."

Aljabr should eventually do:

```text
User
 │
 ▼
Task
 │
 ▼
TaskPlanner
 │
 ▼
code.search
 │
 ▼
symbol.inspect
 │
 ▼
test.run
 │
 ▼
failure.diagnose
 │
 ▼
Hypothesis
 │
 ▼
code.edit
 │
 ▼
test.run
 │
 ├── FAIL → failure.diagnose
 │
 └── PASS
       │
       ▼
   code.review
       │
       ▼
      DONE
```

And every transition is backed by:

```text
state
evidence
policy
verification
```

rather than being hidden inside Gemma's conversation.

---

## The next milestone after this

Once this skill contract is in place, I would go into **`CodeEditSkill` deeply**.

That is the most important mutation path for Aljabr:

```text
CodeEditSkill
      ↓
ChangeIntent
      ↓
ContextBuilder
      ↓
Gemma
      ↓
Structured EditProposal
      ↓
AST/Text Validation
      ↓
Impact Analysis
      ↓
Mutation Policy
      ↓
Atomic Patch
      ↓
Compile
      ↓
Targeted Test
      ↓
Regression Check
      ↓
Evidence
```

That deserves a deep implementation because **this is where a coding agent either becomes trustworthy or becomes a dangerous autocomplete engine**.


Next: **`CodeEditSkill` — the trusted mutation pipeline.**

This is probably the most important Aljabr skill to get right. Searching can be imperfect; editing incorrectly can **destroy a working repository**. So the model should never have direct authority to write files.

The architecture should be:

```text
User Goal
   ↓
Task / Plan
   ↓
CodeEditSkill
   ↓
ChangeIntent
   ↓
ContextBuilder
   ↓
Gemma
   ↓
Structured EditProposal
   ↓
ProposalValidator
   ↓
ImpactAnalyzer
   ↓
MutationPolicy
   ↓
AtomicApply
   ↓
Compile / Test
   ↓
Review
   ↓
Evidence
```

The important principle:

> **Gemma proposes. Wayang validates and applies.**

---

# 1. Separate `ChangeIntent` from `EditProposal`

This distinction is extremely useful.

The planner says:

> "Fix the expiration boundary bug."

That's a **ChangeIntent**.

```java
public record ChangeIntent(
    String objective,
    List<SymbolRef> targets,
    List<String> constraints,
    List<EvidenceId> supportingEvidence
) {}
```

Example:

```text
objective:
Fix token expiration boundary handling.

targets:
TokenValidator.validateExpiration()

constraints:
- preserve existing API
- don't change token format
- add regression test
```

The model then turns that into an actual edit.

---

# 2. `EditProposal`

```java
public record EditProposal(
    EditProposalId id,

    List<FileEdit> edits,

    String summary,

    List<String> expectedEffects,

    List<String> assumptions
) {}
```

And:

```java
public record FileEdit(
    Path file,
    SourceRange range,
    String replacement
) {}
```

This is much safer than asking Gemma for arbitrary shell commands.

---

# 3. Don't initially ask for complete files

Avoid:

```text
Gemma:
"Here is the entire modified AuthService.java..."
```

For a local model this wastes tokens and increases the chance of accidental changes.

Prefer:

```text
target:
AuthService.java:112-117

replace:
old code

with:
new code
```

The model only emits the smallest necessary mutation.

---

# 4. Minimal edit principle

Define:

```java
public enum EditStrategy {

    MINIMAL_PATCH,
    MULTI_FILE_PATCH,
    STRUCTURAL_REFACTOR
}
```

Default:

```text
MINIMAL_PATCH
```

Aljabr should prefer:

```text
1 file
5 lines
```

over:

```text
1 file
rewritten entirely
```

unless there is a strong reason otherwise.

---

# 5. Why this matters for Gemma 12B

Smaller output means:

```text
less generation
less context
less parsing
less accidental modification
easier validation
```

So this optimization improves both **efficiency and correctness**.

It doesn't inherently reduce quality because you're not removing information necessary to make the decision—you are reducing unnecessary representation.

---

# 6. Context for CodeEditSkill

The skill should request:

```java
ContextRequirement(
    required = Set.of(
        TARGET_SOURCE,
        CHANGE_INTENT,
        RELEVANT_EVIDENCE
    ),

    optional = Set.of(
        CALLERS,
        CALLEES,
        RELATED_TESTS,
        RECENT_CHANGES
    ),

    preferredTokenBudget = 3500,
    maximumTokenBudget = 7000
)
```

The ContextEngine decides what actually gets included.

---

# 7. The model context

Something like:

```text
CHANGE INTENT

Fix token expiration boundary handling.

TARGET

TokenValidator.validateExpiration()

CURRENT IMPLEMENTATION

...

RELEVANT TEST

TokenValidatorTest.expiredToken()

CONSTRAINTS

- preserve public API
- don't change token format

EXPECTED RESULT

Expired token → false
```

That's enough for many edits.

---

# 8. Ask Gemma for a structured proposal

For example:

```json
{
  "summary": "Fix expiration comparison",
  "edits": [
    {
      "file": "src/.../TokenValidator.java",
      "startLine": 78,
      "endLine": 78,
      "replacement": "return now.isBefore(expiration);"
    }
  ],
  "expectedEffects": [
    "expired tokens are rejected"
  ],
  "assumptions": [
    "expiration timestamp is inclusive"
  ]
}
```

The actual schema should be enforced by your inference adapter.

---

# 9. Never trust line numbers alone

This is important.

Gemma says:

```text
line 78
```

but the file might have changed.

Before applying, locate the intended source using a stronger anchor:

```java
public record EditAnchor(
    Optional<SourceRange> range,
    Optional<String> expectedText,
    Optional<SymbolRef> symbol,
    Optional<String> beforeContext,
    Optional<String> afterContext
) {}
```

---

# 10. Best anchor hierarchy

Use:

```text
AST/symbol identity
        ↓
exact source match
        ↓
contextual source match
        ↓
line number
```

Line numbers should be the weakest signal.

---

# 11. `ProposalValidator`

Before mutation:

```java
public interface ProposalValidator {

    ValidationResult validate(
        EditProposal proposal,
        WorkspaceSnapshot workspace
    );
}
```

Checks:

```text
file exists
target exists
expected source matches
syntax remains plausible
scope is allowed
edit is minimal
```

---

# 12. Exact source mismatch

Suppose Gemma proposes:

```text
old:
if (expiry.isBefore(now))
```

but actual repository has:

```text
if (expiry.isBefore(clock.now()))
```

Do **not** guess.

Return:

```text
EDIT_TARGET_MISMATCH
```

Then rebuild context.

This is far safer than fuzzy replacing something similar.

---

# 13. Proposal validation stages

I'd make the pipeline explicit:

```text
EditProposal
    │
    ▼
StructuralValidation
    │
    ▼
TargetValidation
    │
    ▼
ScopeValidation
    │
    ▼
SyntaxValidation
    │
    ▼
ImpactAnalysis
    │
    ▼
PolicyValidation
```

Only then:

```text
AtomicApply
```

---

# 14. `StructuralValidation`

Check:

```text
empty patch?
duplicate edits?
overlapping ranges?
invalid ranges?
invalid paths?
```

For example:

```text
edit A:
lines 10-20

edit B:
lines 15-25
```

Reject unless explicitly supported.

---

# 15. `ScopeValidation`

If the task says:

```text
modify authentication module
```

and Gemma proposes:

```text
README.md
deployment/prod.yaml
database/migration.sql
```

that's suspicious.

The runtime should reject or request confirmation.

---

# 16. `ImpactAnalyzer`

Before applying:

```java
public interface ImpactAnalyzer {

    ImpactReport analyze(
        EditProposal proposal,
        RepositoryContext repository
    );
}
```

Possible result:

```text
affected symbols: 3
affected files: 2
affected tests: 5
public API changed: false
dependency graph changed: false
```

---

# 17. Impact should influence risk

For example:

```text
1 private method
→ LOW

public interface
→ MODERATE

database schema
→ HIGH

dependency version
→ HIGH

build configuration
→ HIGH
```

So:

```java
RiskLevel risk = riskEngine.assess(impact);
```

---

# 18. Mutation policy

Then:

```java
AuthorizationDecision decision =
    mutationPolicy.authorize(
        proposal,
        impact,
        userPolicy
    );
```

Possible:

```text
ALLOW
ALLOW_WITH_REVIEW
REQUIRE_APPROVAL
DENY
```

---

# 19. Local-first default

For normal source changes:

```text
private method
+
targeted test
+
no API change
```

Aljabr can autonomously apply.

For:

```text
database migration
dependency upgrade
public API change
security configuration
```

you can require approval.

---

# 20. Atomic apply

Never:

```text
write file 1
write file 2
write file 3
```

without rollback.

Instead:

```java
public interface MutationEngine {

    Uni<MutationResult> apply(
        ChangeSet changeSet,
        MutationTransaction transaction
    );
}
```

Conceptually:

```text
snapshot
   ↓
validate all edits
   ↓
stage all edits
   ↓
apply atomically
   ↓
verify
   ↓
commit
```

If verification fails:

```text
rollback
```

---

# 21. Workspace snapshot

Before mutation:

```java
public record WorkspaceSnapshot(
    WorkspaceFingerprint fingerprint,
    Map<Path, FileFingerprint> files,
    GitState gitState
) {}
```

This gives you optimistic concurrency protection.

---

# 22. User changes during reasoning

This scenario matters:

```text
T0:
Aljabr reads AuthService

T1:
User modifies AuthService

T2:
Gemma proposes patch based on old source
```

Before apply:

```text
currentFingerprint != originalFingerprint
```

Therefore:

```text
STALE_WORKSPACE
```

Do not apply.

Re-read the file and regenerate the proposal.

---

# 23. This prevents a very nasty class of bugs

Without this:

```text
old context
→ valid patch
→ applied to changed code
→ unintended corruption
```

With it:

```text
old context
→ workspace changed
→ invalidate proposal
→ refresh context
→ regenerate
```

---

# 24. Patch staging

I'd use:

```java
public record StagedChange(
    ChangeSet changeSet,
    WorkspaceSnapshot base,
    WorkspaceSnapshot staged
) {}
```

The staged workspace can be validated before touching the real workspace.

---

# 25. Best architecture

If your environment permits it:

```text
Real Workspace
      │
      ▼
Workspace Snapshot
      │
      ▼
Staging Workspace
      │
      ├── apply patch
      ├── compile
      ├── test
      └── review
             │
          PASS?
          /   \
        yes   no
         │     │
         ▼     ▼
      commit rollback
```

This is far more trustworthy.

---

# 26. Verification levels

Don't always run the entire project.

Use an escalation ladder:

```text
LEVEL 0
patch validation

LEVEL 1
syntax / formatting

LEVEL 2
compile affected module

LEVEL 3
targeted tests

LEVEL 4
affected test suite

LEVEL 5
full test suite
```

---

# 27. Verification policy

```java
public record VerificationPolicy(
    VerificationLevel minimum,
    VerificationLevel maximum,
    boolean escalateOnFailure
) {}
```

Typical code edit:

```text
minimum:
compile

maximum:
affected tests
```

Bug repair:

```text
minimum:
targeted regression test

maximum:
full suite
```

---

# 28. Escalation

Suppose:

```text
targeted test passes
```

But impact analyzer says:

```text
public API affected
```

Then escalate:

```text
targeted
→ module tests
→ possibly full suite
```

No need to ask Gemma to decide something deterministic.

---

# 29. Regression detection

After a successful targeted test:

```text
before:
127 passing
3 ignored

after:
126 passing
4 ignored
```

Something changed unexpectedly.

Verification should compare baseline.

```java
public record TestDelta(
    int newlyPassing,
    int newlyFailing,
    int noLongerExecuted
) {}
```

---

# 30. A patch is not successful because the requested test passes

This distinction is crucial.

Success means:

```text
requested behavior fixed
+
no unacceptable regressions
+
policy satisfied
```

---

# 31. `CodeEditSkill` result

```java
public record CodeEditResult(
    EditProposal proposal,
    MutationResult mutation,
    ImpactReport impact,
    VerificationResult verification,
    List<Evidence> evidence
) {}
```

Now the task engine has everything it needs.

---

# 32. If the patch fails

Don't immediately ask Gemma:

> "Try again."

Instead classify the failure.

```text
PATCH_REJECTED
TARGET_MISMATCH
COMPILE_FAILURE
TEST_FAILURE
REGRESSION
POLICY_DENIED
STALE_WORKSPACE
```

Each leads to a different response.

---

# 33. Example: `TARGET_MISMATCH`

```text
Gemma proposal
 ↓
target mismatch
 ↓
refresh source context
 ↓
regenerate proposal
```

No debugging yet.

---

# 34. Example: compile failure

```text
patch
 ↓
compile
 ↓
compile failure
 ↓
failure.diagnose
```

Now the debugging system takes over.

---

# 35. Example: test failure

```text
patch
 ↓
test
 ↓
failure
 ↓
compare against original failure
```

If it's the **same failure**:

```text
patch ineffective
```

If it's a **new failure**:

```text
regression or side effect
```

This distinction is valuable.

---

# 36. Example: regression

```text
original:
expiredToken test failed

after patch:
expiredToken passes
refreshToken test fails
```

Don't tell Gemma:

> "Tests failed."

Tell it:

```text
ORIGINAL FAILURE:
expiredToken

FIXED:
yes

NEW REGRESSION:
refreshToken

LIKELY IMPACT:
token expiration/session interaction
```

That gives the model much better reasoning material.

---

# 37. Patch iteration budget

Never allow infinite edits.

```java
public record MutationBudget(
    int maxAttempts,
    int maxFilesChanged,
    int maxLinesChanged,
    int maxRegressionCount
) {}
```

Example:

```text
max attempts = 3
```

After three failed repair attempts:

```text
BLOCKED
```

and either:

```text
ask user
```

or:

```text
switch reasoning strategy
```

---

# 38. Patch similarity detection

Detect when Gemma keeps proposing nearly identical patches.

For example:

```text
Patch 1:
expiry.isBefore(now)

Patch 2:
expiry.isBefore(clock.now())

Patch 3:
expiry.isBefore(currentTime)
```

If all are semantically attempting the same change and fail:

```text
REPAIR_STAGNATION
```

Send the task back to diagnosis rather than allowing endless edits.

---

# 39. Keep an edit history

```java
public record EditAttempt(
    EditProposal proposal,
    MutationResult result,
    VerificationResult verification,
    FailureClassification failure
) {}
```

Then Gemma can receive a compact summary:

```text
ATTEMPTS

1. changed comparison → test still failed
2. changed timestamp source → regression introduced

DO NOT REPEAT:
comparison-only changes
```

Again, useful for a small model.

---

# 40. Code review after mutation

Even for autonomous changes, add a lightweight review stage:

```text
ChangeSet
 ↓
ReviewSkill
 ↓
ReviewResult
```

Review questions:

```text
Does change match intent?
Is scope reasonable?
Any suspicious unrelated modifications?
Any API changes?
Any obvious complexity increase?
```

---

# 41. Don't ask Gemma to review the whole repository

Review only:

```text
diff
+
intent
+
impact
+
verification
```

Example:

```text
INTENT
Fix token expiration.

DIFF
3 lines.

IMPACT
1 private method.

VERIFICATION
2 targeted tests pass.
```

Very small context.

---

# 42. Review result

```java
public record ReviewResult(
    ReviewStatus status,
    List<ReviewFinding> findings,
    boolean safeToComplete
) {}
```

Statuses:

```text
APPROVED
NEEDS_REVISION
BLOCKED
```

---

# 43. Now the complete `CodeEditSkill`

```text
                  ChangeIntent
                       │
                       ▼
                 ContextEngine
                       │
                       ▼
                     Gemma
                       │
                       ▼
                 EditProposal
                       │
                       ▼
              ProposalValidator
                       │
                       ▼
                ImpactAnalyzer
                       │
                       ▼
                MutationPolicy
                       │
                       ▼
                 MutationEngine
                       │
                       ▼
                 Verification
                       │
              ┌────────┴────────┐
              ▼                 ▼
            PASS              FAIL
              │                 │
              ▼                 ▼
          CodeReview        FailureClass
              │                 │
              ▼                 ▼
             DONE          DebugEngine
                                │
                                ▼
                           new evidence
                                │
                                ▼
                              retry
```

That's a strong architecture.

---

# 44. One particularly important optimization

I would make `CodeEditSkill` **two-stage model interaction**.

### Stage 1 — intent reasoning

Gemma determines:

```text
what should change
```

### Stage 2 — patch generation

Only if necessary, Gemma determines:

```text
exact source modification
```

So:

```text
ChangeIntent
 ↓
Reasoning
 ↓
EditPlan
 ↓
PatchGeneration
```

This prevents the model from mixing:

> "What should the solution be?"

with:

> "What exact text should I replace?"

---

# 45. `EditPlan`

```java
public record EditPlan(
    List<TargetChange> targets,
    List<String> invariants,
    List<String> expectedEffects
) {}
```

Example:

```text
target:
TokenValidator.validateExpiration

change:
use strict expiration comparison

invariant:
token parsing remains unchanged

expected:
expired token rejected
```

Then patch generation is constrained.

---

# 46. This is particularly good for small models

Instead of asking Gemma 12B to simultaneously:

```text
understand repository
diagnose bug
design solution
find exact code
generate patch
```

you decompose:

```text
runtime
 ↓
context
 ↓
Gemma
 ↓
small decision
 ↓
runtime
 ↓
Gemma
 ↓
small patch
```

The model has a much smaller reasoning problem.

---

# 47. But don't over-decompose everything

This is important too.

If a trivial change is:

```text
rename local variable
```

don't perform:

```text
5 model calls
```

The policy should recognize low complexity:

```text
LOW_COMPLEXITY_EDIT
```

and use one model call or deterministic tooling.

---

# 48. The ideal model escalation

```text
Simple deterministic edit
        ↓
no model

Simple ambiguous edit
        ↓
1 local call

Moderate edit
        ↓
local reasoning + validation

Complex multi-module edit
        ↓
local planning
+
possibly cloud reasoning

Critical architectural change
        ↓
strong model
+
mandatory review
```

This is the local-first strategy I would build into Aljabr.

---

# 49. The final contract

So your `CodeEditSkill` descriptor might look conceptually like:

```java
SkillDescriptor(
    id = "code.edit",

    category = CODING,

    requiredTools = {
        "repository.read",
        "mutation.apply",
        "workspace.snapshot"
    },

    requiredCapabilities = {
        SOURCE_ACCESS,
        MUTATION,
        COMPILER
    },

    contextRequirement = ...,

    reasoningRequirement = MEDIUM,

    riskProfile = MODERATE,

    verification = {
        compile = REQUIRED,
        targetedTests = CONDITIONAL
    },

    policy = {
        autonomous = true,
        approval = false,
        maxRetries = 3
    }
)
```

That is the kind of contract I think belongs in the framework.

---

# 50. The next milestone

Now that we have a trustworthy editing pipeline, the next thing I'd build is:

## **`Repository Intelligence / Code Intelligence Layer`**

Because everything above becomes dramatically better when Aljabr can understand code structurally rather than treating the repository as a pile of text.

The architecture would be:

```text
Repository
    │
    ├── File Index
    ├── Symbol Index
    ├── AST Index
    ├── Reference Index
    ├── Call Graph
    ├── Dependency Graph
    ├── Test Map
    └── Change History
             │
             ▼
      RepositoryIntel
             │
     ┌───────┼────────┐
     ▼       ▼        ▼
  Search   Impact   Context
     │       │        │
     └───────┼────────┘
             ▼
           Aljabr
```

And **this should be heavily deterministic**, not LLM-driven.

That is the next big piece that will make your Gemma 12B feel much smarter than the raw model actually is.


Yes. **Next milestone: `Repository Intelligence / Code Intelligence Layer`.**

This is the layer that makes Aljabr genuinely strong with a small local model. Instead of asking Gemma to understand a repository from raw files, Wayang computes the structural facts deterministically and gives Gemma only the relevant slice.

The core principle:

> **Use deterministic code intelligence to replace LLM work whenever the answer can be computed reliably.**

For your M4 16 GB + Gemma 12B setup, this is arguably more important than increasing model size.

---

# 1. Overall architecture

I'd make it:

```text
                    Repository
                        │
             ┌──────────┴──────────┐
             │                     │
        File Scanner          Language Adapters
             │                     │
             └──────────┬──────────┘
                        ▼
                Code Intelligence
                        │
       ┌────────────────┼─────────────────┐
       ▼                ▼                 ▼
   File Index       Symbol Index      Reference Index
       │                │                 │
       ├────────────────┼─────────────────┤
       ▼                ▼                 ▼
   AST Index       Call Graph       Dependency Graph
       │                │                 │
       └────────────────┼─────────────────┘
                        ▼
                 RepositoryIntel
                        │
       ┌────────────────┼────────────────┐
       ▼                ▼                ▼
   SearchEngine    ImpactAnalyzer   ContextBuilder
       │                │                │
       └────────────────┼────────────────┘
                        ▼
                     Aljabr
                        │
                     Gemma
```

Notice:

**Gemma is at the end.**

Not the beginning.

---

# 2. First abstraction: `RepositoryIntel`

Keep the public interface small:

```java
public interface RepositoryIntel {

    Uni<RepositorySnapshot> snapshot();

    Uni<List<Symbol>> findSymbols(SymbolQuery query);

    Uni<List<Reference>> findReferences(ReferenceQuery query);

    Uni<ImpactReport> analyzeImpact(ImpactQuery query);

    Uni<List<CodeChunk>> search(CodeQuery query);

    Uni<DependencyGraph> dependencies(DependencyQuery query);

    Uni<TestMap> tests(TestQuery query);
}
```

The implementation can be complicated.

The consumer shouldn't care.

---

# 3. `RepositorySnapshot`

Don't make every operation rescan the repository.

```java
public record RepositorySnapshot(
    WorkspaceFingerprint workspace,
    Instant createdAt,

    FileIndex files,
    SymbolIndex symbols,
    ReferenceIndex references,
    DependencyIndex dependencies,
    TestIndex tests
) {}
```

This snapshot becomes the foundation for the entire task.

---

# 4. Workspace fingerprint

This connects directly with the mutation architecture from the previous milestone.

```java
public record WorkspaceFingerprint(
    String repositoryId,
    String revision,
    String contentHash
) {}
```

For Git repositories:

```text
repository
+
HEAD
+
working-tree changes
```

should contribute to the identity.

---

# 5. Don't hash every file every time

For a large repository that becomes expensive.

Use:

```text
.git/HEAD
git status
file metadata
size
mtime
content hash when necessary
```

Then selectively hash changed files.

---

# 6. Incremental indexing

This is essential.

Bad:

```text
every task
→ parse entire repository
```

Good:

```text
initial scan
    ↓
index

user changes 3 files
    ↓
detect changes
    ↓
reparse 3 files
    ↓
update affected symbols
    ↓
update affected references
```

That keeps Aljabr responsive on a laptop.

---

# 7. `FileIndex`

Start extremely simple:

```java
public record FileEntry(
    Path path,
    String language,
    long size,
    Instant modifiedAt,
    String contentHash
) {}
```

Then:

```java
public interface FileIndex {

    Optional<FileEntry> find(Path path);

    List<FileEntry> findByLanguage(String language);

    List<FileEntry> all();
}
```

---

# 8. `SymbolIndex`

This becomes much more valuable.

```java
public record Symbol(
    SymbolId id,

    SymbolKind kind,

    String name,

    Path file,

    SourceRange range,

    Optional<SymbolId> parent,

    Optional<String> signature
) {}
```

Symbol kinds:

```java
public enum SymbolKind {

    PACKAGE,
    MODULE,

    CLASS,
    INTERFACE,
    ENUM,
    RECORD,

    FUNCTION,
    METHOD,
    CONSTRUCTOR,

    FIELD,
    VARIABLE,

    PROPERTY
}
```

Don't overdo this initially.

---

# 9. Symbol IDs

Don't use:

```text
"AuthService"
```

because repositories can have many of them.

Use something closer to:

```text
java://com.example.auth/AuthService#validate(Token)
```

or internally:

```java
public record SymbolId(String value) {}
```

The exact encoding can evolve.

The important property:

> Symbol identity should survive line movement.

---

# 10. Why symbol identity matters

Suppose:

```text
AuthService.validate()
```

moves:

```text
line 120 → line 180
```

Line-based references break.

Symbol identity doesn't.

This improves:

```text
impact analysis
context retrieval
edit targeting
cache invalidation
memory
```

all at once.

---

# 11. `ReferenceIndex`

Represent relationships explicitly:

```java
public record Reference(
    SymbolId source,
    SymbolId target,
    ReferenceKind kind,
    SourceRange location
) {}
```

For example:

```text
LoginController.login
       │
       │ CALLS
       ▼
AuthService.authenticate
```

---

# 12. Reference kinds

Start with:

```java
public enum ReferenceKind {

    CALLS,
    REFERENCES,
    EXTENDS,
    IMPLEMENTS,
    IMPORTS,
    CREATES,
    OVERRIDES,
    READS,
    WRITES
}
```

Language adapters can add more later.

---

# 13. Call graph

Don't immediately build a sophisticated whole-program static analyzer.

Start with:

```text
CALLS
```

and provide:

```java
public interface CallGraph {

    List<SymbolId> callers(SymbolId symbol);

    List<SymbolId> callees(SymbolId symbol);
}
```

This alone is extremely useful.

---

# 14. Example

User asks:

> "What will be affected if I change `TokenValidator.validate()`?"

Deterministic engine:

```text
TokenValidator.validate()
        │
        ├── AuthService.authenticate()
        ├── RefreshService.refresh()
        └── LoginController.login()
```

Gemma doesn't need to discover this.

It receives the result.

---

# 15. Dependency graph

Separate code references from package/module dependencies.

```java
public record DependencyEdge(
    ModuleId source,
    ModuleId target,
    DependencyKind kind
) {}
```

Example:

```text
auth
 ↓
token

api
 ↓
auth
```

Useful for risk analysis.

---

# 16. Dependency kinds

```java
public enum DependencyKind {

    SOURCE,
    MODULE,
    PACKAGE,
    BUILD,
    RUNTIME,
    TEST
}
```

---

# 17. Test index

This deserves its own index.

```java
public record TestMapping(
    SymbolId test,
    Set<SymbolId> targets,
    Path file
) {}
```

The mapping can be:

```text
explicit
```

or:

```text
inferred
```

because not every framework provides perfect test metadata.

---

# 18. Test impact

Then:

```java
public interface TestIndex {

    List<SymbolId> testsFor(SymbolId symbol);

    List<SymbolId> testsFor(Set<SymbolId> symbols);
}
```

So after:

```text
AuthService.authenticate()
```

changes:

```text
affected tests:
AuthenticationTest
LoginFlowTest
TokenRefreshTest
```

Now `test.run` can run only those.

---

# 19. This is huge for local execution

Instead of:

```text
run 3,000 tests
```

Aljabr might run:

```text
run 8 relevant tests
```

That's a massive improvement on a local machine.

---

# 20. `CodeSearch`

Your search engine should not just be grep.

Use layers:

```text
exact text
   ↓
symbol
   ↓
path
   ↓
reference
   ↓
structural
   ↓
semantic
```

And stop as soon as sufficient evidence is found.

---

# 21. Search strategy

For:

> "Where is authentication handled?"

Don't immediately ask Gemma.

First:

```text
authentication
auth
authenticate
login
authorize
```

Then:

```text
symbols
references
callers
```

Then Gemma summarizes.

---

# 22. Search result normalization

Don't send 50 raw matches.

Convert them into:

```java
public record CodeSearchResult(
    List<SearchMatch> matches,
    SearchStatistics statistics
) {}
```

And rank:

```text
exact symbol
>
exact identifier
>
reference
>
same module
>
text match
```

---

# 23. `SearchMatch`

```java
public record SearchMatch(
    Path file,
    SourceRange range,
    Optional<SymbolId> symbol,
    String excerpt,
    double relevance
) {}
```

The excerpt should be small.

For example:

```text
AuthService.java:42

public AuthResult authenticate(Token token) {
```

Not 300 lines.

---

# 24. Repository intelligence should produce evidence

This is an important architectural decision.

Don't return only:

```text
List<Symbol>
```

Internally, associate facts with evidence.

```java
public record IntelEvidence(
    EvidenceId id,
    EvidenceType type,
    String description,
    EvidenceDependency dependency
) {}
```

Example:

```text
E-182:
AuthService.authenticate calls TokenValidator.validate
source:
AuthService.java:44
```

---

# 25. Evidence becomes memory

This connects to the memory architecture you were designing earlier.

```text
RepositoryIntel
      ↓
Evidence
      ↓
TaskMemory
      ↓
ContextEngine
```

Now Aljabr doesn't repeatedly rediscover facts.

---

# 26. But distinguish repository facts from model conclusions

This is very important.

```text
FACT:
AuthService.authenticate calls TokenValidator.validate.

HYPOTHESIS:
TokenValidator may be responsible for the failure.

DECISION:
Modify TokenValidator.
```

These should not be stored as equivalent memory.

---

# 27. Evidence types

```java
public enum EvidenceType {

    SOURCE_FACT,
    SYMBOL_FACT,
    REFERENCE_FACT,
    TEST_RESULT,
    BUILD_RESULT,
    GIT_FACT,

    MODEL_HYPOTHESIS,
    MODEL_DECISION
}
```

This gives your memory system epistemic structure.

---

# 28. ContextEngine can prioritize facts

For a code edit:

```text
SOURCE_FACT
TEST_RESULT
REFERENCE_FACT
```

are highly valuable.

A 20-message model conversation is much less valuable.

So context becomes:

```text
evidence-first
```

rather than:

```text
conversation-first
```

That's an excellent optimization for local models.

---

# 29. `ImpactAnalyzer`

Now we can make the previous CodeEdit pipeline much stronger.

```java
public interface ImpactAnalyzer {

    Uni<ImpactReport> analyze(
        Set<SymbolId> changedSymbols
    );
}
```

Result:

```java
public record ImpactReport(
    Set<SymbolId> affectedSymbols,
    Set<Path> affectedFiles,
    Set<SymbolId> affectedTests,
    Set<ModuleId> affectedModules,

    boolean publicApiChanged,
    boolean dependencyChanged
) {}
```

---

# 30. Impact levels

```java
public enum ImpactLevel {

    LOCAL,
    MODULE,
    CROSS_MODULE,
    PUBLIC_API,
    SYSTEM
}
```

Then:

```text
private method
→ LOCAL

service method
→ MODULE

interface
→ PUBLIC_API

shared library
→ CROSS_MODULE
```

---

# 31. Impact determines verification

For example:

```text
LOCAL
→ compile + targeted tests

MODULE
→ module tests

CROSS_MODULE
→ affected modules

PUBLIC_API
→ broader suite

SYSTEM
→ full verification
```

This makes verification deterministic.

---

# 32. Impact also determines context

This is a subtle but powerful connection.

For a local change:

```text
target method
+
class
+
tests
```

For cross-module change:

```text
target
+
callers
+
callees
+
dependency graph
+
affected tests
```

So:

```text
ImpactAnalyzer
       ↓
ContextRequirement adjustment
```

The ContextEngine can dynamically expand context **only when impact requires it**.

---

# 33. This is exactly what your `ContextController` should do

Instead of:

```text
always use 4,000 tokens
```

do:

```text
simple local edit
→ 1,500 tokens

moderate edit
→ 3,000

cross-module
→ 5,000

architecture change
→ 8,000+
```

Then the model capacity detector determines whether to:

```text
compress
or
expand
```

---

# 34. Local Gemma strategy

For your environment:

```text
M4
16 GB
Gemma 12B GGUF
```

I'd optimize for:

```text
small context
high evidence density
few model calls
deterministic retrieval
structured outputs
small patches
targeted tests
incremental indexing
```

Not:

```text
huge context
massive repository dump
long chain-of-thought
full-file generation
full-suite testing
```

---

# 35. Don't confuse "small context" with "less understanding"

The goal is:

```text
BAD

10,000 tokens
90% irrelevant
10% useful
```

versus:

```text
GOOD

2,500 tokens
80% useful
20% supporting context
```

The second is often **better** for a 12B model.

---

# 36. RepositoryIntel should therefore expose a context-oriented API

I'd add:

```java
public interface RepositoryIntel {

    Uni<ContextEvidence> explainSymbol(
        SymbolId symbol,
        ContextDepth depth
    );

    Uni<ContextEvidence> explainChangeImpact(
        Set<SymbolId> symbols,
        ImpactDepth depth
    );
}
```

Where:

```java
public enum ContextDepth {
    MINIMAL,
    STANDARD,
    DEEP
}
```

---

# 37. Example

For `TokenValidator.validate()`:

### MINIMAL

```text
signature
implementation
direct tests
```

### STANDARD

```text
above
+
callers
+
callees
+
relevant tests
```

### DEEP

```text
above
+
module dependencies
+
interfaces
+
recent changes
+
related implementations
```

This maps beautifully to your ContextPolicy.

---

# 38. Language adapter

Now we need one critical abstraction:

```java
public interface LanguageAdapter {

    String language();

    ParseResult parse(SourceFile file);

    List<Symbol> symbols(ParseResult parse);

    List<Reference> references(ParseResult parse);

    List<Diagnostic> diagnostics(ParseResult parse);
}
```

Wayang doesn't implement Java parsing.

Aljabr loads adapters.

---

# 39. Example

```text
JavaAdapter
KotlinAdapter
PythonAdapter
TypeScriptAdapter
RustAdapter
GoAdapter
```

Each provides deterministic structural information.

---

# 40. Don't build your own parser

This is a place where I'd strongly resist framework bloat.

Use existing parsers/tree-sitter/compiler APIs where appropriate.

Wayang owns:

```text
indexing
storage
caching
invalidation
querying
evidence
```

Language ecosystems own:

```text
parsing
AST
language semantics
```

---

# 41. Index persistence

Don't keep everything only in memory.

Use a local repository cache:

```text
.aljabr/
    index/
    cache/
    evidence/
    tasks/
```

For example:

```text
.aljabr/
    repository.db
    symbols.db
    references.db
    tests.db
    metadata.json
```

The exact storage technology can come later.

---

# 42. Important: don't create five databases

Start with one embedded store.

Conceptually:

```text
.aljabr/index.db
```

Tables:

```text
files
symbols
references
dependencies
tests
evidence
```

This is much easier to maintain.

---

# 43. Cache invalidation

When:

```text
AuthService.java
```

changes:

```text
invalidate:
AuthService symbols
AuthService references
affected call graph edges
affected test mappings
related evidence
```

But don't invalidate:

```text
README
unrelated module
unrelated symbols
```

---

# 44. Git integration

Git provides valuable deterministic evidence.

Track:

```text
HEAD
working tree
changed files
recent commits
blame
diff
```

But don't dump Git history into context.

Expose targeted queries:

```java
git.recentChanges(file)

git.lastModified(symbol)

git.diff(base, current)
```

---

# 45. Recent-change evidence is particularly useful for debugging

If a test suddenly fails:

```text
failure
+
changed symbols
+
recent diff
```

is usually much more useful than:

```text
entire repository
```

This can dramatically improve local-model debugging.

---

# 46. The debugging context becomes

```text
FAILURE

NullPointerException

TARGET

AuthService.authenticate()

RECENT CHANGE

TokenValidator.java
changed 12 minutes ago

DEPENDENCY

AuthService → TokenValidator

TEST

LoginIntegrationTest
```

Gemma 12B has a very manageable reasoning task.

---

# 47. RepositoryIntel should also expose "why"

Eventually:

```java
Uni<ExplanationGraph> explain(
    CodeEntity entity
);
```

For example:

```text
AuthService.authenticate
        │
        ├── calls TokenValidator.validate
        │
        ├── called by LoginController.login
        │
        └── tested by LoginIntegrationTest
```

This graph can be rendered into a compact context packet.

---

# 48. Don't send graph JSON directly to Gemma

Instead serialize it into a compact form:

```text
SYMBOL:
AuthService.authenticate(Token)

CALLS:
- TokenValidator.validate(Token)

CALLED BY:
- LoginController.login(Request)

TESTED BY:
- LoginIntegrationTest.authenticateValidToken
- LoginIntegrationTest.rejectExpiredToken
```

Much more token-efficient.

---

# 49. RepositoryIntel + ContextEngine

The final relationship should be:

```text
                Repository
                    │
                    ▼
             RepositoryIntel
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
       Symbols   Impact     Tests
          │         │         │
          └─────────┼─────────┘
                    ▼
              ContextEngine
                    │
              ContextPolicy
                    │
              ContextPacket
                    │
                    ▼
                  Gemma
```

This is the architecture I would optimize around.

---

# 50. The really important separation

You now have three different kinds of intelligence:

### 1. Deterministic intelligence

```text
RepositoryIntel
```

Answers:

> "What exists?"

> "What references what?"

> "What tests are affected?"

---

### 2. Policy intelligence

```text
Wayang runtime
```

Answers:

> "What are we allowed to do?"

> "How much context should we use?"

> "Should this change be applied?"

---

### 3. Probabilistic intelligence

```text
Gemma / cloud model
```

Answers:

> "What does this probably mean?"

> "What change should we make?"

> "What hypothesis explains this failure?"

This separation is **very important for Aljabr**.

---

# 51. The resulting Aljabr architecture

We're getting toward:

```text
┌────────────────────────────────────────────┐
│                   Aljabr                   │
│                                            │
│  TaskPlanner ──────── SkillSelector       │
│       │                    │               │
│       ▼                    ▼               │
│  ContextEngine ──────── SkillExecutor     │
│       │                    │               │
│       │              ┌─────┴─────┐         │
│       │              │           │         │
│       ▼              ▼           ▼         │
│ RepositoryIntel   Tools      Reasoning     │
│       │                          │         │
│       └──────────┬───────────────┘         │
│                  ▼                         │
│             Evidence/Memory               │
│                                            │
└────────────────────────────────────────────┘
                     │
                     ▼
              Wayang Runtime
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
     Policy       Capability    Execution
```

That's becoming a very coherent system.

---

# 52. What I would implement next, concretely

Don't implement the entire RepositoryIntel yet.

Build this vertical slice:

```text
RepositoryScanner
        ↓
FileIndex
        ↓
LanguageAdapter
        ↓
SymbolIndex
        ↓
ReferenceIndex
        ↓
TestIndex
        ↓
RepositoryIntel
```

Then make these work:

```text
find symbol
find callers
find callees
find tests
search code
```

That's enough to power the next generation of `CodeEditSkill`.

---

## Next milestone after that

Once RepositoryIntel exists, the next major piece should be **`TaskPlanner + StateMachine`**.

That will connect everything we've built:

```text
User Request
     ↓
Task Understanding
     ↓
Plan
     ↓
Skill
     ↓
Evidence
     ↓
State Update
     ↓
Next Skill
     ↓
Verification
     ↓
Done
```

And importantly, the planner should **not be a giant autonomous ReAct loop**.

For Aljabr/local Gemma, I'd make it a **bounded state machine with model-assisted decisions**, where Wayang owns the control flow and Gemma only handles the ambiguous decisions.


Absolutely. **Next milestone: `TaskPlanner + ExecutionStateMachine`.**

This is where Aljabr stops being "an LLM with coding tools" and becomes an actual **coding agent runtime**.

The key design decision I'd make for your local-first architecture is:

> **The model proposes the next decision; Wayang owns the state machine.**

Do **not** let Gemma generate an unrestricted agent loop like:

```text
think → tool → think → tool → ...
```

Instead:

```text
Task
 ↓
StateMachine
 ↓
Decision
 ↓
Skill
 ↓
Evidence
 ↓
StateMachine
 ↓
Decision
 ↓
...
```

That gives you predictability, low token usage, resumability, and much better recovery when local inference is interrupted.

---

# 1. The core architecture

```text
                         User Request
                              │
                              ▼
                       TaskNormalizer
                              │
                              ▼
                        TaskPlanner
                              │
                              ▼
                    ┌──────────────────┐
                    │ ExecutionState   │
                    │    Machine       │
                    └────────┬─────────┘
                             │
                   ┌─────────┼─────────┐
                   ▼         ▼         ▼
                Inspect     Search     Edit
                   │         │         │
                   └─────────┼─────────┘
                             ▼
                          Evidence
                             │
                             ▼
                         Evaluator
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
                  Done              Retry
                                      │
                                      ▼
                                  Re-plan
```

The important thing is that **state is external to Gemma**.

---

# 2. `Task`

Start with a persistent task object.

```java
public record Task(
    TaskId id,
    String userRequest,
    TaskType type,
    TaskStatus status,
    TaskConstraints constraints,
    Instant createdAt
) {}
```

For example:

```text
Task
  id: task-123
  request: "Fix expired token validation"
  type: BUG_FIX
  status: RUNNING
```

---

# 3. Don't make the entire task one giant prompt

This is one of the biggest mistakes I'd avoid.

Bad:

```text
Here is the user request.
Here is the repository.
Here are all files.
Here is our conversation.
Figure out what to do.
```

Instead:

```text
Task
 ↓
small decision
 ↓
retrieve evidence
 ↓
small decision
 ↓
edit
 ↓
verify
```

Every model call has a narrow purpose.

---

# 4. `TaskType`

```java
public enum TaskType {

    QUESTION,
    EXPLAIN,
    SEARCH,
    BUG_FIX,
    FEATURE,
    REFACTOR,
    TEST,
    REVIEW,
    DEBUG,
    DOCUMENTATION
}
```

You don't need perfect classification.

If uncertain:

```text
UNKNOWN
```

and let the planner resolve it.

---

# 5. `TaskState`

I'd keep states explicit:

```java
public enum TaskState {

    CREATED,

    UNDERSTANDING,
    PLANNING,

    INSPECTING,
    SEARCHING,

    DESIGNING,
    EDITING,

    VERIFYING,

    REVIEWING,

    COMPLETED,

    BLOCKED,
    FAILED,
    CANCELLED
}
```

But don't create 50 states.

The state machine should remain understandable.

---

# 6. State transitions are deterministic

For example:

```text
CREATED
  ↓
UNDERSTANDING
  ↓
PLANNING
  ↓
INSPECTING
  ↓
DESIGNING
  ↓
EDITING
  ↓
VERIFYING
  ↓
REVIEWING
  ↓
COMPLETED
```

If verification fails:

```text
VERIFYING
   ↓
FAILURE
   ↓
DIAGNOSING
   ↓
PLANNING
```

You may add `DIAGNOSING` as an explicit state; I would.

---

# 7. `TaskStateMachine`

```java
public interface TaskStateMachine {

    TaskState current(TaskId taskId);

    TransitionResult transition(
        TaskId taskId,
        Transition transition
    );
}
```

But transitions must be validated.

---

# 8. Transition rules

For example:

```java
public interface TransitionPolicy {

    boolean allowed(
        TaskState from,
        TaskState to,
        TaskContext context
    );
}
```

So Gemma cannot accidentally decide:

```text
COMPLETED → EDITING
```

unless Wayang explicitly permits reopening.

---

# 9. Model output should be a decision

Instead of letting Gemma emit:

```text
I'll inspect AuthService, then run grep...
```

make it return structured intent:

```json
{
  "decision": "INSPECT",
  "targets": [
    "AuthService.authenticate"
  ],
  "reason": "Need to determine how expiration is validated"
}
```

Wayang then decides what tool actually executes.

---

# 10. `AgentDecision`

```java
public record AgentDecision(
    DecisionType type,
    List<TargetRef> targets,
    Optional<SkillId> skill,
    String rationale,
    List<String> assumptions
) {}
```

Possible decisions:

```java
public enum DecisionType {

    INSPECT,
    SEARCH,
    PLAN,
    EDIT,
    VERIFY,
    REVIEW,
    ASK_USER,
    COMPLETE,
    ABORT
}
```

---

# 11. Gemma does NOT select arbitrary tools

This is important.

Don't expose:

```text
execute_shell(command)
```

and ask Gemma to decide everything.

Instead:

```text
Gemma:
    decision = SEARCH
    query = "TokenValidator expiration"

Wayang:
    SearchSkill.execute(...)
```

The runtime retains authority.

---

# 12. `Planner`

```java
public interface TaskPlanner {

    Uni<Plan> createPlan(TaskContext context);

    Uni<AgentDecision> nextDecision(TaskContext context);
}
```

Notice that you can support two modes:

### Explicit planning

```text
createPlan()
```

### Incremental planning

```text
nextDecision()
```

For local Gemma, I would favor **incremental planning** for most tasks.

---

# 13. Why incremental planning is better locally

Suppose the task is:

> "Fix this bug."

You don't know the repository yet.

Generating a 15-step plan immediately is often nonsense.

Instead:

```text
inspect
 ↓
learn
 ↓
plan
 ↓
edit
 ↓
verify
```

The plan becomes progressively better.

---

# 14. `Plan`

For larger tasks:

```java
public record Plan(
    PlanId id,
    List<PlanStep> steps,
    PlanStatus status
) {}
```

Example:

```text
1. Inspect failing test
2. Inspect TokenValidator
3. Trace callers
4. Determine boundary semantics
5. Modify implementation
6. Add regression test
7. Run affected tests
8. Review diff
```

---

# 15. But don't execute all eight blindly

Each step should be conditional.

```java
public record PlanStep(
    StepId id,
    String objective,
    StepType type,
    StepCondition condition,
    StepStatus status
) {}
```

Example:

```text
Step 4:
Determine boundary semantics

condition:
only if implementation ambiguity remains
```

---

# 16. This saves tokens

Suppose inspecting the code immediately reveals:

```text
expiration uses strict comparison
```

Then you don't need:

```text
investigate configuration
investigate database
investigate token format
```

The runtime prunes unnecessary work.

---

# 17. `TaskContext`

This is where all accumulated knowledge comes together.

```java
public record TaskContext(
    Task task,

    TaskState state,

    Plan plan,

    WorkspaceSnapshot workspace,

    Set<Evidence> evidence,

    Set<Decision> decisions,

    Set<EditAttempt> editAttempts,

    Set<VerificationResult> verifications
) {}
```

But don't serialize the whole thing into Gemma.

That's a runtime object.

---

# 18. ContextEngine creates the model view

```text
TaskContext
     │
     ▼
ContextController
     │
     ▼
ContextPolicy
     │
     ▼
ContextEngine
     │
     ▼
ContextPacket
```

This is exactly where your earlier architecture pays off.

---

# 19. `ContextPacket`

For example:

```java
public record ContextPacket(
    String systemContext,
    String taskContext,
    List<ContextItem> evidence,
    TokenBudget budget
) {}
```

The model sees only the relevant subset.

---

# 20. Example decision prompt

Gemma receives something like:

```text
TASK
Fix expired token validation.

CURRENT STATE
INSPECTING

TARGET
TokenValidator.validateExpiration()

FACTS
- expiration timestamp comes from Token
- method is called by AuthService.authenticate()
- failing test: rejectExpiredToken()

QUESTION
What should the next action be?

Allowed actions:
INSPECT
SEARCH
PLAN
EDIT
VERIFY
ASK_USER
COMPLETE
```

That's an extremely manageable prompt for Gemma 12B.

---

# 21. Output

```json
{
  "decision": "INSPECT",
  "targets": [
    "TokenValidator.validateExpiration"
  ]
}
```

No giant reasoning transcript.

No unnecessary output.

---

# 22. Internal reasoning doesn't need to be persisted as text

This connects directly to your earlier memory discussion.

Don't store:

```text
"Gemma thought for 800 tokens that..."
```

Store the useful result:

```text
FACT
expiration is checked using Instant comparison

HYPOTHESIS
boundary comparison may be reversed

NEXT_ACTION
inspect regression test
```

This is dramatically more useful.

---

# 23. `EvidenceStore`

```java
public interface EvidenceStore {

    Uni<Evidence> put(Evidence evidence);

    Uni<List<Evidence>> query(EvidenceQuery query);

    Uni<Void> invalidate(EvidenceId id);
}
```

Every important action can produce evidence.

---

# 24. Example evidence

```java
Evidence(
    type = TEST_RESULT,
    source = "LoginTest.rejectExpiredToken",
    content = "FAILED",
    confidence = 1.0
)
```

Or:

```java
Evidence(
    type = SOURCE_FACT,
    source = "TokenValidator.java:78",
    content = "expiration compared with current time",
    confidence = 1.0
)
```

---

# 25. Confidence should be attached to model conclusions

For example:

```java
public record Hypothesis(
    String statement,
    double confidence,
    List<EvidenceId> supportingEvidence
) {}
```

Gemma:

```text
"Likely off-by-one expiration boundary"
confidence: 0.72
```

Wayang should know that this is not a fact.

---

# 26. This enables evidence-based replanning

Suppose:

```text
H1:
expiration boundary bug
confidence 0.72
```

Then testing disproves it.

The runtime marks:

```text
H1 → REFUTED
```

and asks for a new hypothesis.

This is much better than the model endlessly repeating the same idea.

---

# 27. `HypothesisStore`

```java
public interface HypothesisStore {

    Uni<Void> add(Hypothesis hypothesis);

    Uni<Void> update(HypothesisId id, HypothesisStatus status);

    Uni<List<Hypothesis>> active(TaskId task);
}
```

Statuses:

```text
ACTIVE
SUPPORTED
REFUTED
ABANDONED
```

---

# 28. Bounded agent loop

Now we can define the actual Aljabr execution loop:

```text
while task != terminal:

    context = buildContext()

    decision = planner.nextDecision(context)

    validate(decision)

    result = execute(decision)

    evidence = extractEvidence(result)

    updateState(evidence)

    if budgetExceeded:
        BLOCK

    if stagnating:
        REPLAN

    checkpoint()
```

The **runtime owns this loop**.

Gemma does not.

---

# 29. `AgentBudget`

This is particularly important for local inference.

```java
public record AgentBudget(
    int maxSteps,
    int maxModelCalls,
    int maxToolCalls,
    int maxEditAttempts,
    long maxExecutionMillis,
    int maxContextTokens,
    int maxOutputTokens
) {}
```

Example local profile:

```text
maxModelCalls = 20
maxEditAttempts = 3
maxContextTokens = 5000
maxOutputTokens = 1000
```

These should be configurable.

---

# 30. Budget is not only resource protection

It also improves reasoning.

Without a budget:

```text
search
search
search
search
think
search
edit
edit
edit
```

With budget:

```text
inspect
search
decide
edit
verify
```

The agent is forced to converge.

---

# 31. `StagnationDetector`

This should be a first-class component.

```java
public interface StagnationDetector {

    StagnationResult inspect(
        TaskContext context
    );
}
```

Detect things like:

```text
same search repeated
same hypothesis repeated
same patch repeated
same test failure repeated
no new evidence
```

---

# 32. Example

```text
Attempt 1:
Search TokenValidator

Attempt 2:
Search TokenValidator

Attempt 3:
Search TokenValidator
```

No new evidence.

Result:

```text
STAGNATING
```

Then:

```text
change strategy
```

Maybe inspect callers instead.

---

# 33. Strategy switching

Define:

```java
public enum InvestigationStrategy {

    DIRECT,
    SYMBOL_TRACE,
    TEST_FIRST,
    FAILURE_FIRST,
    CHANGE_HISTORY,
    DEPENDENCY_TRACE,
    BROAD_SEARCH
}
```

Gemma doesn't have to invent strategies.

Wayang can provide them.

---

# 34. Example debugging progression

```text
DIRECT
 ↓
not enough evidence
 ↓
TEST_FIRST
 ↓
still ambiguous
 ↓
SYMBOL_TRACE
 ↓
caller identified
 ↓
CHANGE_HISTORY
 ↓
recent regression found
```

That's much more reliable than a free-form loop.

---

# 35. `TaskCheckpoint`

This is essential for your earlier concern about connection loss.

After every meaningful state transition:

```java
public record TaskCheckpoint(
    TaskId task,
    TaskState state,
    WorkspaceFingerprint workspace,
    Plan plan,
    EvidenceCursor evidenceCursor,
    Instant timestamp
) {}
```

Persist it.

---

# 36. Then if inference crashes

You don't restart:

```text
from zero
```

You resume:

```text
Task
 ↓
last checkpoint
 ↓
verify workspace unchanged
 ↓
continue
```

This is one of the biggest advantages of your runtime architecture.

---

# 37. If the model process crashes

Same thing.

```text
Gemma
  ↓
crash
  ↓
Wayang survives
  ↓
checkpoint survives
  ↓
new inference session
  ↓
rebuild ContextPacket
  ↓
continue
```

The model is disposable.

The task state is not.

---

# 38. If the machine loses connection to cloud

For local-first Aljabr, this is especially nice.

If using cloud fallback:

```text
local model
 ↓
unavailable
 ↓
cloud capability
 ↓
continue
```

or:

```text
cloud unavailable
 ↓
local model
 ↓
continue
```

provided the task policy permits it.

---

# 39. `CapabilityRouter`

This ties back to your `CapabilityDetector`.

```java
public interface ReasoningRouter {

    ReasoningProvider select(
        ReasoningRequirement requirement,
        CapabilitySnapshot capabilities
    );
}
```

Example:

```text
simple edit
→ Gemma local

complex architecture
→ larger cloud model

privacy-sensitive
→ local only

offline
→ local only
```

---

# 40. Important: cloud should not change the state machine

This is another strong architectural boundary.

Whether reasoning comes from:

```text
Gemma 12B
```

or:

```text
large cloud model
```

the runtime still executes:

```text
Task
→ Decision
→ Skill
→ Evidence
→ State
```

Only:

```text
ReasoningProvider
```

changes.

This is exactly the abstraction you want.

---

# 41. Local model can therefore benefit from "agent scaffolding"

This is the bigger Aljabr idea.

You're not trying to make:

```text
Gemma 12B
```

behave like a huge model.

You're giving it:

```text
better retrieval
better structure
better tools
better state
better evidence
better verification
better constraints
```

So the effective system becomes much stronger than the raw model.

---

# 42. But avoid scaffolding explosion

There's a danger here.

You don't want:

```text
Planner
MetaPlanner
PlannerCoordinator
DecisionPlanner
StrategyPlanner
TaskPlannerManager
ReasoningController
ReasoningOrchestrator
```

😂

Keep the core small.

I'd target something like:

```text
TaskEngine
TaskPlanner
ContextEngine
RepositoryIntel
SkillRegistry
EvidenceStore
MutationEngine
VerificationEngine
ReasoningRouter
```

Everything else should be a strategy/plugin unless complexity truly demands it.

---

# 43. `TaskEngine`

The central coordinator:

```java
public interface TaskEngine {

    Uni<TaskResult> execute(TaskId task);

    Uni<TaskStatus> status(TaskId task);

    Uni<Void> cancel(TaskId task);

    Uni<Void> resume(TaskId task);
}
```

Implementation:

```java
final class DefaultTaskEngine implements TaskEngine {
    ...
}
```

But unlike a giant `DefaultWayangAgent`, this is **an execution engine**, not a generic personality.

That's an important distinction.

---

# 44. This also answers the old `DefaultWayangAgent` question

I would **not** make a giant:

```text
DefaultWayangAgent
```

responsible for:

```text
memory
planning
context
tools
coding
research
editing
verification
```

Instead:

```text
Wayang Runtime
       │
       ▼
TaskEngine
       │
       ├── Planner
       ├── Context
       ├── Skills
       ├── Evidence
       ├── Memory
       └── Verification
```

Then:

```text
Aljabr
```

is a domain configuration/skill set over the runtime.

Much cleaner.

---

# 45. Aljabr becomes a profile

Conceptually:

```java
AgentProfile aljabr = AgentProfile.builder()
    .name("aljabr")
    .skills(codingSkills)
    .policies(codingPolicies)
    .tools(codingTools)
    .memoryPolicy(codingMemory)
    .build();
```

Wayang remains generic.

---

# 46. The complete local Aljabr flow

Let's put everything together.

User:

> "Fix the failing authentication test."

```text
User
 │
 ▼
TaskEngine
 │
 ▼
TaskNormalizer
 │
 ▼
TaskPlanner
 │
 ▼
RepositoryIntel
 │
 ├── failing test
 ├── target symbols
 ├── callers
 └── recent changes
 │
 ▼
ContextEngine
 │
 ▼
Gemma 12B
 │
 ▼
Decision:
INSPECT
 │
 ▼
RepositoryIntel
 │
 ▼
Evidence
 │
 ▼
Gemma
 │
 ▼
Decision:
EDIT
 │
 ▼
CodeEditSkill
 │
 ▼
EditProposal
 │
 ▼
Validator
 │
 ▼
Mutation
 │
 ▼
Verification
 │
 ▼
Evidence
 │
 ▼
Review
 │
 ▼
TaskEngine
 │
 ▼
COMPLETED
```

That's a **real agent runtime**.

---

# 47. And the token flow is excellent

Instead of:

```text
huge repository
→ huge prompt
→ huge response
```

you get:

```text
request
→ ~small decision context

evidence
→ ~small decision context

edit
→ ~small patch context

failure
→ ~small diagnostic context
```

So your local Gemma spends its limited context on **high-value reasoning**.

---

# 48. Next milestone

After `TaskPlanner + StateMachine`, I would move to something very important:

## **`VerificationEngine + DebugEngine`**

Because coding agents should not stop at:

> "I generated a patch."

They should close the loop:

```text
EDIT
 ↓
BUILD
 ↓
TEST
 ↓
FAILURE CLASSIFICATION
 ↓
DIAGNOSIS
 ↓
REPAIR
 ↓
RETEST
 ↓
REGRESSION CHECK
 ↓
REVIEW
 ↓
DONE
```

And this is where Aljabr can become particularly powerful locally, because **compilers, tests, linters, and static analysis are deterministic intelligence that effectively augment Gemma's reasoning**.

The next milestone should therefore build that closed-loop **`Verify → Diagnose → Repair → Verify`** system.

Absolutely. **Next milestone: `VerificationEngine + DebugEngine`**.

This is where Aljabr becomes a **closed-loop coding agent** instead of a patch generator.

The central loop should be:

```text
             ┌──────────────────────┐
             │      EditProposal    │
             └──────────┬───────────┘
                        ▼
                 MutationEngine
                        │
                        ▼
                 VerificationEngine
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
            PASS                FAIL
              │                   │
              ▼                   ▼
           Review            FailureClassifier
                                  │
                                  ▼
                            DebugEngine
                                  │
                                  ▼
                            Diagnosis
                                  │
                                  ▼
                             RepairPlan
                                  │
                                  ▼
                            CodeEditSkill
                                  │
                                  └──────────► Verify
```

The important design rule:

> **Tests/build tools produce facts. Gemma interprets those facts.**

Don't make Gemma infer whether compilation succeeded.

---

# 1. `VerificationEngine`

Start with a simple contract:

```java
public interface VerificationEngine {

    Uni<VerificationResult> verify(
        VerificationRequest request
    );
}
```

Request:

```java
public record VerificationRequest(
    TaskId taskId,
    WorkspaceSnapshot baseline,
    WorkspaceSnapshot candidate,
    VerificationPolicy policy
) {}
```

Result:

```java
public record VerificationResult(
    VerificationStatus status,
    List<VerificationEvidence> evidence,
    TestDelta testDelta,
    List<Diagnostic> diagnostics,
    Duration duration
) {}
```

---

# 2. Verification should be layered

Don't always run:

```text
./gradlew test
```

or:

```text
npm test
```

immediately.

Use escalation:

```text
L0  Patch validation
 ↓
L1  Syntax / formatting
 ↓
L2  Compile
 ↓
L3  Targeted tests
 ↓
L4  Affected test suite
 ↓
L5  Full suite
```

This is especially important on your M4.

---

# 3. `VerificationPolicy`

```java
public record VerificationPolicy(
    VerificationLevel minimum,
    VerificationLevel maximum,
    boolean escalateOnFailure,
    boolean runRegressionChecks
) {}
```

For example:

```text
LOCAL_EDIT

minimum:
compile

maximum:
affected tests
```

But:

```text
PUBLIC_API_CHANGE

minimum:
compile

maximum:
full suite
```

---

# 4. Let RepositoryIntel decide affected tests

This connects directly to the previous milestone.

```text
Changed symbols
      ↓
RepositoryIntel
      ↓
affected symbols
      ↓
TestIndex
      ↓
8 relevant tests
```

Then:

```text
VerificationEngine
      ↓
run 8 tests
```

rather than:

```text
run 3,000 tests
```

---

# 5. `Verifier`

Make verification providers pluggable:

```java
public interface Verifier {

    VerificationKind kind();

    Uni<VerifierResult> run(
        VerificationRequest request
    );
}
```

Implementations:

```text
CompileVerifier
TestVerifier
LintVerifier
FormatVerifier
StaticAnalysisVerifier
```

Later:

```text
TypeCheckVerifier
SecurityVerifier
MigrationVerifier
```

---

# 6. Don't let the model construct shell commands

Instead:

```java
public interface BuildToolAdapter {

    Uni<BuildResult> compile(BuildRequest request);

    Uni<TestResult> test(TestRequest request);
}
```

For Java:

```text
GradleAdapter
MavenAdapter
```

For JS:

```text
NpmAdapter
PnpmAdapter
BunAdapter
```

etc.

Gemma says:

```text
VERIFY affected tests
```

Wayang determines how.

---

# 7. Command execution needs a boundary

I'd introduce:

```java
public interface ExecutionSandbox {

    Uni<ProcessResult> execute(
        ExecutionRequest request
    );
}
```

With:

```java
public record ExecutionRequest(
    List<String> command,
    Path workingDirectory,
    Duration timeout,
    ExecutionLimits limits
) {}
```

This is where you enforce:

```text
timeout
memory
CPU
network policy
working directory
environment
output limit
```

---

# 8. Never let test output grow without bounds

A failing test can print enormous logs.

Bad:

```text
50 MB output
→ context
→ Gemma
```

Instead:

```text
raw output
   ↓
OutputNormalizer
   ↓
DiagnosticExtractor
   ↓
compact failure
```

---

# 9. `DiagnosticExtractor`

```java
public interface DiagnosticExtractor {

    List<Diagnostic> extract(
        ProcessResult result
    );
}
```

Result:

```java
public record Diagnostic(
    DiagnosticKind kind,
    Optional<Path> file,
    Optional<SourceRange> range,
    String message,
    DiagnosticSeverity severity
) {}
```

---

# 10. Example

Raw:

```text
> Task :test FAILED

AuthenticationTest > expiredToken() FAILED

java.lang.AssertionError:
Expected: false
Actual: true

at AuthenticationTest.java:143
...
```

Don't send all of it to Gemma.

Normalize:

```text
TEST FAILURE

test:
AuthenticationTest.expiredToken()

file:
AuthenticationTest.java:143

expected:
false

actual:
true

exception:
AssertionError
```

That's much better.

---

# 11. Failure classification

This is one of the most important components.

```java
public interface FailureClassifier {

    FailureClassification classify(
        VerificationResult result,
        TaskContext context
    );
}
```

Categories:

```java
public enum FailureKind {

    COMPILE_ERROR,
    TYPE_ERROR,
    SYNTAX_ERROR,

    TEST_FAILURE,
    ASSERTION_FAILURE,

    RUNTIME_EXCEPTION,
    TIMEOUT,

    LINT_FAILURE,
    FORMAT_FAILURE,

    ENVIRONMENT_FAILURE,
    DEPENDENCY_FAILURE,

    REGRESSION,
    UNKNOWN
}
```

---

# 12. Why classification should be deterministic first

Suppose:

```text
exit code = 1
```

That doesn't tell you much.

But the build adapter knows:

```text
Java compilation failed
```

and the diagnostic parser knows:

```text
cannot find symbol
```

Therefore:

```text
FailureKind = COMPILE_ERROR
```

No LLM needed.

---

# 13. Then use Gemma for diagnosis

Now the model receives:

```text
TASK
Fix expired token validation.

CHANGE
TokenValidator.java:78

VERIFICATION
AuthenticationTest.expiredToken() failed.

FAILURE
Expected false
Actual true

RECENT CHANGE
expiration comparison modified.

TARGET SYMBOL
TokenValidator.validateExpiration()

CALLERS
AuthService.authenticate()

TEST
AuthenticationTest.expiredToken()
```

Then ask:

```text
Determine the most likely cause and next investigation step.
```

This is a **small, high-quality context packet**.

---

# 14. `Diagnosis`

```java
public record Diagnosis(
    DiagnosisId id,

    FailureKind failure,

    String cause,

    double confidence,

    List<EvidenceId> supportingEvidence,

    List<EvidenceId> contradictoryEvidence,

    NextInvestigation next
) {}
```

Notice:

```text
supportingEvidence
contradictoryEvidence
```

This is very valuable.

---

# 15. Don't let Gemma state guesses as facts

Gemma might say:

> "The comparison is probably reversed."

Store:

```text
HYPOTHESIS
comparison may be reversed
confidence: 0.71
```

Not:

```text
FACT
comparison is reversed
```

---

# 16. `DebugEngine`

```java
public interface DebugEngine {

    Uni<Diagnosis> diagnose(
        DebugContext context
    );

    Uni<RepairPlan> proposeRepair(
        Diagnosis diagnosis,
        DebugContext context
    );
}
```

The engine owns the debugging process.

---

# 17. Debugging should have phases

```text
FAILURE
   ↓
CLASSIFY
   ↓
LOCALIZE
   ↓
HYPOTHESIZE
   ↓
INVESTIGATE
   ↓
DIAGNOSE
   ↓
REPAIR
   ↓
VERIFY
```

Don't collapse everything into one Gemma call.

---

# 18. `LOCALIZE`

First determine:

```text
Which symbol is actually failing?
```

Use deterministic information:

```text
stack trace
source location
test location
compiler diagnostic
changed files
```

Then RepositoryIntel:

```text
source location
→ enclosing symbol
→ callers
→ callees
```

---

# 19. `HYPOTHESIZE`

Only now ask Gemma.

Input:

```text
failure
+
localized symbol
+
relevant code
+
recent changes
```

Output:

```text
H1:
expiration comparison is incorrect

H2:
test expectation conflicts with intended semantics
```

Potentially:

```json
{
  "hypotheses": [
    {
      "statement": "Expiration boundary comparison is incorrect",
      "confidence": 0.74
    },
    {
      "statement": "Test expectation may be outdated",
      "confidence": 0.31
    }
  ]
}
```

---

# 20. Then investigate the cheapest hypothesis first

This is important for local efficiency.

Suppose:

```text
H1 = 0.74
H2 = 0.31
```

Investigate H1 first.

But don't just trust probability.

Use:

```text
expected information gain
+
cost
```

---

# 21. `InvestigationAction`

```java
public record InvestigationAction(
    InvestigationType type,
    TargetRef target,
    int estimatedCost,
    Set<HypothesisId> hypotheses
) {}
```

Examples:

```text
inspect source
run test
inspect caller
inspect recent diff
search references
run debugger
```

---

# 22. Cost-aware debugging

For local Aljabr:

```text
cheap:
source inspection

cheap:
symbol search

cheap:
git diff

moderate:
compile

moderate:
targeted test

expensive:
full test suite

very expensive:
large integration environment
```

The engine should prefer cheap evidence first.

---

# 23. Example

Hypothesis:

```text
Token expiration boundary is reversed.
```

Possible actions:

```text
A:
inspect comparison
cost = 1

B:
run full authentication suite
cost = 10

C:
inspect production configuration
cost = 8
```

Choose:

```text
A
```

first.

That's deterministic optimization around the model.

---

# 24. `RepairPlan`

After diagnosis:

```java
public record RepairPlan(
    String objective,
    List<RepairStep> steps,
    List<String> invariants,
    List<EvidenceId> supportingEvidence
) {}
```

Example:

```text
OBJECTIVE
Reject tokens whose expiration is at or before current time.

CHANGE
Use strict future-time comparison.

INVARIANTS
- token parsing unchanged
- public API unchanged

REGRESSION TEST
Add boundary expiration case.
```

Then send this to `CodeEditSkill`.

---

# 25. Don't let DebugEngine modify files

Important boundary:

```text
DebugEngine
    ↓
RepairPlan
    ↓
CodeEditSkill
    ↓
EditProposal
    ↓
MutationEngine
```

This avoids two different components having mutation authority.

---

# 26. The repair loop

Now the complete debugging cycle:

```text
                 Test Failure
                      │
                      ▼
               FailureClassifier
                      │
                      ▼
                  DebugEngine
                      │
              ┌───────┴────────┐
              ▼                ▼
         Evidence          Gemma
              │                │
              └───────┬────────┘
                      ▼
                  Diagnosis
                      │
                      ▼
                 RepairPlan
                      │
                      ▼
                 CodeEditSkill
                      │
                      ▼
                  Verification
                      │
              ┌───────┴────────┐
              ▼                ▼
            PASS              FAIL
              │                │
              ▼                ▼
          Continue        Re-classify
```

---

# 27. Detect "same failure"

This is critical.

Baseline:

```text
AuthenticationTest.expiredToken
Expected false
Actual true
```

After patch:

```text
AuthenticationTest.expiredToken
Expected false
Actual true
```

Then:

```text
failure fingerprint = same
```

Don't count that as meaningful progress.

---

# 28. `FailureFingerprint`

```java
public record FailureFingerprint(
    FailureKind kind,
    Optional<String> test,
    Optional<String> errorCode,
    Optional<Path> sourceFile,
    String normalizedMessage
) {}
```

Normalize things like:

```text
timestamps
memory addresses
temporary paths
random IDs
```

so equivalent failures compare equal.

---

# 29. Failure progression

You can track:

```text
attempt 1
same failure

attempt 2
same failure

attempt 3
new failure

attempt 4
original fixed
new regression
```

This becomes useful evidence for the planner.

---

# 30. `RepairProgress`

```java
public record RepairProgress(
    int attempt,
    FailureFingerprint before,
    FailureFingerprint after,
    boolean improved,
    boolean regressed,
    Set<EvidenceId> newEvidence
) {}
```

Then:

```text
improved = false
```

means the agent should reconsider its strategy.

---

# 31. Don't retry blindly

A bad agent does:

```text
failure
→ modify code
→ failure
→ modify code
→ failure
→ modify code
```

Aljabr should instead:

```text
failure
→ diagnosis
→ repair
→ verify

if no improvement:
    invalidate hypothesis
    change strategy
```

---

# 32. Strategy change

For example:

```text
attempt 1:
implementation inspection

fails

attempt 2:
test-first investigation

fails

attempt 3:
git-history investigation
```

This is far more intelligent than generating increasingly random patches.

---

# 33. Baseline verification

This is another subtle but important feature.

Before editing:

```text
run relevant tests
```

If the test already fails:

```text
BASELINE_FAILURE
```

If it passes before editing but fails after:

```text
REGRESSION
```

That distinction is essential.

---

# 34. `VerificationBaseline`

```java
public record VerificationBaseline(
    WorkspaceFingerprint workspace,
    List<TestResult> tests,
    List<Diagnostic> diagnostics
) {}
```

Then candidate result:

```text
baseline
vs
candidate
```

---

# 35. Test delta

```java
public record TestDelta(
    Set<String> newlyPassing,
    Set<String> newlyFailing,
    Set<String> unchangedFailing,
    Set<String> unchangedPassing
) {}
```

Now the agent knows exactly what happened.

---

# 36. Example

```text
BASELINE

expiredToken       FAIL
refreshToken       PASS
loginValidToken    PASS

AFTER PATCH

expiredToken       PASS
refreshToken       FAIL
loginValidToken    PASS
```

The correct interpretation is:

```text
requested failure fixed
+
new regression introduced
```

Therefore:

```text
REJECT PATCH
```

Not:

```text
DONE
```

---

# 37. Regression policy

```java
public record RegressionPolicy(
    boolean rejectNewFailures,
    boolean allowPreexistingFailures,
    boolean requireTargetedPass
) {}
```

Typical default:

```text
new failures = reject
preexisting failures = allowed
target test must pass
```

---

# 38. This makes Aljabr trustworthy

A lot of coding agents effectively do:

```text
test command exited 0
→ success
```

You want:

```text
success =
    intended behavior passes
    AND
    no unacceptable regression
    AND
    workspace matches expected scope
    AND
    policy permits completion
```

---

# 39. `VerificationEngine` should emit evidence

Every run becomes:

```text
Evidence:
  verification.compile = PASS

Evidence:
  test.AuthenticationTest.expiredToken = PASS

Evidence:
  test.RefreshTokenTest = PASS
```

Then TaskEngine doesn't have to remember raw command output.

---

# 40. Store raw output separately

Keep:

```text
Evidence
```

small.

Store:

```text
raw process log
```

as an artifact.

For example:

```text
.aljabr/
    artifacts/
        task-123/
            build.log
            test.log
            diff.patch
```

Evidence references the artifact.

---

# 41. This gives you excellent recovery

If Gemma needs more information:

```text
Evidence:
test failed

Artifact:
test.log
```

ContextEngine can retrieve the relevant section.

You don't inject the entire log automatically.

---

# 42. `OutputNormalizer`

I'd make this a first-class service:

```java
public interface OutputNormalizer {

    NormalizedOutput normalize(
        ProcessResult result
    );
}
```

It should:

```text
remove noise
collapse repeated stack frames
extract source locations
extract test names
extract error codes
truncate irrelevant output
preserve important context
```

---

# 43. Local model context optimization

This is exactly where your previous ContextPolicy becomes useful.

For a compile failure:

```text
Gemma sees:

ERROR
file + line
compiler message
enclosing symbol
recent edit
relevant imports
```

Not:

```text
entire compiler log
```

For a test failure:

```text
test name
assertion
expected
actual
stack location
changed symbol
```

Again:

```text
high information density
```

---

# 44. `DebugContextBuilder`

I'd make:

```java
public interface DebugContextBuilder {

    Uni<ContextPacket> build(
        FailureContext failure
    );
}
```

And it uses:

```text
Failure
+
RepositoryIntel
+
Git
+
Evidence
+
Task history
+
ContextPolicy
```

---

# 45. The model prompt can be tiny

For example:

```text
TASK
Fix expired token validation.

FAILURE
AuthenticationTest.expiredToken()

EXPECTED
false

ACTUAL
true

CHANGED SYMBOL
TokenValidator.validateExpiration()

RECENT CHANGE
return expiration.isBefore(now);

CALLERS
AuthService.authenticate()

QUESTION
Identify the most likely cause and propose the cheapest next investigation.
```

That's excellent input for a 12B local model.

---

# 46. The model output should also be tiny

Instead of:

> "After carefully analyzing the architecture, I believe..."

Return:

```json
{
  "diagnosis": "Expiration boundary is likely incorrect",
  "confidence": 0.78,
  "nextAction": {
    "type": "INSPECT_TEST",
    "target": "AuthenticationTest.expiredToken"
  }
}
```

Then Wayang handles the rest.

---

# 47. Output budget

For this class of calls:

```text
diagnosis:
~300-600 tokens max

decision:
~100-300

patch:
minimal

review:
~300
```

Don't ask Gemma to produce essays.

---

# 48. But don't over-compress user-facing output

This distinction matters.

Internal agent communication:

```text
minimal
```

User response:

```text
clear
```

So after successful completion:

```text
Fixed expired-token validation.

Changes:
- corrected expiration boundary comparison
- added regression coverage

Verification:
- 8 affected tests passed
- no new failures
```

The user gets a useful summary without exposing internal reasoning.

---

# 49. Debug loop budget

Add a separate budget:

```java
public record DebugBudget(
    int maxDiagnoses,
    int maxRepairs,
    int maxInvestigations,
    int maxRepeatedFailure
) {}
```

Example:

```text
max diagnoses = 3
max repairs = 3
max repeated failure = 2
```

---

# 50. Terminal states

The agent should be able to end with:

```text
COMPLETED
BLOCKED
FAILED
CANCELLED
```

But **BLOCKED != FAILED**.

Example:

```text
BLOCKED
```

means:

> "I know what needs to happen, but policy/user/environment prevents me."

Example:

```text
database migration requires approval
```

Whereas:

```text
FAILED
```

means:

> "The agent attempted the task and could not solve it."

---

# 51. `TaskResult`

```java
public record TaskResult(
    TaskId taskId,
    TaskStatus status,
    String summary,
    List<EvidenceId> evidence,
    Optional<ChangeSet> changes,
    Optional<FailureReport> failure
) {}
```

This gives your UI/API a clean contract.

---

# 52. The full Aljabr coding loop now looks like this

```text
USER
 │
 ▼
TaskEngine
 │
 ▼
TaskPlanner
 │
 ▼
RepositoryIntel
 │
 ▼
ContextEngine
 │
 ▼
Gemma
 │
 ▼
Decision
 │
 ▼
Skill
 │
 ├── inspect
 ├── search
 ├── edit
 └── verify
        │
        ▼
   Verification
        │
   ┌────┴─────┐
   ▼          ▼
 PASS        FAIL
   │          │
   ▼          ▼
 Review    Classify
   │          │
   │          ▼
   │       DebugEngine
   │          │
   │          ▼
   │       Diagnosis
   │          │
   │          ▼
   │       RepairPlan
   │          │
   │          ▼
   │       CodeEdit
   │          │
   │          └───────────┐
   │                      │
   └──────────────► Verify│
                          │
                          ▼
                       DONE
```

At this point, Aljabr has a **closed-loop autonomous coding architecture**.

---

# 53. One more architectural improvement I'd make here

Don't call this simply:

```text
DebugEngine
```

internally.

I'd separate:

```text
FailureAnalyzer
```

from:

```text
RepairEngine
```

because diagnosis and repair are different responsibilities.

```text
VerificationEngine
        │
        ▼
FailureAnalyzer
        │
        ▼
Diagnosis
        │
        ▼
RepairEngine
        │
        ▼
RepairPlan
        │
        ▼
CodeEditSkill
```

That makes the system much easier to test.

---

# 54. Deterministic vs model responsibilities

This should be one of Aljabr's strongest design rules:

| Problem                         | Owner              |
| ------------------------------- | ------------------ |
| Did compilation fail?           | Tool               |
| Which test failed?              | Tool               |
| Which line failed?              | Parser             |
| What symbol is there?           | RepositoryIntel    |
| What changed?                   | Git/Workspace      |
| Which tests are affected?       | RepositoryIntel    |
| Is this a regression?           | VerificationEngine |
| What might explain the failure? | Gemma              |
| What should we inspect next?    | Gemma + policy     |
| What patch should be generated? | Gemma              |
| Is patch safe to apply?         | Wayang             |
| Does patch work?                | VerificationEngine |
| Whether task is complete        | Wayang             |

This division is **exactly right for local inference**.

---

# 55. The next milestone after this

Now we're ready for something very interesting:

## **`Aljabr Coding Skills`**

Rather than one giant coding agent prompt, build specialized skills:

```text
code.inspect
code.search
code.explain
code.trace
code.plan
code.edit
code.test
code.debug
code.refactor
code.review
code.commit
```

Each skill should have:

```text
Skill.md
input contract
context requirement
tools
output schema
risk level
verification policy
model strategy
```

And importantly, **most skills should be executable without Gemma at all when deterministic tooling can answer the question**.

That is where the Wayang framework starts becoming a genuinely reusable foundation rather than just an Aljabr implementation.
