# ddsl

`ddsl` is a Rust compiler for an intent-driven container build DSL. The DSL describes build, test, and runtime image intent; Dockerfiles are a generated backend artifact.

The project is deliberately not a Dockerfile wrapper or templating layer. Users express language-aware actions, artifacts, runtime behavior, tools, and base metadata. The compiler owns Dockerfile mechanics such as package-manager commands, carried-tool filesystem paths, and entrypoint shape.

## Architecture

The compiler pipeline is split into testable phases:

1. Parse source with a real `pest` grammar into a typed AST.
2. Resolve `let` variables and `{{NAME}}` interpolation.
3. Build a semantic model with explicit stage, base, artifact, tool, and runtime types.
4. Validate semantics before generation.
5. Generate a deterministic Dockerfile.

Important modules:

- `src/grammar.pest`: DSL grammar.
- `src/ast.rs`: parsed syntax tree types.
- `src/semantics.rs`: variable resolution and semantic model construction.
- `src/validate.rs`: validation rules.
- `src/metadata.rs`: compiler-owned base and tool metadata.
- `src/generator/dockerfile.rs`: Dockerfile backend.
- `src/main.rs`: small CLI.

## Supported Constructs

- `let NAME = "value"` declarations.
- `stage <name> as build|test|image { ... }`.
- `base chainguard` blocks with `variant`, `distro`, and `image`.
- `workdir`, `user`, `update`, `tools`, source copies, and artifact copies.
- Logical build artifact labels with `produces artifact "application" at "/app/target/application-*.jar"`.
- Named target artifacts that reference labels with `copy artifact "application" as "app.jar" to "/app/app.jar"`.
- Repeated language-aware `tool` blocks for Go, Maven, Python, and Node.
- `carry tool` for compiler-mapped runtime copies.
- Runtime blocks for binary, Java, Python, and Node.
- `expose <port>`.

## Validation

The compiler rejects invalid programs before Dockerfile generation, including:

- image stage not last,
- `update` or `tools` in image stages,
- runtime blocks outside image stages,
- artifact references without a producer,
- carrying tools not declared in a prior build/test stage,
- unresolved interpolation variables,
- package-manager mutation in immutable runtime images,
- unsupported base metadata combinations.

Diagnostics are structured and include source spans where practical.

## Metadata

The compiler owns base behavior metadata. It does not infer mutability, package-manager availability, or shell support from image names.

Initial distro package managers:

- Wolfi and Alpine: `apk`
- Debian and Ubuntu: `apt`
- RHEL: `dnf`
- Distroless and Windows: none

Initial mutability:

- `variant dev`: mutable
- `variant runtime`: immutable

Wolfi tool mappings cover the authoritative examples: `git`, `curl`, `aws-cli`, `ca-certificates`, `openssl`, `build-base`, and `unzip`.

## Running

Compile a DSL file to stdout:

```sh
cargo run --bin transpiler -- examples/go.dsl
```

Write to a Dockerfile:

```sh
cargo run --bin transpiler -- examples/go.dsl Dockerfile
```

Run tests:

```sh
cargo test
```

Format:

```sh
cargo fmt --all
```

## Examples

Authoritative inputs live in `examples/`. Expected generated Dockerfiles live in `expected/`.

## Current Limitations

- Dockerfile is the only backend.
- Windows bases are rejected by the initial backend.
- Distroless bases are modeled as having no package manager and no shell.
- Carried-tool filesystem paths are intentionally conservative and currently provided for `git`, `ca-certificates`, and `aws-cli`.
- Artifact paths may use wildcard patterns for versioned outputs, such as Maven jars.
- Go build output inference requires an exact artifact path because the compiler passes it to `go build -o`.
