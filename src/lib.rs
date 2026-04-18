pub mod ast;
pub mod error;
pub mod generator;
pub mod metadata;
pub mod model;
pub mod parser;
pub mod semantics;
pub mod validate;

use crate::error::Diagnostic;

pub fn compile_to_dockerfile(source: &str) -> Result<String, Diagnostic> {
    let ast = parser::parse_program(source)?;
    let model = semantics::build_model(&ast)?;
    validate::validate(&model)?;
    generator::dockerfile::generate(&model)
}
