use ddsl::{compile_to_dockerfile, parser, semantics, validate};

const EXAMPLES: &[(&str, &str)] = &[
    ("go", include_str!("../examples/go.dsl")),
    ("java", include_str!("../examples/java.dsl")),
    ("python", include_str!("../examples/python.dsl")),
    ("node", include_str!("../examples/node.dsl")),
];

#[test]
fn parser_accepts_authoritative_examples() {
    for (name, source) in EXAMPLES {
        parser::parse_program(source).unwrap_or_else(|err| panic!("{name} failed to parse: {err}"));
    }
}

#[test]
fn semantic_model_accepts_authoritative_examples() {
    for (name, source) in EXAMPLES {
        let ast = parser::parse_program(source).unwrap();
        let model = semantics::build_model(&ast)
            .unwrap_or_else(|err| panic!("{name} failed semantics: {err}"));
        validate::validate(&model).unwrap_or_else(|err| panic!("{name} failed validation: {err}"));
    }
}

#[test]
fn dockerfile_golden_outputs_match() {
    let cases = [
        (
            "go",
            include_str!("../examples/go.dsl"),
            include_str!("../expected/go.Dockerfile"),
        ),
        (
            "java",
            include_str!("../examples/java.dsl"),
            include_str!("../expected/java.Dockerfile"),
        ),
        (
            "python",
            include_str!("../examples/python.dsl"),
            include_str!("../expected/python.Dockerfile"),
        ),
        (
            "node",
            include_str!("../examples/node.dsl"),
            include_str!("../expected/node.Dockerfile"),
        ),
    ];

    for (name, source, expected) in cases {
        let actual = compile_to_dockerfile(source)
            .unwrap_or_else(|err| panic!("{name} failed compile: {err}"));
        assert_eq!(
            actual.trim_end(),
            expected.trim_end(),
            "{name} golden mismatch"
        );
    }
}

#[test]
fn rejects_update_in_image_stage() {
    let err = compile_to_dockerfile(
        r#"
stage image as image {
  base chainguard { variant runtime distro wolfi image "img" }
  update
}
"#,
    )
    .unwrap_err();
    assert!(err
        .message
        .contains("update is not allowed in image stages"));
}

#[test]
fn rejects_carry_tool_not_declared() {
    let err = compile_to_dockerfile(
        r#"
stage image as image {
  base chainguard { variant runtime distro wolfi image "img" }
  carry tool aws-cli
}
"#,
    )
    .unwrap_err();
    assert!(err.message.contains("carry tool 'aws-cli' requires"));
}

#[test]
fn rejects_missing_artifact_reference() {
    let err = compile_to_dockerfile(
        r#"
stage image as image {
  base chainguard { variant runtime distro wolfi image "img" }
  copy artifact "jar" to "/app/app.jar"
}
"#,
    )
    .unwrap_err();
    assert!(err.message.contains("artifact 'jar' is referenced"));
}

#[test]
fn rejects_runtime_outside_image_stage() {
    let err = compile_to_dockerfile(
        r#"
stage build-java as build {
  base chainguard { variant dev distro wolfi image "img" }
  runtime java { jar "/app/app.jar" }
}
"#,
    )
    .unwrap_err();
    assert!(err
        .message
        .contains("runtime blocks are only allowed in image stages"));
}

#[test]
fn rejects_image_stage_not_last() {
    let err = compile_to_dockerfile(
        r#"
stage image as image {
  base chainguard { variant runtime distro wolfi image "img" }
}
stage build-go as build {
  base chainguard { variant dev distro wolfi image "img2" }
}
"#,
    )
    .unwrap_err();
    assert!(err.message.contains("image stage must be last"));
}

#[test]
fn rejects_unresolved_variable() {
    let err = compile_to_dockerfile(
        r#"
stage image as image {
  base chainguard { variant runtime distro wolfi image "{{REGISTRY}}/img" }
}
"#,
    )
    .unwrap_err();
    assert!(err.message.contains("unresolved variable interpolation"));
}

#[test]
fn rejects_go_build_with_wildcard_artifact_output() {
    let err = compile_to_dockerfile(
        r#"
stage build-go as build {
  base chainguard { variant dev distro wolfi image "img" }
  tool go { build true }
  produces artifact "binary" at "/app/bin/*"
}
"#,
    )
    .unwrap_err();
    assert!(err
        .message
        .contains("go build output artifact must use an exact path"));
}
