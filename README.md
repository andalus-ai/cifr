# Aljabr (الجبر) — Autonomous AI Coding & Engineering Substrate

> **Aljabr (Algebra / Restoration)** is a local-first, autonomous software engineering platform built on the Wayang Platform framework. It synthesizes the interactive ergonomics of **Codex / Cursor**, the autonomous planning and verification rigor of **Google Antigravity**, and the deterministic tool orchestration and execution safety of **Claude Code**.

---

## 🌟 Executive Synthesis: Codex + Antigravity + Claude Code

Aljabr bridges the gap between interactive IDE assistants and fully autonomous agent loops:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                ALJABR UNIFIED SUBSTRATE                                │
├─────────────────────────┬─────────────────────────────┬────────────────────────────────┤
│   CODEX / CURSOR UX     │     ANTIGRAVITY RIGOR       │        CLAUDE CODE SAFETY      │
├─────────────────────────┼─────────────────────────────┼────────────────────────────────┤
│ • Side-by-side Diff     │ • Adaptive Reasoning Gate   │ • Semantic Tool Orchestrator   │
│ • Context Chips (@file) │ • Graduated Verify Ladder   │ • Confined Process Sandbox     │
│ • Real-time Streaming   │ • Closed-Loop Self-Repair   │ • Human-in-the-Loop Gateways   │
│ • Line-level Jump-links │ • Structured Step Checklist │ • Deterministic Diagnostics    │
│ • Problems / Sonar Tab  │ • Transactional Rollbacks   │ • Multi-Language Auto-Detect   │
└─────────────────────────┴─────────────────────────────┴────────────────────────────────┘
```

---

## 🏛 Architecture & Building Blocks

Aljabr operates across three coordinated tiers:
1. **Frontend Tier (`aljabr_gui`)**: A desktop IDE interface written in Flutter (Riverpod state management) providing side-by-side Code/Diff/Terminal/Problems views, context pinning (`@` mentions), live plan tracking, and verification status badges.
2. **Backend Engine Tier (`aljabr-engine` & `aljabr-api`)**: A reactive Quarkus service (Java 25) implementing the cognitive lifecycle: Reasoning Gate $\to$ Context Planning $\to$ Mutation Engine $\to$ Verification Ladder $\to$ Closed-Loop Autonomous Repair.
3. **LSP & Tool Substrate (`muqabalah`)**: A headless LSP and Debug Adapter Protocol (DAP) kernel managing language servers, symbol tables, Open VSX extensions, and AST indexers.

```mermaid
graph TB
    subgraph Frontend ["Desktop GUI (Flutter / Riverpod)"]
        UI_Chat["Chat & Plan Stream"]
        UI_Editor["Code Editor & Line Highlighting"]
        UI_Diff["Side-by-Side Diff Inspector"]
        UI_Terminal["Interactive Terminal Shell"]
        UI_Problems["Problems & Sonar Quality Gate"]
    end

    subgraph BackendEngine ["Aljabr Backend Engine (Java 25 / Quarkus)"]
        RG["Reasoning Gate & Skill Router"]
        CE["Context Engine & Budget Planning"]
        ME["Transactional Mutation Engine"]
        VE["Verification Engine (L0-L5 Ladder)"]
        CL["Closed-Loop Autonomous Repair"]
        TO["Semantic Tool Orchestrator"]
        WI["Workspace Indexer & Symbol Graph"]
    end

    subgraph Substrate ["Infrastructure & Toolchains"]
        LSP["Muqabalah LSP Kernel (gRPC / WS)"]
        TC["Multi-Language Toolchains (Maven, CMake, SwiftPM, Xcode, Flutter, Sonar)"]
        SANDBOX["Confined Process Sandbox & Tool Policy"]
    end

    UI_Chat <-->|gRPC / Stream| BackendEngine
    UI_Problems <-->|State Notifiers| BackendEngine
    UI_Editor <-->|File Buffers| BackendEngine
    
    BackendEngine <-->|Language Server Protocol| LSP
    TO -->|Sandboxed Execution| TC
    TO -->|Security Gates| SANDBOX
    CL -->|Auto Repair Loop| VE
    VE -->|Test / Diagnostic Feedback| CE
```

---

## 🧩 Component Specifications

```mermaid
classDiagram
    class ReasoningGate {
        +evaluate(TaskContext) ReasoningDecision
    }
    class WorkspaceIndexer {
        +index(Path) WorkspaceKnowledge
    }
    class ContextEngine {
        +planContext(Task, Budget) ContextPlan
        +assemble(ContextPlan) ContextPacket
    }
    class MutationEngine {
        +apply(EditProposal, Path) WorkspaceFingerprint
        +rollback(WorkspaceFingerprint) void
    }
    class VerificationEngine {
        +verify(Fingerprint, Proposal, Level) VerificationResult
        +runTargetedTests(String, Path) VerificationResult
    }
    class ClosedLoopCoder {
        +executeLoop(ContextPlan, maxAttempts) RepairResult
    }
    class ToolOrchestrator {
        +executeIntent(ToolIntent, Path) ExecutionResult
    }
    class OutputProcessor {
        +process(exitCode, stdout, stderr) ProcessedOutput
    }

    ReasoningGate --> ContextEngine
    ContextEngine --> ClosedLoopCoder
    ClosedLoopCoder --> MutationEngine
    ClosedLoopCoder --> VerificationEngine
    ClosedLoopCoder --> ToolOrchestrator
    ToolOrchestrator --> OutputProcessor
    WorkspaceIndexer --> ContextEngine
```

| Component | Responsibility | Key Classes / Interfaces |
| :--- | :--- | :--- |
| **Reasoning Gate** | Analyzes task complexity and dynamically selects whether to use direct reactive tools, single-step execution, or multi-step autonomous planning. | [`ReasoningGate`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/skill/routing/ReasoningGate.java), [`SkillSelector`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/skill/routing/SkillSelector.java) |
| **Workspace Indexer** | Parses ASTs, indexes symbol definitions, references, call graphs, and dependency relationships across workspace files. | [`WorkspaceIndexer`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/index/indexer/WorkspaceIndexer.java), [`RelationIndex`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/index/RelationIndex.java) |
| **Context Engine** | Constructs budget-constrained prompt context packets with deterministic prioritization (active file $\to$ symbols $\to$ dependencies $\to$ related files $\to$ recent failure traces). | [`ContextEngine`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/context/DefaultContextEngine.java), [`ContextBudgeter`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/context/ContextBudgeter.java) |
| **Mutation Engine** | Applies atomic file edits, unified diffs, and AST replacements with transactional rollback checkpoints (`WorkspaceFingerprint`). | [`MutationEngine`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/patch/mutation/MutationEngine.java) |
| **Verification Engine** | Executes a graduated 6-tier verification ladder (L0: Syntax $\to$ L1: Sonar Lint & Security $\to$ L2: Targeted Tests $\to$ L3: Affected Callers $\to$ L4: Module Tests $\to$ L5: Full Suite). | [`VerificationEngine`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/verification/VerificationEngine.java), [`DefaultVerificationEngine`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/verification/DefaultVerificationEngine.java) |
| **Closed-Loop Coder** | Coordinates the self-healing cycle: Generates patch $\to$ applies mutation $\to$ verifies $\to$ if failed, rolls back and re-prompts with extracted diagnostics until success. | [`ClosedLoopCoder`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/repair/DefaultClosedLoopCoder.java) |
| **Tool Orchestrator** | Discovers workspace toolchains and maps high-level intents (`BUILD`, `RUN_TEST`, `FORMAT`, `LINT`, `STATIC_ANALYSIS`) to sandboxed execution commands. | [`ToolOrchestrator`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/orchestrator/ToolOrchestrator.java), [`ToolPolicy`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/orchestrator/policy/ToolPolicy.java) |
| **Output Processor** | Ingests raw terminal output, compiler logs (`javac`, `clang`, `swiftc`), test assertions (`JUnit`, `ctest`, `XCTest`), and OASIS standard SARIF JSON reports into structured `ExecutionDiagnostic` records. | [`OutputProcessor`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/orchestrator/output/OutputProcessor.java), [`SarifReportParser`](file:///Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine/src/main/java/tech/kayys/aljabr/engine/orchestrator/output/SarifReportParser.java) |

---

## 🔄 End-to-End Sequence Diagrams

### 1. Autonomous Coding & Closed-Loop Repair Loop

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / UI
    participant RG as ReasoningGate
    participant CE as ContextEngine
    participant LLM as Model / LLM (Local / Cloud)
    participant ME as MutationEngine
    participant VE as VerificationEngine
    participant TO as ToolOrchestrator

    Dev->>RG: Prompt: "Fix authentication token validation in AuthService"
    RG->>CE: Request Context Plan (Budget: 8192 tokens)
    CE->>CE: Assemble active file, symbol refs, call hierarchy
    CE-->>RG: Structured ContextPacket
    RG->>LLM: Inference Request with ContextPacket
    LLM-->>RG: Generated EditProposal (FileEdits + TextEdits)

    loop Autonomous Repair Loop (Attempts 1..N)
        RG->>ME: Apply EditProposal (Create WorkspaceFingerprint)
        ME-->>RG: Checkpoint Created
        RG->>VE: Verify(Level: L1 Static Analysis + L2 Targeted Tests)
        
        alt Verification Passed
            VE-->>RG: VerificationResult(SUCCESS, Level: L2)
            RG->>Dev: Stream Plan Completion & Diffs
        else Verification Failed (e.g. Compiler Error or Test Assertion)
            VE->>TO: Extract Diagnostics from OutputProcessor
            TO-->>VE: List<ExecutionDiagnostic> (File, Line, Message)
            VE-->>RG: VerificationResult(FAILED, TestFailureSummary)
            RG->>ME: Rollback to Checkpoint Fingerprint
            ME-->>RG: Clean Pre-Mutation Workspace Restored
            RG->>CE: Inject Failure Traces into Next Context Packet
            CE-->>RG: Updated ContextPacket with Error Diagnostics
            RG->>LLM: Re-prompt: "Attempt failed at AuthService.java:42. Fix diagnostics..."
        end
    end
```

---

### 2. Multi-Language Toolchain Orchestration & Sandboxed Execution

```mermaid
sequenceDiagram
    autonumber
    participant Agent as Aljabr Engine
    participant Detector as DefaultProjectDetector
    participant Resolver as DefaultToolResolver
    participant Policy as ToolPolicy Safety Gate
    participant Exec as DefaultExecutionEngine
    participant Output as DefaultOutputProcessor

    Agent->>Detector: detect(workspaceRoot)
    Detector-->>Agent: ProjectToolchain(BuildSystem: CMAKE, Languages: [C, C++])
    
    Agent->>Resolver: resolve(ToolIntent: BUILD, Toolchain)
    Resolver-->>Agent: ResolvedTool(command: ["cmake", "--build", "build"], sandboxed: true)
    
    Agent->>Policy: evaluate(ToolIntent, ResolvedTool, workspaceRoot)
    Policy-->>Agent: ToolPolicyDecision(ALLOWED)
    
    Agent->>Exec: execute(ResolvedTool, timeout: 30s)
    Exec->>Exec: Run confined sub-process with bounded output buffer
    Exec-->>Output: Process exitCode, stdout, stderr
    Output->>Output: Match Clang/GCC/SARIF diagnostic regexes
    Output-->>Agent: ProcessedOutput(status: BUILD_FAILURE, diagnostics: [...])
```

---

### 3. Human-in-the-Loop Approval & Interactive Security Gate

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / User
    participant UI as Flutter GUI (aljabr_gui)
    participant Agent as Aljabr Engine
    participant Policy as ToolPolicy Gate
    participant Sandbox as Sandbox Process Runner

    Agent->>Policy: Evaluate command (e.g. `rm -rf build` or `git push --force`)
    Policy-->>Agent: Decision: REQUIRES_HUMAN_APPROVAL (Risk: HIGH / CRITICAL)
    Agent->>UI: Emit ApprovalRequestCard(id, command, risk, reason)
    UI->>Dev: Display inline approval card with command diff & risk badge
    
    alt User Clicks Approve
        Dev->>UI: Click "Approve & Run"
        UI->>Agent: resolveApproval(id, approved: true)
        Agent->>Sandbox: Execute command in sandbox
        Sandbox-->>Agent: Command output
        Agent->>UI: Update status: APPROVED & show output
    else User Clicks Deny
        Dev->>UI: Click "Deny"
        UI->>Agent: resolveApproval(id, approved: false)
        Agent->>Agent: Abort command execution safely
        Agent->>UI: Update status: DENIED
    end
```

---

## 🛠 Supported Build Systems & Multi-Language Toolchains

Aljabr automatically discovers project toolchains and executes builds, tests, formatting, linting, and static analysis without manual script configuration:

| Build System | Languages | Build Command | Test Command | Format / Lint Command | Static Analysis / SARIF |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Maven** | Java, Kotlin | `mvn compile -DskipTests` | `mvn test -Dtest=...` | `mvn spotless:apply` / `checkstyle` | `sonar-scanner` / `SpotBugs` |
| **Gradle** | Java, Kotlin, Groovy | `./gradlew assemble` | `./gradlew test --tests ...` | `./gradlew ktlintFormat` | `sonar-scanner` / Detekt |
| **CMake** | C, C++ | `cmake --build build` | `ctest --test-dir build -R ...` | `clang-format -i` / `clang-tidy` | `sonar-scanner` / `cppcheck` |
| **Make / Meson**| C, C++ | `make` / `meson compile` | `make test` / `meson test` | `clang-format -i` | `sonar-scanner` |
| **Swift PM** | Swift | `swift build` | `swift test --filter ...` | `swift-format -i` / `swiftlint` | `swiftlint --reporter sarif` |
| **Xcode** | Swift, Objective-C | `xcodebuild build` | `xcodebuild test -only-testing:...` | `clang-format -i` (ObjC) | `sonar-scanner` / `swiftlint` |
| **Flutter** | Dart | `flutter build` | `flutter test <path>` | `dart format` / `dart analyze` | `dart analyze` |
| **Cargo** | Rust | `cargo build` | `cargo test <name>` | `cargo fmt` / `cargo clippy` | `cargo clippy --message-format=json` |
| **Go** | Go | `go build ./...` | `go test -run ...` | `gofmt -w` / `golangci-lint` | `golangci-lint run --out-format sarif` |
| **Python** | Python | `python -m build` | `pytest -k ...` | `black` / `ruff` | `bandit -f json` / `ruff --output-format=sarif` |

---

## 🚀 Building & Running

### Prerequisites
- **JDK 25** (with Maven 3.9+)
- **Flutter SDK 3.x+**
- **Docker** (optional, for sandboxed container isolation)

### 1. Build Backend & LSP Kernel
```bash
# Build headless LSP Kernel (Muqabalah)
cd Wayang-Projects/Utilities/muqabalah
mvn clean package -DskipTests

# Build Aljabr Engine & API
cd ../../Wayang-Agents/Aljabr/Backend
mvn clean package -DskipTests
```

### 2. Run Automated Verification Tests
```bash
# Run all Aljabr backend unit & integration tests (40/40 tests)
cd Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-engine
mvn test

# Run Flutter GUI unit & widget tests (10/10 tests)
cd ../../aljabr_gui
flutter test
```

### 3. Launch Desktop Application
```bash
# Start Backend Service (Quarkus Dev Mode with Live Reload)
cd Wayang-Projects/Wayang-Agents/Aljabr/Backend/aljabr-api
mvn quarkus:dev

# Start Flutter Desktop GUI
cd ../aljabr_gui
flutter run -d macos # or -d linux, -d windows
```

---

## 📜 License
Part of the **Wayang Platform** repository. Licensed under the Apache License 2.0.
