import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private final LLVMModuleRef module = LLVMModuleCreateWithName("module");
    private final LLVMBuilderRef builder = LLVMCreateBuilder();
    private final LLVMTypeRef i32Type = LLVMInt32Type();
    private final LLVMTypeRef voidType = LLVMVoidType();
    private final LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);

    private final Scope global = new Scope("global", null);
    private Scope scope = null;
    private LLVMValueRef function = null;

    private final Stack<LLVMBasicBlockRef> whileStack = new Stack<>();
    private final Stack<LLVMBasicBlockRef> entryStack = new Stack<>();
    private boolean isRet = false;

    public LLVMVisitor(){
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    public LLVMModuleRef getModule() {
        return module;
    }

    @Override
    public LLVMValueRef visitTerminal(TerminalNode node) {
        Token symbol = node.getSymbol();
        int type = symbol.getType();
        if(type == SysYParser.INTEGER_CONST){
            return LLVMConstInt(i32Type, Integer.parseInt(toDecimal(node.getText())), 1);
        }

        return super.visitTerminal(node);
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        scope = global;
        LLVMValueRef valueRef = super.visitProgram(ctx);
        scope = scope.getOuterScope();
        return valueRef;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        int params = 0;
        if(ctx.funcFParams() != null){
            params = ctx.funcFParams().funcFParam().size();
        }
        PointerPointer<Pointer> types = new PointerPointer<>(params);
        for(int i = 0;i < params;i++){
            SysYParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef typeRef = getTypeRef(funcFParamContext.bType().getText());
            types.put(i, typeRef);
        }

        LLVMTypeRef retType = getTypeRef(ctx.funcType().getText());
        LLVMTypeRef funcType = LLVMFunctionType(retType, types, params, 0);
        String funcName = ctx.IDENT().getText();
        function = LLVMAddFunction(module, funcName, funcType);
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        for(int i = 0;i < params;i++){
            SysYParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef typeRef = getTypeRef(funcFParamContext.bType().getText());
            String paramName = ctx.funcFParams().funcFParam(i).IDENT().getText();
            LLVMValueRef valueRef = LLVMBuildAlloca(builder, typeRef, paramName);
            scope.addRef(paramName, valueRef);
            LLVMValueRef arg = LLVMGetParam(function, i);
            LLVMBuildStore(builder, arg, valueRef);
        }

        isRet = false;
        scope.addRef(funcName, function);
        scope = new Scope("function", scope);
        super.visitFuncDef(ctx);

        if(!isRet){
            LLVMBuildRet(builder, null);
            isRet = false;
        }
        return function;
    }

    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for(SysYParser.ConstDefContext constDefContext: ctx.constDef()){
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String text = constDefContext.IDENT().getText();
            LLVMValueRef valueRef;
            if(scope == global){
                valueRef = LLVMAddGlobal(module, typeRef, text);
                LLVMSetInitializer(valueRef, zero);
            } else valueRef = LLVMBuildAlloca(builder, typeRef, text);

            SysYParser.ConstExpContext constExpContext = constDefContext.constInitVal().constExp();
            LLVMValueRef initVal = visit(constExpContext);
            if(scope == global){
                LLVMSetInitializer(valueRef, initVal);
            } else LLVMBuildStore(builder, initVal, valueRef);
            scope.addRef(text, valueRef);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for (SysYParser.VarDefContext varDefContext : ctx.varDef()) {
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String text = varDefContext.IDENT().getText();
            LLVMValueRef valueRef;

            if (scope == global) {
                valueRef = LLVMAddGlobal(module, typeRef, text);
                LLVMSetInitializer(valueRef, zero);
            } else {
                valueRef = LLVMBuildAlloca(builder, typeRef, text);
            }

            if (varDefContext.ASSIGN() != null) {
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                LLVMValueRef initVal = visit(expContext);
                if (scope == global) {
                    LLVMSetInitializer(valueRef, initVal);
                } else LLVMBuildStore(builder, initVal, valueRef);
            }

            scope.addRef(text, valueRef);
        }

        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        scope = new Scope("block", scope);
        LLVMValueRef valueRef = super.visitBlock(ctx);
        scope = scope.getOuterScope();
        return valueRef;
    }

/* stmt
   : lVal ASSIGN exp SEMICOLON                      # assignStmt
   | (exp)? SEMICOLON                               # questionStmt
   | block                                          # blockStmt
   | IF L_PAREN cond R_PAREN stmt (ELSE stmt)?      # ifStmt
   | WHILE L_PAREN cond R_PAREN stmt                # whileStmt
   | BREAK SEMICOLON                                # breakStmt
   | CONTINUE SEMICOLON                             # continueStmt
   | RETURN (exp)? SEMICOLON                        # returnStmt
   ;
 */

    @Override
    public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        return LLVMBuildStore(builder, visit(ctx.exp()), visitLVal(ctx.lVal()));
    }

    @Override
    public LLVMValueRef visitIfStmt(SysYParser.IfStmtContext ctx) {
        LLVMValueRef cond = visit(ctx.cond());
        LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntNE, zero, cond, "cmp_");
        LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(function, "true");
        LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(function, "false");
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, "entry");

        LLVMBuildCondBr(builder, cmp, trueBlock, falseBlock);

        LLVMPositionBuilderAtEnd(builder, trueBlock);
        visit(ctx.stmt(0));
        LLVMBuildBr(builder, entry);

        LLVMPositionBuilderAtEnd(builder, falseBlock);
        if (ctx.ELSE() != null) {
            visit(ctx.stmt(1));
        }
        LLVMBuildBr(builder, entry);

        LLVMPositionBuilderAtEnd(builder, entry);
        return null;
    }

    // WHILE L_PAREN cond R_PAREN stmt                # whileStmt
    @Override
    public LLVMValueRef visitWhileStmt(SysYParser.WhileStmtContext ctx) {
        LLVMBasicBlockRef whileCond = LLVMAppendBasicBlock(function, "whileCondition");
        LLVMBasicBlockRef whileBody = LLVMAppendBasicBlock(function, "whileBody");
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, "entry");
        LLVMBuildBr(builder, whileCond);

        LLVMPositionBuilderAtEnd(builder, whileCond);
        LLVMValueRef cond = visit(ctx.cond());
        LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntNE, zero, cond, "cmp_");
        LLVMBuildCondBr(builder, cmp, whileBody, entry);

        LLVMPositionBuilderAtEnd(builder, whileBody);
        whileStack.push(whileCond);
        entryStack.push(entry);
        visit(ctx.stmt());
        LLVMBuildBr(builder, whileCond);
        whileStack.pop();
        entryStack.pop();
        LLVMBuildBr(builder, entry);

        LLVMPositionBuilderAtEnd(builder, entry);
        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx) {
        return LLVMBuildBr(builder, entryStack.peek());
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx) {
        return LLVMBuildBr(builder, whileStack.peek());
    }

    @Override
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        LLVMValueRef ret = null;
        if(ctx.exp() != null){
            ret = visit(ctx.exp());
        }
        isRet = true;
        return LLVMBuildRet(builder, ret);
    }

/* exp
   : L_PAREN exp R_PAREN                            # parenExp
   | lVal                                           # lValExp
   | number                                         # numberExp
   | IDENT L_PAREN funcRParams? R_PAREN             # funcExp
   | unaryOp exp                                    # unaryExp
   | exp (MUL | DIV | MOD) exp                      # mulDivModExp
   | exp (PLUS | MINUS) exp                         # plusMinusExp
   ;
 */

    @Override
    public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitLValExp(SysYParser.LValExpContext ctx) {
        LLVMValueRef lVal = visitLVal(ctx.lVal());
        return LLVMBuildLoad(builder, lVal, ctx.lVal().getText());
    }

    @Override
    public LLVMValueRef visitNumberExp(SysYParser.NumberExpContext ctx) {
        return visit(ctx.number());
    }

    @Override
    public LLVMValueRef visitFuncExp(SysYParser.FuncExpContext ctx) {
        LLVMValueRef func = scope.find(ctx.IDENT().getText());
        PointerPointer<Pointer> args = null;
        int count = 0;
        if(ctx.funcRParams() != null){
            count = ctx.funcRParams().param().size();
            args = new PointerPointer<>(count);
            for(int i = 0;i < count;i++){
                SysYParser.ExpContext expContext = ctx.funcRParams().param(i).exp();
                args.put(i, visit(expContext));
            }
        }
        return LLVMBuildCall(builder, func, args, count, "");
    }

    @Override
    public LLVMValueRef visitUnaryExp(SysYParser.UnaryExpContext ctx) {
        String op = ctx.unaryOp().getText();
        LLVMValueRef exp = visit(ctx.exp());
        switch (op){
            case "+":
                return exp;
            case "-":
                return LLVMBuildNeg(builder, exp, "neg_");
            case "!":
                if(LLVMConstIntGetZExtValue(exp) == 0){
                    return LLVMConstInt(i32Type, 1, 0);
                } else return LLVMConstInt(i32Type, 0, 0);
        }
        return super.visitUnaryExp(ctx);
    }

    @Override
    public LLVMValueRef visitMulDivModExp(SysYParser.MulDivModExpContext ctx) {
        LLVMValueRef ref1 = visit(ctx.exp(0));
        LLVMValueRef ref2 = visit(ctx.exp(1));
        if(ctx.MUL() != null){
            return LLVMBuildMul(builder, ref1, ref2, "mul_");
        } else if(ctx.DIV() != null){
            return LLVMBuildSDiv(builder, ref1, ref2, "div_");
        } else return LLVMBuildSRem(builder, ref1, ref2, "rem_");
    }

    @Override
    public LLVMValueRef visitPlusMinusExp(SysYParser.PlusMinusExpContext ctx) {
        LLVMValueRef ref1 = visit(ctx.exp(0));
        LLVMValueRef ref2 = visit(ctx.exp(1));
        if(ctx.PLUS() != null){
            return LLVMBuildAdd(builder, ref1, ref2, "add_");
        } else return LLVMBuildSub(builder, ref1, ref2, "sub_");
    }

/* cond
    : exp                                            # expCond
    | cond (LT | GT | LE | GE) cond                  # compareCond
    | cond (EQ | NEQ) cond                           # equalCond
    | cond AND cond                                  # andCond
    | cond OR cond                                   # orCond
    ;
*/

    @Override
    public LLVMValueRef visitExpCond(SysYParser.ExpCondContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitCompareCond(SysYParser.CompareCondContext ctx) {
        LLVMValueRef lVal = visit(ctx.cond(0));
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMValueRef res;
        if(ctx.LT() != null){
            res = LLVMBuildICmp(builder, LLVMIntSLT, lVal, rVal, "LT");
        } else if(ctx.GT() != null){
            res = LLVMBuildICmp(builder, LLVMIntSGT, lVal, rVal, "GT");
        } else if(ctx.LE() != null){
            res = LLVMBuildICmp(builder, LLVMIntSLE, lVal, rVal, "LE");
        } else {
            res = LLVMBuildICmp(builder, LLVMIntSGE, lVal, rVal, "GE");
        }
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitEqualCond(SysYParser.EqualCondContext ctx) {
        LLVMValueRef lVal = visit(ctx.cond(0));
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMValueRef res;
        if(ctx.EQ() != null){
            res = LLVMBuildICmp(builder, LLVMIntEQ, lVal, rVal, "EQ");
        } else res = LLVMBuildICmp(builder, LLVMIntNE, lVal, rVal, "NEQ");
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitAndCond(SysYParser.AndCondContext ctx) {
        LLVMValueRef lVal = visit(ctx.cond(0));
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMValueRef res = LLVMBuildAnd(builder, lVal, rVal, "AND");
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitOrCond(SysYParser.OrCondContext ctx) {
        LLVMValueRef lVal = visit(ctx.cond(0));
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMValueRef res = LLVMBuildOr(builder, lVal, rVal, "OR");
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        String lName = ctx.IDENT().getText();
        return scope.find(lName);
    }

    @Override
    public LLVMValueRef visitNumber(SysYParser.NumberContext ctx) {
        return visit(ctx.INTEGER_CONST());
    }

    private String toDecimal(String text){
        if (text.length() > 2 &&(text.startsWith("0x") || text.startsWith("0X"))) {
            text = String.valueOf(Integer.parseInt(text.substring(2), 16));
        } else if (text.length() > 1 && text.startsWith("0")) {
            text = String.valueOf(Integer.parseInt(text.substring(1), 8));
        }
        return text;
    }

    private LLVMTypeRef getTypeRef(String name){
        if(name.equals("int")){
            return i32Type;
        } else return voidType;
    }

}
