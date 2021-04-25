package bin.luajava;

public interface LuaMetaTable {
    Object __call(Object... arg) throws LuaException;

    Object __index(String key);

    void __newIndex(String key, Object value);
}
