use crate::ast::*;
use crate::error::Diagnostic;
use pest::iterators::Pair;
use pest::Parser;
use pest_derive::Parser;

#[derive(Parser)]
#[grammar = "grammar.pest"]
struct DdslParser;

pub fn parse_program(source: &str) -> Result<Program, Diagnostic> {
    let mut pairs = DdslParser::parse(Rule::program, source)
        .map_err(|err| Diagnostic::new(format!("parse error: {err}"), None))?;
    let program = pairs.next().expect("program pair");
    let mut lets = Vec::new();
    let mut stages = Vec::new();

    for pair in program.into_inner() {
        match pair.as_rule() {
            Rule::let_decl => lets.push(parse_let(pair)?),
            Rule::stage_decl => stages.push(parse_stage(pair)?),
            Rule::EOI => {}
            _ => {}
        }
    }

    Ok(Program { lets, stages })
}

fn parse_let(pair: Pair<'_, Rule>) -> Result<LetDecl, Diagnostic> {
    let mut inner = pair.into_inner();
    let name = parse_ident(inner.next().expect("let name"));
    let value = parse_string(inner.next().expect("let value"));
    Ok(LetDecl { name, value })
}

fn parse_stage(pair: Pair<'_, Rule>) -> Result<StageDecl, Diagnostic> {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let name = parse_ident(inner.next().expect("stage name"));
    let kind_pair = inner.next().expect("stage kind");
    let kind = Spanned::new(
        parse_stage_kind(kind_pair.as_str())?,
        span(kind_pair.as_span()),
    );
    let mut statements = Vec::new();
    for item in inner {
        statements.push(parse_stage_stmt(item)?);
    }
    Ok(StageDecl {
        name,
        kind,
        statements,
        span: full_span,
    })
}

fn parse_stage_stmt(pair: Pair<'_, Rule>) -> Result<StageStmt, Diagnostic> {
    Ok(match pair.as_rule() {
        Rule::base_decl => StageStmt::Base(parse_base(pair)?),
        Rule::workdir_stmt => StageStmt::Workdir(parse_single_string(pair)),
        Rule::user_stmt => StageStmt::User(parse_single_string(pair)),
        Rule::update_stmt => StageStmt::Update(span(pair.as_span())),
        Rule::tools_block => StageStmt::Tools(parse_tools(pair)?),
        Rule::copy_paths_stmt => StageStmt::Copy(parse_copy_paths(pair)),
        Rule::copy_artifact_stmt => StageStmt::Copy(parse_copy_artifact(pair)),
        Rule::tool_block => StageStmt::Tool(parse_tool(pair)?),
        Rule::produces_stmt => StageStmt::Produces(parse_produces(pair)),
        Rule::carry_stmt => StageStmt::Carry(parse_carry(pair)),
        Rule::runtime_block => StageStmt::Runtime(parse_runtime(pair)?),
        Rule::expose_stmt => StageStmt::Expose(parse_expose(pair)?),
        _ => {
            return Err(Diagnostic::new(
                "unexpected stage statement",
                Some(span(pair.as_span())),
            ))
        }
    })
}

fn parse_base(pair: Pair<'_, Rule>) -> Result<BaseDecl, Diagnostic> {
    let full_span = span(pair.as_span());
    let mut family = None;
    let mut variant = None;
    let mut distro = None;
    let mut image = None;

    for item in pair.into_inner() {
        match item.as_rule() {
            Rule::base_family => {
                family = Some(Spanned::new(BaseFamily::Chainguard, span(item.as_span())));
            }
            Rule::variant_field => {
                let value = item.into_inner().next().expect("variant value");
                variant = Some(Spanned::new(
                    parse_variant(value.as_str())?,
                    span(value.as_span()),
                ));
            }
            Rule::distro_field => {
                let value = item.into_inner().next().expect("distro value");
                distro = Some(Spanned::new(
                    parse_distro(value.as_str())?,
                    span(value.as_span()),
                ));
            }
            Rule::image_field => {
                image = Some(parse_single_string(item));
            }
            _ => {}
        }
    }

    Ok(BaseDecl {
        family: family
            .ok_or_else(|| Diagnostic::new("base family is required", Some(full_span)))?,
        variant: variant
            .ok_or_else(|| Diagnostic::new("base variant is required", Some(full_span)))?,
        distro: distro
            .ok_or_else(|| Diagnostic::new("base distro is required", Some(full_span)))?,
        image: image.ok_or_else(|| Diagnostic::new("base image is required", Some(full_span)))?,
        span: full_span,
    })
}

fn parse_tools(pair: Pair<'_, Rule>) -> Result<ToolsBlock, Diagnostic> {
    let full_span = span(pair.as_span());
    let build = pair
        .into_inner()
        .find(|p| p.as_rule() == Rule::build_tools)
        .map(|p| {
            p.into_inner()
                .find(|i| i.as_rule() == Rule::ident_list)
                .map(parse_ident_list)
                .unwrap_or_default()
        })
        .unwrap_or_default();
    Ok(ToolsBlock {
        build,
        span: full_span,
    })
}

fn parse_copy_paths(pair: Pair<'_, Rule>) -> CopyStmt {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let sources = parse_string_list(inner.next().expect("copy sources"));
    let dest = parse_string(inner.next().expect("copy dest"));
    CopyStmt::Paths {
        sources,
        dest,
        span: full_span,
    }
}

fn parse_copy_artifact(pair: Pair<'_, Rule>) -> CopyStmt {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let name = parse_string(inner.next().expect("artifact name"));
    let next = inner.next().expect("artifact target or dest");
    let (target_name, dest) = if next.as_rule() == Rule::artifact_target {
        let target_name = parse_string(next.into_inner().next().expect("artifact target name"));
        let dest = parse_string(inner.next().expect("artifact dest"));
        (Some(target_name), dest)
    } else {
        (None, parse_string(next))
    };
    CopyStmt::Artifact {
        name,
        target_name,
        dest,
        span: full_span,
    }
}

fn parse_tool(pair: Pair<'_, Rule>) -> Result<ToolBlock, Diagnostic> {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let kind_pair = inner.next().expect("tool kind");
    let kind_value = parse_tool_kind(kind_pair.as_str())?;
    let kind = Spanned::new(kind_value, span(kind_pair.as_span()));

    let fields: Vec<_> = inner.collect();
    let config = match kind_value {
        ToolKind::Go => {
            let build = fields
                .iter()
                .find(|p| p.as_rule() == Rule::go_build_field)
                .and_then(|p| p.clone().into_inner().next())
                .map(|p| p.as_str() == "true")
                .unwrap_or(false);
            ToolConfig::Go { build }
        }
        ToolKind::Maven => {
            let mut goals = Vec::new();
            let mut skip_tests = false;
            for field in fields {
                match field.as_rule() {
                    Rule::maven_goals_field => {
                        if let Some(list) = field.into_inner().next() {
                            goals = parse_string_list(list)
                                .into_iter()
                                .map(|s| s.value)
                                .collect();
                        }
                    }
                    Rule::maven_skip_tests_field => {
                        skip_tests = field
                            .into_inner()
                            .next()
                            .map(|p| p.as_str() == "true")
                            .unwrap_or(false);
                    }
                    _ => {}
                }
            }
            ToolConfig::Maven { goals, skip_tests }
        }
        ToolKind::Python => {
            let requirements = fields
                .into_iter()
                .find(|p| p.as_rule() == Rule::python_pip_block)
                .and_then(|p| p.into_inner().next())
                .and_then(|p| p.into_inner().next())
                .map(parse_string)
                .ok_or_else(|| {
                    Diagnostic::new("tool python requires pip requirements", Some(full_span))
                })?
                .value;
            ToolConfig::Python { requirements }
        }
        ToolKind::Node => {
            let install = fields
                .into_iter()
                .find(|p| p.as_rule() == Rule::node_install_field)
                .and_then(|p| p.into_inner().next())
                .map(|p| match p.as_str() {
                    "prod" => Ok(NodeInstall::Prod),
                    _ => Err(Diagnostic::new(
                        "unsupported node install mode",
                        Some(span(p.as_span())),
                    )),
                })
                .transpose()?
                .ok_or_else(|| {
                    Diagnostic::new("tool node requires install mode", Some(full_span))
                })?;
            ToolConfig::Node { install }
        }
    };

    Ok(ToolBlock {
        kind,
        config,
        span: full_span,
    })
}

fn parse_produces(pair: Pair<'_, Rule>) -> ArtifactDecl {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let name = parse_string(inner.next().expect("artifact name"));
    let path = parse_string(inner.next().expect("artifact path"));
    ArtifactDecl {
        name,
        path,
        span: full_span,
    }
}

fn parse_carry(pair: Pair<'_, Rule>) -> CarryTool {
    let full_span = span(pair.as_span());
    let name = parse_ident(pair.into_inner().next().expect("carry tool"));
    CarryTool {
        name,
        span: full_span,
    }
}

fn parse_runtime(pair: Pair<'_, Rule>) -> Result<RuntimeBlock, Diagnostic> {
    let full_span = span(pair.as_span());
    let mut inner = pair.into_inner();
    let kind_pair = inner.next().expect("runtime kind");
    let kind_value = parse_runtime_kind(kind_pair.as_str())?;
    let kind = Spanned::new(kind_value, span(kind_pair.as_span()));
    let fields: Vec<_> = inner.collect();
    let config = match kind_value {
        RuntimeKind::Binary => RuntimeConfig::Binary {
            entry: required_runtime_string(
                &fields,
                Rule::runtime_entry_field,
                "runtime binary requires entry",
                full_span,
            )?,
        },
        RuntimeKind::Java => RuntimeConfig::Java {
            jar: required_runtime_string(
                &fields,
                Rule::runtime_jar_field,
                "runtime java requires jar",
                full_span,
            )?,
        },
        RuntimeKind::Python => RuntimeConfig::Python {
            entry: required_runtime_string(
                &fields,
                Rule::runtime_entry_field,
                "runtime python requires entry",
                full_span,
            )?,
        },
        RuntimeKind::Node => RuntimeConfig::Node {
            entry: required_runtime_string(
                &fields,
                Rule::runtime_entry_field,
                "runtime node requires entry",
                full_span,
            )?,
        },
    };
    Ok(RuntimeBlock {
        kind,
        config,
        span: full_span,
    })
}

fn required_runtime_string(
    fields: &[Pair<'_, Rule>],
    rule: Rule,
    message: &str,
    span: Span,
) -> Result<String, Diagnostic> {
    fields
        .iter()
        .find(|p| p.as_rule() == rule)
        .and_then(|p| p.clone().into_inner().next())
        .map(parse_string)
        .map(|s| s.value)
        .ok_or_else(|| Diagnostic::new(message, Some(span)))
}

fn parse_expose(pair: Pair<'_, Rule>) -> Result<Spanned<u16>, Diagnostic> {
    let value = pair.into_inner().next().expect("port");
    let port = value.as_str().parse::<u16>().map_err(|_| {
        Diagnostic::new(
            "expose port must be between 0 and 65535",
            Some(span(value.as_span())),
        )
    })?;
    Ok(Spanned::new(port, span(value.as_span())))
}

fn parse_single_string(pair: Pair<'_, Rule>) -> Spanned<String> {
    parse_string(pair.into_inner().next().expect("string"))
}

fn parse_string_list(pair: Pair<'_, Rule>) -> Vec<Spanned<String>> {
    pair.into_inner().map(parse_string).collect()
}

fn parse_ident_list(pair: Pair<'_, Rule>) -> Vec<Spanned<String>> {
    pair.into_inner().map(parse_ident).collect()
}

fn parse_ident(pair: Pair<'_, Rule>) -> Spanned<String> {
    Spanned::new(pair.as_str().to_string(), span(pair.as_span()))
}

fn parse_string(pair: Pair<'_, Rule>) -> Spanned<String> {
    let raw = pair.as_str();
    let unquoted = &raw[1..raw.len() - 1];
    let value = unquoted.replace("\\\"", "\"").replace("\\\\", "\\");
    Spanned::new(value, span(pair.as_span()))
}

fn parse_stage_kind(value: &str) -> Result<StageKind, Diagnostic> {
    match value {
        "build" => Ok(StageKind::Build),
        "test" => Ok(StageKind::Test),
        "image" => Ok(StageKind::Image),
        _ => Err(Diagnostic::new(
            format!("unsupported stage kind '{value}'"),
            None,
        )),
    }
}

fn parse_variant(value: &str) -> Result<Variant, Diagnostic> {
    match value {
        "dev" => Ok(Variant::Dev),
        "runtime" => Ok(Variant::Runtime),
        _ => Err(Diagnostic::new(
            format!("unsupported variant '{value}'"),
            None,
        )),
    }
}

fn parse_distro(value: &str) -> Result<Distro, Diagnostic> {
    match value {
        "wolfi" => Ok(Distro::Wolfi),
        "debian" => Ok(Distro::Debian),
        "ubuntu" => Ok(Distro::Ubuntu),
        "rhel" => Ok(Distro::Rhel),
        "alpine" => Ok(Distro::Alpine),
        "distroless" => Ok(Distro::Distroless),
        "windows" => Ok(Distro::Windows),
        _ => Err(Diagnostic::new(
            format!("unsupported distro '{value}'"),
            None,
        )),
    }
}

fn parse_tool_kind(value: &str) -> Result<ToolKind, Diagnostic> {
    match value {
        "go" => Ok(ToolKind::Go),
        "maven" => Ok(ToolKind::Maven),
        "python" => Ok(ToolKind::Python),
        "node" => Ok(ToolKind::Node),
        _ => Err(Diagnostic::new(
            format!("unsupported tool kind '{value}'"),
            None,
        )),
    }
}

fn parse_runtime_kind(value: &str) -> Result<RuntimeKind, Diagnostic> {
    match value {
        "binary" => Ok(RuntimeKind::Binary),
        "java" => Ok(RuntimeKind::Java),
        "python" => Ok(RuntimeKind::Python),
        "node" => Ok(RuntimeKind::Node),
        _ => Err(Diagnostic::new(
            format!("unsupported runtime kind '{value}'"),
            None,
        )),
    }
}

fn span(span: pest::Span<'_>) -> Span {
    Span {
        start: span.start(),
        end: span.end(),
    }
}
