package city.fini.ddsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import city.fini.ddsl.diagnostics.DdslException;
import java.io.IOException;
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
          copy artifact "jar" to "/app/app.jar"
        }
        """));
    assertTrue(err.diagnostic().message().contains("artifact 'jar' is referenced"));
  }

  @Test
  void rejectsRuntimeOutsideImageStage() {
    DdslException err = assertThrows(DdslException.class, () -> compiler.compileToDockerfile("""
        stage build-java as build {
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
        stage build-go as build {
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
        stage build-go as build {
          base chainguard { variant dev distro wolfi image "img" }
          tool go { build true }
          produces artifact "binary" at "/app/bin/*"
        }
        """));
    assertTrue(err.diagnostic().message().contains("go build output artifact must use an exact path"));
  }

  private void assertGolden(String name) throws IOException {
    String source = Files.readString(Path.of("..", "examples", name + ".dsl"));
    String expected = Files.readString(Path.of("..", "expected", name + ".Dockerfile")).stripTrailing();
    String actual = compiler.compileToDockerfile(source).stripTrailing();
    assertEquals(expected, actual, name + " golden mismatch");
  }
}
