public class BasicSymbol implements Symbol{
    private final String name;
    private final Type type;

    public BasicSymbol(String name, Type type){
        this.name = name;
        this.type = type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return type;
    }


}
