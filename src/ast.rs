#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Span {
    pub start: usize,
    pub end: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Spanned<T> {
    pub value: T,
    pub span: Span,
}

impl<T> Spanned<T> {
    pub fn new(value: T, span: Span) -> Self {
        Self { value, span }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Program {
    pub lets: Vec<LetDecl>,
    pub stages: Vec<StageDecl>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LetDecl {
    pub name: Spanned<String>,
    pub value: Spanned<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StageDecl {
    pub name: Spanned<String>,
    pub kind: Spanned<StageKind>,
    pub statements: Vec<StageStmt>,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StageKind {
    Build,
    Test,
    Image,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum StageStmt {
    Base(BaseDecl),
    Workdir(Spanned<String>),
    User(Spanned<String>),
    Update(Span),
    Tools(ToolsBlock),
    Copy(CopyStmt),
    Tool(ToolBlock),
    Produces(ArtifactDecl),
    Carry(CarryTool),
    Runtime(RuntimeBlock),
    Expose(Spanned<u16>),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BaseDecl {
    pub family: Spanned<BaseFamily>,
    pub variant: Spanned<Variant>,
    pub distro: Spanned<Distro>,
    pub image: Spanned<String>,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BaseFamily {
    Chainguard,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Variant {
    Dev,
    Runtime,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Distro {
    Wolfi,
    Debian,
    Ubuntu,
    Rhel,
    Alpine,
    Distroless,
    Windows,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolsBlock {
    pub build: Vec<Spanned<String>>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CopyStmt {
    Paths {
        sources: Vec<Spanned<String>>,
        dest: Spanned<String>,
        span: Span,
    },
    Artifact {
        name: Spanned<String>,
        target_name: Option<Spanned<String>>,
        dest: Spanned<String>,
        span: Span,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolBlock {
    pub kind: Spanned<ToolKind>,
    pub config: ToolConfig,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ToolKind {
    Go,
    Maven,
    Python,
    Node,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToolConfig {
    Go {
        build: bool,
    },
    Maven {
        goals: Vec<String>,
        skip_tests: bool,
    },
    Python {
        requirements: String,
    },
    Node {
        install: NodeInstall,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeInstall {
    Prod,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArtifactDecl {
    pub name: Spanned<String>,
    pub path: Spanned<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CarryTool {
    pub name: Spanned<String>,
    pub span: Span,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RuntimeBlock {
    pub kind: Spanned<RuntimeKind>,
    pub config: RuntimeConfig,
    pub span: Span,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RuntimeKind {
    Binary,
    Java,
    Python,
    Node,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RuntimeConfig {
    Binary { entry: String },
    Java { jar: String },
    Python { entry: String },
    Node { entry: String },
}
