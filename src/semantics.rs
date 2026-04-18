use crate::ast::*;
use crate::error::Diagnostic;
use crate::metadata::metadata_for;
use crate::model::*;
use std::collections::{HashMap, HashSet};

#[derive(Debug, Clone)]
struct ArtifactLocation {
    from_stage: String,
    source_path: ArtifactPath,
}

pub fn build_model(program: &Program) -> Result<ProgramModel, Diagnostic> {
    let variables = collect_variables(program)?;
    let mut artifacts: HashMap<String, ArtifactLocation> = HashMap::new();
    let mut declared_build_tools: HashSet<String> = HashSet::new();
    let mut stages = Vec::new();

    for stage in &program.stages {
        let base_decl = stage
            .statements
            .iter()
            .find_map(|stmt| match stmt {
                StageStmt::Base(base) => Some(base),
                _ => None,
            })
            .ok_or_else(|| {
                Diagnostic::new(
                    format!("stage '{}' requires a base block", stage.name.value),
                    Some(stage.span),
                )
            })?;

        let image = resolve_string(&base_decl.image.value, &variables, base_decl.image.span)?;
        let metadata = metadata_for(base_decl.distro.value, base_decl.variant.value)?;
        let base = BaseModel {
            family: base_decl.family.value,
            variant: base_decl.variant.value,
            distro: base_decl.distro.value,
            image,
            metadata,
        };

        let mut ops = Vec::new();
        for stmt in &stage.statements {
            match stmt {
                StageStmt::Base(_) => {}
                StageStmt::Workdir(value) => ops.push(StageOp::Workdir(resolve_string(&value.value, &variables, value.span)?)),
                StageStmt::User(value) => ops.push(StageOp::User(resolve_string(&value.value, &variables, value.span)?)),
                StageStmt::Update(_) => ops.push(StageOp::Update),
                StageStmt::Tools(block) => {
                    let tools = block.build.iter().map(|t| t.value.clone()).collect::<Vec<_>>();
                    for tool in &tools {
                        declared_build_tools.insert(tool.clone());
                    }
                    ops.push(StageOp::InstallTools(tools));
                }
                StageStmt::Copy(copy) => match copy {
                    CopyStmt::Paths { sources, dest, .. } => ops.push(StageOp::CopyPaths {
                        sources: sources.iter().map(|s| resolve_string(&s.value, &variables, s.span)).collect::<Result<Vec<_>, _>>()?,
                        dest: resolve_string(&dest.value, &variables, dest.span)?,
                    }),
                    CopyStmt::Artifact {
                        name,
                        target_name,
                        dest,
                        ..
                    } => {
                        let artifact_name = resolve_string(&name.value, &variables, name.span)?;
                        let target_name = target_name
                            .as_ref()
                            .map(|target| {
                                resolve_string(&target.value, &variables, target.span)
                            })
                            .transpose()?;
                        let artifact = artifacts.get(&artifact_name).cloned().ok_or_else(|| {
                            Diagnostic::new(
                                format!("artifact '{artifact_name}' is referenced in stage '{}' but was never produced", stage.name.value),
                                Some(name.span),
                            )
                        })?;
                        ops.push(StageOp::CopyArtifact {
                            name: artifact_name,
                            target_name,
                            from_stage: artifact.from_stage,
                            source_path: artifact.source_path,
                            dest: resolve_string(&dest.value, &variables, dest.span)?,
                        });
                    }
                },
                StageStmt::Tool(tool) => ops.push(StageOp::RunTool(ToolInvocation {
                    kind: tool.kind.value,
                    config: resolve_tool_config(&tool.config, &variables, tool.span)?,
                })),
                StageStmt::Produces(artifact) => {
                    let name = resolve_string(&artifact.name.value, &variables, artifact.name.span)?;
                    let path = ArtifactPath::new(resolve_string(&artifact.path.value, &variables, artifact.path.span)?);
                    if artifacts.insert(name.clone(), ArtifactLocation {
                        from_stage: stage.name.value.clone(),
                        source_path: path.clone(),
                    }).is_some() {
                        return Err(Diagnostic::new(format!("duplicate artifact name '{name}'"), Some(artifact.name.span)));
                    }
                    ops.push(StageOp::ProduceArtifact { name, path });
                }
                StageStmt::Carry(carry) => ops.push(StageOp::CarryTool {
                    name: carry.name.value.clone(),
                    from_stage: find_prior_tool_stage(&stages, &carry.name.value, &declared_build_tools)
                        .ok_or_else(|| Diagnostic::new(
                            format!("carry tool '{}' requires it to be declared in tools {{ build [...] }} in a prior build stage", carry.name.value),
                            Some(carry.name.span),
                        ))?,
                }),
                StageStmt::Runtime(runtime) => ops.push(StageOp::Runtime(RuntimeSpec {
                    kind: runtime.kind.value,
                    config: resolve_runtime_config(&runtime.config, &variables, runtime.span)?,
                })),
                StageStmt::Expose(port) => ops.push(StageOp::Expose(port.value)),
            }
        }

        stages.push(StageModel {
            name: stage.name.value.clone(),
            kind: stage.kind.value,
            base,
            statements: ops,
        });
    }

    Ok(ProgramModel { stages })
}

fn collect_variables(program: &Program) -> Result<HashMap<String, String>, Diagnostic> {
    let mut variables = HashMap::new();
    for decl in &program.lets {
        let value = resolve_string(&decl.value.value, &variables, decl.value.span)?;
        variables.insert(decl.name.value.clone(), value);
    }
    Ok(variables)
}

fn resolve_tool_config(
    config: &ToolConfig,
    vars: &HashMap<String, String>,
    span: Span,
) -> Result<ToolConfig, Diagnostic> {
    Ok(match config {
        ToolConfig::Go { build } => ToolConfig::Go { build: *build },
        ToolConfig::Maven { goals, skip_tests } => ToolConfig::Maven {
            goals: goals
                .iter()
                .map(|g| resolve_string(g, vars, span))
                .collect::<Result<Vec<_>, _>>()?,
            skip_tests: *skip_tests,
        },
        ToolConfig::Python { requirements } => ToolConfig::Python {
            requirements: resolve_string(requirements, vars, span)?,
        },
        ToolConfig::Node { install } => ToolConfig::Node { install: *install },
    })
}

fn resolve_runtime_config(
    config: &RuntimeConfig,
    vars: &HashMap<String, String>,
    span: Span,
) -> Result<RuntimeConfig, Diagnostic> {
    Ok(match config {
        RuntimeConfig::Binary { entry } => RuntimeConfig::Binary {
            entry: resolve_string(entry, vars, span)?,
        },
        RuntimeConfig::Java { jar } => RuntimeConfig::Java {
            jar: resolve_string(jar, vars, span)?,
        },
        RuntimeConfig::Python { entry } => RuntimeConfig::Python {
            entry: resolve_string(entry, vars, span)?,
        },
        RuntimeConfig::Node { entry } => RuntimeConfig::Node {
            entry: resolve_string(entry, vars, span)?,
        },
    })
}

fn resolve_string(
    input: &str,
    vars: &HashMap<String, String>,
    span: Span,
) -> Result<String, Diagnostic> {
    let mut output = String::new();
    let mut rest = input;
    while let Some(start) = rest.find("{{") {
        output.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        let Some(end) = after.find("}}") else {
            return Err(Diagnostic::new(
                "unterminated variable interpolation",
                Some(span),
            ));
        };
        let name = after[..end].trim();
        let value = vars.get(name).ok_or_else(|| {
            Diagnostic::new(
                format!("unresolved variable interpolation '{name}'"),
                Some(span),
            )
        })?;
        output.push_str(value);
        rest = &after[end + 2..];
    }
    output.push_str(rest);
    Ok(output)
}

fn find_prior_tool_stage(
    stages: &[StageModel],
    tool: &str,
    declared: &HashSet<String>,
) -> Option<String> {
    if !declared.contains(tool) {
        return None;
    }
    stages.iter().rev().find_map(|stage| {
        stage
            .statements
            .iter()
            .any(|op| matches!(op, StageOp::InstallTools(tools) if tools.iter().any(|t| t == tool)))
            .then(|| stage.name.clone())
    })
}
