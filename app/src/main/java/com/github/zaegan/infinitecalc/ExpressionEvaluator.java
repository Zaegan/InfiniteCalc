package com.github.zaegan.infinitecalc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent expression evaluator.
 *
 * Supported operators : + - × ÷ ^ % (and ASCII * /)
 * Supported functions : sin cos tan ln log sqrt (and √ prefix)
 * Supported constants : π  e
 * Supported variables : A–H  (looked up in the variables map)
 *
 * All trig functions operate in radians.
 */
public class ExpressionEvaluator {

    // ── Public API ────────────────────────────────────────────────────────

    /** Full evaluation — expression must be syntactically complete. */
    public static double evaluate(String expression,
                                   Map<String, Double> variables) throws Exception {
        List<Token> tokens = tokenize(expression);
        Parser p = new Parser(tokens, variables);
        double result = p.parseExpression();
        if (p.pos < tokens.size()) throw new Exception("Unexpected token");
        return result;
    }

    /**
     * Partial evaluation for live preview.
     * Auto-closes any unmatched open parentheses before evaluating.
     */
    public static double evaluatePartial(String expression,
                                          Map<String, Double> variables) throws Exception {
        int open = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        StringBuilder sb = new StringBuilder(expression);
        for (int i = 0; i < open; i++) sb.append(')');
        List<Token> tokens = tokenize(sb.toString());
        Parser p = new Parser(tokens, variables);
        double result = p.parseExpression();
        if (p.pos < tokens.size()) throw new Exception("Unexpected token");
        return result;
    }

    /**
     * Returns true if the expression is evaluable via evaluatePartial
     * (i.e. it is a valid partial expression with no hard syntax errors).
     */
    public static boolean isValidPartial(String expression,
                                          Map<String, Double> variables) {
        if (expression == null || expression.trim().isEmpty()) return false;
        // Reject immediately if any ) appears without a matching (
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth < 0) return false; }
        }
        try {
            evaluatePartial(expression, variables);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Tokeniser ─────────────────────────────────────────────────────────

    private static List<Token> tokenize(String expr) throws Exception {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (c == ' ') { i++; continue; }

            // ── Numbers ──────────────────────────────────────────────────
            if (Character.isDigit(c) || c == '.') {
                StringBuilder num = new StringBuilder();
                while (i < expr.length()
                        && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    num.append(expr.charAt(i++));
                }
                try {
                    tokens.add(new Token(TokenType.NUMBER, Double.parseDouble(num.toString())));
                } catch (NumberFormatException e) {
                    throw new Exception("Invalid number: " + num);
                }
                continue;
            }

            // ── Unicode constants / sqrt prefix ───────────────────────────
            if (c == 'π') { tokens.add(new Token(TokenType.CONST_PI)); i++; continue; }
            if (c == '√') { tokens.add(new Token(TokenType.FUNC_SQRT)); i++; continue; }

            // ── Identifiers (functions, constants, variables) ─────────────
            if (Character.isLetter(c)) {
                StringBuilder id = new StringBuilder();
                while (i < expr.length() && Character.isLetter(expr.charAt(i))) {
                    id.append(expr.charAt(i++));
                }
                String name = id.toString();
                switch (name) {
                    case "sin":  tokens.add(new Token(TokenType.FUNC_SIN));  break;
                    case "cos":  tokens.add(new Token(TokenType.FUNC_COS));  break;
                    case "tan":  tokens.add(new Token(TokenType.FUNC_TAN));  break;
                    case "ln":   tokens.add(new Token(TokenType.FUNC_LN));   break;
                    case "log":  tokens.add(new Token(TokenType.FUNC_LOG));  break;
                    case "sqrt": tokens.add(new Token(TokenType.FUNC_SQRT)); break;
                    case "e":    tokens.add(new Token(TokenType.CONST_E));   break;
                    case "pi":   tokens.add(new Token(TokenType.CONST_PI));  break;
                    default:
                        if (name.length() == 1) {
                            char v = name.charAt(0);
                            if ((v >= 'A' && v <= 'Z') || v == '\u03B1' || v == '\u03B2') {
                                tokens.add(new Token(TokenType.VARIABLE, v));
                                break;
                            }
                        }
                        throw new Exception("Unknown identifier: " + name);
                }
                continue;
            }

            // ── Operators and parens ──────────────────────────────────────
            switch (c) {
                case '+':      tokens.add(new Token(TokenType.OP_PLUS));  break;
                case '-':      tokens.add(new Token(TokenType.OP_MINUS)); break;
                case '\u2212': tokens.add(new Token(TokenType.OP_MINUS)); break; // Unicode −
                case '*':      tokens.add(new Token(TokenType.OP_MULT));  break;
                case '\u00D7': tokens.add(new Token(TokenType.OP_MULT));  break; // ×
                case '/':      tokens.add(new Token(TokenType.OP_DIV));   break;
                case '\u00F7': tokens.add(new Token(TokenType.OP_DIV));   break; // ÷
                case '%':      tokens.add(new Token(TokenType.OP_MOD));   break;
                case '^':      tokens.add(new Token(TokenType.OP_POW));   break;
                case '(':      tokens.add(new Token(TokenType.LPAREN));   break;
                case ')':      tokens.add(new Token(TokenType.RPAREN));   break;
                default: throw new Exception("Invalid character: " + c);
            }
            i++;
        }
        return tokens;
    }

    // ── Parser ────────────────────────────────────────────────────────────

    private static class Parser {
        final List<Token> tokens;
        final Map<String, Double> variables;
        int pos = 0;

        Parser(List<Token> tokens, Map<String, Double> variables) {
            this.tokens = tokens;
            this.variables = variables != null ? variables : Collections.<String, Double>emptyMap();
        }

        double parseExpression() throws Exception {
            return parseAdditive();
        }

        // expr := term (('+' | '-') term)*
        double parseAdditive() throws Exception {
            double val = parseMultiplicative();
            while (pos < tokens.size()) {
                TokenType t = tokens.get(pos).type;
                if (t == TokenType.OP_PLUS)  { pos++; val += parseMultiplicative(); }
                else if (t == TokenType.OP_MINUS) { pos++; val -= parseMultiplicative(); }
                else break;
            }
            return val;
        }

        // term := power (('*' | '/' | '%') power)*
        double parseMultiplicative() throws Exception {
            double val = parsePower();
            while (pos < tokens.size()) {
                TokenType t = tokens.get(pos).type;
                if (t == TokenType.OP_MULT) {
                    pos++; val *= parsePower();
                } else if (t == TokenType.OP_DIV) {
                    pos++;
                    double d = parsePower();
                    if (d == 0) throw new Exception("Division by zero");
                    val /= d;
                } else if (t == TokenType.OP_MOD) {
                    pos++;
                    double d = parsePower();
                    if (d == 0) throw new Exception("Modulo by zero");
                    val %= d;
                } else break;
            }
            return val;
        }

        // power := unary ('^' unary)*  — right-associative: 2^3^2 = 2^(3^2)
        double parsePower() throws Exception {
            double base = parseUnary();
            if (pos < tokens.size() && tokens.get(pos).type == TokenType.OP_POW) {
                pos++;
                double exp = parsePower(); // recursive = right-assoc
                return Math.pow(base, exp);
            }
            return base;
        }

        // unary := '-' unary | primary
        double parseUnary() throws Exception {
            if (pos < tokens.size()) {
                if (tokens.get(pos).type == TokenType.OP_MINUS) { pos++; return -parseUnary(); }
                if (tokens.get(pos).type == TokenType.OP_PLUS)  { pos++; return  parseUnary(); }
            }
            return parsePrimary();
        }

        // primary := NUMBER | VARIABLE | CONST | FUNCTION '(' expr ')' | '(' expr ')'
        double parsePrimary() throws Exception {
            if (pos >= tokens.size()) throw new Exception("Unexpected end of expression");
            Token t = tokens.get(pos);

            switch (t.type) {
                case NUMBER:   pos++; return t.number;
                case CONST_E:  pos++; return Math.E;
                case CONST_PI: pos++; return Math.PI;

                case VARIABLE: {
                    pos++;
                    String name = String.valueOf(t.variable);
                    Double val = variables.get(name);
                    if (val == null) throw new Exception("Undefined variable: " + name);
                    return val;
                }

                case LPAREN: {
                    pos++;
                    double val = parseExpression();
                    expect(TokenType.RPAREN, ")");
                    return val;
                }

                case FUNC_SIN: case FUNC_COS: case FUNC_TAN:
                case FUNC_LN:  case FUNC_LOG: case FUNC_SQRT: {
                    TokenType fn = t.type;
                    pos++;
                    expect(TokenType.LPAREN, "( after function name");
                    double arg = parseExpression();
                    expect(TokenType.RPAREN, ") after function argument");
                    switch (fn) {
                        case FUNC_SIN:  return Math.sin(arg);
                        case FUNC_COS:  return Math.cos(arg);
                        case FUNC_TAN:  return Math.tan(arg);
                        case FUNC_LN:
                            if (arg <= 0) throw new Exception("ln of non-positive number");
                            return Math.log(arg);
                        case FUNC_LOG:
                            if (arg <= 0) throw new Exception("log of non-positive number");
                            return Math.log10(arg);
                        case FUNC_SQRT:
                            if (arg < 0) throw new Exception("Square root of negative number");
                            return Math.sqrt(arg);
                        default: throw new Exception("Unknown function");
                    }
                }

                default:
                    throw new Exception("Unexpected token: " + t.type);
            }
        }

        private void expect(TokenType type, String description) throws Exception {
            if (pos >= tokens.size() || tokens.get(pos).type != type)
                throw new Exception("Expected " + description);
            pos++;
        }
    }

    // ── Token model ───────────────────────────────────────────────────────

    enum TokenType {
        NUMBER, VARIABLE, CONST_E, CONST_PI,
        FUNC_SIN, FUNC_COS, FUNC_TAN, FUNC_LN, FUNC_LOG, FUNC_SQRT,
        LPAREN, RPAREN,
        OP_PLUS, OP_MINUS, OP_MULT, OP_DIV, OP_MOD, OP_POW
    }

    static class Token {
        final TokenType type;
        final double number;
        final char variable;

        Token(TokenType type)             { this.type = type; this.number = 0;  this.variable = 0; }
        Token(TokenType type, double n)   { this.type = type; this.number = n;  this.variable = 0; }
        Token(TokenType type, char v)     { this.type = type; this.number = 0;  this.variable = v; }
    }
}
