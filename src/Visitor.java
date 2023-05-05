import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Objects;

public class Visitor extends SysYParserBaseVisitor<Void>{
    private static int ident = 0;
    private ArrayList<String> text = new ArrayList<>();
    private Scope global;
    private Scope current;
    private boolean error = false;

    public ArrayList<String> getText(){
        return text;
    }

    public boolean isError(){
        return error;
    }

    private void printError(int type, int line, String message){
        error = true;
        System.err.println("Error type " + type + " at Line " + line + ": " + message + ".");
    }

    private String getIdent(){
        return "  ".repeat(Math.max(0, ident));
    }

    private String getColor(String ruleName){
        switch (ruleName) {
            case "CONST":
            case "INT":
            case "VOID":
            case "IF":
            case "ELSE":
            case "WHILE":
            case "BREAK":
            case "CONTINUE":
            case "RETURN": {
                return "orange";
            }
            case "PLUS":
            case "MINUS":
            case "MUL":
            case "DIV":
            case "MOD":
            case "ASSIGN":
            case "EQ":
            case "NEQ":
            case "LT":
            case "GT":
            case "LE":
            case "GE":
            case "NOT":
            case "AND":
            case "OR": {
                return "blue";
            }
            case "IDENT": {
                return "red";
            }
            case "INTEGER_CONST": {
                return "green";
            }
            default: {
                return "";
            }
        }
    }

    private int getLine(ParserRuleContext ctx){return ctx.getStart().getLine();}

    @Override
    public Void visitChildren(RuleNode node) {
        RuleContext ctx = node.getRuleContext();
        int ruleIndex = ctx.getRuleIndex();
        String ruleName = SysYParser.ruleNames[ruleIndex];
        String name = ruleName.substring(0, 1).toUpperCase() + ruleName.substring(1);

        text.add(getIdent()+name+"\n");

        ident++;
        Void ret = super.visitChildren(node);
        ident--;

        return ret;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        Token token = node.getSymbol();
        int ruleNum = token.getType() - 1;

        if(ruleNum < 0){
            return super.visitTerminal(node);
        }

        String ruleName = SysYLexer.ruleNames[ruleNum];
        String text = token.getText();
        String color = getColor(ruleName);

        if (Objects.equals(ruleName, "INTEGER_CONST")) {
            if (text.length() > 2 &&(text.startsWith("0x") || text.startsWith("0X"))) {
                text = String.valueOf(Integer.parseInt(text.substring(2), 16));
            }
            else if (text.length() > 1 && text.startsWith("0")) {
                text = String.valueOf(Integer.parseInt(text.substring(1), 8));
            }
        }

        if (!Objects.equals(color, "")) {
            this.text.add(getIdent()+text+" "+ruleName+"["+color+"]"+"\n");
        }

        return super.visitTerminal(node);
    }

    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        global = new Scope("GlobalScope", null);
        global.addSymbol(new BasicSymbol("int", new BasicType("int")));
        global.addSymbol(new BasicSymbol("null", new BasicType("null")));
        current = global;
        Void ret = super.visitProgram(ctx);
        current = current.getOuterScope();
        return ret;
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        if(current.haveSymbol(funcName)){
            printError(4, getLine(ctx), "函数重复定义: "+funcName);
            return null;
        }

        Type retType = global.getSymbol(ctx.funcType().getText()).getType();
        ArrayList<Type> paramsType = new ArrayList<>();
        FunctionType functionType = new FunctionType(retType, paramsType);
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        return super.visitBlock(ctx);
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        return super.visitLVal(ctx);
    }

    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        return super.visitStmt(ctx);
    }
}
