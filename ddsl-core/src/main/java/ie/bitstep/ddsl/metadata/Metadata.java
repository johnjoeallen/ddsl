package ie.bitstep.ddsl.metadata;

import ie.bitstep.ddsl.ast.Ast;
import ie.bitstep.ddsl.diagnostics.DdslException;
import ie.bitstep.ddsl.model.Model;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Metadata {
  private static final Map<String, List<String>> CARRIED_TOOL_PATHS = Map.of(
      "git", List.of("/usr/bin/git", "/usr/libexec/git-core"),
      "ca-certificates", List.of("/etc/ssl/certs"),
      "aws-cli", List.of("/usr/bin/aws", "/usr/lib/aws-cli"));

  private Metadata() {}

  public static Model.BaseMetadata forBase(Ast.Distro distro, Ast.Variant variant) {
    if (distro == Ast.Distro.WINDOWS) {
      throw new DdslException("windows base images are not supported by the Dockerfile backend yet", null);
    }
    Model.PackageManager packageManager = switch (distro) {
      case WOLFI, ALPINE -> Model.PackageManager.APK;
      case DEBIAN, UBUNTU -> Model.PackageManager.APT;
      case RHEL -> Model.PackageManager.DNF;
      case DISTROLESS, WINDOWS -> Model.PackageManager.NONE;
    };
    boolean shellAvailable = distro != Ast.Distro.DISTROLESS && distro != Ast.Distro.WINDOWS;
    boolean mutable = variant == Ast.Variant.DEV;
    return new Model.BaseMetadata(mutable, packageManager, shellAvailable);
  }

  public static Optional<String> packageForTool(String tool, Ast.Distro distro) {
    return switch (distro) {
      case WOLFI, ALPINE -> switch (tool) {
        case "git", "curl", "aws-cli", "ca-certificates", "openssl", "build-base", "unzip" -> Optional.of(tool);
        default -> Optional.empty();
      };
      case DEBIAN, UBUNTU -> switch (tool) {
        case "git", "curl", "ca-certificates", "openssl", "unzip" -> Optional.of(tool);
        case "aws-cli" -> Optional.of("awscli");
        case "build-base" -> Optional.of("build-essential");
        default -> Optional.empty();
      };
      case RHEL -> switch (tool) {
        case "git", "curl", "ca-certificates", "openssl", "unzip" -> Optional.of(tool);
        case "aws-cli" -> Optional.of("awscli");
        case "build-base" -> Optional.of("@development-tools");
        default -> Optional.empty();
      };
      case DISTROLESS, WINDOWS -> Optional.empty();
    };
  }

  public static Optional<List<String>> carriedToolPaths(String tool) {
    return Optional.ofNullable(CARRIED_TOOL_PATHS.get(tool));
  }
}
