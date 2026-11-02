# CIFR IDE

CIFR IDE is the headless IDE engine embedded in the CIFR repository. It is
built from selected OpenIDEA Rust crates and is intentionally independent of
the CIFR coding-agent orchestration and any particular model or UI.

## Headless server

The `ide-host` crate exposes the headless server executable:

```sh
cd cifr-ide
cargo build -p ide-host --bin cifridea-server
cargo run -p ide-host --bin cifridea-server -- --stdio
```

The server uses stdin/stdout for JSON-RPC 2.0 with Content-Length framing.
Protocol version 2 currently supports:

- `initialize` with `{ "version": 2 }`
- `commands` to enumerate remotely exposed host commands
- `invoke` with `{ "window", "cmd", "args" }`

Logs are written to stderr so stdout remains protocol-only. The default
persistent server directory is `~/.cifr-ide`; override it with
`CIFR_IDE_SERVER_HOME`. Set `CIFR_IDE_LOG` to configure tracing.

CIFR IDE does not currently include the OpenIDEA Tauri desktop shell. The Rust
workspace therefore includes only the crates under `crates/*`.

## Relationship to CIFR

CIFR remains responsible for coding-agent planning, task context, model and
tool orchestration, patch authorization, verification strategy, memory, and
public agent APIs. CIFR IDE owns workspace/document state, language servers,
syntax and navigation, project indexing/search primitives, build/run/debug,
and filesystem-facing IDE operations.

The Java-side client boundary is being introduced separately from the Rust
host. Do not add a second Java LSP implementation or duplicate the IDE's
workspace/document/index state. See
[`docs/architecture/cifr-ide-integration.md`](../docs/architecture/cifr-ide-integration.md)
for the integration contract and migration status.
