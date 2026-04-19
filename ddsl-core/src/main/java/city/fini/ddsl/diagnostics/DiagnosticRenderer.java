package city.fini.ddsl.diagnostics;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticRenderer {
  private DiagnosticRenderer() {}

  public static String render(Diagnostic diagnostic, String sourceName, String source) {
    StringBuilder out = new StringBuilder("error: ").append(diagnostic.message());
    if (diagnostic.span() != null && source != null) {
      Location location = location(source, diagnostic.span().start());
      String line = line(source, location.line());
      int caretWidth = Math.max(1, Math.min(Math.max(1, diagnostic.span().end() - diagnostic.span().start()), Math.max(1, line.length() - location.column() + 1)));

      out.append("\n\n")
          .append("  --> ")
          .append(sourceName == null ? "<input>" : sourceName)
          .append(':')
          .append(location.line())
          .append(':')
          .append(location.column())
          .append('\n');

      String lineNumber = Integer.toString(location.line());
      out.append("   ").append(lineNumber).append(" | ").append(line).append('\n');
      out.append("   ").append(" ".repeat(lineNumber.length())).append(" | ");
      int caretStart = Math.max(0, location.column() - 1);
      out.append(" ".repeat(caretStart)).append("^".repeat(caretWidth));
    }
    if (diagnostic.help() != null && !diagnostic.help().isBlank()) {
      out.append("\nhelp: ").append(diagnostic.help());
    }
    return out.toString();
  }

  private static Location location(String source, int offset) {
    int bounded = Math.max(0, Math.min(offset, source.length()));
    int line = 1;
    int column = 1;
    for (int i = 0; i < bounded; i++) {
      if (source.charAt(i) == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new Location(line, column);
  }

  private static String line(String source, int targetLine) {
    List<String> lines = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < source.length(); i++) {
      if (source.charAt(i) == '\n') {
        lines.add(source.substring(start, i));
        start = i + 1;
      }
    }
    lines.add(source.substring(start));
    if (targetLine < 1 || targetLine > lines.size()) return "";
    return lines.get(targetLine - 1);
  }

  private record Location(int line, int column) {}
}
