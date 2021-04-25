package bin.androidlua;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import bin.luajava.JavaFunction;
import bin.luajava.LuaException;
import bin.luajava.LuaState;
import bin.luajava.LuaStateFactory;

@SuppressWarnings("WeakerAccess")
public class LuaRunner {

    private final LuaState L;
    private final Context context;

    public LuaRunner(Context context) throws LuaException {
        this.context = context.getApplicationContext();
        L = LuaStateFactory.newLuaState();
        L.openLibs();

        new LuaPrint(L).register("print");

        L.pushJavaTopPackage("java");
        L.pushJavaTopPackage("javax");
        L.pushJavaTopPackage("org");
        L.pushJavaTopPackage("com");
        L.pushJavaTopPackage("android");
        L.pushJavaTopPackage("bin");

        JavaFunction assetLoader = new AssetLoader(context, L);
        L.getGlobal("package");             // package
        L.getField(-1, "loaders");          // package loaders
        int nLoaders = L.objLen(-1);        // package loaders
        L.pushJavaFunction(assetLoader);    // package loaders loader
        L.rawSetI(-2, nLoaders + 1);        // package loaders
        L.pop(1);                           // package
        L.pop(1);                           //

        L.getGlobal("require");
        L.pushString("import");
        L.call(1, 0);
    }

    public LuaState getLuaState() {
        return L;
    }

    public void run(String src, String name) throws LuaException {
        run(src.getBytes(Charset.forName("utf-8")), name);
    }

    public void runFromAssets(String fileName) throws LuaException, IOException {
        run(context.getAssets().open(fileName), fileName);
    }

    public void run(InputStream is, String name) throws LuaException, IOException {
        run(readAll(is), name);
    }

    public void run(byte[] data, String name) throws LuaException {
        L.setTop(0);

        int result = L.LloadBuffer(data, name);

        if (result == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            result = L.pcall(0, 0, -2);

            if (result == 0) {
                return;// L.toJavaObject(-1);
            }
        }
        throw L.toJavaException(result);
    }

    public Object runFunction(String functionName, Object... args) throws LuaException {
        L.setTop(0);
        L.getGlobal(functionName);
        L.getGlobal("debug");
        L.getField(-1, "traceback");
        L.remove(-2);
        L.insert(-2);
        for (Object arg : args) {
            L.pushObjectValue(arg);
        }
        int l = args.length;
        int result = L.pcall(l, 1, -2 - l);
        if (result == 0) {
            return L.toJavaObject(-1);
        } else {
            throw L.toJavaException(result);
        }
    }

    public boolean isClosed() {
        return L.isClosed();
    }

    public void close() {
        L.close();
    }

    static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        input.close();
        return output.toByteArray();
    }
}
