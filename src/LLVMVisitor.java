import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private final LLVMModuleRef module = LLVMModuleCreateWithName("module");
    private final LLVMBuilderRef builder = LLVMCreateBuilder();
    private final LLVMTypeRef i32Type = LLVMInt32Type();

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
        }
        else if (text.length() > 1 && text.startsWith("0")) {
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
        LLVMTypeRef type = LLVMFunctionType(i32Type, LLVMVoidType(), 0, 0);
        LLVMValueRef res = LLVMAddFunction(module, ctx.IDENT().getText(), type);
        LLVMBasicBlockRef main = LLVMAppendBasicBlock(res, "mainEntry");
        LLVMPositionBuilderAtEnd(builder, main);
        super.visitFuncDef(ctx);
        return res;
    }

    @Override
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        LLVMValueRef res = visit(ctx.exp());
        return LLVMBuildRet(builder, res);
    }

    @Override
    public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
        return visit(ctx.exp());
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

}
