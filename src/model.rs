use crate::ast::{
    BaseFamily, Distro, NodeInstall, RuntimeConfig, RuntimeKind, StageKind, ToolConfig, ToolKind,
    Variant,
};
use crate::metadata::BaseMetadata;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProgramModel {
    pub stages: Vec<StageModel>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StageModel {
    pub name: String,
    pub kind: StageKind,
    pub base: BaseModel,
    pub statements: Vec<StageOp>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BaseModel {
    pub family: BaseFamily,
    pub variant: Variant,
    pub distro: Distro,
    pub image: String,
    pub metadata: BaseMetadata,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArtifactPath {
    pub raw: String,
    pub kind: ArtifactPathKind,
}

impl ArtifactPath {
    pub fn new(raw: String) -> Self {
        let kind = if raw.contains('*') || raw.contains('?') || raw.contains('[') {
            ArtifactPathKind::Pattern
        } else {
            ArtifactPathKind::Exact
        };
        Self { raw, kind }
    }

    pub fn is_pattern(&self) -> bool {
        self.kind == ArtifactPathKind::Pattern
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ArtifactPathKind {
    Exact,
    Pattern,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum StageOp {
    Workdir(String),
    User(String),
    Update,
    InstallTools(Vec<String>),
    CopyPaths {
        sources: Vec<String>,
        dest: String,
    },
    CopyArtifact {
        name: String,
        target_name: Option<String>,
        from_stage: String,
        source_path: ArtifactPath,
        dest: String,
    },
    RunTool(ToolInvocation),
    ProduceArtifact {
        name: String,
        path: ArtifactPath,
    },
    CarryTool {
        name: String,
        from_stage: String,
    },
    Runtime(RuntimeSpec),
    Expose(u16),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolInvocation {
    pub kind: ToolKind,
    pub config: ToolConfig,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RuntimeSpec {
    pub kind: RuntimeKind,
    pub config: RuntimeConfig,
}

#[allow(dead_code)]
fn _keep_import(_: NodeInstall) {}
