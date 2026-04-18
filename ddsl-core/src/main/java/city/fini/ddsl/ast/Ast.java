package city.fini.ddsl.ast;

import city.fini.ddsl.diagnostics.Span;
import java.util.List;
import java.util.Optional;

public final class Ast {
  private Ast() {}

  public record Node<T>(T value, Span span) {}

  public record Program(List<LetDecl> lets, List<StageDecl> stages) {}

  public record LetDecl(Node<String> name, Node<String> value) {}

  public record StageDecl(Node<String> name, Node<StageKind> kind, List<StageStmt> statements, Span span) {}

  public enum StageKind {
    BUILD,
    TEST,
    IMAGE
  }

  public sealed interface StageStmt permits BaseDecl, Workdir, User, Update, ToolsBlock, CopyPaths,
      CopyArtifact, ToolBlock, ArtifactDecl, CarryTool, RuntimeBlock, Expose {}

  public record BaseDecl(Node<BaseFamily> family, Node<Variant> variant, Node<Distro> distro, Node<String> image, Span span)
      implements StageStmt {}

  public enum BaseFamily {
    CHAINGUARD
  }

  public enum Variant {
    DEV,
    RUNTIME
  }

  public enum Distro {
    WOLFI,
    DEBIAN,
    UBUNTU,
    RHEL,
    ALPINE,
    DISTROLESS,
    WINDOWS
  }

  public record Workdir(Node<String> path) implements StageStmt {}

  public record User(Node<String> user) implements StageStmt {}

  public record Update(Span span) implements StageStmt {}

  public record ToolsBlock(List<Node<String>> buildTools, Span span) implements StageStmt {}

  public record CopyPaths(List<Node<String>> sources, Node<String> dest, Span span) implements StageStmt {}

  public record CopyArtifact(Node<String> name, Optional<Node<String>> targetName, Node<String> dest, Span span)
      implements StageStmt {}

  public record ToolBlock(Node<ToolKind> kind, ToolConfig config, Span span) implements StageStmt {}

  public enum ToolKind {
    GO,
    MAVEN,
    PYTHON,
    NODE
  }

  public sealed interface ToolConfig permits GoTool, MavenTool, PythonTool, NodeTool {}

  public record GoTool(boolean build) implements ToolConfig {}

  public record MavenTool(List<String> goals, boolean skipTests) implements ToolConfig {}

  public record PythonTool(String requirements) implements ToolConfig {}

  public record NodeTool(NodeInstall install) implements ToolConfig {}

  public enum NodeInstall {
    PROD
  }

  public record ArtifactDecl(Node<String> name, Node<String> path, Span span) implements StageStmt {}

  public record CarryTool(Node<String> name, Span span) implements StageStmt {}

  public record RuntimeBlock(Node<RuntimeKind> kind, RuntimeConfig config, Span span) implements StageStmt {}

  public enum RuntimeKind {
    BINARY,
    JAVA,
    PYTHON,
    NODE
  }

  public sealed interface RuntimeConfig permits BinaryRuntime, JavaRuntime, PythonRuntime, NodeRuntime {}

  public record BinaryRuntime(String entry) implements RuntimeConfig {}

  public record JavaRuntime(String jar) implements RuntimeConfig {}

  public record PythonRuntime(String entry) implements RuntimeConfig {}

  public record NodeRuntime(String entry) implements RuntimeConfig {}

  public record Expose(Node<Integer> port) implements StageStmt {}
}
