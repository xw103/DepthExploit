/*
 * $Id: LuaJavaAPI.java,v 1.4 2006/12/22 14:06:40 thiago Exp $
 * Copyright (C) 2003-2007 Kepler Project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Softwarea.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package bin.luajava;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that contains functions accessed by lua.
 */
@SuppressWarnings({"WeakerAccess", "unused", "SameParameterValue"})
public final class LuaJavaAPI {
    private static final boolean DEBUG = false;
    public static HashMap<String, Method[]> methodsMap = new HashMap<>();
    public static HashMap<String, Method[]> methodCache = new HashMap<>();

    private static Class<?> LuaState_class = LuaState.class;

    private static Class<?> String_class = String.class;

    private static Class<?> List_class = List.class;

    private static Class<?> ArrayList_class = ArrayList.class;

    private static Class<?> HashMap_class = HashMap.class;

    private static Class<?> Map_class = Map.class;

    private static Class<?> LuaFunction_class = LuaFunction.class;

    private static Class<?> LuaObject_class = LuaObject.class;

    private static Class<?> LuaTable_class = LuaTable.class;

    private static Class<?> Number_class = Number.class;

    private static Class<?> Character_class = Character.class;


    private LuaJavaAPI() {
    }


    /**
     * Java implementation of the metamethod __index
     *
     * @param luaState int that indicates the state used
     * @param obj      Object to be indexed
     * @return number of returned objects
     */
    public static int objectIndex(long luaState, Object obj, String searchName, int type)
            throws Throwable {
        if (DEBUG) Log.d("Lua", "objectIndex " + obj + " " + searchName);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            int ret;
            if (type == 0)
                if (checkMethod(L, obj, searchName) != 0)
                    return 2;

            if (type == 0 || type == 1 || type == 5)
                if ((ret = checkField(L, obj, searchName)) != 0)
                    return ret;

            if (type == 0 || type == 4)
                if (javaGetter(L, obj, searchName) != 0)
                    return 4;

            if (type == 0 || type == 3)
                if (checkClass(L, obj, searchName) != 0)
                    return 3;

            if ((type == 0 || type == 6) && obj instanceof LuaMetaTable) {
                Object res = ((LuaMetaTable) obj).__index(searchName);
                L.pushObjectValue(res);
                return 6;
            }
            return 0;
        }
    }

    public static int callMethod(long luaState, Object obj, String cacheName)
            throws Throwable {
        if (DEBUG) Log.d("Lua", "callMethod " + obj.getClass().getSimpleName() + " " + cacheName);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            Method[] methods = methodCache.get(cacheName);
            int top = L.getTop();
            Object[] args = null;
            Object[] objs = new Object[top];
            Method method = null;
            long score = Long.MAX_VALUE;
            // gets method and arguments
            for (Method m : methods) {
                Class[] parameters = m.getParameterTypes();
                if (parameters.length != top)
                    continue;

                boolean okMethod = true;
                long thisScore = 0;

                for (int j = 0; j < parameters.length; j++) {
                    try {
                        Object[] ret = compareTypes(L, parameters[j], j + 1);
                        objs[j] = ret[0];
                        thisScore = (thisScore << 4) | (long) ret[1];
                    } catch (Exception e) {
                        okMethod = false;
                        break;
                    }
                }
                if (okMethod) {
//                    Log.d("Lua", String.format("Score: %X %s", thisScore, AssignScorer.parameterListToString(parameters)));
                    if (thisScore < score) {
                        method = m;
                        Object[] tmp = args;
                        args = objs;
                        if (thisScore == 0)
                            break;
                        objs = tmp == null ? new Object[top] : tmp;
                        score = thisScore;
                    }
                }
            }

            // If method is null means there isn't one receiving the given arguments
            if (method == null) {
                StringBuilder sb = new StringBuilder();
                for (Method m : methods) {
                    sb.append(m.toString());
                    sb.append("\n");
                }
                throw new LuaException("Invalid method call. Invalid Parameters.\n" + sb.toString());
            }

            Object ret;
            try {
                if (!Modifier.isPublic(method.getModifiers()))
                    method.setAccessible(true);
                if (DEBUG)
                    Log.d("Lua", "invoke method " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(" + AssignScorer.parameterListToString(method.getParameterTypes()) + ")");
                ret = method.invoke(obj, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }

            // Void function returns null
            if (ret == null && method.getReturnType().equals(Void.TYPE))
                return 0;

            // push result
            L.pushObjectValue(ret);

            return 1;
        }
    }


    /**
     * Java implementation of the metamethod __newindex
     *
     * @param luaState int that indicates the state used
     * @param obj      Object to be indexed
     * @return number of returned objects
     */

    public static int objectNewIndex(long luaState, Object obj, String searchName)
            throws Throwable {
        if (DEBUG) Log.d("Lua", "objectNewIndex " + obj.getClass().getSimpleName() + " " + searchName);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            int res;

            res = setFieldValue(L, obj, searchName);
            if (res != 0)
                return 1;

            res = javaSetter(L, obj, searchName);
            if (res != 0)
                return 1;

            return 0;
        }
    }


    public static int setFieldValue(LuaState L, Object obj, String fieldName) throws LuaException {
        if (DEBUG) Log.d("Lua", "setFieldValue " + (obj == null ? "null" : obj.getClass().getSimpleName()) + " " + fieldName);

        synchronized (L) {
            Field field;
            Class objClass;
            boolean isClass = false;

            if (obj == null)
                return 0;

            if (obj instanceof Class) {
                objClass = (Class) obj;
                isClass = true;
            } else {
                objClass = obj.getClass();
            }

            try {
                field = objClass.getField(fieldName);
            } catch (NoSuchFieldException e) {
                /*try
                 {
				 field = objClass.getDeclaredField(fieldName);
				 }
				 catch (Exception e2)
				 */
                {
                    return 0;
                }
            }

            if (field == null)
                return 0;
            if (isClass && !Modifier.isStatic(field.getModifiers()))
                return 0;
            Class type = field.getType();
            try {
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);

                field.set(obj, compareTypes(L, type, 3)[0]);
            } catch (LuaException e) {
                argError(L, fieldName, 3, type);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            return 1;
        }
    }

    private static void argError(LuaState L, String name, int idx, Class type) throws LuaException {
        throw new LuaException("bad argument to '" + name + "' (" + type.getName() + " expected, got " + typeName(L, 3) + " value)");
    }

    private static String typeName(LuaState L, int idx) throws LuaException {
        if (L.isObject(idx)) {
            return L.getObjectFromUserdata(idx).getClass().getName();
        }
        switch (L.type(idx)) {
            case LuaState.LUA_TSTRING:
                return "string";
            case LuaState.LUA_TNUMBER:
                return "number";
            case LuaState.LUA_TBOOLEAN:
                return "boolean";
            case LuaState.LUA_TFUNCTION:
                return "function";
            case LuaState.LUA_TTABLE:
                return "table";
            case LuaState.LUA_TTHREAD:
                return "thread";
            case LuaState.LUA_TLIGHTUSERDATA:
            case LuaState.LUA_TUSERDATA:
                return "userdata";
        }
        return "unkown";
    }

    /**
     * Java implementation of the metamethod __index
     *
     * @param luaState int that indicates the state used
     * @param obj      Object to be indexed
     * @return number of returned objects
     */
    public static int setArrayValue(long luaState, Object obj, int index) throws LuaException {
        if (DEBUG) Log.d("Lua", "setArrayValue " + obj.getClass().getSimpleName() + " " + index);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            if (obj.getClass().isArray()) {
                Class<?> type = obj.getClass().getComponentType();
                try {
                    Object value = compareTypes(L, type, 3)[0];
                    Array.set(obj, index, value);
                } catch (LuaException e) {
                    argError(L, obj.getClass().getName() + " [" + index + "]", 3, type);
                }
            } else if (obj instanceof List) {
                //noinspection unchecked
                ((List<Object>) obj).set(index, L.toJavaObject(3));
            } else if (obj instanceof Map) {
                //noinspection unchecked
                ((Map<Integer, Object>) obj).put(index, L.toJavaObject(3));
            } else {
                throw new LuaException("can not set " + obj.getClass().getName() + " value: " + L.toJavaObject(3) + " in " + index);
            }
            return 0;
        }
    }

    public static int getArrayValue(long luaState, Object obj, int index) throws LuaException {
        if (DEBUG) Log.d("Lua", "getArrayValue " + obj.getClass().getSimpleName() + " " + index);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            Object ret;
            if (obj.getClass().isArray()) {
                ret = Array.get(obj, index);
            } else if (obj instanceof List) {
                ret = ((List) obj).get(index);
            } else if (obj instanceof Map) {
                ret = ((Map) obj).get(index);
            } else {
                throw new LuaException("can not get " + obj.getClass().getName() + " value in " + index);
            }
            L.pushObjectValue(ret);
            return 1;
        }
    }

    public static int asTable(long luaState, Object obj) throws LuaException {
        if (DEBUG) Log.d("Lua", "asTable " + obj.getClass().getSimpleName());

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {

            try {
                L.newTable();
                if (obj.getClass().isArray()) {
                    int n = Array.getLength(obj);
                    for (int i = 0; i <= n - 1; i++) {
                        L.pushObjectValue(Array.get(obj, i));
                        L.rawSetI(-2, i + 1);
                    }
                } else if (obj instanceof Collection) {
                    Collection list = (Collection) obj;
                    int i = 1;
                    for (Object v : list) {
                        L.pushObjectValue(v);
                        L.rawSetI(-2, i++);
                    }
                } else if (obj instanceof Map) {
                    Map map = (Map) obj;
                    for (Object o : map.entrySet()) {
                        Map.Entry entry = (Map.Entry) o;
                        L.pushObjectValue(entry.getKey());
                        L.pushObjectValue(entry.getValue());
                        L.setTable(-3);
                    }
                    /*
                     for (Map.Entry entry : map.entrySet())
					 { 
					 L.pushObjectValue(entry.getKey());
					 L.pushObjectValue(entry.getValue());
					 L.setTable(-3);
					 }*/
                }
                L.pushValue(-1);
                return 1;
            } catch (Exception e) {
                throw new LuaException("can not as table: " + e.getMessage());
            }

        }
    }

    public static int newArray(long luaState, Class<?> clazz, int size) throws LuaException {
        if (DEBUG) Log.d("Lua", "newArray " + clazz.getSimpleName() + " " + size);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Object obj = Array.newInstance(clazz, size);
                L.pushJavaObject(obj);
            } catch (Exception e) {
                throw new LuaException("can not create a array: " + e.getMessage());
            }
            return 1;
        }
    }

    public static int newArray(long luaState, Class<?> clazz) throws LuaException {
        if (DEBUG) Log.d("Lua", "newArray " + clazz.getSimpleName());

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                int top = L.getTop();
                int[] dimensions = new int[top - 1];
                for (int i = 0; i < top - 1; i++) {
                    dimensions[i] = (int) L.toInteger(i + 2);
                }
                Object obj = Array.newInstance(clazz, dimensions);
                L.pushJavaObject(obj);
            } catch (Exception e) {
                throw new LuaException("can not create a array: " + e.getMessage());
            }
            return 1;
        }
    }

    public static Class javaBindClass(String className) throws LuaException {
        if (DEBUG) Log.d("Lua", "javaBindClass " + className);

        Class clazz;
        try {
            clazz = Class.forName(className);
        } catch (Exception e) {
            switch (className) {
                case "boolean":
                    clazz = Boolean.TYPE;
                    break;
                case "byte":
                    clazz = Byte.TYPE;
                    break;
                case "char":
                    clazz = Character.TYPE;
                    break;
                case "short":
                    clazz = Short.TYPE;
                    break;
                case "int":
                    clazz = Integer.TYPE;
                    break;
                case "long":
                    clazz = Long.TYPE;
                    break;
                case "float":
                    clazz = Float.TYPE;
                    break;
                case "double":
                    clazz = Double.TYPE;
                    break;
                default:
                    throw new LuaException("Class not found: " + className);
            }
        }
        return clazz;
    }


    /**
     * Pushes a new instance of a java Object of the type className
     *
     * @param luaState  int that represents the state to be used
     * @param className name of the class
     * @return number of returned objects
     */
    public static int javaNewInstance(long luaState, String className) throws Throwable {
        if (DEBUG) Log.d("Lua", "javaNewInstance " + className);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            Class clazz;
            clazz = javaBindClass(className);
            if (clazz.isPrimitive())
                return toPrimitive(L, clazz, -1);
            else
                return getObjInstance(L, clazz);
        }
    }

    /**
     * javaNew returns a new instance of a given clazz
     *
     * @param luaState int that represents the state to be used
     * @param clazz    class to be instanciated
     * @return number of returned objects
     */
    public static int javaNew(long luaState, Class<?> clazz) throws Throwable {
        if (DEBUG) Log.d("Lua", "javaNew " + clazz.getSimpleName());

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            if (clazz.isPrimitive()) {
                return toPrimitive(L, clazz, -1);
            } else {
                return getObjInstance(L, clazz);
            }
        }
    }

    public static int javaCreate(long luaState, Class<?> clazz) throws Throwable {
        if (DEBUG) Log.d("Lua", "javaCreate " + clazz.getSimpleName());

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            if (clazz.isInterface()) {
                return createProxyObject(L, clazz);
            } else if (clazz.isPrimitive()) {
                return createArray(L, clazz);
            } else if (List_class.isAssignableFrom(clazz)) {
                return createList(L, clazz);
            } else if (Map_class.isAssignableFrom(clazz)) {
                return createMap(L, clazz);
            } else {
                if (L.type(-1) == LuaState.LUA_TTABLE) {
                    boolean empty = true;
                    boolean allFunction = true;

                    L.pushNil(); // 第一个键
                    while (L.next(-2) != 0) {
                        if (empty) empty = false;
                        // value -1 key -2
                        if (L.type(-1) != LuaState.LUA_TFUNCTION) {
                            L.pop(1);
                            allFunction = false;
                            break;
                        }
                        L.pop(1); // 移除value, 保留key做下一次迭代
                    }

                    if (!empty && allFunction) {
                        if (DEBUG) Log.d("Lua", "javaCreate => javaNew");
                        return getObjInstance(L, clazz);
                    }
                }
                if (L.objLen(-1) == 0)
                    return createArray(L, clazz);
                else if (clazz.isAssignableFrom(new LuaTable(L, -1).get(1).getClass()))
                    return createArray(L, clazz);
                else
                    return getObjInstance(L, clazz);
            }
        }

    }

    public static int objectCall(long luaState, Object obj) throws LuaException {
        if (DEBUG) Log.d("Lua", "objectCall " + obj.getClass().getSimpleName());

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            if (obj instanceof LuaMetaTable) {
                int n = L.getTop();
                Object[] args = new Object[n - 1];
                for (int i = 2; i <= n; i++) {
                    args[i - 2] = L.toJavaObject(i);
                }
                Object ret = ((LuaMetaTable) obj).__call(args);
                L.pushObjectValue(ret);
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Function that creates an object proxy and pushes it into the stack
     *
     * @param luaState int that represents the state to be used
     * @param implem   interfaces implemented separated by comma (<code>,</code>)
     * @return number of returned objects
     */
    public static int createProxy(long luaState, String implem)
            throws LuaException {
        if (DEBUG) Log.d("Lua", "createProxy " + implem);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            return createProxyObject(L, implem);
        }
    }

    public static int createArray(long luaState, String className)
            throws LuaException {
        if (DEBUG) Log.d("Lua", "createArray " + className);

        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            Class type = javaBindClass(className);
            return createArray(L, type);
        }
    }

    /**
     * Calls the static method <code>methodName</code> in class <code>className</code>
     * that receives a LuaState as first parameter.
     *
     * @param luaState   int that represents the state to be used
     * @param className  name of the class that has the open library method
     * @param methodName method to open library
     * @return number of returned objects
     */
    public static int javaLoadLib(long luaState, String className, String methodName)
            throws LuaException {
        if (DEBUG) Log.d("Lua", "javaLoadLib " + className + " " + methodName);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            Class clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new LuaException(e);
            }

            try {
                //noinspection unchecked
                Method mt = clazz.getMethod(methodName, LuaState_class);
                Object obj = mt.invoke(null, L);

                if (obj != null && obj instanceof Integer) {
                    return (Integer) obj;
                } else
                    return 0;
            } catch (Exception e) {
                throw new LuaException("Error on calling method. Library could not be loaded. " + e.getMessage());
            }
        }
    }

    public static int javaToString(long luaState, Object obj) throws LuaException {
        if (DEBUG) Log.d("Lua", "javaToString " + obj);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else
                L.pushString(obj.toString());
            return 1;
        }
    }

    public static int javaEquals(long luaState, Object obj, Object obj2) throws LuaException {
        if (DEBUG) Log.d("Lua", "javaEquals " + obj + " " + obj2);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            boolean eq = obj.equals(obj2);
            L.pushBoolean(eq);
            return 1;
        }
    }

    public static int javaObjectLength(long luaState, Object obj) throws LuaException {
        if (DEBUG) Log.d("Lua", "javaObjectLength " + obj);

        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            int ret;
            try {
                if (obj instanceof CharSequence)
                    ret = ((CharSequence) obj).length();
                else if (obj instanceof Collection)
                    ret = ((Collection) obj).size();
                else if (obj instanceof Map)
                    ret = ((Map) obj).size();
                else
                    ret = Array.getLength(obj);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            L.pushInteger(ret);

            return 1;
        }
    }


    private static int getObjInstance(LuaState L, Class<?> clazz) throws Throwable {
        synchronized (L) {
            int top = L.getTop();
            if (top == 1) {
                try {
                    Object ret = clazz.newInstance();
                    L.pushJavaObject(ret);
                    return 1;
                } catch (Exception e) {
                    throw new LuaException(e);
                }
            }
            Object[] objs = new Object[top - 1];
            Object[] args = null;

            Constructor[] constructors = clazz.getConstructors();
            Constructor constructor = null;
            long score = Long.MAX_VALUE;

            // gets method and arguments
            for (Constructor cs : constructors) {
                Class<?>[] parameters = cs.getParameterTypes();
                if (parameters.length != top - 1)
                    continue;

                boolean okConstruc = true;
                long thisScore = 0;

                for (int j = 0; j < parameters.length; j++) {
                    try {
                        Object[] ret = compareTypes(L, parameters[j], j + 2);
                        objs[j] = ret[0];
                        thisScore = (thisScore << 4) | (long) ret[1];
                    } catch (Exception e) {
                        okConstruc = false;
                        break;
                    }
                }

                if (okConstruc) {
                    if (thisScore < score) {
                        constructor = cs;
                        Object[] tmp = args;
                        args = objs;
                        if (thisScore == 0)
                            break;
                        objs = tmp == null ? new Object[top - 1] : tmp;
                        score = thisScore;
                    }
                }

            }

            // If method is null means there isn't one receiving the given arguments
            if (constructor == null) {
                StringBuilder sb = new StringBuilder();
                for (Constructor sc : constructors) {
                    sb.append(sc.toString());
                    sb.append("\n");
                }
                throw new LuaException("Invalid constructor method call. Invalid Parameters.\n" + sb.toString());
            }

            try {
                Object ret = constructor.newInstance(args);
                L.pushJavaObject(ret);
                return 1;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    /**
     * Checks if there is a field on the obj with the given name
     *
     * @param L         int that represents the state to be used
     * @param obj       object to be inspected
     * @param fieldName name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkField(LuaState L, Object obj, String fieldName)
            throws LuaException {
        if (DEBUG) Log.d("Lua", "checkField " + obj.getClass().getSimpleName() + " " + fieldName);

        synchronized (L) {
            Field field;
            Class objClass;
            boolean isClass = false;

            if (obj instanceof Class) {
                objClass = (Class) obj;
                isClass = true;
            } else {
                objClass = obj.getClass();
            }

            try {
                field = objClass.getField(fieldName);
            } catch (NoSuchFieldException e) {
                return 0;
            }

            if (field == null)
                return 0;

            if (isClass && !Modifier.isStatic(field.getModifiers()))
                return 0;

            Object ret;
            try {
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);
                ret = field.get(obj);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            L.pushObjectValue(ret);
            if (Modifier.isFinal(field.getModifiers()))
                return 5;
            else
                return 1;
        }
    }

    /**
     * Checks to see if there is a method with the given name.
     *
     * @param L          int that represents the state to be used
     * @param obj        object to be inspected
     * @param methodName name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkMethod(LuaState L, Object obj, String methodName) throws LuaException {
        if (DEBUG) Log.d("Lua", "checkMethod " + methodName);

        synchronized (L) {
            Class clazz;
            boolean isClass = false;
            if (obj instanceof Class) {
                clazz = (Class) obj;
                isClass = true;
            } else {
                clazz = obj.getClass();
            }

            String className = clazz.getName();
            String cacheName = L.toString(-1);//String.format("%c %s %s",c,className,methodName);
            Method[] mlist = methodCache.get(cacheName);
            if (mlist == null) {
                Method[] methods = methodsMap.get(className);
                if (methods == null) {
                    methods = clazz.getMethods();
                    methodsMap.put(className, methods);
                }
                ArrayList<Method> list = new ArrayList<>();

                for (Method method : methods) {
                    if (method.getName().equals(methodName)) {
                        if (isClass && !Modifier.isStatic(method.getModifiers()))
                            continue;
                        list.add(method);
                    }
                }

                if (list.isEmpty() && isClass) {
                    methods = clazz.getClass().getMethods();
                    for (Method method : methods) {
                        if (method.getName().equals(methodName))
                            list.add(method);
                    }

                }

                mlist = new Method[list.size()];
                list.toArray(mlist);
                methodCache.put(cacheName, mlist);
            }
            if (mlist.length == 0)
                return 0;
            return 2;
        }
    }

    /**
     * Checks to see if there is a class with the given name.
     *
     * @param L         int that represents the state to be used
     * @param obj       object to be inspected
     * @param className name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkClass(LuaState L, Object obj, String className) throws LuaException {
        if (DEBUG) Log.d("Lua", "checkClass " + obj.getClass().getSimpleName() + " " + className);

        synchronized (L) {
            Class clazz;

            if (obj instanceof Class) {
                clazz = (Class) obj;
            } else {
                return 0;
            }

            Class[] clazzes = clazz.getClasses();

            for (Class cls : clazzes) {
                if (cls.getSimpleName().equals(className)) {
                    L.pushJavaObject(cls);
                    return 3;
                }
            }
            return 0;
        }
    }

    public static int javaGetter(LuaState L, Object obj, String methodName) throws Throwable {
        if (DEBUG) Log.d("Lua", "javaGetter " + obj.getClass().getSimpleName() + " " + methodName);

        synchronized (L) {
            Class clazz;

            Method method;
            boolean isClass = false;
            if (obj instanceof Map) {
                Map map = (Map) obj;
                L.pushObjectValue(map.get(methodName));
                return 1;
            } else if (obj instanceof LuaJavaPackage) {
                L.pushJavaObject(((LuaJavaPackage) obj).get(L, methodName));
                return 1;
            } else if (obj instanceof Class) {
                clazz = (Class) obj;
                isClass = true;
            } else {
                clazz = obj.getClass();
            }

            if (!Character.isLowerCase(methodName.charAt(0)))
                return 0;
            try {
                char[] chars = methodName.toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                //noinspection unchecked
                method = clazz.getMethod("get" + new String(chars));
            } catch (NoSuchMethodException e) {
                return 0;
            }

            if (isClass && !Modifier.isStatic(method.getModifiers()))
                return 0;

            Object ret;
            try {
                ret = method.invoke(obj);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }

            if (ret instanceof CharSequence)
                L.pushString(ret.toString());
            else
                L.pushObjectValue(ret);
            return 1;
        }
    }

    public static int javaSetter(LuaState L, Object obj, String methodName) throws Throwable {
        if (DEBUG) Log.d("Lua", "javaSetter " + obj.getClass().getSimpleName() + " " + methodName);

        synchronized (L) {
            Class clazz;
            boolean isClass = false;

            if (obj instanceof Map) {
                Map map = (Map) obj;
                //noinspection unchecked
                map.put(methodName, L.toJavaObject(3));
                return 1;
            } else if (obj instanceof Class) {
                clazz = (Class) obj;
                isClass = true;
            } else {
                clazz = obj.getClass();
            }

            String className = clazz.getName();
            Method[] methods = methodsMap.get(className);
            if (methods == null) {
                methods = clazz.getMethods();
                methodsMap.put(className, methods);
            }

//            if (methodName.length() > 2 && methodName.substring(0, 2).equals("on") && L.type(-1) == LuaState.LUA_TFUNCTION)
//                return javaSetListener(L, obj, methodName, methods, isClass);

            if (!Character.isLowerCase(methodName.charAt(0)))
                return 0;
            char[] chars = methodName.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            return javaSetMethod(L, obj, new String(chars), methods, isClass);

        }
    }

//    private static int javaSetListener(LuaState L, Object obj, String methodName, Method[] methods, boolean isClass) throws LuaException {
//        synchronized (L) {
//            String name = "setOn" + methodName.substring(2) + "Listener";
//            for (Method m : methods) {
//                if (!m.getName().equals(name))
//                    continue;
//                if (isClass && !Modifier.isStatic(m.getModifiers()))
//                    continue;
//
//                Class<?>[] tp = m.getParameterTypes();
//                if (tp.length == 1 && tp[0].isInterface()) {
//                    L.newTable();
//                    L.pushValue(-2);
//                    L.setField(-2, methodName);
//                    try {
//                        Object listener = L.getLuaObject(-1).createProxy(tp[0]);
//                        m.invoke(obj, listener);
//                        return 1;
//                    } catch (Exception e) {
//                        throw new LuaException(e);
//                    }
//                }
//            }
//        }
//        return 0;
//    }

    private static int javaSetMethod(LuaState L, Object obj, String methodName, Method[] methods, boolean isClass)
            throws Throwable {
        synchronized (L) {
            String name = "set" + methodName;
            StringBuilder buf = new StringBuilder();

            Method method = null;
            Object arg = null;
            long score = Long.MAX_VALUE;

            for (Method m : methods) {
                if (!m.getName().equals(name))
                    continue;
                if (isClass && !Modifier.isStatic(m.getModifiers()))
                    continue;

                Class<?>[] tp = m.getParameterTypes();
                if (tp.length != 1)
                    continue;

                try {
                    Object[] ret = compareTypes(L, tp[0], -1);
                    long thisCore = (long) ret[1];
                    if (thisCore < score) {
                        arg = ret[0];
                        method = m;
                        if (thisCore == 0)
                            break;
                        score = thisCore;
                    }
                } catch (LuaException e) {
                    buf.append(tp[0]);
                    buf.append("\n");
                }
            }
            if (method != null) {
                try {
                    method.invoke(obj, arg);
                    return 1;
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }
            if (buf.length() > 0)
                throw new LuaException("Invalid setter " + methodName + ". Invalid Parameters.\n" + buf.toString() + L.typeName(-1));
        }
        return 0;
    }

    private static int createProxyObject(LuaState L, String implem)
            throws LuaException {
        synchronized (L) {
            try {
                LuaObject luaObj = L.getLuaObject(2);
                Object proxy = luaObj.createProxy(implem);
                L.pushJavaObject(proxy);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            return 1;
        }
    }

    private static int createProxyObject(LuaState L, Class implem) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createProxyObject(L, implem, 2));
            return 1;
        }
    }

    private static Object createProxyObject(LuaState L, Class implem, int idx) throws LuaException {
        synchronized (L) {
            try {
                LuaObject luaObj = L.getLuaObject(idx);
                return luaObj.createProxy(implem);
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    private static int createArray(LuaState L, Class<?> type) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createArray(L, type, 2));
            return 1;
        }
    }


    private static Object createArray(LuaState L, Class<?> type, int idx) throws LuaException {
        synchronized (L) {
            try {
                int n = L.objLen(idx);
                Object array = Array.newInstance(type, n);

                if (type == String_class) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toString(-1));
                        L.pop(1);
                    }
                } else if (type == Double.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toNumber(-1));
                        L.pop(1);
                    }
                } else if (type == Float.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (float) L.toNumber(-1));
                        L.pop(1);
                    }
                } else if (type == Long.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Integer.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (int) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Short.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (short) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Character.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (char) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Byte.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (byte) L.toInteger(-1));
                        L.pop(1);
                    }
                } else {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, compareTypes(L, type, -1)[0]);
                        L.pop(1);
                    }
                }
                return array;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    private static int createList(LuaState L, Class<?> type) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createList(L, type, 2));
            return 1;
        }
    }


    private static Object createList(LuaState L, Class<?> type, int idx) throws LuaException {
        synchronized (L) {
            int n = L.objLen(idx);
            try {
                if (type.equals(List_class))
                    type = ArrayList_class;
                //noinspection unchecked
                List<Object> list = (List<Object>) type.newInstance();
                for (int i = 1; i <= n; i++) {
                    L.pushNumber(i);
                    L.getTable(idx);
                    list.add(L.toJavaObject(-1));
                    L.pop(1);
                }
                return list;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }


    private static int createMap(LuaState L, Class<?> clazz) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createMap(L, clazz, 2));
            return 1;
        }
    }

    private static Object createMap(LuaState L, Class<?> clazz, int idx) throws LuaException {
        synchronized (L) {
            try {
                if (clazz.equals(Map_class))
                    clazz = HashMap_class;
                //noinspection unchecked
                Map<Object, Object> map = (Map<Object, Object>) clazz.newInstance();
                L.pushNil();
                while (L.next(idx) != 0) {
                    map.put(L.toJavaObject(-2), L.toJavaObject(-1));
                    L.pop(1);
                }
                return map;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    private static Object[] compareTypes(LuaState L, Class<?> parameter, int idx)
            throws LuaException {
        boolean okType = true;

        Object[] ret = new Object[2];

        Object obj = null;
        // 得分越低匹配度越高
        long score = 0xFF;

        int type = L.type(idx);

        switch (type) {
            case 0: //nil
                if (parameter.isPrimitive()) {
                    okType = false;
                } else {
                    obj = null;
                    score = 0;
                }
                break;
            case 1: //boolean
                if (parameter != boolean.class && parameter != Boolean.class)
                    okType = false;
                else {
                    obj = L.toBoolean(idx);
                    score = 0;
                }
                break;
            case 3: //number
                score = L.isInteger(idx) ?
                        AssignScorer.scoreInteger(parameter, L.toInteger(idx), ret) :
                        AssignScorer.scoreDouble(parameter, L.toNumber(idx), ret);
                if (score == -1)
                    okType = false;
                else
                    obj = ret[0];
                break;
            case 4: //string
                if (parameter.isAssignableFrom(String_class)) {
                    obj = L.toString(idx);
                    if (parameter == String.class)
                        score = 0;
                    else
                        score = 1;
                } else {
                    okType = false;
                }
                break;
            case 5: //table
                if (parameter.isAssignableFrom(LuaTable_class)) {
                    obj = L.getLuaObject(idx);
                    score = 0;
                } else if (parameter.isArray()) {
                    obj = createArray(L, parameter.getComponentType(), idx);
                    score = 0;
                } else if (parameter.isInterface()) {
                    obj = createProxyObject(L, parameter, idx);
                    score = 0;
                } else if (Map_class.isAssignableFrom(parameter)) {
                    obj = createMap(L, parameter, idx);
                    score = 0;
                } else {
                    okType = false;
                }
                break;
            case 6: //function
                if (parameter.isAssignableFrom(LuaFunction_class)) {
                    obj = L.getLuaObject(idx);
                    score = 0;
                } else {
                    if (parameter.isInterface()) {
                        int methodCount = LuaObject.getVirtualMethodCount(parameter);
                        if (methodCount == 1) {
                            LuaObject luaObj = L.getLuaObject(idx);
                            InvocationHandler handler = new LuaFunctionInvocationHandler(luaObj);
                            obj = Proxy.newProxyInstance(parameter.getClassLoader(), new Class[]{parameter}, handler);
                            score = 0;
                        } else
                            okType = false;
                    } else
                        okType = false;
                }
                break;
            case 7: //userdata
                if (L.isObject(idx)) {
                    Object userObj = L.getObjectFromUserdata(idx);
                    if (userObj == null) {
                        if (parameter.isPrimitive()) {
                            okType = false;
                        } else {
                            obj = null;
                            score = 0;
                        }
                    } else {
                        Class objClass = userObj.getClass();
                        score = AssignScorer.scoreClass(parameter, objClass);
                        if (score == -1) {
                            okType = false;
                        } else {
                            obj = userObj;
                        }
                    }
                } else if (parameter.isAssignableFrom(LuaObject_class)) {
                    obj = L.getLuaObject(idx);
                    score = 0;
                } else {
                    okType = false;
                }
                break;
            default: //other
                throw new LuaException("Invalid Parameters.");
        }
        if (!okType) {
            throw new LuaException("Invalid Parameter.");
        }
        ret[0] = obj;
        ret[1] = score;

        return ret;
    }


    private static int toPrimitive(LuaState L, Class type, int idx) throws LuaException {
        Object obj = null;

        if (type == Character.TYPE && L.type(idx) == LuaState.LUA_TSTRING) {
            String s = L.toString(idx);
            if (s.length() == 1)
                obj = s.charAt(0);
            else
                obj = s.toCharArray();
        } else if (type == Boolean.TYPE) {
            obj = L.toBoolean(idx);
        } else if (!L.isNumber(idx)) {
            throw new LuaException(L.toString(idx) + " is not number");
        } else if (type == Double.TYPE) {
            obj = L.toNumber(idx);
        } else if (type == Float.TYPE) {
            obj = (float) L.toNumber(idx);
        } else if (type == Long.TYPE) {
            obj = L.toInteger(idx);
        } else if (type == Integer.TYPE) {
            obj = (int) L.toInteger(idx);
        } else if (type == Short.TYPE) {
            obj = (short) L.toInteger(idx);
        } else if (type == Character.TYPE) {
            obj = (char) L.toInteger(idx);
        } else if (type == Byte.TYPE) {
            obj = (byte) L.toInteger(idx);
        }
        L.pushJavaObject(obj);
        return 1;
    }


}
