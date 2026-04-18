use std::env;
use std::fs;
use std::io::{self, Write};
use std::process::ExitCode;

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(err) => {
            eprintln!("error: {err}");
            ExitCode::FAILURE
        }
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let mut args = env::args().skip(1);
    let Some(input) = args.next() else {
        return Err("usage: transpiler <input.dsl> [output.Dockerfile]".into());
    };
    let output = args.next();
    let source = fs::read_to_string(&input)?;
    let dockerfile = ddsl::compile_to_dockerfile(&source)?;

    if let Some(path) = output {
        fs::write(path, dockerfile)?;
    } else {
        io::stdout().write_all(dockerfile.as_bytes())?;
    }
    Ok(())
}
