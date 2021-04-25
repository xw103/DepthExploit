package bin.androidlua;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import bin.luajava.JavaFunction;
import bin.luajava.LuaException;
import bin.luajava.LuaState;

public class LuaPrint extends JavaFunction {

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private PrintStream printStream;

    LuaPrint(LuaState L) {
        super(L);
        try {
            printStream = new PrintStream(baos, false, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int execute() throws LuaException {
        if (L.getTop() < 2) {
            return 0;
        }
        // 1是this 跳过
        for (int i = 2; i <= L.getTop(); i++) {
            int type = L.type(i);
            String val = null;
            String typeName = L.typeName(type);
            boolean isThrowable = false;
            switch (typeName) {
                case "userdata":
                    Object obj = L.toJavaObject(i);
                    if (obj != null) {
                        if (obj instanceof Throwable) {
                            if (baos.size() > 0)
                                printStream.append("\n");
                            ((Throwable) obj).printStackTrace(printStream);
                            isThrowable = true;
                        } else
                            val = obj.toString();
                    }
                    break;
                case "boolean":
                    val = L.toBoolean(i) ? "true" : "false";
                    break;
                default:
                    val = L.toString(i);
                    break;
            }
            if (!isThrowable) {
                if (val == null)
                    val = typeName;
                if (baos.size() > 0)
                    printStream.append("\t");
                printStream.append(val);
            }
        }
        Log.i("Lua", baos.toString());
        baos.reset();
        return 0;
    }


}

