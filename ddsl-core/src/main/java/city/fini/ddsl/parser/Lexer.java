package city.fini.ddsl.parser;

import city.fini.ddsl.diagnostics.DdslException;
import city.fini.ddsl.diagnostics.Span;
import java.util.ArrayList;
import java.util.List;

final class Lexer {
  private final String source;
  private int pos;

  Lexer(String source) {
    this.source = source;
  }

  List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    while (true) {
      skipWhitespaceAndComments();
      if (pos >= source.length()) {
        tokens.add(new Token(TokenType.EOF, "", new Span(pos, pos)));
        return tokens;
      }
      int start = pos;
      char c = source.charAt(pos);
      switch (c) {
        case '{' -> {
          pos++;
          tokens.add(new Token(TokenType.LBRACE, "{", new Span(start, pos)));
        }
        case '}' -> {
          pos++;
          tokens.add(new Token(TokenType.RBRACE, "}", new Span(start, pos)));
        }
        case '[' -> {
          pos++;
          tokens.add(new Token(TokenType.LBRACKET, "[", new Span(start, pos)));
        }
        case ']' -> {
          pos++;
          tokens.add(new Token(TokenType.RBRACKET, "]", new Span(start, pos)));
        }
        case ',' -> {
          pos++;
          tokens.add(new Token(TokenType.COMMA, ",", new Span(start, pos)));
        }
        case '=' -> {
          pos++;
          tokens.add(new Token(TokenType.EQUAL, "=", new Span(start, pos)));
        }
        case '"' -> tokens.add(readString());
        default -> {
          if (Character.isDigit(c)) {
            tokens.add(readInteger());
          } else if (isIdentChar(c)) {
            tokens.add(readIdent());
          } else {
            throw new DdslException("unexpected character '" + c + "'", new Span(start, start + 1));
          }
        }
      }
    }
  }

  private void skipWhitespaceAndComments() {
    while (pos < source.length()) {
      char c = source.charAt(pos);
      if (Character.isWhitespace(c)) {
        pos++;
      } else if (c == '#') {
        while (pos < source.length() && source.charAt(pos) != '\n') {
          pos++;
        }
      } else {
        return;
      }
    }
  }

  private Token readString() {
    int start = pos++;
    StringBuilder value = new StringBuilder();
    while (pos < source.length()) {
      char c = source.charAt(pos++);
      if (c == '"') {
        return new Token(TokenType.STRING, value.toString(), new Span(start, pos));
      }
      if (c == '\\') {
        if (pos >= source.length()) {
          throw new DdslException("unterminated string escape", new Span(start, pos));
        }
        char escaped = source.charAt(pos++);
        value.append(switch (escaped) {
          case '"', '\\' -> escaped;
          case 'n' -> '\n';
          case 't' -> '\t';
          default -> escaped;
        });
      } else {
        value.append(c);
      }
    }
    throw new DdslException("unterminated string literal", new Span(start, pos));
  }

  private Token readInteger() {
    int start = pos;
    while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
      pos++;
    }
    return new Token(TokenType.INTEGER, source.substring(start, pos), new Span(start, pos));
  }

  private Token readIdent() {
    int start = pos;
    while (pos < source.length() && isIdentChar(source.charAt(pos))) {
      pos++;
    }
    return new Token(TokenType.IDENT, source.substring(start, pos), new Span(start, pos));
  }

  private boolean isIdentChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-';
  }
}
