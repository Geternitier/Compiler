import org.antlr.runtime.tree.ParseTree;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.util.List;

public class Main {

    public static SysYLexer lexer(String path) throws IOException{
        CharStream input = CharStreams.fromFileName(path);
        SysYLexer sysYLexer = new SysYLexer(input);

        // SysYErrorListener
        SysYErrorListener listener = new SysYErrorListener();
        sysYLexer.removeErrorListeners();
        sysYLexer.addErrorListener(listener);
        List<? extends Token> tokens = sysYLexer.getAllTokens();
        if(listener.listen()){
            return sysYLexer;
        }

        String[] rules = sysYLexer.getRuleNames();

        for(Token token: tokens){
            int num = token.getType()-1;
            String text = token.getText();
            if (text.length() > 2 &&(text.startsWith("0x") || text.startsWith("0X"))) {
                text = String.valueOf(Integer.parseInt(text.substring(2), 16));
            }
            else if (text.length() > 1 && text.startsWith("0")) {
                text = String.valueOf(Integer.parseInt(text.substring(1), 8));
            }
            System.err.println(rules[num]+' '+text+" at Line "+token.getLine()+'.');
        }
        return sysYLexer;
    }

    public static SysYParser parser(SysYLexer lexer){
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SysYParser sysYParser = new SysYParser(tokens);
        SysYParserErrorListener parserErrorListener = new SysYParserErrorListener();
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(parserErrorListener);

        SysYParser.ProgramContext tree = sysYParser.program();
        if(parserErrorListener.listen()){
            return sysYParser;
        }

        return sysYParser;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
            return;
        }
        String source = args[0];
        SysYLexer sysYLexer = lexer(source);


    }

}
