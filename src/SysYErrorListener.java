import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class SysYErrorListener extends BaseErrorListener {
    private boolean isError = false;
    public boolean listen(){
        return isError;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object o, int lineNum, int posInLine, String msg, RecognitionException e){
        isError = true;
        System.err.println("Error type A at Line " + lineNum + ": " + msg + '.');
    }

}
