package bin.luajava;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"WeakerAccess", "unused"})
public class LuaTable<K, V> extends LuaObject implements Map<K, V> {

    @Override
    public void clear() {
        push();
        L.pushNil();
        while (L.next(-2) != 0) {
            L.pop(1);
            L.pushValue(-1);
            L.pushNil();
            L.setTable(-4);
        }
        L.pop(1);
    }

    @Override
    public boolean containsKey(Object key) {
        boolean b;
        push();
        try {
            L.pushObjectValue(key);
            b = L.getTable(-2) == LuaState.LUA_TNIL;
            L.pop(1);
        } catch (LuaException e) {
            return false;
        }
        L.pop(1);
        return b;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @NonNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        HashSet<Entry<K, V>> sets = new HashSet<>();
        push();
        L.pushNil();
        while (L.next(-2) != 0) {
            try {
                //noinspection unchecked
                sets.add(new LuaEntry<>((K) L.toJavaObject(-2), (V) L.toJavaObject(-1)));
            } catch (LuaException ignored) {
            }
            L.pop(1);
        }
        L.pop(1);
        return sets;
    }

    @Override
    public V get(Object key) {
        push();
        V obj = null;
        try {
            L.pushObjectValue(key);
            L.getTable(-2);
            //noinspection unchecked
            obj = (V) L.toJavaObject(-1);
            L.pop(1);
        } catch (LuaException ignored) {
        }
        L.pop(1);
        return obj;
    }


    @Override
    public boolean isEmpty() {
        push();
        L.pushNil();
        boolean b = L.next(-2) == 0;
        if (b)
            L.pop(1);
        else
            L.pop(3);
        return b;
    }

    @NonNull
    @Override
    public Set<K> keySet() {
        HashSet<K> sets = new HashSet<>();
        push();
        L.pushNil();
        while (L.next(-2) != 0) {
            try {
                //noinspection unchecked
                sets.add((K) L.toJavaObject(-2));
            } catch (LuaException ignored) {
            }
            L.pop(1);
        }
        L.pop(1);
        return sets;
    }

    @Override
    public V put(K key, V value) {
        push();
        try {
            L.pushObjectValue(key);
            L.pushObjectValue(value);
            L.setTable(-3);
        } catch (LuaException ignored) {
        }
        L.pop(1);
        return null;
    }

    @Override
    public void putAll(@NonNull Map p1) {
    }

    @Override
    public V remove(Object key) {
        push();
        try {
            L.pushObjectValue(key);
            L.setTable(-2);
        } catch (LuaException ignored) {
        }
        L.pop(1);
        return null;
    }

    public boolean isList() {
        push();
        int len = L.rawLen(-1);
        if (len != 0) {
            pop();
            return true;
        }
        L.pushNil();
        boolean b = L.next(-2) == 0;
        if (b)
            L.pop(1);
        else
            L.pop(3);
        return b;
    }

    public int length() {
        push();
        int len = L.rawLen(-1);
        pop();
        return len;
    }

    @Override
    public int size() {
        int n = 0;
        push();
        L.pushNil();
        while (L.next(-2) != 0) {
            n++;
            L.pop(1);
        }
        L.pop(1);
        return n;
    }

    @NonNull
    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }


    protected LuaTable(LuaState L, String globalName) {
        super(L, globalName);
    }

    protected LuaTable(LuaState L, int index) {
        super(L, index);
    }

    public LuaTable(LuaState L) {
        super(L);
        L.newTable();
        registerValue(-1);
    }

    public class LuaEntry<EK, EV> implements Entry<EK, EV> {

        private EK mKey;

        private EV mValue;

        @Override
        public EK getKey() {
            return mKey;
        }

        @Override
        public EV getValue() {
            return mValue;
        }

        public EV setValue(EV value) {
            EV old = mValue;
            mValue = value;
            return old;
        }

        public LuaEntry(EK k, EV v) {
            mKey = k;
            mValue = v;
        }
    }
}
