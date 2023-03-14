import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class Main
{    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
            return;
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);

        // ErrorListener
        SysYErrorListener listener = new SysYErrorListener();
        sysYLexer.removeErrorListeners();
        sysYLexer.addErrorListener(listener);
        List<? extends Token> tokens = sysYLexer.getAllTokens();
        if(listener.listen()){
            return;
        }

        String[] rules = sysYLexer.getRuleNames();

        for(Token token: tokens){
            int num = token.getType()-1;
            String text = token.getText();
            if(num == 33){
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    text = String.valueOf(Integer.parseInt(text.substring(2), 16));
                }
                if (Pattern.matches("^0\n+",text)) {
                    text = String.valueOf(Integer.parseInt(text.substring(1), 8));
                }
            }
            System.err.println(rules[num]+' '+text+" at Line "+token.getLine()+'.');
        }

    }

}
