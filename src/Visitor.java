import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Objects;

public class Visitor extends SysYParserBaseVisitor<Void>{
    private static int ident = 0;

    private void printIdent() {
        for (int i = 0; i < ident; ++i)
            System.err.print("  ");
    }

    private String getColor(String ruleName){
        switch (ruleName) {
            case "CONST", "INT", "VOID", "IF", "ELSE", "WHILE", "BREAK", "CONTINUE", "RETURN" -> {
                return "orange";
            }
            case "PLUS", "MINUS", "MUL", "DIV", "MOD", "ASSIGN", "EQ", "NEQ", "LT", "GT", "LE", "GE", "NOT", "AND", "OR" -> {
                return "blue";
            }
            case "IDENT" -> {
                return "red";
            }
            case "INTEGER_CONST" -> {
                return "green";
            }
            default -> {
                return "";
            }
        }
    }


    @Override
    public Void visitChildren(RuleNode node) {
        RuleContext ctx = node.getRuleContext();
        int ruleIndex = ctx.getRuleIndex();
        String ruleName = SysYParser.ruleNames[ruleIndex];
        String name = ruleName.substring(0, 1).toUpperCase() + ruleName.substring(1);

        printIdent();
        System.err.println(name);

        ident++;
        Void ret = super.visitChildren(node);
        ident--;

        return ret;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        Token token = node.getSymbol();
        int ruleNum = token.getType() - 1;

        if (ruleNum >= 0) {
            String ruleName = SysYLexer.ruleNames[ruleNum];
            String tokenText = token.getText();
            String color = getColor(ruleName);

            if (Objects.equals(ruleName, "INTEGER_CONST")) {
                if (tokenText.startsWith("0x") || tokenText.startsWith("0X")) {
                    tokenText = String.valueOf(Integer.parseInt(tokenText.substring(2), 16));
                } else if (tokenText.startsWith("0")) {
                    tokenText = String.valueOf(Integer.parseInt(tokenText, 8));
                }
            }

            if (!Objects.equals(color, "")) {
                printIdent();
                System.err.println(tokenText + " " + ruleName + "[" + color + "]");
            }
        }

        return super.visitTerminal(node);
    }
}
