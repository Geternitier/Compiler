public class ArrayType implements Type{
    Type type;
    int count;

    public ArrayType(Type type, int count){
        this.type = type;
        this.count = count;
    }

    public Type getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString(){
        return "array("+type+")";
    }
}
