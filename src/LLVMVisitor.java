import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private final LLVMModuleRef module = LLVMModuleCreateWithName("module");
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

    private String toDecimal(String text){
        if (text.length() > 2 &&(text.startsWith("0x") || text.startsWith("0X"))) {
            text = String.valueOf(Integer.parseInt(text.substring(2), 16));
        } else if (text.length() > 1 && text.startsWith("0")) {
            text = String.valueOf(Integer.parseInt(text.substring(1), 8));
        }
        return text;
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
        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(func, funcName + "_entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        for(int i = 0;i < params;i++){
            SysYParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef typeRef = getTypeRef(funcFParamContext.bType().getText());
            String paramName = ctx.funcFParams().funcFParam(i).IDENT().getText();
            LLVMValueRef valueRef = LLVMBuildAlloca(builder, typeRef, "pointer_" + paramName);
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
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        LLVMValueRef res = null;
        if(ctx.exp() != null)
               res = visit(ctx.exp());
        return LLVMBuildRet(builder, res);
    }

    @Override
    public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
        return visit(ctx.exp());
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
            return LLVMBuildMul(builder, ref1, ref2, "mul_");
        } else if(ctx.DIV() != null){
            return LLVMBuildSDiv(builder, ref1, ref2, "sdiv_");
        } else return LLVMBuildSRem(builder, ref1, ref2, "srem_");
    }

    @Override
    public LLVMValueRef visitPlusMinusExp(SysYParser.PlusMinusExpContext ctx) {
        LLVMValueRef ref1 = visit(ctx.exp(0));
        LLVMValueRef ref2 = visit(ctx.exp(1));
        if(ctx.PLUS() != null){
            return LLVMBuildAdd(builder, ref1, ref2, "add_");
        } else return LLVMBuildSub(builder, ref1, ref2, "sub_");
    }

    @Override
    public LLVMValueRef visitExpCond(SysYParser.ExpCondContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitNumber(SysYParser.NumberContext ctx) {
        return visit(ctx.INTEGER_CONST());
    }

    private LLVMTypeRef getTypeRef(String name){
        if(name.equals("int")){
            return i32Type;
        } else return voidType;
    }

}
