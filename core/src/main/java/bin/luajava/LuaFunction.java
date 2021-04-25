package bin.luajava;

public class LuaFunction<T> extends LuaObject implements LuaMetaTable {

    @Override
    public T __call(Object[] arg) throws LuaException {
        //noinspection unchecked
        return (T) super.call(arg);
    }

    @Override
    public Object __index(String key) {
        return null;
    }

    @Override
    public void __newIndex(String key, Object value) {
    }

    @Override
    public T call(Object... args) throws LuaException {
        //noinspection unchecked
        return (T) super.call(args);
    }

    LuaFunction(LuaState L, String globalName) {
        super(L, globalName);
    }

    LuaFunction(LuaState L, int index) {
        super(L, index);
    }
}
