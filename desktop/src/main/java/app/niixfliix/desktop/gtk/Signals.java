package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.LONG;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import app.niixfliix.desktop.platform.Native;
import app.niixfliix.util.Logs;

public final class Signals {

	enum Sig {
		V(null),
		B(INT),
		P(null, PTR),
		PRESSED(null, INT, DOUBLE, DOUBLE),
		XY(null, DOUBLE, DOUBLE),
		SCROLL(INT, DOUBLE, DOUBLE),
		KEY(INT, INT, INT, INT),
		CHANGE_VALUE(INT, INT, DOUBLE);

		final MemoryLayout result;
		final MemoryLayout[] params;
		MemorySegment stub;

		Sig(MemoryLayout result, MemoryLayout... params) {
			this.result = result;
			this.params = params;
		}
	}

	public interface Bool {
		boolean get();
	}

	public interface Ptr {
		void on(@NonNull MemorySegment pointer);
	}

	public interface Pressed {
		void on(int count, double x, double y);
	}

	public interface Xy {
		void on(double x, double y);
	}

	public interface Scroll {
		boolean on(double dx, double dy);
	}

	public interface Key {
		boolean on(int keyval, int keycode, int state);
	}

	public interface ChangeValue {
		boolean on(int scrollType, double value);
	}

	public interface Async {
		void done(@NonNull MemorySegment source, @NonNull MemorySegment result);
	}

	private static final String TAG = "Signals";
	private static final Map<Long, Object> HANDLERS = new ConcurrentHashMap<>();
	private static final AtomicLong NEXT = new AtomicLong();
	private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();

	private static final Fn CONNECT = Fn.of(Lib.GOBJECT, "g_signal_connect_data", LONG, PTR, PTR, PTR, PTR, PTR, INT);
	private static final Fn DISCONNECT = Fn.of(Lib.GOBJECT, "g_signal_handler_disconnect", null, PTR, LONG);
	private static final Fn LOOKUP = Fn.of(Lib.GOBJECT, "g_signal_lookup", INT, PTR, LONG);
	private static final Fn QUERY = Fn.of(Lib.GOBJECT, "g_signal_query", null, INT, PTR);
	private static final Fn FUNDAMENTAL = Fn.of(Lib.GOBJECT, "g_type_fundamental", LONG, LONG);
	private static final Fn TYPE_NAME = Fn.of(Lib.GOBJECT, "g_type_name", PTR, LONG);

	private static final long QUERY_SIZE = 56;
	private static final long QUERY_RETURN = 32;
	private static final long QUERY_N_PARAMS = 40;
	private static final long QUERY_PARAMS = 48;
	private static final long STATIC_SCOPE = 1;

	private static final MemorySegment DESTROY;
	static final MemorySegment ASYNC;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			Arena global = Arena.global();
			for (Sig sig : Sig.values()) {
				List<MemoryLayout> layouts = new ArrayList<>();
				layouts.add(PTR);
				layouts.addAll(Arrays.asList(sig.params));
				layouts.add(PTR);
				List<Class<?>> types = new ArrayList<>();
				for (MemoryLayout l : layouts) {
					types.add(carrier(l));
				}
				MethodType type = MethodType.methodType(sig.result == null ? void.class : int.class, types);
				MethodHandle target = lookup.findStatic(Signals.class, "on" + sig.name(), type);
				sig.stub = Native.callback(target, sig.result, global, layouts.toArray(new MemoryLayout[0]));
			}
			DESTROY = Native.callback(lookup.findStatic(Signals.class, "onDestroy",
					MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)), null, global, PTR, PTR);
			ASYNC = Native.callback(lookup.findStatic(Signals.class, "onAsync", MethodType.methodType(void.class,
					MemorySegment.class, MemorySegment.class, MemorySegment.class)), null, global, PTR, PTR, PTR);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private Signals() {
	}

	private static Class<?> carrier(MemoryLayout layout) {
		return ((ValueLayout) layout).carrier();
	}

	static long connect(@NonNull GObj object, @NonNull String signal, @NonNull Sig sig, @NonNull Object handler) {
		if (!object.alive()) {
			return 0;
		}
		check(object, signal, sig);
		long id = NEXT.incrementAndGet();
		HANDLERS.put(id, handler);
		long connection = CONNECT.l(object, signal, sig.stub, MemorySegment.ofAddress(id), DESTROY, 0);
		if (connection == 0) {
			HANDLERS.remove(id);
		}
		return connection;
	}

	static void disconnect(@NonNull GObj object, long connection) {
		if (connection != 0) {
			DISCONNECT.v(object, connection);
		}
	}

	@NonNull
	static MemorySegment async(@NonNull Async handler) {
		long id = NEXT.incrementAndGet();
		HANDLERS.put(id, handler);
		return MemorySegment.ofAddress(id);
	}

	private static void check(GObj object, String signal, Sig sig) {
		long type = typeOf(object.ptr);
		int detail = signal.indexOf("::");
		String name = detail >= 0 ? signal.substring(0, detail) : signal;
		String key = type + "|" + name + "|" + sig;
		if (CHECKED.contains(key)) {
			return;
		}
		int id = LOOKUP.i(name, type);
		if (id == 0) {
			throw new IllegalStateException(typeName(type) + " has no signal " + name);
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment query = arena.allocate(QUERY_SIZE, 8);
			QUERY.v(id, query);
			MemoryLayout result = kind(query.get(ValueLayout.JAVA_LONG, QUERY_RETURN));
			int count = query.get(ValueLayout.JAVA_INT, QUERY_N_PARAMS);
			MemorySegment params = Native.at(query.get(ValueLayout.ADDRESS, QUERY_PARAMS), 8L * Math.max(0, count));
			boolean same = sameKind(result, sig.result) && count == sig.params.length;
			for (int i = 0; same && i < count; i++) {
				same = sameKind(kind(params.getAtIndex(ValueLayout.JAVA_LONG, i)), sig.params[i]);
			}
			if (!same) {
				throw new IllegalStateException("Wrong handler for " + typeName(type) + "::" + name + ": " + sig);
			}
		}
		CHECKED.add(key);
	}

	private static long typeOf(MemorySegment instance) {
		MemorySegment klass = Native.at(instance, 8).get(ValueLayout.ADDRESS, 0);
		return Native.at(klass, 8).get(ValueLayout.JAVA_LONG, 0);
	}

	private static String typeName(long type) {
		String name = TYPE_NAME.s(type);
		return name != null ? name : String.valueOf(type);
	}

	private static MemoryLayout kind(long type) {
		long fundamental = FUNDAMENTAL.l(type & ~STATIC_SCOPE);
		switch ((int) (fundamental >> 2)) {
			case 1:
				return null;
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 12:
			case 13:
				return INT;
			case 8:
			case 9:
			case 10:
			case 11:
				return LONG;
			case 14:
				return ValueLayout.JAVA_FLOAT;
			case 15:
				return DOUBLE;
			default:
				return PTR;
		}
	}

	private static boolean sameKind(MemoryLayout a, MemoryLayout b) {
		return a == null ? b == null : b != null && carrier(a) == carrier(b);
	}

	private static Object handler(MemorySegment data) {
		return HANDLERS.get(data.address());
	}

	private static void fail(Throwable t) {
		Logs.w(TAG, "A signal handler failed", t);
	}

	@SuppressWarnings("unused")
	private static void onV(MemorySegment instance, MemorySegment data) {
		try {
			if (handler(data) instanceof Runnable r) {
				r.run();
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	@SuppressWarnings("unused")
	private static int onB(MemorySegment instance, MemorySegment data) {
		try {
			return handler(data) instanceof Bool h && h.get() ? 1 : 0;
		} catch (Throwable t) {
			fail(t);
			return 0;
		}
	}

	@SuppressWarnings("unused")
	private static void onP(MemorySegment instance, MemorySegment pointer, MemorySegment data) {
		try {
			if (handler(data) instanceof Ptr h) {
				h.on(pointer);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	@SuppressWarnings("unused")
	private static void onPRESSED(MemorySegment instance, int count, double x, double y, MemorySegment data) {
		try {
			if (handler(data) instanceof Pressed h) {
				h.on(count, x, y);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	@SuppressWarnings("unused")
	private static void onXY(MemorySegment instance, double x, double y, MemorySegment data) {
		try {
			if (handler(data) instanceof Xy h) {
				h.on(x, y);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	@SuppressWarnings("unused")
	private static int onSCROLL(MemorySegment instance, double dx, double dy, MemorySegment data) {
		try {
			return handler(data) instanceof Scroll h && h.on(dx, dy) ? 1 : 0;
		} catch (Throwable t) {
			fail(t);
			return 0;
		}
	}

	@SuppressWarnings("unused")
	private static int onKEY(MemorySegment instance, int keyval, int keycode, int state, MemorySegment data) {
		try {
			return handler(data) instanceof Key h && h.on(keyval, keycode, state) ? 1 : 0;
		} catch (Throwable t) {
			fail(t);
			return 0;
		}
	}

	@SuppressWarnings("unused")
	private static int onCHANGE_VALUE(MemorySegment instance, int scrollType, double value, MemorySegment data) {
		try {
			return handler(data) instanceof ChangeValue h && h.on(scrollType, value) ? 1 : 0;
		} catch (Throwable t) {
			fail(t);
			return 0;
		}
	}

	@SuppressWarnings("unused")
	private static void onDestroy(MemorySegment data, MemorySegment closure) {
		HANDLERS.remove(data.address());
	}

	@SuppressWarnings("unused")
	private static void onAsync(MemorySegment source, MemorySegment result, MemorySegment data) {
		Object h = HANDLERS.remove(data.address());
		try {
			if (h instanceof Async a) {
				a.done(source, result);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}
}
