package ie.bitstep.ddsl.parser;

import ie.bitstep.ddsl.diagnostics.Span;

record Token(TokenType type, String text, Span span) {}
