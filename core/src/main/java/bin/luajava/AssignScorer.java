package bin.luajava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

class AssignScorer {
    private static final HashMap<String, Integer> CACHE = new HashMap<>();

    static int scoreClass(Class<?> parameter, Class<?> objClass) {
        String key = parameter.getName() + " " + objClass.getName();
        Integer integer = CACHE.get(key);
        if (integer != null)
            return integer;

        //noinspection unused
        int baseScore = 0;
        if (parameter.isPrimitive()) {
            baseScore = 1;
            parameter = toPrimitiveBoxClass(parameter);
        }

        int score;
        if (parameter == objClass) {
            score = baseScore;
        } else if (parameter == Character.class) {
            score = -1;
        } else if (parameter == Boolean.class) {
            score = -1;
        } else if (parameter == Byte.class) {
            score = -1;
        } else if (parameter == Short.class) {
            if (objClass == Byte.class) score = 2;
            else score = -1;
        } else if (parameter == Integer.class) {
            if (objClass == Byte.class) score = 3;
            else if (objClass == Short.class) score = 2;
            else score = -1;
        } else if (parameter == Long.class) {
            if (objClass == Byte.class) score = 4;
            else if (objClass == Short.class) score = 3;
            else if (objClass == Integer.class) score = 2;
            else score = -1;
        } else if (parameter == Float.class) {
            if (objClass == Byte.class) score = 5;
            else if (objClass == Short.class) score = 4;
            else if (objClass == Integer.class) score = 3;
            else if (objClass == Long.class) score = 2;
            else score = -1;
        } else if (parameter == Double.class) {
            if (objClass == Byte.class) score = 6;
            else if (objClass == Short.class) score = 5;
            else if (objClass == Integer.class) score = 4;
            else if (objClass == Long.class) score = 3;
            else if (objClass == Float.class) score = 2;
            else score = -1;
        } else if (parameter.isAssignableFrom(objClass)) {
            score = 7;
            if (parameter.isInterface()) {
                List<Class<?>> current = new ArrayList<>();
                List<Class<?>> collection = new ArrayList<>();
                Collections.addAll(current, objClass.getInterfaces());
                ok:
                while (!current.isEmpty()) {
                    for (Class<?> aClass : current) {
                        if (aClass == parameter)
                            break ok;
                        Collections.addAll(collection, aClass.getInterfaces());
                    }
                    score++;
                    current.clear();
                    //noinspection unused
                    List<Class<?>> tmp = current;
                    current = collection;
                    collection = tmp;
                }
            } else {
                for (Class cls = objClass.getSuperclass(); cls != null && cls != parameter; cls = cls.getSuperclass()) {
                    score++;
                }
            }
        } else {
            score = -1;
        }
        CACHE.put(key, score);
        return score;
    }

//    static boolean isAssignableFrom(Class<?> src, Class<?> cls) {
//        if (src == null || cls == null)
//            return false;
//        if (src == cls) {
//            return true;  // Can always assign to things of the same type.
//        } else if (src == Object.class) {
//            return !cls.isPrimitive();  // Can assign any reference to java.lang.Object.
//        } else if (src.isArray()) {
//            return cls.isArray() && isAssignableFrom(src.getComponentType(), cls.getComponentType());
//        } else if (src.isInterface()) {
//            // Search iftable which has a flattened and uniqued list of interfaces.
//            Object[] iftable = cls.getInterfaces();
//            if (iftable != null) {
//                for (int i = 0; i < iftable.length; i += 2) {
//                    if (iftable[i] == src) {
//                        return true;
//                    }
//                }
//            }
//            return false;
//        } else {
//            if (!cls.isInterface()) {
//                for (cls = cls.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
//                    if (cls == src) {
//                        return true;
//                    }
//                }
//            }
//            return false;
//        }
//    }

    static int scoreInteger(Class<?> parameter, long value, Object[] ret) {
        int score = -1;
        if (parameter == Number.class) {
            ret[0] = value;
            score = 1;
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            if (parameter == int.class || parameter == Integer.class) {
                ret[0] = (int) value;
                score = 0;
            } else if (parameter == long.class || parameter == Long.class) {
                ret[0] = value;
                score = 1;
            } else if (parameter == float.class || parameter == Float.class) {
                ret[0] = (float) value;
                score = 2;
            } else if (parameter == double.class || parameter == Double.class) {
                ret[0] = (double) value;
                score = 3;
            } else if (parameter == byte.class || parameter == Byte.class) {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    ret[0] = (byte) value;
                    score = 4;
                }
            } else if (parameter == short.class || parameter == Short.class) {
                if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    ret[0] = (short) value;
                    score = 4;
                }
            }
        } else if (parameter == long.class || parameter == Long.class) {
            ret[0] = value;
            score = 0;
        } else if (parameter == float.class || parameter == Float.class) {
            ret[0] = (float) value;
            score = 1;
        } else if (parameter == double.class || parameter == Double.class) {
            ret[0] = (double) value;
            score = 2;
        }
        return score;
    }

    static int scoreDouble(Class<?> parameter, double value, Object[] ret) {
        int score = -1;
        if (parameter == Number.class) {
            ret[0] = value;
            score = 1;
        } else if (value >= Float.MIN_VALUE && value <= Float.MAX_VALUE) {
            if (parameter == float.class || parameter == Float.class) {
                ret[0] = (float) value;
                score = 0;
            } else if (parameter == double.class || parameter == Double.class) {
                ret[0] = value;
                score = 1;
            }
        } else if (parameter == double.class || parameter == Double.class) {
            ret[0] = value;
            score = 0;
        }
        return score;
    }

    static Class<?> toPrimitiveBoxClass(Class clazz) {
        if (clazz == int.class) {
            return Integer.class;
        } else if (clazz == char.class) {
            return Character.class;
        } else if (clazz == byte.class) {
            return Byte.class;
        } else if (clazz == short.class) {
            return Short.class;
        } else if (clazz == long.class) {
            return Long.class;
        } else if (clazz == boolean.class) {
            return Boolean.class;
        } else if (clazz == float.class) {
            return Float.class;
        } else if (clazz == double.class) {
            return Double.class;
        } else
            return clazz;
    }

    static String parameterListToString(Class[] parameters) {
        StringBuilder sb = new StringBuilder();
        for (Class parameter : parameters) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(parameter.getName());
        }
        return sb.toString();
    }
}
