import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private final LLVMModuleRef module = LLVMModuleCreateWithName("moudle");
    private final LLVMBuilderRef builder = LLVMCreateBuilder();
    private final LLVMTypeRef i32Type = LLVMInt32Type();
    private final LLVMTypeRef voidType = LLVMVoidType();
    private final LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);

    private final Scope global = new Scope("global", null);
    private Scope scope = null;

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
        LLVMValueRef func = LLVMAddFunction(module, funcName, funcType);
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(func, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        for(int i = 0;i < params;i++){
            SysYParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef typeRef = getTypeRef(funcFParamContext.bType().getText());
            String paramName = ctx.funcFParams().funcFParam(i).IDENT().getText();
            LLVMValueRef valueRef = LLVMBuildAlloca(builder, typeRef, paramName);
            scope.addRef(paramName, valueRef);
            LLVMValueRef arg = LLVMGetParam(func, i);
            LLVMBuildStore(builder, arg, valueRef);
        }

        scope.addRef(funcName, func);
        scope = new Scope("function", scope);
        super.visitFuncDef(ctx);

        return func;
    }

    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for(SysYParser.ConstDefContext constDefContext: ctx.constDef()){
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String text = constDefContext.IDENT().getText();
            int count = 0;
            for(SysYParser.ConstExpContext constExpContext: constDefContext.constExp()){
                count = Integer.parseInt(toDecimal(constExpContext.getText()));
                typeRef = LLVMVectorType(typeRef, count);
            }
            LLVMValueRef valueRef;
            if(scope == global){
                valueRef = LLVMAddGlobal(module, typeRef, text);
                LLVMSetInitializer(valueRef, zero);
            } else valueRef = LLVMBuildAlloca(builder, typeRef, text);

            SysYParser.ConstExpContext constExpContext = constDefContext.constInitVal().constExp();
            if(constExpContext != null){
                LLVMValueRef initVal = visit(constExpContext);
                if(scope == global){
                    LLVMSetInitializer(valueRef, initVal);
                } else LLVMBuildStore(builder, initVal, valueRef);
            } else {
                int initValCount = constDefContext.constInitVal().constInitVal().size();
                if (scope == global) {
                    PointerPointer<LLVMValueRef> pointerPointer = new PointerPointer<>(count);
                    for (int i = 0; i < count; ++i) {
                        if (i < initValCount) {
                            pointerPointer.put(i, visit(constDefContext.constInitVal().constInitVal(i).constExp()));
                        } else {
                            pointerPointer.put(i, zero);
                        }
                    }
                    LLVMValueRef initArray = LLVMConstArray(typeRef, pointerPointer, count);
                    LLVMSetInitializer(valueRef, initArray);
                } else {
                    LLVMValueRef[] initArray = new LLVMValueRef[count];
                    for (int i = 0; i < count; ++i) {
                        if (i < initValCount) {
                            initArray[i] = visit(constDefContext.constInitVal().constInitVal(i).constExp());
                        } else {
                            initArray[i] = LLVMConstInt(i32Type, 0, 0);
                        }
                    }
                    LLVMValueRef[] arrayPointer = new LLVMValueRef[2];
                    arrayPointer[0] = zero;
                    for (int i = 0; i < count; i++) {
                        arrayPointer[1] = LLVMConstInt(i32Type, i, 0);
                        PointerPointer<LLVMValueRef> indexPointer = new PointerPointer<>(arrayPointer);
                        LLVMValueRef ptr = LLVMBuildGEP(builder, valueRef, indexPointer, 2, "" + i);
                        LLVMBuildStore(builder, initArray[i], ptr);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for (SysYParser.VarDefContext varDefContext : ctx.varDef()) {
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String varName = varDefContext.IDENT().getText();
            int count = 0;

            for (SysYParser.ConstExpContext constExpContext : varDefContext.constExp()) {
                count = Integer.parseInt(toDecimal(constExpContext.getText()));
                typeRef = LLVMVectorType(typeRef, count);
            }

            LLVMValueRef valueRef;
            if (scope == global) {
                valueRef = LLVMAddGlobal(module, typeRef, varName);
                LLVMSetInitializer(valueRef, zero);
            } else {
                valueRef = LLVMBuildAlloca(builder, typeRef, varName);
            }

            if (varDefContext.ASSIGN() != null) {
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                if (expContext != null) {
                    LLVMValueRef initVal = visit(expContext);
                    if (scope == global) {
                        LLVMSetInitializer(valueRef, initVal);
                    } else {
                        LLVMBuildStore(builder, initVal, valueRef);
                    }
                } else {
                    int initValCount = varDefContext.initVal().initVal().size();
                    if (scope == global) {
                        PointerPointer<LLVMValueRef> pointerPointer = new PointerPointer<>(count);
                        for (int i = 0; i < count; ++i) {
                            if (i < initValCount) {
                                pointerPointer.put(i, this.visit(varDefContext.initVal().initVal(i).exp()));
                            } else {
                                pointerPointer.put(i, zero);
                            }
                        }
                        LLVMValueRef initArray = LLVMConstArray(typeRef, pointerPointer, count);
                        LLVMSetInitializer(valueRef, initArray);
                    } else {
                        LLVMValueRef[] initArray = new LLVMValueRef[count];
                        for (int i = 0; i < count; ++i) {
                            if (i < initValCount) {
                                initArray[i] = this.visit(varDefContext.initVal().initVal(i).exp());
                            } else {
                                initArray[i] = LLVMConstInt(i32Type, 0, 0);
                            }
                        }
                        LLVMValueRef[] arrayPointer = new LLVMValueRef[2];
                        arrayPointer[0] = zero;
                        for (int i = 0; i < count; i++) {
                            arrayPointer[1] = LLVMConstInt(i32Type, i, 0);
                            PointerPointer<LLVMValueRef> indexPointer = new PointerPointer<>(arrayPointer);
                            LLVMValueRef elementPtr = LLVMBuildGEP(builder, valueRef, indexPointer, 2, ""+i);
                            LLVMBuildStore(builder, initArray[i], elementPtr);
                        }
                    }
                }
            }

            scope.addRef(varName, valueRef);
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
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        LLVMValueRef res = null;
        if(ctx.exp() != null)
               res = visit(ctx.exp());
        return LLVMBuildRet(builder, res);
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
                args.put(i, this.visit(expContext));
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
                return LLVMBuildNeg(builder, exp, "tmp_");
            case "!":
                if(LLVMConstIntGetZExtValue(exp) == 0){
                    return LLVMConstInt(i32Type, 1, 1);
                } else return LLVMConstInt(i32Type, 0, 1);
        }
        return super.visitUnaryExp(ctx);
    }

    @Override
    public LLVMValueRef visitMulDivModExp(SysYParser.MulDivModExpContext ctx) {
        LLVMValueRef ref1 = visit(ctx.exp(0));
        LLVMValueRef ref2 = visit(ctx.exp(1));
        if(ctx.MUL() != null){
            return LLVMBuildMul(builder, ref1, ref2, "tmp_");
        } else if(ctx.DIV() != null){
            return LLVMBuildSDiv(builder, ref1, ref2, "tmp_");
        } else return LLVMBuildSRem(builder, ref1, ref2, "tmp_");
    }

    @Override
    public LLVMValueRef visitPlusMinusExp(SysYParser.PlusMinusExpContext ctx) {
        LLVMValueRef ref1 = visit(ctx.exp(0));
        LLVMValueRef ref2 = visit(ctx.exp(1));
        if(ctx.PLUS() != null){
            return LLVMBuildAdd(builder, ref1, ref2, "tmp_");
        } else return LLVMBuildSub(builder, ref1, ref2, "tmp_");
    }

    @Override
    public LLVMValueRef visitExpCond(SysYParser.ExpCondContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        String lName = ctx.IDENT().getText();
        LLVMValueRef valueRef = scope.find(lName);
        if(ctx.exp().size() == 0){
            return valueRef;
        } else {
            lName += "[" + ctx.exp(0).getText() + "]";
            LLVMValueRef[] valueRefs = new LLVMValueRef[2];
            valueRefs[0] = zero;
            valueRefs[1] = visit(ctx.exp(0));
            return LLVMBuildGEP(builder, valueRef, new PointerPointer<>(valueRefs), 2, lName);
        }
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
