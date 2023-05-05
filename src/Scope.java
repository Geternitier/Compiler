import java.util.HashMap;
import java.util.Map;

public class Scope {
    private final String name;
    private final Map<String, Symbol> symbolMap = new HashMap<>();
    private final Scope outerScope;

    public Scope(String name, Scope outerScope){
        this.name = name;
        this.outerScope = outerScope;
    }

    public String getName(){
        return name;
    }

    public Scope getOuterScope() {
        return outerScope;
    }

    public boolean haveSymbol(String name){
        return symbolMap.containsKey(name);
    }

    public void addSymbol(Symbol symbol){
        symbolMap.put(symbol.getName(), symbol);
    }

    public Symbol getSymbol(String name){
        Symbol symbol = symbolMap.get(name);
        if(symbol != null){
            return symbol;
        }

        if(outerScope != null){
            return outerScope.getSymbol(name);
        }

        return null;
    }

}
