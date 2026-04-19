package city.fini.ddsl.parser;

import city.fini.ddsl.ast.Ast;
import city.fini.ddsl.ast.Ast.*;
import city.fini.ddsl.diagnostics.DdslException;
import city.fini.ddsl.diagnostics.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DdslParser {
  private final List<Token> tokens;
  private int pos;

  private DdslParser(String source) {
    this.tokens = new Lexer(source).tokenize();
  }

  public static Program parse(String source) {
    return new DdslParser(source).parseProgram();
  }

  private Program parseProgram() {
    List<LetDecl> lets = new ArrayList<>();
    List<StageDecl> stages = new ArrayList<>();
    while (!check(TokenType.EOF)) {
      if (checkIdent("let")) {
        lets.add(parseLet());
      } else if (checkIdent("stage")) {
        stages.add(parseStage());
      } else {
        throw error(peek(), "expected 'let' or 'stage'");
      }
    }
    return new Program(List.copyOf(lets), List.copyOf(stages));
  }

  private LetDecl parseLet() {
    expectIdent("let");
    Node<String> name = identNode();
    expect(TokenType.EQUAL, "expected '=' after let name");
    Node<String> value = stringNode();
    return new LetDecl(name, value);
  }

  private StageDecl parseStage() {
    Token start = expectIdent("stage");
    Node<String> name = identNode();
    if (!checkIdent("as")) {
      throw error(
          peek(),
          "invalid stage declaration: expected 'as' after stage name",
          "write `stage <name> as build|test|image { ... }`");
    }
    expectIdent("as");
    Node<StageKind> kind = new Node<>(stageKind(expect(TokenType.IDENT, "expected stage kind")), previous().span());
    expect(TokenType.LBRACE, "expected '{' after stage declaration");
    List<StageStmt> statements = new ArrayList<>();
    while (!check(TokenType.RBRACE)) {
      statements.add(parseStageStmt());
    }
    Token end = expect(TokenType.RBRACE, "expected '}' after stage");
    return new StageDecl(name, kind, List.copyOf(statements), new Span(start.span().start(), end.span().end()));
  }

  private StageStmt parseStageStmt() {
    if (checkIdent("base")) return parseBase();
    if (checkIdent("workdir")) {
      expectIdent("workdir");
      return new Workdir(stringNode());
    }
    if (checkIdent("user")) {
      expectIdent("user");
      return new User(stringNode());
    }
    if (checkIdent("update")) {
      return new Update(expectIdent("update").span());
    }
    if (checkIdent("tools")) return parseTools();
    if (checkIdent("copy")) return parseCopy();
    if (checkIdent("tool")) return parseTool();
    if (checkIdent("produces")) return parseProduces();
    if (checkIdent("carry")) return parseCarry();
    if (checkIdent("runtime")) return parseRuntime();
    if (checkIdent("expose")) {
      expectIdent("expose");
      Token port = expect(TokenType.INTEGER, "expected expose port");
      return new Expose(new Node<>(parsePort(port), port.span()));
    }
    throw error(peek(), "unexpected stage statement");
  }

  private BaseDecl parseBase() {
    Token start = expectIdent("base");
    Node<BaseFamily> family = new Node<>(baseFamily(expect(TokenType.IDENT, "expected base family")), previous().span());
    expect(TokenType.LBRACE, "expected '{' after base family");
    Node<Variant> variant = null;
    Node<Distro> distro = null;
    Node<String> image = null;
    while (!check(TokenType.RBRACE)) {
      if (checkIdent("variant")) {
        expectIdent("variant");
        Token value = expect(TokenType.IDENT, "expected base variant");
        variant = new Node<>(variant(value), value.span());
      } else if (checkIdent("distro")) {
        expectIdent("distro");
        Token value = expect(TokenType.IDENT, "expected base distro");
        distro = new Node<>(distro(value), value.span());
      } else if (checkIdent("image")) {
        expectIdent("image");
        image = stringNode();
      } else {
        throw error(peek(), "unexpected base field");
      }
    }
    Token end = expect(TokenType.RBRACE, "expected '}' after base");
    Span span = new Span(start.span().start(), end.span().end());
    if (variant == null) throw new DdslException("base variant is required", span);
    if (distro == null) throw new DdslException("base distro is required", span);
    if (image == null) throw new DdslException("base image is required", span);
    return new BaseDecl(family, variant, distro, image, span);
  }

  private ToolsBlock parseTools() {
    Token start = expectIdent("tools");
    expect(TokenType.LBRACE, "expected '{' after tools");
    expectIdent("build");
    List<Node<String>> tools = identList();
    Token end = expect(TokenType.RBRACE, "expected '}' after tools");
    return new ToolsBlock(List.copyOf(tools), new Span(start.span().start(), end.span().end()));
  }

  private StageStmt parseCopy() {
    Token start = expectIdent("copy");
    if (matchIdent("artifact")) {
      if (check(TokenType.STRING)) {
        throw error(
            peek(),
            "artifact references are identifiers, not strings",
            "write `copy artifact package.application as app to \"/app/app.jar\"`");
      }
      Node<String> name = identNode();
      Optional<Node<String>> sourceStage = Optional.empty();
      if (match(TokenType.DOT)) {
        sourceStage = Optional.of(name);
        if (check(TokenType.STRING)) {
          throw error(
              peek(),
              "artifact labels are identifiers, not strings",
              "write `copy artifact package.application to \"/path\"`");
        }
        name = identNode();
      }
      Optional<Node<String>> target = Optional.empty();
      if (matchIdent("as")) {
        if (check(TokenType.STRING)) {
          throw error(
              peek(),
              "target artifact names are identifiers, not strings",
              "write `copy artifact package.application as app to \"/app/app.jar\"`");
        }
        target = Optional.of(identNode());
      }
      expectIdent("to");
      Node<String> dest = stringNode();
      return new CopyArtifact(sourceStage, name, target, dest, new Span(start.span().start(), dest.span().end()));
    }
    List<Node<String>> sources = stringList();
    expectIdent("to");
    Node<String> dest = stringNode();
    return new CopyPaths(List.copyOf(sources), dest, new Span(start.span().start(), dest.span().end()));
  }

  private ToolBlock parseTool() {
    Token start = expectIdent("tool");
    Token kindToken = expect(TokenType.IDENT, "expected tool kind");
    ToolKind kind = toolKind(kindToken);
    expect(TokenType.LBRACE, "expected '{' after tool kind");
    ToolConfig config = switch (kind) {
      case GO -> parseGoTool();
      case MAVEN -> parseMavenTool();
      case PYTHON -> parsePythonTool();
      case NODE -> parseNodeTool();
    };
    Token end = expect(TokenType.RBRACE, "expected '}' after tool");
    return new ToolBlock(new Node<>(kind, kindToken.span()), config, new Span(start.span().start(), end.span().end()));
  }

  private ToolConfig parseGoTool() {
    boolean build = false;
    while (!check(TokenType.RBRACE)) {
      expectIdent("build");
      build = booleanValue(expect(TokenType.IDENT, "expected boolean"));
    }
    return new GoTool(build);
  }

  private ToolConfig parseMavenTool() {
    List<String> goals = List.of();
    boolean skipTests = false;
    while (!check(TokenType.RBRACE)) {
      if (matchIdent("goals")) {
        goals = stringList().stream().map(Node::value).toList();
      } else if (matchIdent("skipTests")) {
        skipTests = booleanValue(expect(TokenType.IDENT, "expected boolean"));
      } else {
        throw error(peek(), "unexpected maven tool field");
      }
    }
    return new MavenTool(goals, skipTests);
  }

  private ToolConfig parsePythonTool() {
    String requirements = null;
    while (!check(TokenType.RBRACE)) {
      expectIdent("pip");
      expect(TokenType.LBRACE, "expected '{' after pip");
      expectIdent("requirements");
      requirements = stringNode().value();
      expect(TokenType.RBRACE, "expected '}' after pip");
    }
    if (requirements == null) throw new DdslException("tool python requires pip requirements", peek().span());
    return new PythonTool(requirements);
  }

  private ToolConfig parseNodeTool() {
    NodeInstall install = null;
    while (!check(TokenType.RBRACE)) {
      expectIdent("install");
      Token mode = expect(TokenType.IDENT, "expected node install mode");
      if (!mode.text().equals("prod")) throw error(mode, "unsupported node install mode");
      install = NodeInstall.PROD;
    }
    if (install == null) throw new DdslException("tool node requires install mode", peek().span());
    return new NodeTool(install);
  }

  private ArtifactDecl parseProduces() {
    Token start = expectIdent("produces");
    expectIdent("artifact");
    if (check(TokenType.STRING)) {
      throw error(
          peek(),
          "artifact labels are identifiers, not strings",
          "write `produces artifact application at \"/path/or/pattern\"`");
    }
    Node<String> name = identNode();
    expectIdent("at");
    Node<String> path = stringNode();
    return new ArtifactDecl(name, path, new Span(start.span().start(), path.span().end()));
  }

  private CarryTool parseCarry() {
    Token start = expectIdent("carry");
    expectIdent("tool");
    Node<String> name = logicalNameNode();
    return new CarryTool(name, new Span(start.span().start(), name.span().end()));
  }

  private RuntimeBlock parseRuntime() {
    Token start = expectIdent("runtime");
    Token kindToken = expect(TokenType.IDENT, "expected runtime kind");
    RuntimeKind kind = runtimeKind(kindToken);
    expect(TokenType.LBRACE, "expected '{' after runtime kind");
    RuntimeConfig config = switch (kind) {
      case BINARY -> new BinaryRuntime(requiredRuntimeString("entry"));
      case JAVA -> new JavaRuntime(requiredRuntimeString("jar"));
      case PYTHON -> new PythonRuntime(requiredRuntimeString("entry"));
      case NODE -> new NodeRuntime(requiredRuntimeString("entry"));
    };
    Token end = expect(TokenType.RBRACE, "expected '}' after runtime");
    return new RuntimeBlock(new Node<>(kind, kindToken.span()), config, new Span(start.span().start(), end.span().end()));
  }

  private String requiredRuntimeString(String field) {
    expectIdent(field);
    return stringNode().value();
  }

  private List<Node<String>> identList() {
    expect(TokenType.LBRACKET, "expected '['");
    List<Node<String>> values = new ArrayList<>();
    if (!check(TokenType.RBRACKET)) {
      do {
        values.add(logicalNameNode());
      } while (match(TokenType.COMMA) && !check(TokenType.RBRACKET));
    }
    expect(TokenType.RBRACKET, "expected ']'");
    return values;
  }

  private List<Node<String>> stringList() {
    expect(TokenType.LBRACKET, "expected '['");
    List<Node<String>> values = new ArrayList<>();
    if (!check(TokenType.RBRACKET)) {
      do {
        values.add(stringNode());
      } while (match(TokenType.COMMA) && !check(TokenType.RBRACKET));
    }
    expect(TokenType.RBRACKET, "expected ']'");
    return values;
  }

  private Node<String> identNode() {
    Token token = expect(TokenType.IDENT, "expected identifier");
    if (check(TokenType.DASH)) {
      throw error(
          peek(),
          "hyphenated identifiers are not supported; use '_' in identifiers",
          "rename the identifier without '-' characters, for example `package` or `java_package`");
    }
    return new Node<>(token.text(), token.span());
  }

  private Node<String> logicalNameNode() {
    Token first = expect(TokenType.IDENT, "expected logical name");
    StringBuilder value = new StringBuilder(first.text());
    int end = first.span().end();
    while (match(TokenType.DASH)) {
      Token segment = expect(TokenType.IDENT, "expected name segment after '-'");
      value.append('-').append(segment.text());
      end = segment.span().end();
    }
    return new Node<>(value.toString(), new Span(first.span().start(), end));
  }

  private Node<String> stringNode() {
    Token token = expect(TokenType.STRING, "expected string");
    return new Node<>(token.text(), token.span());
  }

  private int parsePort(Token token) {
    int port = Integer.parseInt(token.text());
    if (port < 0 || port > 65535) throw error(token, "expose port must be between 0 and 65535");
    return port;
  }

  private boolean booleanValue(Token token) {
    return switch (token.text()) {
      case "true" -> true;
      case "false" -> false;
      default -> throw error(token, "expected boolean");
    };
  }

  private StageKind stageKind(Token token) {
    return switch (token.text()) {
      case "build" -> StageKind.BUILD;
      case "test" -> StageKind.TEST;
      case "image" -> StageKind.IMAGE;
      default -> throw error(token, "unsupported stage kind '" + token.text() + "'");
    };
  }

  private BaseFamily baseFamily(Token token) {
    if (token.text().equals("chainguard")) return BaseFamily.CHAINGUARD;
    throw error(token, "unsupported base family '" + token.text() + "'");
  }

  private Variant variant(Token token) {
    return switch (token.text()) {
      case "dev" -> Variant.DEV;
      case "runtime" -> Variant.RUNTIME;
      default -> throw error(token, "unsupported variant '" + token.text() + "'");
    };
  }

  private Distro distro(Token token) {
    return switch (token.text()) {
      case "wolfi" -> Distro.WOLFI;
      case "debian" -> Distro.DEBIAN;
      case "ubuntu" -> Distro.UBUNTU;
      case "rhel" -> Distro.RHEL;
      case "alpine" -> Distro.ALPINE;
      case "distroless" -> Distro.DISTROLESS;
      case "windows" -> Distro.WINDOWS;
      default -> throw error(token, "unsupported distro '" + token.text() + "'");
    };
  }

  private ToolKind toolKind(Token token) {
    return switch (token.text()) {
      case "go" -> ToolKind.GO;
      case "maven" -> ToolKind.MAVEN;
      case "python" -> ToolKind.PYTHON;
      case "node" -> ToolKind.NODE;
      default -> throw error(token, "unsupported tool kind '" + token.text() + "'");
    };
  }

  private RuntimeKind runtimeKind(Token token) {
    return switch (token.text()) {
      case "binary" -> RuntimeKind.BINARY;
      case "java" -> RuntimeKind.JAVA;
      case "python" -> RuntimeKind.PYTHON;
      case "node" -> RuntimeKind.NODE;
      default -> throw error(token, "unsupported runtime kind '" + token.text() + "'");
    };
  }

  private boolean check(TokenType type) {
    return peek().type() == type;
  }

  private boolean checkIdent(String text) {
    return check(TokenType.IDENT) && peek().text().equals(text);
  }

  private boolean match(TokenType type) {
    if (!check(type)) return false;
    pos++;
    return true;
  }

  private boolean matchIdent(String text) {
    if (!checkIdent(text)) return false;
    pos++;
    return true;
  }

  private Token expect(TokenType type, String message) {
    if (check(type)) return tokens.get(pos++);
    throw error(peek(), message);
  }

  private Token expectIdent(String text) {
    if (checkIdent(text)) return tokens.get(pos++);
    throw error(peek(), "expected '" + text + "'");
  }

  private Token peek() {
    return tokens.get(pos);
  }

  private Token previous() {
    return tokens.get(pos - 1);
  }

  private DdslException error(Token token, String message) {
    return new DdslException(message, token.span());
  }

  private DdslException error(Token token, String message, String help) {
    return new DdslException(message, token.span(), help);
  }
}
