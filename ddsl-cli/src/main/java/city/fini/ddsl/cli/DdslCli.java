package city.fini.ddsl.cli;

import city.fini.ddsl.DdslCompiler;
import city.fini.ddsl.diagnostics.DiagnosticRenderer;
import city.fini.ddsl.diagnostics.DdslException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DdslCli {
  private DdslCli() {}

  public static void main(String[] args) {
    int exit = run(args);
    if (exit != 0) System.exit(exit);
  }

  static int run(String[] args) {
    if (args.length != 1 && args.length != 3) {
      System.err.println("usage: ddsl <input.dsl> [-o output.Dockerfile]");
      return 2;
    }
    if (args.length == 3 && !args[1].equals("-o")) {
      System.err.println("usage: ddsl <input.dsl> [-o output.Dockerfile]");
      return 2;
    }

    try {
      String source = Files.readString(Path.of(args[0]));
      String dockerfile = new DdslCompiler().compileToDockerfile(source);
      if (args.length == 3) {
        Path output = Path.of(args[2]);
        Path parent = output.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, dockerfile);
      } else {
        System.out.print(dockerfile);
      }
      return 0;
    } catch (DdslException err) {
      String source = readSourceBestEffort(args[0]);
      System.err.println(DiagnosticRenderer.render(err.diagnostic(), args[0], source));
      return 1;
    } catch (IOException err) {
      System.err.println("error: " + err.getMessage());
      return 1;
    }
  }

  private static String readSourceBestEffort(String path) {
    try {
      return Files.readString(Path.of(path));
    } catch (IOException ignored) {
      return null;
    }
  }
}
