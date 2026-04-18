package city.fini.ddsl.model;

import city.fini.ddsl.ast.Ast;
import java.util.List;
import java.util.Optional;

public final class Model {
  private Model() {}

  public record ProgramModel(List<StageModel> stages) {}

  public record StageModel(String name, Ast.StageKind kind, BaseModel base, List<StageOp> statements) {}

  public record BaseModel(Ast.BaseFamily family, Ast.Variant variant, Ast.Distro distro, String image, BaseMetadata metadata) {}

  public record BaseMetadata(boolean mutable, PackageManager packageManager, boolean shellAvailable) {}

  public enum PackageManager {
    APK,
    APT,
    DNF,
    NONE
  }

  public record ArtifactPath(String raw, ArtifactPathKind kind) {
    public static ArtifactPath of(String raw) {
      boolean pattern = raw.indexOf('*') >= 0 || raw.indexOf('?') >= 0 || raw.indexOf('[') >= 0;
      return new ArtifactPath(raw, pattern ? ArtifactPathKind.PATTERN : ArtifactPathKind.EXACT);
    }

    public boolean isPattern() {
      return kind == ArtifactPathKind.PATTERN;
    }
  }

  public enum ArtifactPathKind {
    EXACT,
    PATTERN
  }

  public sealed interface StageOp permits Workdir, User, Update, InstallTools, CopyPaths, CopyArtifact,
      RunTool, ProduceArtifact, CarryTool, Runtime, Expose {}

  public record Workdir(String path) implements StageOp {}

  public record User(String user) implements StageOp {}

  public record Update() implements StageOp {}

  public record InstallTools(List<String> tools) implements StageOp {}

  public record CopyPaths(List<String> sources, String dest) implements StageOp {}

  public record CopyArtifact(String name, Optional<String> targetName, String fromStage, ArtifactPath sourcePath, String dest)
      implements StageOp {}

  public record RunTool(Ast.ToolKind kind, Ast.ToolConfig config) implements StageOp {}

  public record ProduceArtifact(String name, ArtifactPath path) implements StageOp {}

  public record CarryTool(String name, String fromStage) implements StageOp {}

  public record Runtime(Ast.RuntimeKind kind, Ast.RuntimeConfig config) implements StageOp {}

  public record Expose(int port) implements StageOp {}
}
