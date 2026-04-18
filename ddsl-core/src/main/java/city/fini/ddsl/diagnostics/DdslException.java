package city.fini.ddsl.diagnostics;

public final class DdslException extends RuntimeException {
  private final Diagnostic diagnostic;

  public DdslException(String message, Span span) {
    this(new Diagnostic(message, span));
  }

  public DdslException(Diagnostic diagnostic) {
    super(diagnostic.toString());
    this.diagnostic = diagnostic;
  }

  public Diagnostic diagnostic() {
    return diagnostic;
  }
}
