public class BasicType implements Type{
    private final String name;

    public BasicType(String name){
        this.name = name;
    }

    @Override
    public String toString(){
        return name;
    }
}
