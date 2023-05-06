import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Objects;

public class Visitor extends SysYParserBaseVisitor<Void>{
    private static int ident = 0;
    private final ArrayList<String> text = new ArrayList<>();
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

    private int getLine(ParserRuleContext ctx){
        return ctx.getStart().getLine();
    }

    private String toDecimal(String text){
        if (text.length() > 2 &&(text.startsWith("0x") || text.startsWith("0X"))) {
            text = String.valueOf(Integer.parseInt(text.substring(2), 16));
        }
        else if (text.length() > 1 && text.startsWith("0")) {
            text = String.valueOf(Integer.parseInt(text.substring(1), 8));
        }
        return text;
    }

    private Type getLValType(SysYParser.LValContext ctx){// IDENT (L_BRACKT exp R_BRACKT)*
        String varName = ctx.IDENT().getText();
        Symbol symbol = current.getSymbol(varName);
        if(symbol == null){
            return new BasicType("errorType");
        }
        Type varType = symbol.getType();
        for(SysYParser.ExpContext exp: ctx.exp()){
            if(varType instanceof ArrayType){
                varType = ((ArrayType) varType).getType();
            } else {
                return new BasicType("errorType");
            }
        }
        return varType;
    }

    private Type getExpType(SysYParser.ExpContext ctx){
        /**
         *exp
         *    : L_PAREN exp R_PAREN
         *    | lVal
         *    | number
         *    | IDENT L_PAREN funcRParams? R_PAREN
         *    | unaryOp exp
         *    | exp (MUL | DIV | MOD) exp
         *    | exp (PLUS | MINUS) exp
         *    ;
         */
//        if(ctx == null){
//            return new BasicType("errorType");} else
        if(ctx.L_PAREN() != null){
            return getExpType(ctx.exp(0));
        } else if(ctx.lVal() != null){
            return getLValType(ctx.lVal());
        } else if(ctx.number() != null){
            return new BasicType("int");
        } else if(ctx.unaryOp() != null){
            return getExpType(ctx.exp(0));
        } else if(ctx.MUL() != null || ctx.DIV() != null || ctx.MOD() != null ||
                ctx.PLUS() != null || ctx.MINUS() != null){
            Type op1 = getExpType(ctx.exp(0));
            Type op2 = getExpType(ctx.exp(1));
            if(op1.toString().equals("int") && op2.toString().equals("int")){
                return op1;
            }
        } else if(ctx.IDENT() != null){
            String funcName = ctx.IDENT().getText();
            Symbol symbol = current.getSymbol(funcName);
            if(symbol != null && symbol.getType() instanceof FunctionType){
                FunctionType functionType = (FunctionType) symbol.getType();
                ArrayList<Type> paramsType = functionType.getParamsType();
                ArrayList<Type> argsType = new ArrayList<>();
                if(ctx.funcRParams() != null){
                    for(SysYParser.ParamContext paramContext: ctx.funcRParams().param()){
                        argsType.add(getExpType(paramContext.exp()));
                    }
                }
                if(paramsType.equals(argsType)){
                    return functionType.getRetType();
                }
            }
        }

        return new BasicType("errorType");
    }

    public Type getCondType(SysYParser.CondContext ctx){
        if(ctx.exp() != null){
            return getExpType(ctx.exp());
        }
        Type a = getCondType(ctx.cond(0));
        Type b = getCondType(ctx.cond(1));
        if(a.toString().equals("int") && b.toString().equals("int")){
            return a;
        }

        return new BasicType("errorType");
    }

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
            text = toDecimal(text);
        }

        if (!Objects.equals(color, "")) {
            this.text.add(getIdent()+text+" "+ruleName+"["+color+"]"+"\n");
        }

        return super.visitTerminal(node);
    }

    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        global = new Scope("Global", null);
        global.addSymbol(new BasicSymbol("int", new BasicType("int")));
        global.addSymbol(new BasicSymbol("void", new BasicType("void")));
        current = global;
        Void ret = super.visitProgram(ctx);
        current = current.getOuterScope();
        return ret;
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        Scope scope = new Scope("Block", current);
        current = scope;
        Void ret = super.visitBlock(ctx);
        current = scope.getOuterScope();
        return ret;
    }

    @Override
    public Void visitConstDecl(SysYParser.ConstDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for(SysYParser.ConstDefContext constDefContext: ctx.constDef()){
            Type constType = global.getSymbol(typeName).getType();
            String constName = constDefContext.IDENT().getText();
            if(current.haveSymbol(constName)){
                printError(3, getLine(constDefContext), "变量重复定义: "+constName);
                continue;
            }

            for(SysYParser.ConstExpContext constExpContext: constDefContext.constExp()){
                int element = Integer.parseInt(toDecimal(constExpContext.getText()));
                constType = new ArrayType(constType, element);
            }

            SysYParser.ConstExpContext expContext = constDefContext.constInitVal().constExp();
            if(expContext != null){
                Type initValType = getExpType(expContext.exp());
                if(!initValType.toString().equals("errorType") && !constType.toString().equals(initValType.toString())){
                    printError(5, getLine(constDefContext), "赋值号两侧类型不匹配");
                }
            }

            VariableSymbol constSymbol = new VariableSymbol(constName, constType);
            current.addSymbol(constSymbol);
        }

        return super.visitConstDecl(ctx);
    }

    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for(SysYParser.VarDefContext varDefContext: ctx.varDef()){
            Type varType = global.getSymbol(typeName).getType();
            String varName = varDefContext.IDENT().getText();
            if(current.haveSymbol(varName)){
                printError(3, getLine(varDefContext), "变量重复声明: "+varName);
                continue;
            }

            for(SysYParser.ConstExpContext constExpContext: varDefContext.constExp()){
                int element = Integer.parseInt(toDecimal(constExpContext.getText()));
                varType = new ArrayType(varType, element);
            }

            if(varDefContext.ASSIGN() != null){
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                if(expContext != null){
                    Type initValType = getExpType(expContext);
                    if(!initValType.toString().equals("errorType") && !varType.toString().equals(initValType.toString())){
                        printError(5, getLine(varDefContext), "赋值号两侧类型不匹配");
                    }
                }
            }

            VariableSymbol variableSymbol = new VariableSymbol(varName, varType);
            current.addSymbol(variableSymbol);
        }

        return super.visitVarDecl(ctx);
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
        FunctionSymbol functionSymbol = new FunctionSymbol(funcName, functionType);
        FunctionScope functionScope = new FunctionScope(funcName, current, functionType);
        current.addSymbol(functionSymbol);
        current = functionScope;
        Void ret = super.visitFuncDef(ctx);
        current = current.getOuterScope();
        return ret;
    }

    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        String varTypeName = ctx.bType().getText();
        Type varType = global.getSymbol(varTypeName).getType();
        for(TerminalNode node: ctx.L_BRACKT()){
            varType = new ArrayType(varType, 0);
        }
        String varName = ctx.IDENT().getText();

        if(current.haveSymbol(varName)){
            printError(3, getLine(ctx), "变量重复声明: "+varName);
        } else {
            VariableSymbol variableSymbol = new VariableSymbol(varName, varType);
            current.addSymbol(variableSymbol);
            ((FunctionScope)current).getType().getParamsType().add(varType);
        }

        return super.visitFuncFParam(ctx);
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        String varName = ctx.IDENT().getText();
        Symbol symbol = current.getSymbol(varName);
        if(symbol == null){
            printError(1, getLine(ctx), "变量未声明: "+varName);
            return null;
        }

        Type varType = symbol.getType();
        int dimension = ctx.exp().size();
        for(int i = 0;i < dimension;i++){
            if(varType instanceof ArrayType){
                varType = ((ArrayType)varType).getType();
                SysYParser.ExpContext expContext = ctx.exp(i);
                varName += "[" + expContext.getText() + "]";
            } else {
                printError(9, getLine(ctx), "对非数组使用下标运算符: "+varName);
                break;
            }
        }

        return super.visitLVal(ctx);
    }

    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        /**
         * stmt
         *    : lVal ASSIGN exp SEMICOLON
         *    | (exp)? SEMICOLON
         *    | block
         *    | IF L_PAREN cond R_PAREN stmt (ELSE stmt)?
         *    | WHILE L_PAREN cond R_PAREN stmt
         *    | BREAK SEMICOLON
         *    | CONTINUE SEMICOLON
         *    | RETURN (exp)? SEMICOLON
         *    ;
         */
        if(ctx.ASSIGN() != null){
            Type lVal = getLValType(ctx.lVal());
            Type rVal = getExpType(ctx.exp());
            if(lVal instanceof FunctionType){
                printError(11, getLine(ctx), "赋值号左侧非变量或数组元素");
            } else if(!lVal.toString().equals("errorType") && !rVal.toString().equals("errorType") && !lVal.toString().equals(rVal.toString())){
                printError(5, getLine(ctx), "赋值号两侧类型不匹配");
            }
        } else if(ctx.RETURN() != null){
            Type retType;
            if(ctx.exp() != null){
                retType = getExpType(ctx.exp());
            } else {
                retType = new BasicType("void");
            }

            Scope temp = current;
            while (!(temp instanceof FunctionScope)){
                temp = temp.getOuterScope();
            }

            Type type = ((FunctionScope)temp).getType().getRetType();
            if(!retType.toString().equals("errorType") && !type.toString().equals("errorType") && !retType.toString().equals(type.toString())){
                printError(7, getLine(ctx), "返回值类型不匹配");
            }
        }

        return super.visitStmt(ctx);
    }

    private boolean checkParams(ArrayList<Type> params, ArrayList<Type> args){
        for(Type type: params){
            if(type.toString().equals("errorType")){
                return true;
            }
        }
        for(Type type: args){
            if(type.toString().equals("errorType")){
                return true;
            }
        }
        if(params.size() != args.size()){
            return false;
        }

        for(int i = 0;i < params.size();i++){
            Type param = params.get(i);
            Type arg = args.get(i);
            if(!param.toString().equals(arg.toString())){
                return false;
            }
        }
        return true;
    }

    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if(ctx.IDENT() != null){
            String funcName = ctx.IDENT().getText();
            Symbol symbol = current.getSymbol(funcName);
            if(symbol == null){
                printError(2, getLine(ctx), "函数未定义: "+funcName);
            } else if(!(symbol.getType() instanceof FunctionType)){
                printError(10, getLine(ctx), "对变量使用函数调用: "+funcName);
            } else {
                FunctionType functionType = (FunctionType) symbol.getType();
                ArrayList<Type> paramsType = functionType.getParamsType();
                ArrayList<Type> argsType = new ArrayList<>();
                if(ctx.funcRParams() != null){
                    for(SysYParser.ParamContext paramContext: ctx.funcRParams().param()){
                        argsType.add(getExpType(paramContext.exp()));
                    }
                }
                if(!checkParams(paramsType, argsType)){
                    printError(8, getLine(ctx), "函数参数不适用");
                }
            }
        } else if(ctx.unaryOp() != null){
            Type expType = getExpType(ctx.exp(0));
            if(!expType.toString().equals("int")){
                printError(6, getLine(ctx), "运算符需求类型与提供类型不匹配");
            }
        } else if(ctx.MUL() != null || ctx.DIV() != null || ctx.MOD() != null ||
                ctx.PLUS() != null || ctx.MINUS() != null){
            Type op1 = getExpType(ctx.exp(0));
            Type op2 = getExpType(ctx.exp(1));
            if(!((op1.toString().equals("errorType") || op2.toString().equals("errorType")) ||
                    (op1.toString().equals("int") && op2.toString().equals("int")))){
                printError(6, getLine(ctx), "运算符需求类型与提供类型不匹配");
            }
        }

        return super.visitExp(ctx);
    }

    @Override
    public Void visitCond(SysYParser.CondContext ctx) {
        if(ctx.exp() == null && !getCondType(ctx).toString().equals("int")){
            printError(6, getLine(ctx), "运算符需求类型与提供类型不匹配");
        }
        return super.visitCond(ctx);
    }
}
