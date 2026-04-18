use crate::ast::StageKind;
use crate::error::Diagnostic;
use crate::metadata::{carried_tool_paths, package_for_tool, PackageManager};
use crate::model::{ProgramModel, StageOp};
use std::collections::HashSet;

pub fn validate(program: &ProgramModel) -> Result<(), Diagnostic> {
    validate_stage_names(program)?;
    validate_image_last(program)?;

    for (idx, stage) in program.stages.iter().enumerate() {
        let is_image = stage.kind == StageKind::Image;
        for op in &stage.statements {
            match op {
                StageOp::Update => {
                    if is_image {
                        return Err(Diagnostic::new(
                            "update is not allowed in image stages",
                            None,
                        ));
                    }
                    if !stage.base.metadata.mutable {
                        return Err(Diagnostic::new(
                            "package-manager mutation is not allowed on immutable runtime images",
                            None,
                        ));
                    }
                    if stage.base.metadata.package_manager == PackageManager::None {
                        return Err(Diagnostic::new(
                            "update requires a supported package manager",
                            None,
                        ));
                    }
                }
                StageOp::InstallTools(tools) => {
                    if is_image {
                        return Err(Diagnostic::new(
                            "tools blocks are not allowed in image stages",
                            None,
                        ));
                    }
                    if !stage.base.metadata.mutable {
                        return Err(Diagnostic::new(
                            "tool installation is not allowed on immutable runtime images",
                            None,
                        ));
                    }
                    for tool in tools {
                        if package_for_tool(tool, stage.base.distro).is_none() {
                            return Err(Diagnostic::new(
                                format!("tool '{tool}' is not mapped for this distro"),
                                None,
                            ));
                        }
                    }
                }
                StageOp::Runtime(_) if !is_image => {
                    return Err(Diagnostic::new(
                        "runtime blocks are only allowed in image stages",
                        None,
                    ));
                }
                StageOp::Runtime(_) if !stage.base.metadata.shell_available => {
                    return Err(Diagnostic::new("runtime requires shell/runtime support, but this base has no shell support metadata", None));
                }
                StageOp::CarryTool { name, from_stage } => {
                    if !is_image {
                        return Err(Diagnostic::new(
                            "carry tool is only allowed in image stages",
                            None,
                        ));
                    }
                    if carried_tool_paths(name).is_none() {
                        return Err(Diagnostic::new(
                            format!("carried tool '{name}' has no filesystem path mapping"),
                            None,
                        ));
                    }
                    if !program.stages[..idx].iter().any(|s| &s.name == from_stage) {
                        return Err(Diagnostic::new(
                            format!("carry tool '{name}' must reference a prior build/test stage"),
                            None,
                        ));
                    }
                }
                _ => {}
            }
        }
    }

    Ok(())
}

fn validate_stage_names(program: &ProgramModel) -> Result<(), Diagnostic> {
    let mut seen = HashSet::new();
    for stage in &program.stages {
        if !seen.insert(stage.name.clone()) {
            return Err(Diagnostic::new(
                format!("duplicate stage name '{}'", stage.name),
                None,
            ));
        }
    }
    Ok(())
}

fn validate_image_last(program: &ProgramModel) -> Result<(), Diagnostic> {
    for (idx, stage) in program.stages.iter().enumerate() {
        if stage.kind == StageKind::Image && idx + 1 != program.stages.len() {
            return Err(Diagnostic::new("image stage must be last", None));
        }
    }
    Ok(())
}
