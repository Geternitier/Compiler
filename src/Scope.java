import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    private final String name;
    private final Map<String, LLVMValueRef> map = new HashMap<>();
    private final Scope outerScope;

    public Scope(String name, Scope outerScope){
        this.name = name;
        this.outerScope = outerScope;
    }

    public void addRef(String name, LLVMValueRef llvmValueRef){
        map.put(name, llvmValueRef);
    }

    public LLVMValueRef find(String name){
        LLVMValueRef ref = map.get(name);
        if(ref != null){
            return ref;
        }

        if(outerScope != null){
            return outerScope.find(name);
        }

        return null;
    }

    public Scope getOuterScope() {
        return outerScope;
    }

}
