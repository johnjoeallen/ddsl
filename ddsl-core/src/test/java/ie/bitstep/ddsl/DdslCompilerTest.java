package ie.bitstep.ddsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ie.bitstep.ddsl.diagnostics.DiagnosticRenderer;
import ie.bitstep.ddsl.diagnostics.DdslException;
import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DdslCompilerTest {
  private final DdslCompiler compiler = new DdslCompiler();

  @Test
  void goldenExamplesMatch() throws IOException {
    assertGolden("go");
    assertGolden("java");
    assertGolden("python");
    assertGolden("node");
  }

  @Test
  void syntaxErrorDiagnosticsMatch() throws IOException {
    for (String name : List.of("hyphenated-stage", "missing-stage-as", "quoted-copy-artifact", "quoted-produced-artifact")) {
      assertSyntaxErrorGolden(name);
    }
  }

  @Test
  void rejectsUpdateInImageStage() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage image as image {
          base chainguard { variant runtime distro wolfi image "img" }
          update
        }
        """));
    assertTrue(err.diagnostic().message().contains("update is not allowed in image stages"));
  }

  @Test
  void rejectsCarryToolNotDeclared() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage image as image {
          base chainguard { variant runtime distro wolfi image "img" }
          carry tool aws-cli
        }
        """));
    assertTrue(err.diagnostic().message().contains("carry tool 'aws-cli' requires"));
  }

  @Test
  void rejectsMissingArtifactReference() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage image as image {
          base chainguard { variant runtime distro wolfi image "img" }
          copy artifact jar to "/app/app.jar"
        }
        """));
    assertTrue(err.diagnostic().message().contains("artifact 'jar' is referenced"));
  }

  @Test
  void rejectsArtifactQualifiedWithWrongStage() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage package as build {
          base chainguard { variant dev distro wolfi image "img" }
          produces artifact application at "/app/target/application.jar"
        }
        stage image as image {
          base chainguard { variant runtime distro wolfi image "img" }
          copy artifact other_stage.application as app to "/app/app.jar"
        }
        """));
    assertTrue(err.diagnostic().message().contains("but was produced by stage 'package'"));
  }

  @Test
  void rejectsRuntimeOutsideImageStage() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage package as build {
          base chainguard { variant dev distro wolfi image "img" }
          runtime java { jar "/app/app.jar" }
        }
        """));
    assertTrue(err.diagnostic().message().contains("runtime blocks are only allowed in image stages"));
  }

  @Test
  void rejectsImageStageNotLast() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage image as image {
          base chainguard { variant runtime distro wolfi image "img" }
        }
        stage package as build {
          base chainguard { variant dev distro wolfi image "img2" }
        }
        """));
    assertTrue(err.diagnostic().message().contains("image stage must be last"));
  }

  @Test
  void rejectsUnresolvedVariable() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage image as image {
          base chainguard { variant runtime distro wolfi image "{{REGISTRY}}/img" }
        }
        """));
    assertTrue(err.diagnostic().message().contains("unresolved variable interpolation"));
  }

  @Test
  void rejectsGoBuildWithWildcardArtifactOutput() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage package as build {
          base chainguard { variant dev distro wolfi image "img" }
          tool go { build true }
          produces artifact binary at "/app/bin/*"
        }
        """));
    assertTrue(err.diagnostic().message().contains("go build output artifact must use an exact path"));
  }

  @Test
  void rejectsHyphenatedIdentifiers() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage build-java as build {
          base chainguard { variant dev distro wolfi image "img" }
        }
        """));
    assertTrue(err.diagnostic().message().contains("hyphenated identifiers are not supported"));
  }

  private void assertGolden(String name) throws IOException {
    String source = Files.readString(Path.of("..", "examples", name + ".dsl"));
    String expected = Files.readString(Path.of("..", "expected", name + ".Dockerfile")).stripTrailing();
    String actual = compiler.compileToDockerfile(source).stripTrailing();
    assertEquals(expected, actual, name + " golden mismatch");
  }

  private void assertSyntaxErrorGolden(String name) throws IOException {
    Path sourcePath = Path.of("..", "examples", "errors", name + ".dsl");
    String source = Files.readString(sourcePath);
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile(source));
    String expected = Files.readString(Path.of("..", "expected", "errors", name + ".err")).stripTrailing();
    String actual = DiagnosticRenderer.render(err.diagnostic(), "examples/errors/" + name + ".dsl", source).stripTrailing();
    assertEquals(expected, actual, name + " diagnostic mismatch");
  }
}
