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

    /** Full evaluation in radians mode (default). */
    public static double evaluate(String expression,
                                   Map<String, Double> variables) throws Exception {
        return evaluate(expression, variables, true);
    }

    /** Full evaluation — expression must be syntactically complete. */
    public static double evaluate(String expression,
                                   Map<String, Double> variables,
                                   boolean useRadians) throws Exception {
        List<Token> tokens = tokenize(expression);
        Parser p = new Parser(tokens, variables, useRadians);
        double result = p.parseExpression();
        if (p.pos < tokens.size()) throw new Exception("Unexpected token");
        return result;
    }

    /** Partial evaluation for live preview in radians mode (default). */
    public static double evaluatePartial(String expression,
                                          Map<String, Double> variables) throws Exception {
        return evaluatePartial(expression, variables, true);
    }

    /**
     * Partial evaluation for live preview.
     * Auto-closes any unmatched open parentheses before evaluating.
     */
    public static double evaluatePartial(String expression,
                                          Map<String, Double> variables,
                                          boolean useRadians) throws Exception {
        int open = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        StringBuilder sb = new StringBuilder(expression);
        for (int i = 0; i < open; i++) sb.append(')');
        List<Token> tokens = tokenize(sb.toString());
        Parser p = new Parser(tokens, variables, useRadians);
        double result = p.parseExpression();
        if (p.pos < tokens.size()) throw new Exception("Unexpected token");
        return result;
    }

    /** isValidPartial in radians mode (default). */
    public static boolean isValidPartial(String expression,
                                          Map<String, Double> variables) {
        return isValidPartial(expression, variables, true);
    }

    /**
     * Returns true if the expression is evaluable via evaluatePartial
     * (i.e. it is a valid partial expression with no hard syntax errors).
     */
    public static boolean isValidPartial(String expression,
                                          Map<String, Double> variables,
                                          boolean useRadians) {
        if (expression == null || expression.trim().isEmpty()) return false;
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth < 0) return false; }
        }
        try {
            evaluatePartial(expression, variables, useRadians);
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
                // Scientific notation: consume e[+/-]digits as part of the number
                // (e.g. 1.5e10, 1.5e-5) — only when 'e' is immediately followed by
                // an optional sign and then at least one digit, to avoid consuming
                // the Euler constant 'e' used as a standalone value.
                if (i < expr.length() && (expr.charAt(i) == 'e' || expr.charAt(i) == 'E')) {
                    int j = i + 1;
                    if (j < expr.length() && (expr.charAt(j) == '+' || expr.charAt(j) == '-')) j++;
                    if (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                        num.append(expr.charAt(i++)); // 'e'
                        if (i < expr.length() && (expr.charAt(i) == '+' || expr.charAt(i) == '-')) {
                            num.append(expr.charAt(i++)); // sign
                        }
                        while (i < expr.length() && Character.isDigit(expr.charAt(i))) {
                            num.append(expr.charAt(i++));
                        }
                    }
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
                while (i < expr.length() && (Character.isLetter(expr.charAt(i))
                        || (id.length() > 0 && Character.isDigit(expr.charAt(i))))) {
                    id.append(expr.charAt(i++));
                }
                String name = id.toString();
                switch (name) {
                    case "sin":   tokens.add(new Token(TokenType.FUNC_SIN));   break;
                    case "cos":   tokens.add(new Token(TokenType.FUNC_COS));   break;
                    case "tan":   tokens.add(new Token(TokenType.FUNC_TAN));   break;
                    case "ln":    tokens.add(new Token(TokenType.FUNC_LN));    break;
                    case "log":   tokens.add(new Token(TokenType.FUNC_LOG));   break;
                    case "sqrt":  tokens.add(new Token(TokenType.FUNC_SQRT));  break;
                    case "asin":  tokens.add(new Token(TokenType.FUNC_ASIN));  break;
                    case "acos":  tokens.add(new Token(TokenType.FUNC_ACOS));  break;
                    case "atan":  tokens.add(new Token(TokenType.FUNC_ATAN));  break;
                    case "sinh":  tokens.add(new Token(TokenType.FUNC_SINH));  break;
                    case "cosh":  tokens.add(new Token(TokenType.FUNC_COSH));  break;
                    case "tanh":  tokens.add(new Token(TokenType.FUNC_TANH));  break;
                    case "exp":   tokens.add(new Token(TokenType.FUNC_EXP));   break;
                    case "cbrt":  tokens.add(new Token(TokenType.FUNC_CBRT));  break;
                    case "nthrt": tokens.add(new Token(TokenType.FUNC_NTHRT)); break;
                    case "abs":   tokens.add(new Token(TokenType.FUNC_ABS));   break;
                    case "round": tokens.add(new Token(TokenType.FUNC_ROUND)); break;
                    case "floor": tokens.add(new Token(TokenType.FUNC_FLOOR)); break;
                    case "ceil":  tokens.add(new Token(TokenType.FUNC_CEIL));  break;
                    case "log2":  tokens.add(new Token(TokenType.FUNC_LOG2));  break;
                    case "logn":  tokens.add(new Token(TokenType.FUNC_LOGN));  break;
                    case "fact":  tokens.add(new Token(TokenType.FUNC_FACT));  break;
                    case "ncr":   tokens.add(new Token(TokenType.FUNC_NCR));   break;
                    case "npr":   tokens.add(new Token(TokenType.FUNC_NPR));   break;
                    case "e":     tokens.add(new Token(TokenType.CONST_E));    break;
                    case "pi":    tokens.add(new Token(TokenType.CONST_PI));   break;
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
                case ',':      tokens.add(new Token(TokenType.COMMA));    break;
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
        final boolean useRadians;
        int pos = 0;

        Parser(List<Token> tokens, Map<String, Double> variables, boolean useRadians) {
            this.tokens = tokens;
            this.variables = variables != null ? variables : Collections.<String, Double>emptyMap();
            this.useRadians = useRadians;
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

                // ── Single-argument functions ────────────────────────────
                case FUNC_SIN: case FUNC_COS: case FUNC_TAN:
                case FUNC_ASIN: case FUNC_ACOS: case FUNC_ATAN:
                case FUNC_SINH: case FUNC_COSH: case FUNC_TANH:
                case FUNC_LN: case FUNC_LOG: case FUNC_SQRT:
                case FUNC_EXP: case FUNC_CBRT:
                case FUNC_ABS: case FUNC_ROUND: case FUNC_FLOOR: case FUNC_CEIL:
                case FUNC_LOG2: case FUNC_FACT: {
                    TokenType fn = t.type;
                    pos++;
                    double arg = parseOneArgBody();
                    switch (fn) {
                        case FUNC_SIN: {
                            double a = useRadians ? arg : Math.toRadians(arg);
                            return snapTrig(Math.sin(a));
                        }
                        case FUNC_COS: {
                            double a = useRadians ? arg : Math.toRadians(arg);
                            return snapTrig(Math.cos(a));
                        }
                        case FUNC_TAN: {
                            double a = useRadians ? arg : Math.toRadians(arg);
                            return snapTrig(Math.tan(a));
                        }
                        case FUNC_ASIN:
                            if (arg < -1 || arg > 1) throw new Exception("asin domain error: argument must be in [-1, 1]");
                            return snapTrig(useRadians ? Math.asin(arg) : Math.toDegrees(Math.asin(arg)));
                        case FUNC_ACOS:
                            if (arg < -1 || arg > 1) throw new Exception("acos domain error: argument must be in [-1, 1]");
                            return snapTrig(useRadians ? Math.acos(arg) : Math.toDegrees(Math.acos(arg)));
                        case FUNC_ATAN:
                            return snapTrig(useRadians ? Math.atan(arg) : Math.toDegrees(Math.atan(arg)));
                        case FUNC_SINH: return Math.sinh(arg);
                        case FUNC_COSH: return Math.cosh(arg);
                        case FUNC_TANH: return Math.tanh(arg);
                        case FUNC_LN:
                            if (arg <= 0) throw new Exception("ln of non-positive number");
                            return Math.log(arg);
                        case FUNC_LOG:
                            if (arg <= 0) throw new Exception("log of non-positive number");
                            return Math.log10(arg);
                        case FUNC_SQRT:
                            if (arg < 0) throw new Exception("Square root of negative number");
                            return Math.sqrt(arg);
                        case FUNC_EXP: return Math.exp(arg);
                        case FUNC_CBRT: return Math.cbrt(arg);
                        case FUNC_ABS: return Math.abs(arg);
                        case FUNC_ROUND: return (double) Math.round(arg);
                        case FUNC_FLOOR: return Math.floor(arg);
                        case FUNC_CEIL:  return Math.ceil(arg);
                        case FUNC_LOG2:
                            if (arg <= 0) throw new Exception("log2 of non-positive number");
                            return Math.log(arg) / Math.log(2);
                        case FUNC_FACT: {
                            if (arg < 0 || arg != Math.floor(arg))
                                throw new Exception("Factorial requires a non-negative integer");
                            if (arg > 170) throw new Exception("Factorial argument too large");
                            double r = 1;
                            for (int k = 2; k <= (int) arg; k++) r *= k;
                            return r;
                        }
                        default: throw new Exception("Unknown function");
                    }
                }

                // ── Two-argument functions ───────────────────────────────
                case FUNC_NTHRT: case FUNC_LOGN: case FUNC_NCR: case FUNC_NPR: {
                    TokenType fn = t.type;
                    pos++;
                    double[] args = parseTwoArgBody();
                    double x = args[0], y = args[1];
                    switch (fn) {
                        case FUNC_NTHRT: {
                            if (x == 0) throw new Exception("nthrt: degree cannot be zero");
                            if (y < 0) {
                                long ni = (long) x;
                                if (x == ni && ni % 2 != 0) return -Math.pow(-y, 1.0 / x);
                                throw new Exception("Even root of negative number");
                            }
                            return Math.pow(y, 1.0 / x);
                        }
                        case FUNC_LOGN: {
                            if (x <= 0 || x == 1) throw new Exception("logn: invalid base");
                            if (y <= 0) throw new Exception("logn of non-positive number");
                            return Math.log(y) / Math.log(x);
                        }
                        case FUNC_NCR: {
                            if (x < 0 || y < 0 || x != Math.floor(x) || y != Math.floor(y))
                                throw new Exception("nCr requires non-negative integers");
                            int n = (int) x, r = (int) y;
                            if (r > n) throw new Exception("nCr: r cannot exceed n");
                            return combinations(n, r);
                        }
                        case FUNC_NPR: {
                            if (x < 0 || y < 0 || x != Math.floor(x) || y != Math.floor(y))
                                throw new Exception("nPr requires non-negative integers");
                            int n = (int) x, r = (int) y;
                            if (r > n) throw new Exception("nPr: r cannot exceed n");
                            double p = 1;
                            for (int k = 0; k < r; k++) p *= (n - k);
                            return p;
                        }
                        default: throw new Exception("Unknown function");
                    }
                }

                default:
                    throw new Exception("Unexpected token: " + t.type);
            }
        }

        private double parseOneArgBody() throws Exception {
            expect(TokenType.LPAREN, "( after function name");
            double arg = parseExpression();
            expect(TokenType.RPAREN, ") after function argument");
            return arg;
        }

        private double[] parseTwoArgBody() throws Exception {
            expect(TokenType.LPAREN, "( after function name");
            double a = parseExpression();
            expect(TokenType.COMMA, ", between arguments");
            double b = parseExpression();
            expect(TokenType.RPAREN, ") after function arguments");
            return new double[]{a, b};
        }

        private void expect(TokenType type, String description) throws Exception {
            if (pos >= tokens.size() || tokens.get(pos).type != type)
                throw new Exception("Expected " + description);
            pos++;
        }
    }

    // ── Trig helper ───────────────────────────────────────────────────────

    /** Snap values within floating-point noise of an integer to that integer.
     *  Handles e.g. sin(π) = 1.22e-16 → 0, cos(π) = -1.0000000000000002 → -1. */
    private static double snapTrig(double v) {
        double rounded = Math.rint(v);
        return (Math.abs(v - rounded) < 1e-10) ? rounded : v;
    }

    /** C(n, r) = n! / (r! * (n-r)!), computed iteratively to avoid large intermediates. */
    private static double combinations(int n, int r) {
        if (r > n - r) r = n - r;
        double result = 1;
        for (int k = 0; k < r; k++) {
            result = result * (n - k) / (k + 1);
        }
        return Math.round(result);
    }

    // ── Token model ───────────────────────────────────────────────────────

    enum TokenType {
        NUMBER, VARIABLE, CONST_E, CONST_PI,
        FUNC_SIN, FUNC_COS, FUNC_TAN, FUNC_LN, FUNC_LOG, FUNC_SQRT,
        FUNC_ASIN, FUNC_ACOS, FUNC_ATAN,
        FUNC_SINH, FUNC_COSH, FUNC_TANH,
        FUNC_EXP, FUNC_CBRT, FUNC_NTHRT,
        FUNC_ABS, FUNC_ROUND, FUNC_FLOOR, FUNC_CEIL,
        FUNC_LOG2, FUNC_LOGN,
        FUNC_FACT, FUNC_NCR, FUNC_NPR,
        LPAREN, RPAREN, COMMA,
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
