import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.bytedeco.javacpp.BytePointer;

import java.io.IOException;

import static org.bytedeco.llvm.global.LLVM.*;

public class Main {

    public static SysYLexer lexer(String path) throws IOException{
        CharStream input = CharStreams.fromFileName(path);
        return new SysYLexer(input);
    }

    public static SysYParser parser(SysYLexer lexer){
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new SysYParser(tokens);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("input path is required");
            return;
        }
        SysYLexer sysYLexer = lexer(args[0]);
        SysYParser sysYParser = parser(sysYLexer);

        SysYParser.ProgramContext tree = sysYParser.program();

        LLVMVisitor visitor = new LLVMVisitor();
        visitor.visit(tree);

        final BytePointer error = new BytePointer();

        if (LLVMPrintModuleToFile(visitor.getModule(), args[1], error) != 0) {    // module是你自定义的LLVMModuleRef对象
            LLVMDisposeMessage(error);
        }


    }

}
