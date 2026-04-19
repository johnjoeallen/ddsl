package ie.bitstep.ddsl.diagnostics;

public record Diagnostic(String message, Span span, String help) {
  public Diagnostic(String message, Span span) {
    this(message, span, null);
  }

  @Override
  public String toString() {
    String rendered = span == null ? message : message + " at " + span.start() + ".." + span.end();
    return help == null ? rendered : rendered + "\nhelp: " + help;
  }
}
