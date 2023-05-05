public class ArrayType implements Type{
    Type type;
    int count;

    public ArrayType(Type type, int count){
        this.type = type;
        this.count = count;
    }

    @Override
    public String toString(){
        return "array("+type+")";
    }
}
