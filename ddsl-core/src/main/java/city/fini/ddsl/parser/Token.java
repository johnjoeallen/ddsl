package city.fini.ddsl.parser;

import city.fini.ddsl.diagnostics.Span;

record Token(TokenType type, String text, Span span) {}
