package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import app.niixfliix.desktop.platform.GLib;
import app.niixfliix.desktop.platform.Native;

public class GObj {

	private static final Map<Long, Life> LIVES = new ConcurrentHashMap<>();
	private static final AtomicLong NEXT = new AtomicLong();
	private static final Fn WEAK_REF = Fn.of(Lib.GOBJECT, "g_object_weak_ref", null, PTR, PTR, PTR);
	private static final Fn REF = Fn.of(Lib.GOBJECT, "g_object_ref", PTR, PTR);
	private static final Fn REF_SINK = Fn.of(Lib.GOBJECT, "g_object_ref_sink", PTR, PTR);
	private static final Fn UNREF = Fn.of(Lib.GOBJECT, "g_object_unref", null, PTR);
	private static final MemorySegment WEAK_NOTIFY;

	static {
		try {
			WEAK_NOTIFY = Native.callback(MethodHandles.lookup().findStatic(GObj.class, "onGone",
					MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)), null, Arena.global(),
					PTR, PTR);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static final class Life {
		volatile boolean alive = true;
	}

	final MemorySegment ptr;
	private final Life life = new Life();

	protected GObj(@NonNull MemorySegment pointer) {
		if (Native.isNull(pointer)) {
			throw new IllegalStateException("GTK could not create " + getClass().getSimpleName());
		}
		ptr = pointer;
		long id = NEXT.incrementAndGet();
		LIVES.put(id, life);
		WEAK_REF.v(pointer, WEAK_NOTIFY, MemorySegment.ofAddress(id));
	}

	public final boolean alive() {
		return life.alive;
	}

	@NonNull
	public final MemorySegment pointer() {
		return ptr;
	}

	public final void ref() {
		REF.p(this);
	}

	final void refSink() {
		REF_SINK.p(this);
	}

	public final void unref() {
		UNREF.v(this);
	}

	public final void set(@NonNull String property, @NonNull String value) {
		if (alive()) {
			GLib.set(ptr, property, value);
		}
	}

	public final void set(@NonNull String property, boolean value) {
		if (alive()) {
			GLib.set(ptr, property, value);
		}
	}

	public final void set(@NonNull String property, int value) {
		if (alive()) {
			GLib.set(ptr, property, value);
		}
	}

	public final void set(@NonNull String property, double value) {
		if (alive()) {
			GLib.set(ptr, property, value);
		}
	}

	public final long on(@NonNull String signal, @NonNull Runnable handler) {
		return Signals.connect(this, signal, Signals.Sig.V, handler);
	}

	public final long onBool(@NonNull String signal, @NonNull Signals.Bool handler) {
		return Signals.connect(this, signal, Signals.Sig.B, handler);
	}

	public final long onPtr(@NonNull String signal, @NonNull Signals.Ptr handler) {
		return Signals.connect(this, signal, Signals.Sig.P, handler);
	}

	public final long onNotify(@NonNull String property, @NonNull Runnable handler) {
		return onPtr("notify::" + property, spec -> handler.run());
	}

	public final void disconnect(long connection) {
		Signals.disconnect(this, connection);
	}

	@SuppressWarnings("unused")
	private static void onGone(MemorySegment data, MemorySegment object) {
		Life life = LIVES.remove(data.address());
		if (life != null) {
			life.alive = false;
		}
	}
}
