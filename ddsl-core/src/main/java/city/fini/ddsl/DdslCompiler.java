package city.fini.ddsl;

import city.fini.ddsl.generator.DockerfileGenerator;
import city.fini.ddsl.parser.DdslParser;
import city.fini.ddsl.semantics.SemanticAnalyzer;

public final class DdslCompiler {
  public String compileToDockerfile(String source) {
    return DockerfileGenerator.generate(SemanticAnalyzer.analyze(DdslParser.parse(source)));
  }
}
