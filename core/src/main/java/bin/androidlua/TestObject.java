package bin.androidlua;

import android.util.Log;

import bin.luajava.LuaException;


@SuppressWarnings("unused")
public class TestObject {

    private void p(String msg) throws LuaException {
        Log.i("Lua", msg);
    }

    public void print(long i) throws LuaException {
        p("long " + i);
    }

    public void print(short i) throws LuaException {
        p("short " + i);
    }

    public void print(byte i) throws LuaException {
        p("byte " + i);
    }

    public void print(int i) throws LuaException {
        p("int " + i);
    }

    public void print(double i) throws LuaException {
        p("double " + i);
    }

    public void print(float i) throws LuaException {
        p("float " + i);
    }

    public void print(boolean i) throws LuaException {
        p("boolean " + i);
    }

    public void print(char i) throws LuaException {
        p("char " + i);
    }

    public void print(Integer obj) throws LuaException {
        p("integer " + obj);
    }

    public void print(String obj) throws LuaException {
        p("string " + obj);
    }

    public void print(Object obj) throws LuaException {
        p("object " + obj);
    }

    @Override
    public String toString() {
        return "TestObject.toString()";
    }

    public static void error123(String msg) throws LuaException {
        throw new RuntimeException(msg);
    }

    private String message;

    public String getMessage() {
        Log.w("Lua", "=> 执行了getMessage()");
        return message;
    }

    public void setMessage(String message) {
        Log.w("Lua", "=> 执行了setMessage(\"" + message + "\")");
        this.message = message;
    }

    interface TwoMethod {
        void method1();
        void method2(String arg);
    }

    public static void testInterface(TwoMethod iface) {
        iface.method1();
        iface.method2("ABC");
    }
}
