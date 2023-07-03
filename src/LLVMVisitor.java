import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.HashMap;
import java.util.Objects;
import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private final LLVMModuleRef module = LLVMModuleCreateWithName("module");
    private final LLVMBuilderRef builder = LLVMCreateBuilder();
    private final LLVMTypeRef i32Type = LLVMInt32Type();
    private final LLVMTypeRef voidType = LLVMVoidType();
    private final LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
    private final HashMap<String, String> retTypes = new HashMap<>();

    private final Scope global = new Scope("global", null);
    private Scope scope = null;
    private LLVMValueRef function = null;

    private final Stack<LLVMBasicBlockRef> whileStack = new Stack<>();
    private final Stack<LLVMBasicBlockRef> entryStack = new Stack<>();
    private boolean isArray = false;

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

            if(funcFParamContext.L_BRACKT().size() > 0){
                typeRef = LLVMPointerType(typeRef, 0);
            }

            types.put(i, typeRef);
        }

        LLVMTypeRef retType = getTypeRef(ctx.funcType().getText());
        LLVMTypeRef funcType = LLVMFunctionType(retType, types, params, 0);
        String funcName = ctx.IDENT().getText();
        retTypes.put(funcName, ctx.funcType().getText());
        function = LLVMAddFunction(module, funcName, funcType);
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        scope.addRef(funcName, function, funcType);
        scope = new Scope("function", scope);

        for(int i = 0;i < params;i++){
            SysYParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef typeRef = getTypeRef(funcFParamContext.bType().getText());

            if(funcFParamContext.L_BRACKT().size() > 0){
                typeRef = LLVMPointerType(typeRef, 0);
            }

            String paramName = ctx.funcFParams().funcFParam(i).IDENT().getText();
            LLVMValueRef valueRef = LLVMBuildAlloca(builder, typeRef, paramName);
            scope.addRef(paramName, valueRef, typeRef);
            LLVMValueRef arg = LLVMGetParam(function, i);
            LLVMBuildStore(builder, arg, valueRef);
        }

        super.visitFuncDef(ctx);
        scope = scope.getOuterScope();

        if(retType.equals(voidType))
            LLVMBuildRet(builder, null);
        else LLVMBuildRet(builder, zero);
        return function;
    }

    // TODO
    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for(SysYParser.ConstDefContext constDefContext: ctx.constDef()){
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String text = constDefContext.IDENT().getText();
            int number = 0;

            for(SysYParser.ConstExpContext constExpContext: constDefContext.constExp()){
                number = Integer.parseInt(toDecimal(constExpContext.getText()));
                typeRef = LLVMArrayType(typeRef, number);
            }

            LLVMValueRef valueRef;
            if(scope == global){
                valueRef = LLVMAddGlobal(module, typeRef, text);
                LLVMSetInitializer(valueRef, zero);
            } else valueRef = LLVMBuildAlloca(builder, typeRef, text);

            SysYParser.ConstExpContext constExpContext = constDefContext.constInitVal().constExp();
            if(constExpContext != null){
                LLVMValueRef initVal = visit(constExpContext);
                if (scope == global) {
                    LLVMSetInitializer(valueRef, initVal);
                } else LLVMBuildStore(builder, initVal, valueRef);
            } else {
                int count = constDefContext.constInitVal().constInitVal().size();
                if(scope == global){
                    PointerPointer<Pointer> pointer = new PointerPointer<>(number);
                    for(int i = 0;i < number;i++){
                        if(i < count){
                            pointer.put(i, visit(constDefContext.constInitVal().constInitVal(i).constExp()));
                        } else {
                            pointer.put(i, zero);
                        }
                    }
                    LLVMValueRef initArray = LLVMConstArray(typeRef, pointer, number);
                    LLVMSetInitializer(valueRef, initArray);
                } else {
                    LLVMValueRef[] initArray = new LLVMValueRef[number];
                    for (int i = 0;i < number;i++) {
                        if (i < count) {
                            initArray[i] = visit(constDefContext.constInitVal().constInitVal(i).constExp());
                        } else {
                            initArray[i] = LLVMConstInt(i32Type, 0, 0);
                        }
                    }

                    LLVMValueRef[] arrayPointer = new LLVMValueRef[2];
                    arrayPointer[0] = zero;
                    for (int i = 0; i < number; i++) {
                        arrayPointer[1] = LLVMConstInt(i32Type, i, 0);
                        PointerPointer<LLVMValueRef> indexPointer = new PointerPointer<>(arrayPointer);
                        LLVMValueRef elementPtr = LLVMBuildGEP(builder, valueRef, indexPointer, 2, "pointer_" + i);
                        LLVMBuildStore(builder, initArray[i], elementPtr);
                    }
                }
            }

            scope.addRef(text, valueRef, typeRef);
        }
        return null;
    }

    // TODO
    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx) {
        String typeName = ctx.bType().getText();

        for (SysYParser.VarDefContext varDefContext : ctx.varDef()) {
            LLVMTypeRef typeRef = getTypeRef(typeName);
            String text = varDefContext.IDENT().getText();
            int number = 0;
            for(SysYParser.ConstExpContext constExpContext: varDefContext.constExp()){
                number = Integer.parseInt(toDecimal(constExpContext.getText()));
                typeRef = LLVMArrayType(typeRef, number);
            }

            LLVMValueRef valueRef;
            if (scope == global) {
                valueRef = LLVMAddGlobal(module, typeRef, text);
                if(number == 0){
                    LLVMSetInitializer(valueRef, zero);
                } else {
                    PointerPointer<Pointer> pointer = new PointerPointer<>(number);
                    for(int i = 0;i < number;i++){
                        pointer.put(i, zero);
                    }
                    LLVMValueRef array = LLVMConstArray(typeRef, pointer, number);
                    LLVMSetInitializer(valueRef, array);
                }
            } else {
                valueRef = LLVMBuildAlloca(builder, typeRef, text);
            }

            if (varDefContext.ASSIGN() != null) {
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                if(expContext != null){
                    LLVMValueRef initVal = visit(expContext);
                    if(scope == global){
                        LLVMSetInitializer(valueRef, initVal);
                    } else LLVMBuildStore(builder, initVal, valueRef);
                } else {
                    int count = varDefContext.initVal().initVal().size();
                    if(scope == global){
                        PointerPointer<Pointer> pointer = new PointerPointer<>(number);
                        for(int i = 0;i < number;i++){
                            if(i < count){
                                pointer.put(i, visit(varDefContext.initVal().initVal(i).exp()));
                            } else {
                                pointer.put(i, zero);
                            }
                        }
                        LLVMValueRef initArray = LLVMConstArray(typeRef, pointer, number);
                        LLVMSetInitializer(valueRef, initArray);
                    } else {
                        LLVMValueRef[] initArray = new LLVMValueRef[number];
                        for (int i = 0;i < number;i++) {
                            if (i < count) {
                                initArray[i] = visit(varDefContext.initVal().initVal(i).exp());
                            } else {
                                initArray[i] = LLVMConstInt(i32Type, 0, 0);
                            }
                        }

                        LLVMValueRef[] arrayPointer = new LLVMValueRef[2];
                        arrayPointer[0] = zero;
                        for (int i = 0; i < number; i++) {
                            arrayPointer[1] = LLVMConstInt(i32Type, i, 0);
                            PointerPointer<LLVMValueRef> indexPointer = new PointerPointer<>(arrayPointer);
                            LLVMValueRef elementPtr = LLVMBuildGEP(builder, valueRef, indexPointer, 2, "pointer_" + i);
                            LLVMBuildStore(builder, initArray[i], elementPtr);
                        }
                    }
                }
            }

            scope.addRef(text, valueRef, typeRef);
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
        if(isArray){
            isArray = false;
            return lVal;
        }
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
        String name;
        if(Objects.equals(retTypes.get(ctx.IDENT().getText()), "void")){
            name = "";
        } else name = "func_";
        return LLVMBuildCall(builder, func, args, count, name);
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
        LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntEQ, zero, lVal, "cmp_");
        LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(function, "true_");
        LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(function, "false_");
        LLVMBasicBlockRef after = LLVMAppendBasicBlock(function, "after_");
        LLVMValueRef res = LLVMBuildAlloca(builder, i32Type, "and_");
        LLVMBuildStore(builder, lVal, res);

        LLVMBuildCondBr(builder, cmp, trueBlock, falseBlock);

        LLVMPositionBuilderAtEnd(builder, trueBlock);
        LLVMBuildBr(builder, after);

        LLVMPositionBuilderAtEnd(builder, falseBlock);
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMBuildStore(builder, rVal, res);
        LLVMBuildBr(builder, after);

        LLVMPositionBuilderAtEnd(builder, after);
        res = LLVMBuildLoad(builder, res, "load_");
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitOrCond(SysYParser.OrCondContext ctx) {
        LLVMValueRef lVal = visit(ctx.cond(0));
        LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntNE, zero, lVal, "cmp_");
        LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(function, "true_");
        LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(function, "false_");
        LLVMBasicBlockRef after = LLVMAppendBasicBlock(function, "after_");
        LLVMValueRef res = LLVMBuildAlloca(builder, i32Type, "or_");
        LLVMBuildStore(builder, lVal, res);

        LLVMBuildCondBr(builder, cmp, trueBlock, falseBlock);

        LLVMPositionBuilderAtEnd(builder, trueBlock);
        LLVMBuildBr(builder, after);

        LLVMPositionBuilderAtEnd(builder, falseBlock);
        LLVMValueRef rVal = visit(ctx.cond(1));
        LLVMBuildStore(builder, rVal, res);
        LLVMBuildBr(builder, after);

        LLVMPositionBuilderAtEnd(builder, after);
        res = LLVMBuildLoad(builder, res, "load_");
        return LLVMBuildZExt(builder, res, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        String lName = ctx.IDENT().getText();
        LLVMValueRef valueRef = scope.find(lName);
        LLVMTypeRef typeRef = scope.getType(lName);
        if(typeRef.equals(i32Type)){
            return valueRef;
        } else if(typeRef.equals(LLVMPointerType(i32Type, 0))){
            if(ctx.exp().size() > 0){
                LLVMValueRef[] pointer = new LLVMValueRef[1];
                pointer[0] = visit(ctx.exp(0));
                PointerPointer<LLVMValueRef> index = new PointerPointer<>(pointer);
                LLVMValueRef p = LLVMBuildLoad(builder, valueRef, lName);
                return LLVMBuildGEP(builder, p, index, 1, "pointer_"+lName);
            } else {
                return valueRef;
            }
        } else {
            LLVMValueRef[] pointer = new LLVMValueRef[2];
            pointer[0] = zero;
            if(ctx.exp().size() > 0){
                pointer[1] = visit(ctx.exp(0));
            } else {
                isArray = true;
                pointer[1] = zero;
            }
            PointerPointer<LLVMValueRef> index = new PointerPointer<>(pointer);
            return LLVMBuildGEP(builder, valueRef, index, 2, "pointer_"+lName);
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
