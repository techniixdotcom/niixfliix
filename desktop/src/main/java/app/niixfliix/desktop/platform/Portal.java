package app.niixfliix.desktop.platform;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.LONG;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import app.niixfliix.util.Logs;

public final class Portal {

	private static final String TAG = "Portal";
	private static final int BUS_SESSION = 2;
	private static final int INHIBIT_IDLE = 8;
	private static final int TIMEOUT_MS = 5000;
	private static final String NAME = "org.freedesktop.portal.Desktop";
	private static final String PATH = "/org/freedesktop/portal/desktop";

	private static MemorySegment connection;
	private static MethodHandle dbusCall;
	private static MethodHandle parsed;
	private static MethodHandle child;
	private static MethodHandle getString;
	private static MethodHandle unref;

	private Portal() {
	}

	@Nullable
	public static synchronized String inhibitIdle(@NonNull String reason) {
		try {
			if (!connect()) {
				return null;
			}
			String params = "('', uint32 " + INHIBIT_IDLE + ", {'reason': <'" + quote(reason) + "'>})";
			MemorySegment reply = callOn(PATH, "org.freedesktop.portal.Inhibit", "Inhibit", params);
			if (Native.isNull(reply)) {
				return null;
			}
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment first = (MemorySegment) child.invokeExact(reply, 0L);
				String handle = Native.string((MemorySegment) getString.invokeExact(first, arena.allocate(LONG)));
				unref.invokeExact(first);
				return handle;
			} finally {
				unref.invokeExact(reply);
			}
		} catch (Throwable t) {
			Logs.w(TAG, "Could not keep the screen on", t);
			return null;
		}
	}

	public static synchronized void release(@NonNull String handle) {
		try {
			if (!connect()) {
				return;
			}
			MemorySegment reply = callOn(handle, "org.freedesktop.portal.Request", "Close", null);
			if (!Native.isNull(reply)) {
				unref.invokeExact(reply);
			}
		} catch (Throwable t) {
			Logs.w(TAG, "Could not let the screen blank again", t);
		}
	}

	private static MemorySegment callOn(String path, String iface, String method, @Nullable String params)
			throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment args = params == null ? MemorySegment.NULL
					: (MemorySegment) parsed.invokeExact(arena.allocateFrom(params), MemorySegment.NULL);
			MemorySegment error = arena.allocate(PTR);
			MemorySegment reply = (MemorySegment) dbusCall.invokeExact(connection, arena.allocateFrom(NAME),
					arena.allocateFrom(path), arena.allocateFrom(iface), arena.allocateFrom(method), args,
					MemorySegment.NULL, 0, TIMEOUT_MS, MemorySegment.NULL, error);
			String problem = GLib.takeError(error);
			if (problem != null) {
				Logs.w(TAG, method + " failed: " + problem, null);
			}
			return reply;
		}
	}

	private static boolean connect() throws Throwable {
		if (connection != null) {
			return !Native.isNull(connection);
		}
		SymbolLookup gio = Native.library("libgio-2.0.so.0");
		SymbolLookup glib = Native.library("libglib-2.0.so.0");
		MethodHandle busGet = Native.function(gio, "g_bus_get_sync", PTR, INT, PTR, PTR);
		dbusCall = Native.function(gio, "g_dbus_connection_call_sync", PTR,
				PTR, PTR, PTR, PTR, PTR, PTR, PTR, INT, INT, PTR, PTR);
		parsed = Native.variadic(glib, "g_variant_new_parsed", 1, PTR, PTR, PTR);
		child = Native.function(glib, "g_variant_get_child_value", PTR, PTR, LONG);
		getString = Native.function(glib, "g_variant_get_string", PTR, PTR, PTR);
		unref = Native.function(glib, "g_variant_unref", null, PTR);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment error = arena.allocate(PTR);
			connection = (MemorySegment) busGet.invokeExact(BUS_SESSION, MemorySegment.NULL, error);
			String problem = GLib.takeError(error);
			if (problem != null) {
				Logs.w(TAG, "No session bus: " + problem, null);
			}
		}
		return !Native.isNull(connection);
	}

	@NonNull
	static String quote(@NonNull String text) {
		return text.replace("\\", "\\\\").replace("'", "\\'");
	}
}
