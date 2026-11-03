package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

@SuppressWarnings("restricted")
public final class Native {

	public static final AddressLayout PTR = ValueLayout.ADDRESS;
	public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
	public static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;
	public static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE;

	private static final Linker LINKER = Linker.nativeLinker();

	private Native() {
	}

	@NonNull
	public static SymbolLookup library(@NonNull String soname) {
		return SymbolLookup.libraryLookup(soname, Arena.global());
	}

	@NonNull
	public static MethodHandle function(@NonNull SymbolLookup lib, @NonNull String name,
			@Nullable MemoryLayout result, @NonNull MemoryLayout... args) {
		return LINKER.downcallHandle(symbol(lib, name), describe(result, args));
	}

	@NonNull
	public static MethodHandle variadic(@NonNull SymbolLookup lib, @NonNull String name, int fixed,
			@Nullable MemoryLayout result, @NonNull MemoryLayout... args) {
		return LINKER.downcallHandle(symbol(lib, name), describe(result, args),
				Linker.Option.firstVariadicArg(fixed));
	}

	@NonNull
	public static MemorySegment callback(@NonNull MethodHandle target, @Nullable MemoryLayout result,
			@NonNull Arena arena, @NonNull MemoryLayout... args) {
		return LINKER.upcallStub(target, describe(result, args), arena);
	}

	@Nullable
	public static String string(@NonNull MemorySegment pointer) {
		if (pointer.equals(MemorySegment.NULL)) {
			return null;
		}
		return pointer.reinterpret(Long.MAX_VALUE).getString(0);
	}

	@NonNull
	public static MemorySegment at(@NonNull MemorySegment pointer, long size) {
		return pointer.reinterpret(size);
	}

	public static boolean isNull(@Nullable MemorySegment pointer) {
		return pointer == null || pointer.equals(MemorySegment.NULL);
	}

	private static MemorySegment symbol(SymbolLookup lib, String name) {
		return lib.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Missing symbol " + name));
	}

	private static FunctionDescriptor describe(@Nullable MemoryLayout result, MemoryLayout... args) {
		return result == null ? FunctionDescriptor.ofVoid(args) : FunctionDescriptor.of(result, args);
	}

	@NonNull
	public static RuntimeException rethrow(@NonNull Throwable t) {
		if (t instanceof RuntimeException) {
			return (RuntimeException) t;
		}
		if (t instanceof Error) {
			throw (Error) t;
		}
		return new IllegalStateException(t);
	}
}
