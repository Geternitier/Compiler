import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.util.List;

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
                if (text.startsWith("0")) {
                    if(checkOct(text)){
                        text = String.valueOf(Integer.parseInt(text.substring(1), 8));
                    } else {
                        while (text.startsWith("0")){
                            System.err.println(rules[num]+' '+0+" at Line "+token.getLine()+'.');
                            text = text.substring(1);
                        }
                    }
                }
            }
            System.err.println(rules[num]+' '+text+" at Line "+token.getLine()+'.');
        }

    }

    private static boolean checkOct(String s){
        for(char c: s.toCharArray()){
            if(c == '8' || c == '9'){
                return false;
            }
        }
        return true;
    }

}
