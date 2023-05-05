import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;

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
        if (args.length < 1) {
            System.err.println("input path is required");
            return;
        }
        String source = args[0];
        SysYLexer sysYLexer = lexer(source);
        SysYParser sysYParser = parser(sysYLexer);

        SysYParser.ProgramContext tree = sysYParser.program();
        Visitor visitor = new Visitor();
        visitor.visit(tree);

        if(!visitor.isError()){
            for (String s : visitor.getText()) {
                System.err.print(s);
            }
        }
    }

}
