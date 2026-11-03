package app.niixfliix.desktop.gtk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;

import app.niixfliix.desktop.platform.Native;

@SuppressWarnings("restricted")
public final class Fn {

	private static final Linker LINKER = Linker.nativeLinker();
	private static final Object SKIPPED = new Object();

	private final SymbolLookup lib;
	private final String name;
	private final FunctionDescriptor descriptor;
	private final int firstVariadic;
	private final List<MemoryLayout> argLayouts;
	private volatile MethodHandle handle;
	private volatile boolean looked;

	private Fn(SymbolLookup lib, String name, FunctionDescriptor descriptor, int firstVariadic) {
		this.lib = lib;
		this.name = name;
		this.descriptor = descriptor;
		this.firstVariadic = firstVariadic;
		this.argLayouts = descriptor.argumentLayouts();
	}

	@NonNull
	public static Fn of(@NonNull SymbolLookup lib, @NonNull String name, @Nullable MemoryLayout result,
			@NonNull MemoryLayout... args) {
		return new Fn(lib, name, describe(result, args), -1);
	}

	@NonNull
	public static Fn variadic(@NonNull SymbolLookup lib, @NonNull String name, int fixed,
			@Nullable MemoryLayout result, @NonNull MemoryLayout... args) {
		return new Fn(lib, name, describe(result, args), fixed);
	}

	private static FunctionDescriptor describe(@Nullable MemoryLayout result, MemoryLayout... args) {
		return result == null ? FunctionDescriptor.ofVoid(args) : FunctionDescriptor.of(result, args);
	}

	@NonNull
	public String name() {
		return name;
	}

	public boolean exists() {
		return resolve() != null;
	}

	@Nullable
	private MethodHandle resolve() {
		if (!looked) {
			synchronized (this) {
				if (!looked) {
					Optional<MemorySegment> symbol = lib.find(name);
					handle = symbol.map(s -> firstVariadic < 0 ? LINKER.downcallHandle(s, descriptor)
							: LINKER.downcallHandle(s, descriptor, Linker.Option.firstVariadicArg(firstVariadic)))
							.orElse(null);
					looked = true;
				}
			}
		}
		return handle;
	}

	private Object call(Object[] args) {
		MethodHandle h = resolve();
		if (h == null) {
			throw new UnsatisfiedLinkError("The installed library has no " + name);
		}
		if (args.length != argLayouts.size()) {
			throw new IllegalArgumentException(name + " takes " + argLayouts.size() + " arguments, not " + args.length);
		}
		try (Arena arena = Arena.ofConfined()) {
			Object[] converted = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				Object value = convert(args[i], argLayouts.get(i), arena);
				if (value == SKIPPED) {
					return SKIPPED;
				}
				converted[i] = value;
			}
			return h.invokeWithArguments(converted);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	private Object convert(@Nullable Object value, MemoryLayout layout, Arena arena) {
		if (layout instanceof ValueLayout.OfInt) {
			if (value instanceof Boolean b) {
				return b ? 1 : 0;
			}
			return ((Number) value).intValue();
		}
		if (layout instanceof ValueLayout.OfLong) {
			return ((Number) value).longValue();
		}
		if (layout instanceof ValueLayout.OfDouble) {
			return ((Number) value).doubleValue();
		}
		if (layout instanceof ValueLayout.OfFloat) {
			return ((Number) value).floatValue();
		}
		if (value == null) {
			return MemorySegment.NULL;
		}
		if (value instanceof GObj o) {
			return o.alive() ? o.ptr : SKIPPED;
		}
		if (value instanceof String s) {
			return arena.allocateFrom(s);
		}
		if (value instanceof String[] strings) {
			MemorySegment array = arena.allocate(ValueLayout.ADDRESS, strings.length + 1L);
			for (int i = 0; i < strings.length; i++) {
				array.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(strings[i]));
			}
			array.setAtIndex(ValueLayout.ADDRESS, strings.length, MemorySegment.NULL);
			return array;
		}
		if (value instanceof MemorySegment) {
			return value;
		}
		throw new IllegalArgumentException(name + " can't take a " + value.getClass().getName());
	}

	public void v(Object... args) {
		call(args);
	}

	public int i(Object... args) {
		Object r = call(args);
		return r == SKIPPED ? 0 : (int) r;
	}

	public boolean b(Object... args) {
		return i(args) != 0;
	}

	public long l(Object... args) {
		Object r = call(args);
		return r == SKIPPED ? 0 : (long) r;
	}

	public double d(Object... args) {
		Object r = call(args);
		return r == SKIPPED ? 0 : (double) r;
	}

	@NonNull
	public MemorySegment p(Object... args) {
		Object r = call(args);
		return r == SKIPPED ? MemorySegment.NULL : (MemorySegment) r;
	}

	@Nullable
	public String s(Object... args) {
		return Native.string(p(args));
	}
}
