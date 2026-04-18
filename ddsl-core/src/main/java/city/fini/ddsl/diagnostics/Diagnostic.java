package city.fini.ddsl.diagnostics;

public record Diagnostic(String message, Span span) {
  @Override
  public String toString() {
    return span == null ? message : message + " at " + span.start() + ".." + span.end();
  }
}
