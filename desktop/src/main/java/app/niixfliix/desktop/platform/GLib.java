package app.niixfliix.desktop.platform;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.LONG;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

public final class GLib {

	static final long TYPE_STRING = 16L << 2;
	static final long VALUE_SIZE = 24;

	private static final SymbolLookup GLIB = Native.library("libglib-2.0.so.0");
	private static final SymbolLookup GOBJECT = Native.library("libgobject-2.0.so.0");

	private static final MethodHandle FREE = Native.function(GLIB, "g_free", null, PTR);
	private static final MethodHandle ERROR_FREE = Native.function(GLIB, "g_error_free", null, PTR);
	private static final MethodHandle UNREF = Native.function(GOBJECT, "g_object_unref", null, PTR);
	private static final MethodHandle REF_SINK = Native.function(GOBJECT, "g_object_ref_sink", PTR, PTR);
	private static final MethodHandle SET_POINTER = Native.variadic(GOBJECT, "g_object_set", 2, null,
			PTR, PTR, PTR, PTR);
	private static final MethodHandle SET_INT = Native.variadic(GOBJECT, "g_object_set", 2, null,
			PTR, PTR, INT, PTR);
	private static final MethodHandle SET_LONG = Native.variadic(GOBJECT, "g_object_set", 2, null,
			PTR, PTR, LONG, PTR);
	private static final MethodHandle SET_DOUBLE = Native.variadic(GOBJECT, "g_object_set", 2, null,
			PTR, PTR, DOUBLE, PTR);
	private static final MethodHandle GET = Native.variadic(GOBJECT, "g_object_get", 2, null,
			PTR, PTR, PTR, PTR);
	private static final MethodHandle FIND_PROPERTY = Native.function(GOBJECT, "g_object_class_find_property",
			PTR, PTR, PTR);
	private static final MethodHandle CONNECT = Native.function(GOBJECT, "g_signal_connect_data", LONG,
			PTR, PTR, PTR, PTR, PTR, INT);
	private static final MethodHandle EMIT_INT_OUT = Native.variadic(GOBJECT, "g_signal_emit_by_name", 2, null,
			PTR, PTR, INT, PTR);
	private static final MethodHandle VALUE_INIT = Native.function(GOBJECT, "g_value_init", PTR, PTR, LONG);
	private static final MethodHandle VALUE_SET_STRING = Native.function(GOBJECT, "g_value_set_string", null,
			PTR, PTR);
	private static final MethodHandle VALUE_UNSET = Native.function(GOBJECT, "g_value_unset", null, PTR);

	private GLib() {
	}

	public static void free(@NonNull MemorySegment pointer) {
		if (Native.isNull(pointer)) {
			return;
		}
		try {
			FREE.invokeExact(pointer);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void unref(@NonNull MemorySegment object) {
		if (Native.isNull(object)) {
			return;
		}
		try {
			UNREF.invokeExact(object);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment own(@NonNull MemorySegment object) {
		try {
			return (MemorySegment) REF_SINK.invokeExact(object);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@Nullable
	public static String takeError(@NonNull MemorySegment errorPointer) {
		MemorySegment error = errorPointer.get(PTR, 0);
		if (Native.isNull(error)) {
			return null;
		}
		String message = Native.string(Native.at(error, 16).get(PTR, 8));
		try {
			ERROR_FREE.invokeExact(error);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
		return message != null ? message : "unknown error";
	}

	public static void set(@NonNull MemorySegment object, @NonNull String property, @NonNull String value) {
		try (Arena arena = Arena.ofConfined()) {
			setPointer(object, property, arena.allocateFrom(value), arena);
		}
	}

	public static void setPointer(@NonNull MemorySegment object, @NonNull String property,
			@NonNull MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			setPointer(object, property, value, arena);
		}
	}

	private static void setPointer(MemorySegment object, String property, MemorySegment value, Arena arena) {
		try {
			SET_POINTER.invokeExact(object, arena.allocateFrom(property), value, MemorySegment.NULL);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void set(@NonNull MemorySegment object, @NonNull String property, int value) {
		try (Arena arena = Arena.ofConfined()) {
			SET_INT.invokeExact(object, arena.allocateFrom(property), value, MemorySegment.NULL);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void set(@NonNull MemorySegment object, @NonNull String property, boolean value) {
		set(object, property, value ? 1 : 0);
	}

	public static void set(@NonNull MemorySegment object, @NonNull String property, long value) {
		try (Arena arena = Arena.ofConfined()) {
			SET_LONG.invokeExact(object, arena.allocateFrom(property), value, MemorySegment.NULL);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void set(@NonNull MemorySegment object, @NonNull String property, double value) {
		try (Arena arena = Arena.ofConfined()) {
			SET_DOUBLE.invokeExact(object, arena.allocateFrom(property), value, MemorySegment.NULL);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static int getInt(@NonNull MemorySegment object, @NonNull String property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(8);
			GET.invokeExact(object, arena.allocateFrom(property), out, MemorySegment.NULL);
			return out.get(INT, 0);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment getPointer(@NonNull MemorySegment object, @NonNull String property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(PTR);
			GET.invokeExact(object, arena.allocateFrom(property), out, MemorySegment.NULL);
			return out.get(PTR, 0);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static boolean hasProperty(@NonNull MemorySegment object, @NonNull String property) {
		MemorySegment klass = Native.at(object, 8).get(PTR, 0);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment spec = (MemorySegment) FIND_PROPERTY.invokeExact(klass, arena.allocateFrom(property));
			return !Native.isNull(spec);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void connect(@NonNull MemorySegment object, @NonNull String signal, @NonNull MemorySegment handler) {
		try (Arena arena = Arena.ofConfined()) {
			long id = (long) CONNECT.invokeExact(object, arena.allocateFrom(signal), handler, MemorySegment.NULL,
					MemorySegment.NULL, 0);
			if (id == 0) {
				throw new IllegalStateException("No signal " + signal);
			}
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment emit(@NonNull MemorySegment object, @NonNull String signal, int index) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(PTR);
			EMIT_INT_OUT.invokeExact(object, arena.allocateFrom(signal), index, out);
			return out.get(PTR, 0);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	static MemorySegment stringValue(@NonNull String text, @NonNull Arena arena) {
		MemorySegment value = arena.allocate(VALUE_SIZE, 8);
		try {
			MemorySegment ignored = (MemorySegment) VALUE_INIT.invokeExact(value, TYPE_STRING);
			VALUE_SET_STRING.invokeExact(value, arena.allocateFrom(text));
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
		return value;
	}

	static void unsetValue(@NonNull MemorySegment value) {
		try {
			VALUE_UNSET.invokeExact(value);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}
}
