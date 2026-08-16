Absolutely. **Next milestone: `Aljabr Coding Skills`**.

Now that we have:

```text
CapabilityDetector
      ↓
ContextController
      ↓
ContextPolicy
      ↓
ContextEngine
      ↓
TaskEngine
      ↓
Planner / StateMachine
      ↓
VerificationEngine
      ↓
FailureAnalyzer / RepairEngine
```

the next step is to turn those primitives into **small, composable coding capabilities**.

The main principle:

> **A Skill should describe an objective and contract; Wayang decides how to execute it, which tools to use, how much context to provide, and how to verify the result.**

This is particularly important for your **local Gemma 12B + M4 16 GB** setup.

---

# 1. Don't create one giant `CodingAgent`

Avoid:

```text
AljabrCodingAgent
 ├── inspect
 ├── search
 ├── edit
 ├── debug
 ├── test
 ├── review
 ├── refactor
 └── explain
```

with a giant prompt.

Instead:

```text
Aljabr
 │
 ├── code.inspect
 ├── code.search
 ├── code.trace
 ├── code.explain
 ├── code.plan
 ├── code.edit
 ├── code.test
 ├── code.debug
 ├── code.refactor
 └── code.review
```

Each is independently usable.

---

# 2. Skill contract

I'd define:

```java
public interface Skill {

    SkillId id();

    SkillMetadata metadata();

    SkillInputSchema inputSchema();

    SkillOutputSchema outputSchema();

    Uni<SkillResult> execute(
        SkillContext context
    );
}
```

Metadata:

```java
public record SkillMetadata(
    String name,
    String description,
    SkillCategory category,
    RiskLevel risk,
    boolean requiresModel,
    boolean mutatesWorkspace
) {}
```

---

# 3. The important distinction: `requiresModel`

This is one of the best optimizations for local Aljabr.

For example:

```text
code.search
requiresModel = false

code.compile
requiresModel = false

code.test
requiresModel = false

code.inspect
requiresModel = sometimes

code.explain
requiresModel = true

code.edit
requiresModel = usually

code.debug
requiresModel = usually
```

Don't spend Gemma tokens when a deterministic tool can answer the question.

---

# 4. Skill categories

I'd start with:

```java
public enum SkillCategory {

    DISCOVERY,
    UNDERSTANDING,
    PLANNING,
    MODIFICATION,
    VERIFICATION,
    DEBUGGING,
    REVIEW
}
```

Simple.

Don't over-engineer the taxonomy yet.

---

# 5. `Skill.md`

This fits very nicely with the Wayang architecture you've been building.

For example:

```text
skills/
└── code/
    ├── inspect/
    │   ├── Skill.md
    │   └── skill.yaml
    │
    ├── search/
    │   ├── Skill.md
    │   └── skill.yaml
    │
    ├── edit/
    │   ├── Skill.md
    │   └── skill.yaml
    │
    └── test/
        ├── Skill.md
        └── skill.yaml
```

---

# 6. Example `code.inspect/Skill.md`

Something like:

```markdown
# Code Inspect

## Purpose

Inspect source code relevant to a task.

## Input

- task objective
- target file, symbol, or search query
- optional evidence

## Behavior

1. Identify relevant source.
2. Resolve symbols.
3. Inspect surrounding implementation.
4. Identify important dependencies.
5. Return compact evidence.

## Do not

- modify files
- speculate beyond available evidence
- return entire files unnecessarily

## Output

- relevant symbols
- source facts
- dependencies
- uncertainties
```

Notice the prompt isn't trying to describe the whole agent.

It's a **skill contract**.

---

# 7. But `Skill.md` should not contain everything

Don't turn it into:

```text
1000 lines of instructions
```

I'd split responsibilities:

```text
Skill.md
    human/model guidance

skill.yaml
    machine metadata

schema.json
    input/output contract
```

For example:

```yaml
id: code.inspect
category: DISCOVERY
requires_model: false
mutates_workspace: false
risk: LOW

context:
  policy: inspection

tools:
  - repo.read
  - repo.symbol
  - repo.search

verification:
  required: false
```

---

# 8. `requires_model: false` doesn't mean "never use model"

That's an important distinction.

`code.inspect` can use deterministic tooling first:

```text
symbol resolver
AST
LSP
grep
repository index
```

Then optionally ask Gemma:

```text
"What is significant about this implementation?"
```

So I'd actually use:

```yaml
reasoning:
  mode: OPTIONAL
```

rather than a boolean eventually.

---

# 9. Reasoning modes

```java
public enum ReasoningMode {

    NONE,
    OPTIONAL,
    REQUIRED,
    ESCALATABLE
}
```

Examples:

```text
code.search
NONE

code.inspect
OPTIONAL

code.explain
REQUIRED

code.debug
ESCALATABLE
```

This becomes powerful with your `CapabilityDetector`.

---

# 10. `code.search`

This should be overwhelmingly deterministic.

```text
User:
"Where is token expiration checked?"
```

Don't ask Gemma.

Use:

```text
RepositoryIndex
     ↓
symbol search
     ↓
reference search
     ↓
candidate ranking
```

Return:

```text
TokenValidator.java:72
TokenValidator.validateExpiration()
```

Only if ambiguity remains:

```text
Gemma
```

---

# 11. `code.trace`

This is another highly valuable deterministic skill.

```text
code.trace
```

Input:

```text
AuthService.authenticate()
```

Output:

```text
AuthService.authenticate()
    ↓
TokenService.validate()
    ↓
TokenValidator.validateExpiration()
    ↓
Clock.now()
```

This can be built using:

```text
AST
LSP
call graph
language-specific index
```

No LLM necessary for the basic graph.

---

# 12. `code.explain`

This is where Gemma becomes useful.

Input:

```text
symbol:
TokenValidator.validateExpiration()

relevant code:
...

dependencies:
Clock

question:
Why does this reject expired tokens?
```

ContextEngine constructs a small prompt.

Gemma produces:

```text
The method compares expiration against the current clock and rejects
tokens whose expiration is not in the future.
```

Then the skill returns the explanation.

---

# 13. `code.plan`

This is model-assisted.

But don't ask Gemma:

> "Plan everything about this repository."

Instead:

```text
TASK
Add refresh-token rotation.

REPOSITORY FACTS
...

CONSTRAINTS
...

KNOWN AFFECTED COMPONENTS
...

Produce a minimal implementation plan.
```

Output:

```json
{
  "steps": [
    "Update refresh-token persistence",
    "Rotate token after successful refresh",
    "Invalidate previous token",
    "Add replay regression test"
  ]
}
```

---

# 14. `code.edit`

This one needs stronger contracts.

The model should not directly mutate files.

Instead:

```text
Gemma
 ↓
EditProposal
 ↓
MutationEngine
```

---

# 15. `EditProposal`

```java
public record EditProposal(
    List<FileEdit> edits,
    String objective,
    List<String> invariants,
    List<EvidenceId> supportingEvidence
) {}
```

File edit:

```java
public record FileEdit(
    Path file,
    List<TextEdit> edits
) {}
```

---

# 16. Prefer structural edits when possible

Instead of:

```text
replace lines 73-82
```

eventually support:

```text
replace method:
TokenValidator.validateExpiration
```

or:

```text
insert test into:
AuthenticationTest
```

This dramatically reduces accidental changes.

---

# 17. `MutationEngine`

```java
public interface MutationEngine {

    Uni<MutationResult> apply(
        EditProposal proposal
    );

    Uni<Void> rollback(
        MutationId mutation
    );
}
```

The model proposes.

The engine validates.

Then mutation occurs.

---

# 18. Mutation validation

Before applying:

```text
EditProposal
     ↓
file exists?
     ↓
expected content matches?
     ↓
edit range valid?
     ↓
overlap?
     ↓
policy?
     ↓
apply
```

If the file changed since the model inspected it:

```text
REJECT
```

and re-inspect.

This prevents stale-context edits.

---

# 19. This is especially important with local models

Because Gemma might spend:

```text
5 seconds
```

reasoning about a file.

During that time:

```text
user edits file
```

Now the proposal is stale.

Wayang must detect:

```text
workspace fingerprint changed
```

and reject the patch.

---

# 20. `WorkspaceFingerprint`

You already need this for checkpointing.

Use it here too:

```java
public record WorkspaceFingerprint(
    String repositoryHash,
    Map<Path, String> fileHashes
) {}
```

Then:

```text
inspect
 ↓
fingerprint A
 ↓
Gemma
 ↓
proposal references A
 ↓
current fingerprint B
```

If:

```text
A != B
```

don't apply.

---

# 21. `code.test`

This should primarily be deterministic.

```text
code.test
    ↓
TestSelector
    ↓
TestRunner
    ↓
VerificationEngine
```

Gemma can help choose tests when static dependency information isn't sufficient.

---

# 22. `code.review`

This is model-assisted, but again keep it focused.

Don't send the entire repository.

Send:

```text
TASK
...

DIFF
...

AFFECTED SYMBOLS
...

VERIFICATION
...

INVARIANTS
...
```

Ask:

```text
Review this change for:
1. correctness
2. regression risk
3. missing tests
4. unnecessary changes
```

---

# 23. Review should not rewrite code

Review result:

```java
public record ReviewResult(
    ReviewStatus status,
    List<ReviewFinding> findings,
    List<String> strengths
) {}
```

Possible:

```text
PASS
WARN
REJECT
```

Then TaskEngine decides what to do.

---

# 24. `code.debug`

This becomes an orchestrating skill.

It shouldn't reinvent your DebugEngine.

Instead:

```text
code.debug
      ↓
FailureAnalyzer
      ↓
RepositoryIntel
      ↓
HypothesisEngine
      ↓
RepairEngine
      ↓
VerificationEngine
```

So the Skill is the **user-facing capability**.

The engines underneath provide the machinery.

---

# 25. Skill vs Engine

This distinction is important for your architecture.

### Skill

"What can the agent do?"

```text
code.debug
code.edit
code.review
```

### Engine

"How does Wayang perform it reliably?"

```text
TaskEngine
ContextEngine
VerificationEngine
DebugEngine
MutationEngine
```

### Tool

"How does it interact with the environment?"

```text
repo.read
repo.search
process.execute
git.diff
test.run
```

### Policy

"What is allowed?"

```text
ContextPolicy
MutationPolicy
VerificationPolicy
MemoryPolicy
ExecutionPolicy
```

That's a clean four-layer architecture.

---

# 26. The resulting architecture

```text
                    ALJABR
                       │
                  AgentProfile
                       │
                 ┌─────┴─────┐
                 │   Skills   │
                 └─────┬─────┘
                       │
                 ┌─────▼─────┐
                 │ TaskEngine │
                 └─────┬─────┘
                       │
       ┌───────────────┼────────────────┐
       ▼               ▼                ▼
 ContextEngine    PlanningEngine   VerificationEngine
       │               │                │
       ▼               ▼                ▼
 RepositoryIntel   ReasoningRouter   FailureAnalyzer
       │               │                │
       └───────────────┼────────────────┘
                       ▼
                  ToolRegistry
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       RepoTools    ProcessTools   GitTools
```

This is becoming a very solid architecture.

---

# 27. Now optimize the skills for local Gemma

This is where I would introduce a **`SkillContextPolicy`**.

For every skill:

```yaml
context:
  max_tokens: 3500
  strategy: MINIMAL_RELEVANT
  include:
    - task
    - target_symbol
    - direct_dependencies
    - evidence
  exclude:
    - unrelated_files
    - full_history
```

The skill doesn't manually construct prompts.

It declares what it needs.

---

# 28. Context requirements

For example:

```java
public record ContextRequirement(
    Set<ContextSource> required,
    Set<ContextSource> optional,
    ContextCompressionStrategy strategy,
    int preferredTokens
) {}
```

Sources:

```java
public enum ContextSource {

    USER_TASK,
    TARGET_CODE,
    DEPENDENCIES,
    CALLERS,
    TESTS,
    DIFF,
    GIT_HISTORY,
    ERROR_OUTPUT,
    MEMORY,
    PREVIOUS_EVIDENCE
}
```

---

# 29. Example: `code.debug`

```yaml
context:
  required:
    - USER_TASK
    - ERROR_OUTPUT
    - TARGET_CODE

  optional:
    - TESTS
    - DIFF
    - CALLERS
    - PREVIOUS_EVIDENCE

  strategy: FAILURE_FOCUSED
```

So ContextEngine automatically assembles the right packet.

---

# 30. Example: `code.review`

```yaml
context:
  required:
    - USER_TASK
    - DIFF
    - VERIFICATION

  optional:
    - TARGET_CODE
    - TESTS
    - CALLERS

  strategy: CHANGE_FOCUSED
```

Again, no giant prompt.

---

# 31. This is where your small/large model strategy becomes elegant

You don't need:

```text
if Gemma:
    special prompt
else:
    normal prompt
```

Instead:

```text
Skill
 ↓
ContextRequirement
 ↓
ContextPolicy
 ↓
CapabilityDetector
 ↓
ContextController
```

The policy determines compression.

---

# 32. Example

Small model:

```text
Gemma 12B
context capacity:
small

ContextPolicy:
MINIMAL_RELEVANT
```

Large cloud model:

```text
large model
context capacity:
large

ContextPolicy:
RICH_RELEVANT
```

But both receive the **same semantic information hierarchy**.

That is important.

You're not creating two architectures.

---

# 33. The hierarchy

Always prioritize:

```text
1. task objective
2. current state
3. directly relevant code
4. failure/evidence
5. dependencies
6. tests
7. recent changes
8. broader repository context
9. historical/contextual information
```

Small model:

```text
1 → 5
```

Large model:

```text
1 → 9
```

This is precisely the optimization strategy we discussed earlier.

---

# 34. And this should be adaptive

Don't hardcode:

```text
Gemma = 4K
Cloud = 128K
```

Use:

```text
CapabilityDetector
```

to determine:

```text
model context capacity
available token budget
output budget
latency
hardware
provider
cost
```

Then:

```text
ContextController
```

chooses the policy.

---

# 35. `SkillRouter`

Now we can make skill selection explicit:

```java
public interface SkillRouter {

    Uni<SkillSelection> select(
        TaskContext context
    );
}
```

But again:

**don't necessarily ask Gemma to select the skill.**

Use deterministic matching first.

---

# 36. Skill selection hierarchy

```text
User explicitly requests skill
        ↓
exact match

otherwise
        ↓
task type

otherwise
        ↓
state

otherwise
        ↓
planner

otherwise
        ↓
model suggestion
```

For:

> "Run the tests"

there's no reason to ask Gemma what skill to use.

Just:

```text
code.test
```

---

# 37. Skill registry

```java
public interface SkillRegistry {

    Optional<Skill> find(SkillId id);

    List<SkillMetadata> available(
        SkillQuery query
    );
}
```

Then Aljabr can dynamically discover capabilities.

---

# 38. Tool registry remains separate

```java
SkillRegistry
ToolRegistry
```

Do not merge them.

For example:

```text
code.debug
```

may use:

```text
repo.search
repo.read
git.diff
test.run
process.execute
```

A skill is an **orchestrated capability**.

A tool is an **atomic environment operation**.

---

# 39. This creates a very useful composition model

```text
code.debug
    uses
        code.inspect
        code.trace
        code.test
        code.edit
```

But don't physically call skills recursively unless necessary.

Prefer engine composition internally.

Otherwise you can end up with:

```text
skill → skill → skill → skill → ...
```

which becomes difficult to reason about.

---

# 40. Skill execution trace

Every skill execution should create:

```java
public record SkillExecution(
    SkillId skill,
    Instant started,
    Instant completed,
    SkillStatus status,
    List<EvidenceId> producedEvidence
) {}
```

This becomes useful for:

```text
debugging
metrics
resumption
cost analysis
optimization
```

---

# 41. And you can measure what Gemma is actually doing

Eventually you'll have metrics like:

```text
code.debug
  model calls: 4
  tool calls: 11
  context tokens: 8,420
  output tokens: 1,120
  verification attempts: 2
  successful repair: true
```

Now you can optimize Aljabr empirically.

---

# 42. This is important: optimize based on telemetry, not intuition

For example you might discover:

```text
code.inspect
```

uses Gemma 80% of the time but deterministic repository tools solve 70% of those requests.

Then optimize:

```text
before:
repo search → Gemma → answer

after:
repo search → deterministic result
          ↓
      only ask Gemma if ambiguous
```

That's a real local-model optimization.

---

# 43. `ReasoningGate`

I'd introduce one more small component:

```java
public interface ReasoningGate {

    ReasoningDecision evaluate(
        SkillContext context
    );
}
```

Possible:

```text
SKIP_MODEL
USE_MODEL
ESCALATE_MODEL
ASK_USER
```

Example:

```text
"Find where authenticate() is defined."

→ SKIP_MODEL
```

But:

```text
"Why does authentication sometimes reject valid tokens?"

→ USE_MODEL
```

And:

```text
"Resolve conflicting architectural interpretations."

→ ESCALATE_MODEL
```

---

# 44. This will save enormous local inference

A coding agent does not need an LLM for:

```text
find file
read file
find symbol
find references
git diff
run test
compile
format
count files
```

Use the machine.

Use Gemma for:

```text
interpret
infer
plan
diagnose
design
review
```

That should become one of Aljabr's core philosophies.

---

# 45. The resulting philosophy

```text
              ALJABR
                 │
       ┌─────────┴─────────┐
       │                   │
 DETERMINISTIC         REASONING
       │                   │
 repo/index             Gemma
 AST/LSP                cloud model
 compiler               optional
 tests
 git
       │                   │
       └─────────┬─────────┘
                 ▼
             Wayang
          orchestration
```

Neither side replaces the other.

---

# 46. The next milestone after Skills

At this point I'd move into a particularly important subsystem:

## **`Repository Intelligence`**

Because for a coding agent, the repository itself should become an **indexed knowledge substrate**, rather than something Gemma repeatedly searches with grep.

Something like:

```text
Repository
    ↓
WorkspaceIndexer
    ├── files
    ├── symbols
    ├── AST
    ├── references
    ├── call graph
    ├── imports
    ├── tests
    ├── configs
    ├── ownership
    └── git history
             ↓
       RepositoryIntel
             ↓
        ContextEngine
             ↓
           Aljabr
```

That subsystem is probably the **single biggest quality/performance improvement** for your local Gemma setup after the execution/verification architecture we've just established.

And importantly, it lets Aljabr spend its scarce model context on **reasoning about the code instead of discovering basic facts about the code**.



Yes. **Next milestone: `Repository Intelligence`**.

This is probably the most important subsystem for making Aljabr strong on your **local 12B model**.

The goal is simple:

> **Don't make Gemma discover the repository. Let Wayang discover it, index it, and give Gemma only the relevant facts.**

---

# 1. The architecture

I would make it:

```text
                     Workspace
                         │
                         ▼
                  WorkspaceIndexer
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       FileIndex     SymbolIndex     TestIndex
          │              │              │
          ├──────────────┼──────────────┤
          ▼              ▼              ▼
      ImportIndex    ReferenceIndex   ConfigIndex
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                    RepositoryIntel
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
         Search       Trace       Context
             │           │           │
             └───────────┼───────────┘
                         ▼
                    ContextEngine
                         │
                         ▼
                       Gemma
```

The critical boundary is:

```text
RepositoryIndex ≠ model context
```

The index can be large.

The model context should stay small.

---

# 2. Don't build "a vector database" first

This is an important implementation decision.

A coding repository is highly structured.

You want:

```text
symbol
file
line
reference
caller
callee
import
test
module
dependency
git change
```

before you want:

```text
embedding similarity
```

So the priority should be:

```text
1. filesystem index
2. symbol index
3. reference index
4. test index
5. dependency graph
6. git intelligence
7. semantic/vector search
```

Not the other way around.

---

# 3. `RepositoryIntel`

Keep the public interface small.

```java
public interface RepositoryIntel {

    Uni<FileInfo> file(Path path);

    Uni<SymbolInfo> symbol(SymbolRef ref);

    Uni<List<SearchResult>> search(SearchQuery query);

    Uni<List<Reference>> references(SymbolRef symbol);

    Uni<CallGraph> trace(SymbolRef symbol);

    Uni<List<TestInfo>> affectedTests(Set<SymbolRef> symbols);

    Uni<RepositorySnapshot> snapshot();
}
```

That is enough to begin.

---

# 4. `WorkspaceIndexer`

```java
public interface WorkspaceIndexer {

    Uni<IndexResult> index(
        Workspace workspace
    );

    Uni<IndexUpdate> update(
        WorkspaceChange change
    );
}
```

Don't re-index the entire repository after every edit.

That's wasteful.

---

# 5. Incremental indexing

Suppose Aljabr changes:

```text
src/auth/TokenValidator.java
```

Don't do:

```text
re-index 100,000 files
```

Do:

```text
TokenValidator.java
       ↓
parse
       ↓
update symbols
       ↓
update references
       ↓
invalidate dependent queries
```

---

# 6. `WorkspaceSnapshot`

Everything should be tied to a snapshot.

```java
public record WorkspaceSnapshot(
    String id,
    String root,
    WorkspaceFingerprint fingerprint,
    Instant createdAt
) {}
```

For example:

```text
snapshot:
abc123

TokenValidator.java:
hash-9a31

AuthService.java:
hash-b882
```

This becomes extremely useful for stale-context protection.

---

# 7. File index

Start simple.

```java
public record FileInfo(
    Path path,
    String language,
    long size,
    String hash,
    List<String> imports,
    FileKind kind
) {}
```

Don't store entire file content in the normal metadata index.

Content can live in the workspace/artifact layer.

---

# 8. Symbol index

This is the heart of coding intelligence.

```java
public record SymbolInfo(
    SymbolId id,
    SymbolKind kind,
    String name,
    Path file,
    SourceRange range,
    Optional<SymbolId> parent,
    List<SymbolId> children
) {}
```

For:

```java
class AuthService {
    void authenticate() {}
}
```

you might have:

```text
AuthService
└── authenticate()
```

---

# 9. Symbol kinds

Keep this generic:

```java
public enum SymbolKind {

    MODULE,
    PACKAGE,
    CLASS,
    INTERFACE,
    ENUM,

    METHOD,
    FUNCTION,
    CONSTRUCTOR,

    FIELD,
    PROPERTY,
    VARIABLE,

    TYPE,
    CONSTANT
}
```

Language adapters can map their native AST/LSP concepts into this model.

---

# 10. Don't make RepositoryIntel language-specific

This is another important architectural boundary.

Use:

```text
RepositoryIntel
      │
      ▼
LanguageAdapter
      │
 ┌────┼────┐
 ▼    ▼    ▼
Java TypeScript Python
```

Each adapter understands its language.

The rest of Wayang doesn't need to.

---

# 11. `LanguageAdapter`

```java
public interface LanguageAdapter {

    boolean supports(String language);

    Uni<ParsedFile> parse(
        SourceFile file
    );
}
```

Later:

```text
JavaLanguageAdapter
KotlinLanguageAdapter
TypeScriptLanguageAdapter
PythonLanguageAdapter
RustLanguageAdapter
GoLanguageAdapter
```

---

# 12. Don't implement every parser yourself

This is where external deterministic infrastructure should be leveraged.

Prefer:

```text
LSP
Tree-sitter
compiler AST
language server
```

depending on language.

The architecture should abstract the source.

---

# 13. Reference index

Now we get something much more valuable than grep.

```java
public record Reference(
    SymbolId from,
    SymbolId to,
    ReferenceKind kind,
    SourceRange location
) {}
```

For example:

```text
AuthService.authenticate()
       │
       └──calls──► TokenValidator.validate()
```

---

# 14. Reference kinds

```java
public enum ReferenceKind {

    CALL,
    READ,
    WRITE,
    EXTENDS,
    IMPLEMENTS,
    IMPORTS,
    INSTANTIATES,
    OVERRIDES,
    REFERENCES
}
```

This lets RepositoryIntel answer meaningful questions.

---

# 15. Example

User:

> "Where is `validateExpiration()` used?"

Don't send repository contents to Gemma.

Wayang:

```text
symbol lookup
    ↓
ReferenceIndex
    ↓
3 references
```

Return:

```text
AuthService.authenticate():42
RefreshTokenService.validate():71
TokenController.refresh():103
```

Gemma never needed to reason about the search.

---

# 16. Call graph

Expose a graph abstraction:

```java
public record CallGraph(
    SymbolId root,
    Set<CallEdge> edges
) {}
```

Then:

```text
AuthController.login()
       ↓
AuthService.authenticate()
       ↓
TokenService.validate()
       ↓
TokenValidator.validateExpiration()
```

This becomes enormously useful for debugging.

---

# 17. But don't build the entire call graph eagerly

For large repositories:

```text
100k symbols
millions of edges
```

can be expensive.

Use lazy expansion:

```text
root symbol
   ↓
expand callers
   ↓
expand callees
```

Only retrieve what the task needs.

---

# 18. `TraceRequest`

```java
public record TraceRequest(
    SymbolRef root,
    TraceDirection direction,
    int maxDepth,
    int maxNodes
) {}
```

For local Aljabr:

```text
maxDepth = 3
maxNodes = 30
```

is usually much better than dumping the entire dependency graph.

---

# 19. Test index

This should be a first-class index.

```java
public record TestInfo(
    TestId id,
    String name,
    Path file,
    Set<SymbolId> coveredSymbols,
    TestFramework framework
) {}
```

Then:

```text
changed symbol
      ↓
TestIndex
      ↓
affected tests
```

---

# 20. How do you determine affected tests?

Initially, use a conservative strategy:

```text
changed method
 ↓
containing class
 ↓
references
 ↓
known tests
```

Later add:

```text
coverage data
```

if available.

---

# 21. Coverage can become a powerful optimization

Eventually:

```text
test coverage
      ↓
symbol → tests
```

Then:

```text
TokenValidator.validateExpiration()
      ↓
12 tests
```

Aljabr can run those first.

This can dramatically reduce local execution time.

---

# 22. Test selection policy

```java
public interface TestSelector {

    Uni<TestSelection> select(
        Set<SymbolId> changedSymbols,
        VerificationPolicy policy
    );
}
```

Result:

```java
public record TestSelection(
    List<TestInfo> targeted,
    List<TestInfo> regression,
    boolean requireFullSuite
) {}
```

---

# 23. Dependency index

Repositories often have multiple modules.

You need:

```text
module
  ↓
depends on
  ↓
module
```

For example:

```text
api
 ↓
auth
 ↓
database
```

Then a change in:

```text
auth
```

may affect:

```text
api
```

even when no direct source reference exists.

---

# 24. `ModuleGraph`

```java
public record ModuleGraph(
    Set<Module> modules,
    Set<ModuleDependency> dependencies
) {}
```

This can help verification scope.

---

# 25. Config intelligence

Don't overlook this.

Coding agents often fail because behavior is controlled by:

```text
application.yml
.env
config.json
pom.xml
build.gradle
package.json
Dockerfile
CI configuration
```

So:

```text
ConfigIndex
```

should eventually exist.

---

# 26. But don't parse secrets into the index

Very important.

If you encounter:

```text
API_KEY=...
DATABASE_PASSWORD=...
```

the index should store:

```text
DATABASE_PASSWORD exists
```

not:

```text
DATABASE_PASSWORD = actual-secret
```

Use secret-aware redaction.

---

# 27. `GitIntel`

This is another major source of coding intelligence.

```java
public interface GitIntel {

    Uni<Diff> diff();

    Uni<List<CommitInfo>> history(Path path);

    Uni<List<CommitInfo>> history(SymbolRef symbol);

    Uni<BlameInfo> blame(SourceRange range);
}
```

---

# 28. Why Git history matters

Suppose Gemma sees:

```java
if (expiration.isBefore(now))
```

and doesn't understand why.

RepositoryIntel can tell it:

```text
This line changed 2 days ago.

Previous implementation:
if (expiration.isAfter(now))
```

That's extremely valuable evidence.

---

# 29. But don't always include history

For local context:

```text
current code
>
direct dependencies
>
tests
>
recent diff
>
history
```

Git history is **optional context**, not default context.

---

# 30. `RepositoryIntel` should return evidence, not giant text

This is another key design decision.

Bad:

```java
String searchEverything();
```

Better:

```java
List<SearchResult>
List<SymbolInfo>
List<Reference>
List<TestInfo>
```

Structured data gives ContextEngine control over compression.

---

# 31. Search abstraction

```java
public interface RepositorySearch {

    Uni<List<SearchResult>> search(
        SearchQuery query
    );
}
```

Query:

```java
public record SearchQuery(
    String text,
    SearchScope scope,
    SearchMode mode,
    int limit
) {}
```

---

# 32. Search modes

```java
public enum SearchMode {

    TEXT,
    SYMBOL,
    REFERENCE,
    PATH,
    SEMANTIC
}
```

Now you can do:

```text
symbol: TokenValidator
reference: validateExpiration
text: "expired token"
semantic: "where token expiration is enforced"
```

---

# 33. Search ranking

Don't blindly return first grep results.

Rank using:

```text
exact symbol match
>
exact identifier
>
same module
>
same package
>
reference relationship
>
test relationship
>
recently changed
>
semantic similarity
```

---

# 34. `SearchResult`

```java
public record SearchResult(
    SearchTarget target,
    double relevance,
    SearchReason reason
) {}
```

Example:

```text
TokenValidator.java:72
relevance = 0.98
reason = EXACT_SYMBOL
```

This gives ContextEngine useful ranking information.

---

# 35. Semantic search should come later

Eventually:

```text
text search
+
symbol search
+
semantic embeddings
```

But semantic search should **not replace structural search**.

For coding:

```text
"find TokenValidator"
```

should be deterministic.

Semantic search is better for:

> "Where is the logic that prevents replayed refresh tokens?"

---

# 36. Embedding index

When you eventually add it:

```text
CodeChunk
    ↓
Embedding
    ↓
VectorIndex
```

But chunk by **semantic units**, not arbitrary 500-token pieces.

Prefer:

```text
class
method
function
test
configuration block
```

---

# 37. `CodeChunk`

```java
public record CodeChunk(
    ChunkId id,
    SymbolId symbol,
    Path file,
    SourceRange range,
    String content,
    String hash
) {}
```

Now embeddings can point back to actual symbols.

---

# 38. Don't put embeddings into the ContextEngine directly

Instead:

```text
SemanticSearch
    ↓
candidate symbols
    ↓
RepositoryIntel
    ↓
structured evidence
    ↓
ContextEngine
```

That keeps semantic retrieval behind the same abstraction.

---

# 39. Repository cache

This connects to your earlier cache architecture.

You should cache:

```text
parsed files
symbols
references
tests
embeddings
git metadata
```

but **invalidate by file hash**.

For example:

```text
TokenValidator.java
hash A
```

already indexed?

```text
yes → reuse
```

Changed:

```text
hash B
```

then:

```text
reparse
```

---

# 40. Cache structure

Something like:

```text
.aljabr/
    index/
        files/
        symbols/
        references/
        tests/
        modules/
        embeddings/

    artifacts/
        tasks/

    checkpoints/
        tasks/

    cache/
        searches/
        parsed/
```

I'd keep this internal and versioned.

---

# 41. Index version

Your schema will evolve.

Store:

```text
indexVersion = 3
```

Then:

```text
runtime version 4
index version 2
```

can trigger migration/rebuild.

Don't silently consume incompatible index data.

---

# 42. File invalidation

When a file changes:

```text
FileChanged
   ↓
FileIndex invalidated
   ↓
SymbolIndex invalidated
   ↓
ReferenceIndex affected
   ↓
TestIndex affected
   ↓
SemanticIndex affected
```

You don't necessarily need to rebuild everything immediately.

Mark dependent data stale.

---

# 43. Event-driven invalidation

This fits Wayang nicely.

```java
public record WorkspaceChanged(
    Path path,
    ChangeType type,
    String oldHash,
    String newHash
) {}
```

Then:

```text
WorkspaceChanged
       │
       ├──► FileIndex
       ├──► SymbolIndex
       ├──► ReferenceIndex
       └──► TestIndex
```

This keeps the system modular.

---

# 44. `RepositorySnapshot`

RepositoryIntel should provide a concise high-level snapshot:

```java
public record RepositorySnapshot(
    String name,
    List<String> languages,
    List<Module> modules,
    List<String> frameworks,
    List<Path> entryPoints,
    List<Path> testRoots
) {}
```

This is useful during initial task understanding.

---

# 45. But don't send the entire snapshot every turn

Only once when useful:

```text
TASK UNDERSTANDING
```

Later decisions can operate on:

```text
current symbol
current evidence
current state
```

Again:

**cache globally, inject selectively.**

---

# 46. Repository understanding should be progressive

First:

```text
repository snapshot
```

Then:

```text
target module
```

Then:

```text
target symbol
```

Then:

```text
dependencies
```

Then:

```text
tests
```

Only expand if necessary.

---

# 47. This creates an "information funnel"

```text
             Entire Repository
                    │
                    ▼
             Relevant Module
                    │
                    ▼
             Relevant Symbols
                    │
                    ▼
             Relevant Relations
                    │
                    ▼
             Relevant Code
                    │
                    ▼
             Relevant Evidence
                    │
                    ▼
                   Gemma
```

This is probably the single most important visual for Aljabr's local-first architecture.

---

# 48. RepositoryIntel + ContextEngine

These should cooperate but remain separate.

```text
RepositoryIntel
    answers:
    "What exists?"

ContextEngine
    answers:
    "What should the model see?"
```

That separation is excellent.

---

# 49. Example: user asks

> "Fix the bug where expired refresh tokens are accepted."

RepositoryIntel discovers:

```text
Test:
RefreshTokenTest.expiredToken

Symbol:
RefreshTokenValidator.validate

Caller:
RefreshService.refresh

Recent change:
RefreshTokenValidator.java changed yesterday
```

ContextEngine turns that into:

```text
TASK
Fix expired refresh token acceptance.

TARGET
RefreshTokenValidator.validate()

FAILURE
RefreshTokenTest.expiredToken

CALLER
RefreshService.refresh()

RECENT CHANGE
RefreshTokenValidator.java

RELEVANT CODE
...
```

Gemma receives perhaps 2–4K tokens instead of 50K.

---

# 50. And the best part

If the user switches from:

```text
Gemma 12B local
```

to:

```text
large cloud model
```

RepositoryIntel doesn't change.

Only ContextPolicy changes:

```text
local:
focused context

cloud:
focused + broader context
```

So your architecture stays stable.

---

# 51. Recommended implementation order

Don't build all of RepositoryIntel at once.

I'd do:

### Phase A — MVP

```text
FileIndex
SymbolIndex
RepositorySearch
```

### Phase B

```text
ReferenceIndex
CallGraph
TestIndex
```

### Phase C

```text
ModuleGraph
ConfigIndex
GitIntel
```

### Phase D

```text
SemanticSearch
EmbeddingIndex
```

### Phase E

```text
incremental indexing
cache
invalidation
background indexing
```

---

# 52. For your M4 16GB specifically

I'd initially avoid a heavyweight external database.

Use an embedded/local design:

```text
SQLite
+
filesystem artifacts
+
in-memory hot cache
```

or equivalent lightweight storage.

Conceptually:

```text
SQLite
 ├── files
 ├── symbols
 ├── references
 ├── tests
 ├── modules
 └── metadata

Filesystem
 └── large artifacts / source snapshots

Memory
 └── hot query results
```

That is plenty for an initial Aljabr.

---

# 53. Don't embed the entire repository

This is another trap.

For a local machine:

```text
repository = 2 GB
```

does **not** mean:

```text
2 GB embeddings
```

Start with:

```text
symbols
references
tests
```

and only semantically index relevant source regions.

---

# 54. Background indexing

Initial launch:

```text
Aljabr starts
      │
      ▼
quick filesystem scan
      │
      ▼
usable immediately
      │
      ├── background symbol indexing
      ├── background reference indexing
      └── background semantic indexing
```

Don't block the agent for 30 minutes before it can answer:

> "What file is this?"

---

# 55. Index readiness

Expose:

```java
public interface RepositoryReadiness {

    IndexReadiness status();
}
```

Example:

```text
FILES       READY
SYMBOLS     READY
REFERENCES  PARTIAL
TESTS       READY
SEMANTIC    BUILDING
```

Then TaskEngine knows what intelligence is available.

---

# 56. This connects back to `CapabilityDetector`

Capability is not only:

```text
"Can this model handle 8K?"
```

It is also:

```text
"Can the repository index answer this question?"
```

For example:

```text
call graph available?
coverage available?
LSP available?
semantic index ready?
git history available?
```

Then the planner can choose accordingly.

---

# 57. `RepositoryCapability`

```java
public record RepositoryCapability(
    boolean symbolIndex,
    boolean referenceIndex,
    boolean testIndex,
    boolean coverage,
    boolean semanticSearch,
    boolean gitHistory
) {}
```

This becomes part of task context.

---

# 58. The final intelligence stack

We're now getting something quite elegant:

```text
                 WAYANG
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
   Runtime       Policies      Memory
       │
       ▼
   TaskEngine
       │
       ├──────── ContextEngine
       │
       ├──────── RepositoryIntel
       │
       ├──────── SkillRegistry
       │
       ├──────── VerificationEngine
       │
       └──────── ReasoningRouter
                         │
                         ▼
                    Local Gemma
```

And underneath RepositoryIntel:

```text
Repository
   │
   ├── Files
   ├── Symbols
   ├── References
   ├── Tests
   ├── Modules
   ├── Config
   ├── Git
   └── Semantic Index
```

---

# 59. The next milestone

After RepositoryIntel, I would **not immediately jump into more agent intelligence**.

The next thing I'd build is:

## **`Workspace / Mutation / Patch Safety`**

Because now we have:

```text
RepositoryIntel
       ↓
Gemma understands code
       ↓
EditProposal
```

We need to make the final step extremely robust:

```text
EditProposal
      ↓
PatchValidator
      ↓
ConflictDetector
      ↓
MutationPolicy
      ↓
MutationEngine
      ↓
Workspace
      ↓
VerificationEngine
```

This should handle:

* stale edits
* overlapping edits
* accidental unrelated modifications
* partial patch application
* rollback
* atomicity
* user changes during execution
* generated files
* ignored files
* protected files
* patch size limits
* formatting changes
* diff scope enforcement

That milestone is particularly important because **the most dangerous thing a coding agent can do isn't reason incorrectly—it is apply a plausible-looking but overly broad mutation.**

So the next layer should make Aljabr **mutation-safe by construction**, rather than trusting Gemma to edit perfectly.




Absolutely. Let's continue with the next milestone:

# Milestone — Mutation Safety & Patch Engine

At this point Aljabr can:

```text
User task
   ↓
TaskEngine
   ↓
RepositoryIntel
   ↓
ContextEngine
   ↓
Gemma
   ↓
EditProposal
```

Now we need to make this boundary extremely safe:

```text
                 EditProposal
                      │
                      ▼
                PatchValidator
                      │
             ┌────────┴────────┐
             ▼                 ▼
       ConflictDetector   MutationPolicy
             │                 │
             └────────┬────────┘
                      ▼
                MutationEngine
                      │
                      ▼
                   Workspace
                      │
                      ▼
              VerificationEngine
```

The fundamental rule should be:

> **Gemma proposes mutations. Wayang owns mutations.**

Never let the model directly write arbitrary files.

---

# 1. The mutation pipeline

I'd implement the lifecycle as:

```text
PROPOSED
   ↓
VALIDATING
   ↓
VALIDATED
   ↓
APPLYING
   ↓
APPLIED
   ↓
VERIFYING
   ↓
COMMITTED
```

Failure at any stage:

```text
FAILED
   ↓
ROLLBACK
```

And importantly:

```text
PROPOSED
   ↓
REJECTED
```

is a normal outcome.

A proposal being rejected isn't an exceptional system failure.

---

# 2. `EditProposal`

Let's make the proposal richer than the earlier version.

```java
public record EditProposal(
    ProposalId id,
    TaskId taskId,
    WorkspaceSnapshot snapshot,
    String objective,
    List<FileEdit> edits,
    List<Invariant> invariants,
    List<EvidenceId> evidence,
    String rationale
) {}
```

The important addition is:

```java
WorkspaceSnapshot snapshot
```

The model is saying:

> "I created this patch against *this exact workspace state*."

---

# 3. File edit

```java
public record FileEdit(
    Path file,
    FileFingerprint expectedState,
    List<TextEdit> edits
) {}
```

And:

```java
public record TextEdit(
    SourceRange range,
    String expectedText,
    String replacement
) {}
```

That `expectedText` is extremely important.

Don't trust:

```text
line 72 → line 81
```

alone.

Instead:

```text
expected:
if (expiration < now)

replace:
if (expiration <= now)
```

---

# 4. Why expected text matters

Imagine Gemma inspected:

```java
if (expiration < now) {
    reject();
}
```

Then the user changes it:

```java
if (expiration <= now) {
    reject();
}
```

Gemma's patch still references the old text.

MutationEngine sees:

```text
expectedText != currentText
```

and refuses to apply.

That's **stale-context protection**.

---

# 5. `PatchValidator`

```java
public interface PatchValidator {

    Uni<PatchValidation> validate(
        EditProposal proposal,
        WorkspaceState workspace
    );
}
```

Result:

```java
public record PatchValidation(
    ValidationStatus status,
    List<PatchViolation> violations,
    List<PatchWarning> warnings
) {}
```

---

# 6. Validation should be layered

Don't make one giant validator.

Use:

```text
PatchValidator
 ├── SnapshotValidator
 ├── FileValidator
 ├── RangeValidator
 ├── ConflictValidator
 ├── ScopeValidator
 ├── PolicyValidator
 └── RiskValidator
```

Each has one responsibility.

---

# 7. Snapshot validation

First:

```text
proposal.snapshot
        vs
current workspace
```

If repository state changed unexpectedly:

```text
STALE_SNAPSHOT
```

But don't necessarily reject everything.

We can distinguish:

```text
SAFE_UNCHANGED
SAFE_UNRELATED_CHANGE
RELEVANT_CHANGE
CONFLICTING_CHANGE
```

---

# 8. This distinction matters

Suppose Aljabr is editing:

```text
src/auth/AuthService.java
```

while the user changes:

```text
README.md
```

That's probably safe.

But if the user changes:

```text
src/auth/AuthService.java
```

that's relevant.

So the system should not use:

```java
if (workspaceHash != proposalHash)
    reject();
```

blindly.

Instead:

```text
workspace changed
       ↓
determine affected files
       ↓
does mutation overlap?
```

---

# 9. `ConflictDetector`

```java
public interface ConflictDetector {

    Uni<ConflictResult> detect(
        EditProposal proposal,
        WorkspaceState current
    );
}
```

Possible:

```java
public enum ConflictStatus {
    NONE,
    UNRELATED_CHANGE,
    POTENTIAL_CONFLICT,
    DEFINITE_CONFLICT
}
```

---

# 10. File-level conflict

Easy case:

```text
Proposal:
AuthService.java

User:
README.md
```

No conflict.

---

# 11. Range-level conflict

More interesting:

Proposal changes:

```text
AuthService.java:40-55
```

User changes:

```text
AuthService.java:200-220
```

No overlap.

Potentially safe.

But:

```text
Proposal:
40-55

User:
48-62
```

That's a real conflict.

---

# 12. Don't automatically rebase model patches

At least initially.

It's tempting to build:

```text
old version
     ↓
model patch
     ↓
user changes
     ↓
automatic rebase
```

But for an AI coding agent this can become dangerous.

I'd initially do:

```text
conflict
 ↓
stop
 ↓
re-inspect
 ↓
new proposal
```

Safety beats cleverness.

---

# 13. `MutationPolicy`

Now we need policy enforcement.

```java
public interface MutationPolicy {

    PolicyDecision evaluate(
        EditProposal proposal,
        WorkspaceState workspace
    );
}
```

Examples:

```text
ALLOW
ALLOW_WITH_WARNING
REQUIRE_APPROVAL
DENY
```

---

# 14. Example policy

```yaml
mutation:
  max_files: 8
  max_changed_lines: 300

  protected:
    - ".env"
    - "*.pem"
    - "*.key"

  require_approval:
    - migrations
    - lockfiles
    - CI configuration

  deny:
    - ".git/**"
```

The exact defaults can evolve.

---

# 15. Don't hardcode "dangerous files" everywhere

Make this policy-driven.

For example:

```java
public record MutationPolicyConfig(
    Set<PathPattern> protectedPaths,
    Set<PathPattern> approvalPaths,
    int maxFiles,
    int maxChangedLines
) {}
```

Then users/projects can customize it.

---

# 16. Generated files

Another useful category:

```text
generated
```

Example:

```text
target/
build/
dist/
generated/
```

Aljabr shouldn't casually edit generated artifacts.

Policy:

```text
GENERATED_FILE
    ↓
DENY
```

or:

```text
ALLOW_IF_SOURCE_UNKNOWN
```

depending on project configuration.

---

# 17. Binary files

Do not let normal `TextEdit` touch:

```text
.png
.jpg
.pdf
.zip
```

The mutation system should detect:

```text
file type = binary
```

and reject ordinary text patches.

If binary modification becomes necessary, that's a separate capability.

---

# 18. Secrets

MutationPolicy should explicitly protect:

```text
.env
credentials
private keys
certificates
secret configuration
```

But don't rely only on filenames.

Also detect secret-like content.

For example:

```text
AWS_SECRET_ACCESS_KEY=...
PRIVATE KEY
```

should trigger protection.

---

# 19. Scope control

Suppose user asks:

> Fix the authentication bug.

Gemma proposes:

```text
47 files changed
```

That's suspicious.

`ScopeValidator` should compare:

```text
task scope
vs
patch scope
```

---

# 20. `ScopeValidator`

```java
public interface ScopeValidator {

    Uni<ScopeAssessment> assess(
        TaskContext task,
        EditProposal proposal
    );
}
```

Result:

```text
IN_SCOPE
BROAD
UNRELATED
```

For example:

```text
Task:
fix token expiration

Patch:
TokenValidator.java
RefreshTokenTest.java
```

reasonable.

But:

```text
Patch:
TokenValidator.java
RefreshTokenTest.java
README.md
package.json
Dockerfile
frontend/*
```

needs investigation.

---

# 21. Diff budget

For local Aljabr, I'd enforce a soft budget.

```text
small task:
~1-3 files

medium task:
~3-10 files

large task:
explicitly planned
```

Not as absolute limits, but as risk signals.

---

# 22. `RiskEngine`

This is worth making explicit.

```java
public interface RiskEngine {

    Uni<RiskAssessment> assess(
        EditProposal proposal
    );
}
```

Example:

```java
public record RiskAssessment(
    RiskLevel level,
    List<RiskFactor> factors
) {}
```

Factors:

```text
many files
large diff
protected file
dependency change
database migration
security-sensitive code
generated code
unknown language
low verification coverage
```

---

# 23. Risk should influence autonomy

This connects to Wayang's policy system.

```text
LOW
 ↓
automatic

MEDIUM
 ↓
automatic + verification

HIGH
 ↓
approval

CRITICAL
 ↓
deny / explicit confirmation
```

So Aljabr doesn't have one global:

```text
auto_edit = true
```

Instead autonomy is contextual.

---

# 24. `MutationTransaction`

Now the actual mutation layer.

```java
public interface MutationEngine {

    Uni<MutationTransaction> prepare(
        EditProposal proposal
    );

    Uni<MutationResult> commit(
        MutationTransaction transaction
    );

    Uni<Void> rollback(
        MutationTransaction transaction
    );
}
```

---

# 25. Prepare ≠ commit

This distinction is valuable.

```text
prepare
   ↓
validate
   ↓
construct resulting files
   ↓
calculate diff
   ↓
verify constraints
```

Only then:

```text
commit
```

---

# 26. Transaction state

```java
public enum MutationState {

    CREATED,
    PREPARED,
    VALIDATED,
    APPLYING,
    APPLIED,
    ROLLED_BACK,
    FAILED
}
```

---

# 27. Build the patch before writing

Never:

```text
edit file 1
edit file 2
edit file 3
then discover file 4 is invalid
```

Instead:

```text
proposal
 ↓
apply in memory
 ↓
validate entire result
 ↓
commit atomically
```

---

# 28. In-memory mutation

Conceptually:

```java
Map<Path, String> resultingFiles =
    patchEngine.preview(proposal);
```

Then validate:

```text
all files
all edits
all policies
all constraints
```

before disk mutation.

---

# 29. Atomic commit

For each file:

```text
current
   ↓
temporary file
   ↓
fsync if appropriate
   ↓
atomic rename
```

And maintain rollback information.

Exact filesystem semantics will vary by OS/filesystem, so isolate this behind:

```java
WorkspaceWriter
```

---

# 30. `WorkspaceWriter`

```java
public interface WorkspaceWriter {

    Uni<Void> write(
        Path path,
        String content
    );

    Uni<Void> delete(
        Path path
    );

    Uni<Void> move(
        Path source,
        Path destination
    );
}
```

MutationEngine shouldn't directly manipulate filesystem primitives everywhere.

---

# 31. Rollback

Every mutation should record:

```text
before hash
before content/artifact reference
after hash
after content/artifact reference
```

So:

```text
MutationRecord
```

can reconstruct the previous state.

---

# 32. Don't depend entirely on Git for rollback

Git is useful, but:

```text
not every workspace is clean
not every workspace is a Git repository
user may have unrelated modifications
```

Therefore Aljabr needs its own mutation transaction.

Git is supplemental.

---

# 33. `MutationRecord`

```java
public record MutationRecord(
    MutationId id,
    TaskId taskId,
    WorkspaceSnapshot before,
    WorkspaceSnapshot after,
    List<FileMutation> files,
    Instant timestamp
) {}
```

---

# 34. User changes must remain untouched

Suppose:

```text
before:
AuthService.java user-modified
```

Aljabr changes another section.

Rollback should restore **Aljabr's change**, not blindly restore the whole file to some historical copy.

This is why range-aware mutation records matter.

---

# 35. Verification after mutation

Now connect:

```text
MutationEngine
      ↓
VerificationEngine
```

But don't always run the full test suite.

Use RepositoryIntel.

```text
changed symbols
      ↓
affected tests
      ↓
targeted verification
```

---

# 36. Verification ladder

I'd define:

```text
LEVEL 0
syntax / patch validity

LEVEL 1
format / lint

LEVEL 2
targeted tests

LEVEL 3
affected tests

LEVEL 4
module tests

LEVEL 5
full test suite
```

Then VerificationPolicy chooses the level.

---

# 37. Example

Simple one-line method change:

```text
patch validity
 ↓
compile
 ↓
targeted test
```

No need to run:

```text
2-hour integration suite
```

---

# 38. But failure should escalate

Suppose:

```text
targeted test
    ↓
FAIL
```

Then:

```text
FailureAnalyzer
    ↓
expand context
    ↓
new hypothesis
```

Potentially:

```text
affected tests
```

Then:

```text
module tests
```

This becomes adaptive verification.

---

# 39. Mutation + Verification loop

This gives Aljabr its core coding loop:

```text
UNDERSTAND
    ↓
PLAN
    ↓
PROPOSE
    ↓
VALIDATE
    ↓
MUTATE
    ↓
VERIFY
    ↓
    ├── PASS → DONE
    │
    └── FAIL
          ↓
       ANALYZE
          ↓
        REPAIR
          ↓
       PROPOSE
```

That's the real coding-agent loop.

---

# 40. Put a hard loop budget on it

For local Gemma:

```yaml
repair:
  max_attempts: 3
```

Maybe:

```text
attempt 1
attempt 2
attempt 3
```

then:

```text
STOP
```

Don't let:

```text
Gemma
 ↓
failure
 ↓
Gemma
 ↓
failure
 ↓
Gemma
 ↓
...
```

run indefinitely.

---

# 41. Each attempt should have a reason

```java
public record RepairAttempt(
    int number,
    FailureSummary failure,
    Hypothesis hypothesis,
    EditProposal proposal,
    VerificationResult verification
) {}
```

This gives you an auditable reasoning trajectory without needing to preserve giant model transcripts.

---

# 42. This is also where your earlier cache architecture becomes useful

Cache:

```text
repository evidence
search results
symbol resolution
test selection
verification results
failure summaries
```

Then a repair attempt can reuse:

```text
previous evidence
```

instead of repeating expensive discovery.

---

# 43. But don't cache stale truth

Every evidence item should carry:

```java
public record Evidence(
    EvidenceId id,
    WorkspaceSnapshot snapshot,
    EvidenceType type,
    Object data
) {}
```

Then:

```text
evidence.snapshot == current snapshot
```

is valid.

Otherwise:

```text
STALE
```

and RepositoryIntel refreshes it.

---

# 44. This is a major architectural principle

> **Everything that describes code should be snapshot-aware.**

That includes:

```text
SearchResult
SymbolInfo
Reference
TestSelection
EditProposal
ReviewResult
FailureAnalysis
```

Not necessarily every object physically stores the full snapshot, but it should be traceable to one.

---

# 45. Now Aljabr has a very strong safety boundary

```text
                Gemma
                  │
                  │ proposal
                  ▼
             EditProposal
                  │
                  ▼
          ┌───────────────┐
          │  Safety Gate  │
          │               │
          │ stale?        │
          │ conflict?     │
          │ scope?        │
          │ risk?         │
          │ policy?       │
          └───────┬───────┘
                  │
             approved
                  │
                  ▼
           MutationEngine
                  │
                  ▼
              Workspace
                  │
                  ▼
          VerificationEngine
```

This is much safer than:

```text
LLM → shell → hope
```

---

# 46. One more important thing: never make `edit_file` the primary model tool

I would **not** give Gemma a giant tool like:

```text
edit_file(path, content)
```

as its main mutation mechanism.

Instead expose something closer to:

```text
propose_edit
```

The model produces structured intent.

Wayang performs the actual edit.

---

# 47. Model-facing tool

Something like:

```json
{
  "tool": "code.propose_edit",
  "arguments": {
    "file": "src/auth/TokenValidator.java",
    "target": "validateExpiration",
    "expected": "...",
    "replacement": "...",
    "reason": "Reject tokens at expiration boundary"
  }
}
```

Wayang receives it.

Then:

```text
validate
 ↓
risk
 ↓
policy
 ↓
apply
```

---

# 48. Even better: symbolic target

Eventually support:

```json
{
  "target": {
    "symbol": "TokenValidator.validateExpiration"
  }
}
```

rather than:

```json
{
  "line_start": 72,
  "line_end": 81
}
```

RepositoryIntel resolves the symbol.

This makes the model's intent more stable.

---

# 49. But preserve textual anchors too

Don't trust symbolic resolution alone.

Best:

```text
symbol:
TokenValidator.validateExpiration

range:
72-81

expected:
exact source text

snapshot:
abc123
```

Multiple safety anchors.

---

# 50. Local model optimization

This whole architecture also helps Gemma quality.

Instead of asking:

> "Modify this file correctly."

Gemma receives:

```text
TASK
...

TARGET SYMBOL
...

CURRENT IMPLEMENTATION
...

DEPENDENCIES
...

TESTS
...

CONSTRAINTS
...

EXPECTED CHANGE
...
```

and produces:

```text
small structured proposal
```

rather than:

```text
huge free-form answer
```

So we're simultaneously reducing:

* input tokens
* output tokens
* ambiguity
* hallucination surface
* accidental mutation

without necessarily reducing semantic quality.

That's exactly the direction you want for Aljabr.

---

# 51. Suggested package structure

I'd now organize the coding runtime approximately like:

```text
aljabr/
├── task/
│   ├── TaskEngine
│   ├── TaskState
│   └── TaskCheckpoint
│
├── skill/
│   ├── Skill
│   ├── SkillRegistry
│   └── SkillRouter
│
├── repository/
│   ├── RepositoryIntel
│   ├── WorkspaceIndexer
│   ├── FileIndex
│   ├── SymbolIndex
│   ├── ReferenceIndex
│   ├── TestIndex
│   ├── ModuleGraph
│   └── GitIntel
│
├── context/
│   ├── ContextEngine
│   ├── ContextController
│   ├── ContextPolicy
│   └── Evidence
│
├── reasoning/
│   ├── ReasoningRouter
│   ├── ReasoningGate
│   └── ModelAdapter
│
├── mutation/
│   ├── EditProposal
│   ├── PatchValidator
│   ├── ConflictDetector
│   ├── ScopeValidator
│   ├── MutationPolicy
│   ├── RiskEngine
│   ├── MutationEngine
│   └── WorkspaceWriter
│
├── verification/
│   ├── VerificationEngine
│   ├── TestSelector
│   ├── VerificationPolicy
│   └── VerificationResult
│
└── repair/
    ├── FailureAnalyzer
    ├── HypothesisEngine
    └── RepairEngine
```

---

# 52. The milestone after this

Once this is implemented, Aljabr has the machinery to **safely modify code**.

The next major problem is:

## **`FailureAnalyzer → HypothesisEngine → RepairEngine`**

That's where we make Aljabr substantially better at the thing users actually care about:

> **"It didn't work. Figure out why and fix it."**

Rather than simply:

```text
test failed
→ ask Gemma to try again
```

we'll build a structured failure model:

```text
                    VerificationResult
                           │
                           ▼
                    FailureAnalyzer
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
          compiler      test         runtime
           failure      failure       failure
              │            │            │
              └────────────┼────────────┘
                           ▼
                    FailureEvidence
                           │
                           ▼
                    HypothesisEngine
                           │
                  ┌────────┴────────┐
                  ▼                 ▼
              hypothesis A      hypothesis B
                  │                 │
                  └────────┬────────┘
                           ▼
                     RepairEngine
                           │
                           ▼
                     EditProposal
```

That is the point where Aljabr starts becoming a **real autonomous coding system**, rather than an LLM wrapped around file tools.



Absolutely. The next milestone is the **Failure → Diagnosis → Repair loop**.

This is where Aljabr starts moving beyond "LLM that edits code" toward a proper coding agent.

# Milestone — `FailureAnalyzer → HypothesisEngine → RepairEngine`

The core loop becomes:

```text
Task
  ↓
Understand
  ↓
Plan
  ↓
Propose
  ↓
Validate
  ↓
Mutate
  ↓
Verify
  │
  ├── PASS ───────────────► DONE
  │
  └── FAIL
       ↓
   FailureAnalyzer
       ↓
   FailureEvidence
       ↓
   HypothesisEngine
       ↓
   RepairPlan
       ↓
   ContextEngine
       ↓
   Gemma
       ↓
   EditProposal
       ↓
   ...
```

The important idea:

> **A test failure is evidence, not a prompt to blindly try another patch.**

---

# 1. Start with a structured failure model

Don't send raw terminal output directly to Gemma.

Create:

```java
public record FailureReport(
    FailureId id,
    TaskId taskId,
    MutationId mutationId,
    WorkspaceSnapshot snapshot,
    FailureType type,
    FailureSeverity severity,
    String summary,
    List<FailureEvidence> evidence,
    List<FailureLocation> locations,
    List<String> rawOutputRefs
) {}
```

For example:

```text
type:
TEST_FAILURE

summary:
Expected expired token to be rejected,
but request was accepted.

locations:
RefreshTokenTest.java:84
TokenValidator.java:51
```

---

# 2. Failure types

Start small:

```java
public enum FailureType {

    COMPILE_ERROR,

    TYPE_ERROR,

    LINT_ERROR,

    TEST_FAILURE,

    ASSERTION_FAILURE,

    RUNTIME_EXCEPTION,

    TIMEOUT,

    CRASH,

    BUILD_FAILURE,

    DEPENDENCY_FAILURE,

    UNKNOWN
}
```

Later you can add language-specific categories.

---

# 3. Separate failure classification from failure diagnosis

This is important.

Don't make one model call:

```text
"Here is 20,000 lines of logs.
Tell me what happened."
```

Instead:

```text
raw output
   ↓
FailureClassifier
   ↓
FailureType
   ↓
FailureAnalyzer
   ↓
FailureEvidence
```

Classification should preferably be deterministic.

---

# 4. `FailureClassifier`

```java
public interface FailureClassifier {

    Uni<FailureClassification> classify(
        ExecutionResult result
    );
}
```

Example:

```java
public record FailureClassification(
    FailureType type,
    double confidence,
    List<String> indicators
) {}
```

---

# 5. Deterministic first

For example:

```text
"cannot find symbol"
        ↓
COMPILE_ERROR

"AssertionError"
        ↓
ASSERTION_FAILURE

"NullPointerException"
        ↓
RUNTIME_EXCEPTION

"timeout"
        ↓
TIMEOUT
```

No LLM needed.

This is especially valuable on your local model.

---

# 6. Then normalize the failure

Different tools produce wildly different output.

For example:

```text
Gradle
Maven
npm
pytest
cargo
go test
```

should eventually become:

```java
public record NormalizedFailure(
    FailureType type,
    String message,
    Path file,
    Optional<SourceRange> range,
    Optional<String> expected,
    Optional<String> actual,
    List<StackFrame> stack
) {}
```

Now the rest of Aljabr doesn't care whether the failure came from Gradle or pytest.

---

# 7. `FailureEvidence`

This is the important layer.

```java
public record FailureEvidence(
    EvidenceId id,
    EvidenceType type,
    String description,
    double confidence,
    List<EvidenceSource> sources
) {}
```

Possible evidence:

```text
ASSERTION
STACK_TRACE
COMPILER_MESSAGE
SOURCE_LOCATION
RECENT_CHANGE
DEPENDENCY
TEST_RESULT
LOG_LINE
```

---

# 8. Example

Raw failure:

```text
Expected:
false

Actual:
true
```

FailureAnalyzer can produce:

```text
Failure:
expired token accepted

Evidence:
1. RefreshTokenTest.expiredToken failed
2. expected validator result = false
3. actual validator result = true
4. TokenValidator.validateExpiration() is involved
5. validateExpiration() changed in current mutation
```

That's dramatically easier for Gemma to reason about.

---

# 9. `FailureAnalyzer`

```java
public interface FailureAnalyzer {

    Uni<FailureAnalysis> analyze(
        FailureReport failure
    );
}
```

Result:

```java
public record FailureAnalysis(
    FailureReport failure,
    List<FailureEvidence> evidence,
    List<Suspect> suspects,
    List<AnalysisQuestion> unresolved
) {}
```

---

# 10. Suspects are better than conclusions

Don't immediately say:

```text
"The bug is TokenValidator.java."
```

Instead:

```text
Suspect #1
TokenValidator.validateExpiration()
confidence: 0.82

Suspect #2
RefreshService.refresh()
confidence: 0.37
```

This is important because diagnosis is uncertain.

---

# 11. `Suspect`

```java
public record Suspect(
    SymbolRef symbol,
    SuspectReason reason,
    double confidence,
    List<EvidenceId> evidence
) {}
```

Reasons:

```java
public enum SuspectReason {

    DIRECT_FAILURE_LOCATION,
    RECENTLY_CHANGED,
    CALLER,
    CALLEE,
    ASSERTION_MISMATCH,
    EXCEPTION_SOURCE,
    DATA_FLOW,
    CONFIGURATION,
    DEPENDENCY
}
```

---

# 12. RepositoryIntel now becomes extremely useful

Suppose:

```text
Test failed
   ↓
TokenValidator.validateExpiration()
```

RepositoryIntel can expand:

```text
TokenValidator.validateExpiration()
        │
        ├── callers
        ├── callees
        ├── tests
        ├── recent changes
        └── surrounding symbols
```

But **only after the failure tells us where to look**.

This is progressive investigation.

---

# 13. Don't dump the entire call graph

For local Gemma:

```text
maxDepth = 2
maxNodes = 20
```

Then expand only if the evidence remains ambiguous.

---

# 14. Hypothesis Engine

Now we need:

```java
public interface HypothesisEngine {

    Uni<HypothesisSet> generate(
        FailureAnalysis analysis
    );
}
```

Result:

```java
public record HypothesisSet(
    List<Hypothesis> hypotheses
) {}
```

---

# 15. `Hypothesis`

```java
public record Hypothesis(
    HypothesisId id,
    String statement,
    double confidence,
    List<EvidenceId> supportingEvidence,
    List<EvidenceId> contradictingEvidence,
    List<DiagnosticAction> nextActions
) {}
```

Example:

```text
H1:
Expiration comparison is incorrect.

confidence: 0.86

support:
- failing assertion
- changed validator
- direct call path

next:
inspect validateExpiration()
```

---

# 16. Don't ask Gemma to generate 20 hypotheses

For a 12B local model, that's unnecessary.

Start with:

```text
1–3 hypotheses
```

Then rank them.

---

# 17. Hypothesis ranking

Something like:

```text
score =
    evidenceSupport
  + locationRelevance
  + recentChange
  + testCoverage
  - contradiction
  - complexity
```

You don't need sophisticated ML initially.

A deterministic scoring function is enough.

---

# 18. `HypothesisRanker`

```java
public interface HypothesisRanker {

    List<Hypothesis> rank(
        List<Hypothesis> hypotheses
    );
}
```

Then:

```text
H1  0.91
H2  0.63
H3  0.28
```

Only feed the top 1–2 to Gemma.

---

# 19. The crucial part: hypotheses should be testable

Bad:

```text
"The authentication logic seems wrong."
```

Good:

```text
"The expiration boundary uses `<` instead of `<=`,
causing tokens expiring at exactly `now` to remain valid."
```

Even better:

```text
Prediction:
Changing `<` to `<=` should make
RefreshTokenTest.expiredToken pass.
```

That's a real hypothesis.

---

# 20. `DiagnosticAction`

A hypothesis can request more evidence.

```java
public sealed interface DiagnosticAction {

    record InspectSymbol(SymbolRef symbol)
        implements DiagnosticAction {}

    record SearchRepository(SearchQuery query)
        implements DiagnosticAction {}

    record TraceCallGraph(TraceRequest request)
        implements DiagnosticAction {}

    record InspectTest(TestId test)
        implements DiagnosticAction {}

    record InspectDiff(Path file)
        implements DiagnosticAction {}

    record RunTest(TestSelector selector)
        implements DiagnosticAction {}
}
```

This is powerful.

Instead of Gemma hallucinating what to inspect, the system can gather targeted evidence.

---

# 21. Diagnosis becomes an investigation loop

```text
Failure
  ↓
Classify
  ↓
Extract evidence
  ↓
Generate hypothesis
  ↓
Need more evidence?
  │
  ├── yes → DiagnosticAction
  │          ↓
  │       RepositoryIntel
  │          ↓
  │       update hypothesis
  │
  └── no
       ↓
     Repair
```

---

# 22. Give this loop a budget

Absolutely critical for local inference.

```yaml
diagnosis:
  max_rounds: 3
  max_actions: 8
  max_hypotheses: 3
```

So:

```text
Round 1
  inspect failure

Round 2
  inspect suspect

Round 3
  verify hypothesis

STOP
```

No endless thinking.

---

# 23. RepairEngine

Now:

```java
public interface RepairEngine {

    Uni<RepairPlan> plan(
        FailureAnalysis analysis,
        Hypothesis hypothesis
    );
}
```

---

# 24. `RepairPlan`

```java
public record RepairPlan(
    RepairId id,
    HypothesisId hypothesis,
    String objective,
    List<RepairStep> steps,
    VerificationPlan verification
) {}
```

Example:

```text
Objective:
Correct expiration boundary.

Steps:
1. Modify TokenValidator.validateExpiration()
2. Add boundary regression test
3. Run targeted token tests
```

---

# 25. This is different from `EditProposal`

Important distinction:

```text
Hypothesis
    ↓
RepairPlan
    ↓
EditProposal
```

A plan says:

> **What should we do?**

A proposal says:

> **Here is the exact mutation.**

---

# 26. Example

```text
Hypothesis:

expiration boundary is wrong.

RepairPlan:

1. change comparison
2. add regression test
3. run RefreshTokenTest

EditProposal:

TokenValidator.java
    line 51
    < → <=

RefreshTokenTest.java
    add expired-at-boundary case
```

---

# 27. This makes the model's job much smaller

Instead of:

```text
"Understand this entire repository,
diagnose this failure,
figure out a fix,
modify everything,
and make sure it works."
```

Gemma gets:

```text
FAILURE
Expired token accepted.

HYPOTHESIS
Boundary comparison is incorrect.

TARGET
TokenValidator.validateExpiration()

EVIDENCE
...

CONSTRAINT
Do not modify unrelated authentication logic.

REQUEST
Generate a minimal patch.
```

That's exactly the kind of task where a 12B local model can perform much better.

---

# 28. Minimal patch principle

Add a first-class policy:

```java
public enum RepairStrategy {

    MINIMAL,
    TARGETED,
    BROAD
}
```

Default:

```text
MINIMAL
```

The agent should prefer:

```text
1 file
1 method
1 test
```

over:

```text
refactor authentication architecture
```

unless the evidence requires it.

---

# 29. Don't let "clean code" derail bug fixing

This is a common coding-agent failure.

User asks:

> fix null handling.

Model decides:

```text
refactor repository
introduce abstraction
rename 40 classes
```

Aljabr should detect:

```text
scope expansion
```

and reject or ask for approval.

---

# 30. Repair verification should test the hypothesis

This is a very important improvement.

Don't merely ask:

> "Did the tests pass?"

Also ask:

> "Did the predicted behavior change in the expected way?"

For example:

```text
Hypothesis:
expired token should be rejected.

Prediction:
RefreshTokenTest.expiredToken passes.

Observed:
PASS
```

Confidence in the diagnosis increases.

---

# 31. `HypothesisVerdict`

```java
public enum HypothesisVerdict {

    CONFIRMED,
    DISPROVED,
    INCONCLUSIVE
}
```

Then:

```java
public record HypothesisResult(
    Hypothesis hypothesis,
    HypothesisVerdict verdict,
    List<EvidenceId> evidence
) {}
```

---

# 32. Failed repair doesn't mean failed agent

Suppose:

```text
H1
expiration comparison
```

repair fails.

That gives evidence:

```text
H1 weakened
```

Then:

```text
H2
timezone normalization
```

can become more likely.

This is much better than:

```text
try random patch #2
```

---

# 33. Repair attempt state

```java
public record RepairAttempt(
    int number,
    Hypothesis hypothesis,
    RepairPlan plan,
    EditProposal proposal,
    MutationResult mutation,
    VerificationResult verification,
    HypothesisVerdict verdict
) {}
```

Now the entire repair trajectory is structured.

---

# 34. Checkpoint after every attempt

This ties directly into your earlier checkpoint architecture.

```text
TaskCheckpoint
 ├── workspace snapshot
 ├── evidence
 ├── hypotheses
 ├── repair attempts
 ├── verification
 └── next action
```

If the process dies:

```text
Mac sleeps
network disappears
model crashes
process terminates
```

Aljabr resumes from the checkpoint.

No need to rediscover everything.

---

# 35. Local inference failure

This is especially relevant for your M4 setup.

Suppose Gemma inference crashes halfway through.

Don't lose:

```text
FailureAnalysis
HypothesisSet
Repository evidence
```

Persist those before model invocation whenever practical.

Then:

```text
restart
 ↓
load checkpoint
 ↓
resume model call
```

---

# 36. Model invocation should itself be checkpointed

Something like:

```java
public record ModelAttempt(
    String requestHash,
    ModelCapability capability,
    ContextSnapshot context,
    Instant startedAt,
    Optional<String> responseArtifact>
) {}
```

This also prevents accidental duplicate expensive inference.

---

# 37. Important: don't cache model output blindly

Cache should be keyed by:

```text
task
+
workspace snapshot
+
hypothesis
+
context
+
model
+
generation policy
```

So:

```text
same state → reusable
different state → stale
```

---

# 38. Failure loop and ContextEngine

This is where your earlier ContextController becomes useful.

Initial context:

```text
small
```

Failure occurs.

Instead of dumping more context immediately:

```text
ContextController
      ↓
why did it fail?
      ↓
what evidence is missing?
```

Then increase context **only in the relevant dimension**.

---

# 39. Context expansion strategy

For example:

```text
Attempt 1:
target method

failure

Attempt 2:
target method
+ caller

failure

Attempt 3:
target method
+ caller
+ test
+ recent diff
```

Not:

```text
entire repository
```

This is exactly the adaptive context strategy we discussed earlier.

---

# 40. Context budget

For your local Gemma:

```text
failure diagnosis:
~1–2K tokens

hypothesis:
~500–1K

repair:
~2–4K
```

These are starting policy targets, not hard limits.

The important thing is:

> **Context grows because evidence demands it, not because more context is always better.**

---

# 41. Cloud model behavior

With a larger model/context budget:

```text
ContextPolicy
    ↓
allow broader evidence
```

For example:

```text
local:
target → caller → test

large cloud:
target → caller → test → module → related architecture
```

Same RepositoryIntel.

Different ContextPolicy.

This preserves your architecture.

---

# 42. Failure memory

Now we can define useful short-term memory:

```text
TaskMemory
 ├── failure reports
 ├── evidence
 ├── hypotheses
 ├── rejected hypotheses
 ├── successful repairs
 └── verification results
```

But don't put all of this into model context.

Memory is storage.

Context is selection.

---

# 43. Rejected hypotheses are especially valuable

Example:

```text
H1:
expiration boundary
→ disproved

H2:
timezone conversion
→ confirmed
```

Don't make Gemma rediscover H1.

Store:

```text
DO_NOT_RETRY:
expiration comparison hypothesis
```

unless workspace state changes.

---

# 44. This gives you an explicit anti-loop mechanism

```text
RepairAttempt 1
  H1 → failed

RepairAttempt 2
  H1 → prohibited

RepairAttempt 2
  H2 → failed

RepairAttempt 3
  H3 → investigate
```

This is far better than repeated LLM calls with slightly different prompts.

---

# 45. Failure signatures

We can also fingerprint failures.

```java
public record FailureSignature(
    FailureType type,
    String normalizedMessage,
    Optional<Path> location,
    Optional<String> testName
) {}
```

Then:

```text
same signature
+
same snapshot
```

means:

```text
don't rerun identical diagnosis
```

---

# 46. Example

Attempt 1:

```text
RefreshTokenTest.expiredToken
Expected false, actual true
```

Patch fails.

Attempt 2 produces the exact same failure.

Aljabr recognizes:

```text
same failure signature
same hypothesis
same relevant code
```

and prevents:

```text
"try the same thing again"
```

---

# 47. `RepairLoopController`

Put the orchestration here:

```java
public interface RepairLoopController {

    Uni<RepairOutcome> execute(
        TaskContext task
    );
}
```

Implementation conceptually:

```java
while (attempts < maxAttempts) {

    VerificationResult result = verify();

    if (result.success()) {
        return success();
    }

    FailureAnalysis analysis =
        analyze(result);

    Hypothesis hypothesis =
        selectHypothesis(analysis);

    RepairPlan plan =
        planRepair(hypothesis);

    EditProposal proposal =
        generateProposal(plan);

    MutationResult mutation =
        mutate(proposal);

    if (!mutation.success()) {
        continue;
    }
}

return exhausted();
```

But the actual implementation should be event/state-machine driven rather than a giant loop.

---

# 48. Prefer state machine

Given Wayang's architecture, I'd model:

```text
TaskState
```

with states such as:

```text
UNDERSTANDING
PLANNING
PROPOSING
MUTATING
VERIFYING
ANALYZING_FAILURE
FORMING_HYPOTHESIS
REPAIRING
COMPLETED
FAILED
BLOCKED
```

Then transitions are explicit.

---

# 49. Why this matters

You get:

* resumability
* observability
* checkpoints
* cancellation
* retry control
* policy enforcement
* deterministic lifecycle

without burying everything inside an agent loop.

---

# 50. The complete Aljabr loop is now becoming clear

```text
                         USER
                           │
                           ▼
                      TaskEngine
                           │
                           ▼
                    Task Understanding
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
                     EditProposal
                           │
                           ▼
                    Mutation Safety
                           │
                           ▼
                     MutationEngine
                           │
                           ▼
                       Workspace
                           │
                           ▼
                  VerificationEngine
                     │           │
                   PASS         FAIL
                     │           │
                     ▼           ▼
                    DONE    FailureAnalyzer
                                │
                                ▼
                         HypothesisEngine
                                │
                                ▼
                          RepairEngine
                                │
                                ▼
                           ContextEngine
                                │
                                ▼
                              Gemma
                                │
                                └───────► ...
```

That's the core **Aljabr autonomous repair architecture**.

---

# 51. What I would implement first

Don't implement the whole thing immediately.

### Step 1

```text
FailureClassifier
FailureReport
NormalizedFailure
```

### Step 2

```text
FailureAnalyzer
FailureEvidence
Suspect
```

### Step 3

```text
Hypothesis
HypothesisEngine
HypothesisRanker
```

### Step 4

```text
RepairPlan
RepairEngine
```

### Step 5

```text
RepairLoopController
```

### Step 6

```text
TaskCheckpoint integration
```

### Step 7

```text
Failure fingerprint / anti-loop
```

---

# 52. One architectural refinement I'd make now

At this stage, I'd introduce a generic concept:

```text
Evidence
```

because it will unify almost everything we've designed.

```java
public interface Evidence {
    EvidenceId id();
    EvidenceType type();
    WorkspaceSnapshot snapshot();
    double confidence();
}
```

Then:

```text
Repository evidence
      │
      ├── source
      ├── symbol
      ├── reference
      ├── test
      ├── git
      └── config

Execution evidence
      │
      ├── compiler
      ├── test
      ├── runtime
      ├── log
      └── stacktrace

Agent evidence
      │
      ├── hypothesis
      ├── observation
      └── verification
```

And ContextEngine becomes:

```text
EvidenceStore
      ↓
EvidenceSelector
      ↓
EvidenceCompressor
      ↓
ContextPolicy
      ↓
ModelContext
```

That is a **very strong foundation for your local-first design**.

The model doesn't need to "remember everything." Wayang remembers the structured evidence and gives Gemma the **smallest sufficient evidence set** for the current decision.

---

## Next milestone

The next layer I'd tackle is **`ExecutionEngine + Tool Orchestration`**.

That means going below verification and making Aljabr capable of safely doing things like:

```text
discover build system
       ↓
select command
       ↓
execute sandboxed command
       ↓
stream output
       ↓
parse result
       ↓
checkpoint
       ↓
stop/timeout/cancel
       ↓
produce structured ExecutionResult
```

including a proper **tool budget, timeout policy, process isolation, command allow/deny policy, environment handling, and output compression**.

That layer is critical because a coding agent isn't only editing files—it needs to **compile, test, inspect, run, diagnose, and sometimes execute project tooling** without giving a 12B local model uncontrolled shell access.


