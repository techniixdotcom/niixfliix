package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import app.niixfliix.desktop.platform.Native;
import app.niixfliix.util.Logs;

public final class MainLoop {

	private static final String TAG = "MainLoop";
	private static final int PRIORITY_DEFAULT = 0;
	private static final Fn IDLE_ADD = Fn.of(Lib.GLIB, "g_idle_add_full", INT, INT, PTR, PTR, PTR);
	private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
	private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
	private static final MemorySegment DRAIN;
	private static volatile Thread thread;

	static {
		try {
			DRAIN = Native.callback(MethodHandles.lookup().findStatic(MainLoop.class, "drain",
					MethodType.methodType(int.class, MemorySegment.class)), INT, Arena.global(), PTR);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private MainLoop() {
	}

	static void bindToCurrentThread() {
		thread = Thread.currentThread();
	}

	public static boolean isMainThread() {
		return Thread.currentThread() == thread;
	}

	public static void post(@NonNull Runnable task) {
		TASKS.add(task);
		if (SCHEDULED.compareAndSet(false, true)) {
			IDLE_ADD.i(PRIORITY_DEFAULT, DRAIN, MemorySegment.NULL, MemorySegment.NULL);
		}
	}

	@NonNull
	public static Executor executor() {
		return MainLoop::post;
	}

	@SuppressWarnings("unused")
	private static int drain(MemorySegment data) {
		SCHEDULED.set(false);
		int budget = TASKS.size();
		for (int i = 0; i < budget; i++) {
			Runnable task = TASKS.poll();
			if (task == null) {
				break;
			}
			try {
				task.run();
			} catch (Throwable t) {
				Logs.w(TAG, "A task on the GTK thread failed", t);
			}
		}
		return 0;
	}
}
