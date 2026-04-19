package ie.bitstep.ddsl.maven;

import ie.bitstep.ddsl.DdslCompiler;
import ie.bitstep.ddsl.diagnostics.DiagnosticRenderer;
import ie.bitstep.ddsl.diagnostics.DdslException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "transpile", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class TranspileMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.basedir}/container.dsl", property = "ddsl.input")
  private File input;

  @Parameter(defaultValue = "${project.build.directory}/Dockerfile", property = "ddsl.output")
  private File output;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      String source = Files.readString(input.toPath());
      String dockerfile = new DdslCompiler().compileToDockerfile(source);
      File parent = output.getParentFile();
      if (parent != null) Files.createDirectories(parent.toPath());
      Files.writeString(output.toPath(), dockerfile);
      getLog().info("Generated Dockerfile: " + output);
    } catch (DdslException err) {
      String source = readSourceBestEffort();
      throw new MojoExecutionException(DiagnosticRenderer.render(err.diagnostic(), input.toString(), source), err);
    } catch (IOException err) {
      throw new MojoExecutionException("failed to transpile " + input + " to " + output, err);
    }
  }

  private String readSourceBestEffort() {
    try {
      return Files.readString(input.toPath());
    } catch (IOException ignored) {
      return null;
    }
  }
}
