public class FunctionScope extends Scope{
    public FunctionType type;

    public FunctionScope(String name, Scope scope, FunctionType type){
        super(name, scope);
        this.type = type;
    }

    public FunctionType getType() {
        return type;
    }
}
