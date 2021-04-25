package bin.luajava;

public class LuaJavaPackage {
    private String packageName;

    public LuaJavaPackage(String packageName) {
        this.packageName = packageName;
    }

    public Object get(LuaState L, String key) {
        String className = packageName + '.' + key;
        try {
            Class cls = Class.forName(className);
            L.pushJavaObject(cls);
            L.getMetaTable(-1);
            L.pushString("__call");
            L.getGlobal("__java_class_call");
            L.setTable(-3);
            L.pop(2);
            return cls;
        } catch (ClassNotFoundException e) {
            try {
                return new LuaJavaPackage(className);
            } catch (Exception e1) {
                return null;
            }
        }
    }

    @Override
    public String toString() {
        return "package " + packageName;
    }
}
