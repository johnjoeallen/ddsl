package ie.bitstep.ddsl;

import ie.bitstep.ddsl.generator.DockerfileGenerator;
import ie.bitstep.ddsl.parser.DdslParser;
import ie.bitstep.ddsl.semantics.SemanticAnalyzer;

public final class DdslCompiler {
  public String compileToDockerfile(String source) {
    return DockerfileGenerator.generate(SemanticAnalyzer.analyze(DdslParser.parse(source)));
  }
}
