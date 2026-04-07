package com.example.calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates mathematical expressions.
 * Supports: +, -, *, /, %, decimal numbers
 */
public class ExpressionEvaluator {

    public static double evaluate(String expression) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            throw new Exception("Empty expression");
        }

        // Tokenize the expression
        List<Token> tokens = tokenize(expression);
        
        // Parse and evaluate
        return parseExpression(tokens, new int[]{0});
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder numBuffer = new StringBuilder();
        
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if (Character.isDigit(c) || c == '.') {
                numBuffer.append(c);
            } else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                if (numBuffer.length() > 0) {
                    tokens.add(new Token(TokenType.NUMBER, Double.parseDouble(numBuffer.toString())));
                    numBuffer.setLength(0);
                }
                tokens.add(new Token(TokenType.OPERATOR, c));
            } else if (c == ' ') {
                // Skip spaces
            } else {
                throw new Exception("Invalid character: " + c);
            }
        }
        
        if (numBuffer.length() > 0) {
            tokens.add(new Token(TokenType.NUMBER, Double.parseDouble(numBuffer.toString())));
        }
        
        return tokens;
    }

    private static double parseExpression(List<Token> tokens, int[] pos) {
        double value = parseTerm(tokens, pos);
        
        while (pos[0] < tokens.size()) {
            Token op = tokens.get(pos[0]);
            if (op.type == TokenType.OPERATOR && (op.valueChar == '+' || op.valueChar == '-')) {
                pos[0]++;
                double right = parseTerm(tokens, pos);
                if (op.valueChar == '+') {
                    value += right;
                } else {
                    value -= right;
                }
            } else {
                break;
            }
        }
        
        return value;
    }

    private static double parseTerm(List<Token> tokens, int[] pos) {
        double value = parseFactor(tokens, pos);
        
        while (pos[0] < tokens.size()) {
            Token op = tokens.get(pos[0]);
            if (op.type == TokenType.OPERATOR && (op.valueChar == '*' || op.valueChar == '/' || op.valueChar == '%')) {
                pos[0]++;
                double right = parseFactor(tokens, pos);
                if (op.valueChar == '*') {
                    value *= right;
                } else if (op.valueChar == '/') {
                    if (right == 0) {
                        throw new Exception("Division by zero");
                    }
                    value /= right;
                } else {
                    if (right == 0) {
                        throw new Exception("Modulo by zero");
                    }
                    value %= right;
                }
            } else {
                break;
            }
        }
        
        return value;
    }

    private static double parseFactor(List<Token> tokens, int[] pos) {
        if (pos[0] >= tokens.size()) {
            throw new Exception("Unexpected end of expression");
        }
        
        Token token = tokens.get(pos[0]);
        
        if (token.type == TokenType.NUMBER) {
            pos[0]++;
            return token.numberValue;
        } else if (token.type == TokenType.OPERATOR && token.valueChar == '-') {
            pos[0]++;
            return -parseFactor(tokens, pos);
        } else if (token.type == TokenType.OPERATOR && token.valueChar == '+') {
            pos[0]++;
            return parseFactor(tokens, pos);
        } else {
            throw new Exception("Unexpected token");
        }
    }

    // Token classes
    static class Token {
        TokenType type;
        double numberValue;
        char valueChar;

        Token(TokenType type, double numberValue) {
            this.type = type;
            this.numberValue = numberValue;
        }

        Token(TokenType type, char valueChar) {
            this.type = type;
            this.valueChar = valueChar;
        }
    }

    enum TokenType {
        NUMBER,
        OPERATOR
    }
}
