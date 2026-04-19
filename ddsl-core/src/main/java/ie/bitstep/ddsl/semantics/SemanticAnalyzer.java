package ie.bitstep.ddsl.semantics;

import ie.bitstep.ddsl.ast.Ast;
import ie.bitstep.ddsl.ast.Ast.*;
import ie.bitstep.ddsl.diagnostics.DdslException;
import ie.bitstep.ddsl.diagnostics.Span;
import ie.bitstep.ddsl.metadata.Metadata;
import ie.bitstep.ddsl.model.Model;
import ie.bitstep.ddsl.model.Model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SemanticAnalyzer {
  private SemanticAnalyzer() {}

  private record ArtifactLocation(String fromStage, ArtifactPath sourcePath) {}

  public static ProgramModel analyze(Program program) {
    Map<String, String> variables = collectVariables(program);
    Map<String, ArtifactLocation> artifacts = new HashMap<>();
    Set<String> declaredBuildTools = new HashSet<>();
    List<StageModel> stages = new ArrayList<>();

    for (StageDecl stage : program.stages()) {
      BaseDecl baseDecl = stage.statements().stream()
          .filter(BaseDecl.class::isInstance)
          .map(BaseDecl.class::cast)
          .findFirst()
          .orElseThrow(() -> new DdslException("stage '" + stage.name().value() + "' requires a base block", stage.span()));

      BaseModel base = new BaseModel(
          baseDecl.family().value(),
          baseDecl.variant().value(),
          baseDecl.distro().value(),
          resolveString(baseDecl.image().value(), variables, baseDecl.image().span()),
          Metadata.forBase(baseDecl.distro().value(), baseDecl.variant().value()));

      List<StageOp> ops = new ArrayList<>();
      for (StageStmt stmt : stage.statements()) {
        if (stmt instanceof BaseDecl) {
          continue;
        } else if (stmt instanceof Ast.Workdir workdir) {
          ops.add(new Model.Workdir(resolveString(workdir.path().value(), variables, workdir.path().span())));
        } else if (stmt instanceof Ast.User user) {
          ops.add(new Model.User(resolveString(user.user().value(), variables, user.user().span())));
        } else if (stmt instanceof Ast.Update) {
          ops.add(new Model.Update());
        } else if (stmt instanceof ToolsBlock tools) {
          List<String> names = tools.buildTools().stream().map(Ast.Node::value).toList();
          declaredBuildTools.addAll(names);
          ops.add(new InstallTools(names));
        } else if (stmt instanceof Ast.CopyPaths copy) {
          ops.add(new Model.CopyPaths(
              copy.sources().stream().map(s -> resolveString(s.value(), variables, s.span())).toList(),
              resolveString(copy.dest().value(), variables, copy.dest().span())));
        } else if (stmt instanceof Ast.CopyArtifact copy) {
          String name = resolveString(copy.name().value(), variables, copy.name().span());
          Optional<String> targetName = copy.targetName().map(target -> resolveString(target.value(), variables, target.span()));
          ArtifactLocation artifact = Optional.ofNullable(artifacts.get(name)).orElseThrow(() ->
              new DdslException("artifact '" + name + "' is referenced in stage '" + stage.name().value() + "' but was never produced", copy.name().span()));
          if (copy.sourceStage().isPresent() && !artifact.fromStage().equals(copy.sourceStage().get().value())) {
            throw new DdslException(
                "artifact '" + name + "' is referenced from stage '" + copy.sourceStage().get().value()
                    + "' but was produced by stage '" + artifact.fromStage() + "'",
                copy.sourceStage().get().span());
          }
          ops.add(new Model.CopyArtifact(
              name,
              targetName,
              artifact.fromStage(),
              artifact.sourcePath(),
              resolveString(copy.dest().value(), variables, copy.dest().span())));
        } else if (stmt instanceof ToolBlock tool) {
          ops.add(new RunTool(tool.kind().value(), resolveToolConfig(tool.config(), variables, tool.span())));
        } else if (stmt instanceof ArtifactDecl artifact) {
          String name = resolveString(artifact.name().value(), variables, artifact.name().span());
          ArtifactPath path = ArtifactPath.of(resolveString(artifact.path().value(), variables, artifact.path().span()));
          if (artifacts.put(name, new ArtifactLocation(stage.name().value(), path)) != null) {
            throw new DdslException("duplicate artifact name '" + name + "'", artifact.name().span());
          }
          ops.add(new ProduceArtifact(name, path));
        } else if (stmt instanceof Ast.CarryTool carry) {
          String name = carry.name().value();
          String fromStage = findPriorToolStage(stages, name, declaredBuildTools)
              .orElseThrow(() -> new DdslException(
                  "carry tool '" + name + "' requires it to be declared in tools { build [...] } in a prior build stage",
                  carry.name().span()));
          ops.add(new Model.CarryTool(name, fromStage));
        } else if (stmt instanceof RuntimeBlock runtime) {
          ops.add(new Model.Runtime(runtime.kind().value(), resolveRuntimeConfig(runtime.config(), variables, runtime.span())));
        } else if (stmt instanceof Ast.Expose expose) {
          ops.add(new Model.Expose(expose.port().value()));
        }
      }

      stages.add(new StageModel(stage.name().value(), stage.kind().value(), base, List.copyOf(ops)));
    }

    ProgramModel model = new ProgramModel(List.copyOf(stages));
    validate(model);
    return model;
  }

  private static Map<String, String> collectVariables(Program program) {
    Map<String, String> variables = new HashMap<>();
    for (LetDecl decl : program.lets()) {
      variables.put(decl.name().value(), resolveString(decl.value().value(), variables, decl.value().span()));
    }
    return variables;
  }

  private static ToolConfig resolveToolConfig(ToolConfig config, Map<String, String> vars, Span span) {
    if (config instanceof MavenTool maven) {
      return new MavenTool(maven.goals().stream().map(goal -> resolveString(goal, vars, span)).toList(), maven.skipTests());
    }
    if (config instanceof PythonTool python) {
      return new PythonTool(resolveString(python.requirements(), vars, span));
    }
    return config;
  }

  private static RuntimeConfig resolveRuntimeConfig(RuntimeConfig config, Map<String, String> vars, Span span) {
    if (config instanceof BinaryRuntime binary) return new BinaryRuntime(resolveString(binary.entry(), vars, span));
    if (config instanceof JavaRuntime java) return new JavaRuntime(resolveString(java.jar(), vars, span));
    if (config instanceof PythonRuntime python) return new PythonRuntime(resolveString(python.entry(), vars, span));
    if (config instanceof NodeRuntime node) return new NodeRuntime(resolveString(node.entry(), vars, span));
    return config;
  }

  private static String resolveString(String input, Map<String, String> vars, Span span) {
    StringBuilder output = new StringBuilder();
    int cursor = 0;
    while (true) {
      int start = input.indexOf("{{", cursor);
      if (start < 0) {
        output.append(input.substring(cursor));
        return output.toString();
      }
      output.append(input, cursor, start);
      int end = input.indexOf("}}", start + 2);
      if (end < 0) throw new DdslException("unterminated variable interpolation", span);
      String name = input.substring(start + 2, end).trim();
      String value = vars.get(name);
      if (value == null) throw new DdslException("unresolved variable interpolation '" + name + "'", span);
      output.append(value);
      cursor = end + 2;
    }
  }

  private static Optional<String> findPriorToolStage(List<StageModel> stages, String tool, Set<String> declared) {
    if (!declared.contains(tool)) return Optional.empty();
    for (int i = stages.size() - 1; i >= 0; i--) {
      StageModel stage = stages.get(i);
      boolean hasTool = stage.statements().stream()
          .filter(InstallTools.class::isInstance)
          .map(InstallTools.class::cast)
          .anyMatch(op -> op.tools().contains(tool));
      if (hasTool) return Optional.of(stage.name());
    }
    return Optional.empty();
  }

  private static void validate(ProgramModel program) {
    Set<String> stageNames = new HashSet<>();
    for (int i = 0; i < program.stages().size(); i++) {
      StageModel stage = program.stages().get(i);
      if (!stageNames.add(stage.name())) throw new DdslException("duplicate stage name '" + stage.name() + "'", null);
      if (stage.kind() == StageKind.IMAGE && i + 1 != program.stages().size()) {
        throw new DdslException("image stage must be last", null);
      }
      boolean image = stage.kind() == StageKind.IMAGE;
      for (StageOp op : stage.statements()) {
        if (op instanceof Model.Update) {
          if (image) throw new DdslException("update is not allowed in image stages", null);
          if (!stage.base().metadata().mutable()) throw new DdslException("package-manager mutation is not allowed on immutable runtime images", null);
          if (stage.base().metadata().packageManager() == PackageManager.NONE) throw new DdslException("update requires a supported package manager", null);
        } else if (op instanceof InstallTools install) {
          if (image) throw new DdslException("tools blocks are not allowed in image stages", null);
          if (!stage.base().metadata().mutable()) throw new DdslException("tool installation is not allowed on immutable runtime images", null);
          for (String tool : install.tools()) {
            if (Metadata.packageForTool(tool, stage.base().distro()).isEmpty()) {
              throw new DdslException("tool '" + tool + "' is not mapped for this distro", null);
            }
          }
        } else if (op instanceof Model.Runtime) {
          if (!image) throw new DdslException("runtime blocks are only allowed in image stages", null);
          if (!stage.base().metadata().shellAvailable()) {
            throw new DdslException("runtime requires shell/runtime support, but this base has no shell support metadata", null);
          }
        } else if (op instanceof Model.CarryTool carry) {
          if (!image) throw new DdslException("carry tool is only allowed in image stages", null);
          if (Metadata.carriedToolPaths(carry.name()).isEmpty()) {
            throw new DdslException("carried tool '" + carry.name() + "' has no filesystem path mapping", null);
          }
        }
      }
    }
  }
}
