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
import java.util.Map;
import java.util.function.Consumer;

public final class Gst {

	public static final int STATE_NULL = 1;
	public static final int STATE_READY = 2;
	public static final int STATE_PAUSED = 3;
	public static final int STATE_PLAYING = 4;
	public static final int STATE_CHANGE_FAILURE = 0;

	public static final int MESSAGE_EOS = 1;
	public static final int MESSAGE_ERROR = 1 << 1;
	public static final int MESSAGE_BUFFERING = 1 << 5;
	public static final int MESSAGE_STATE_CHANGED = 1 << 6;
	public static final int MESSAGE_ASYNC_DONE = 1 << 21;

	private static final int FORMAT_TIME = 3;
	private static final int SEEK_FLAG_FLUSH = 1;
	private static final int SEEK_FLAG_KEY_UNIT = 1 << 2;
	private static final int SEEK_FLAG_SNAP_NEAREST = (1 << 5) | (1 << 6);
	private static final int MAP_READ = 1;
	private static final long MESSAGE_TYPE_OFFSET = 64;
	private static final long MESSAGE_SRC_OFFSET = 80;
	private static final long MAP_INFO_SIZE = 128;
	private static final long MAP_DATA_OFFSET = 16;
	private static final long MAP_SIZE_OFFSET = 24;
	private static final long GVALUE_SIZE = 24;
	private static final int ITERATOR_OK = 1;
	private static final int ITERATOR_RESYNC = 2;
	private static final int MAX_ITERATIONS = 10_000;

	private static volatile boolean initialized;

	private static SymbolLookup gst;
	private static SymbolLookup app;
	private static MethodHandle init;
	private static MethodHandle factoryMake;
	private static MethodHandle setState;
	private static MethodHandle queryPosition;
	private static MethodHandle queryDuration;
	private static MethodHandle seekSimple;
	private static MethodHandle getBus;
	private static MethodHandle busPop;
	private static MethodHandle miniUnref;
	private static MethodHandle parseError;
	private static MethodHandle parseBuffering;
	private static MethodHandle parseStateChanged;
	private static MethodHandle pullSample;
	private static MethodHandle pullPreroll;
	private static MethodHandle sampleBuffer;
	private static MethodHandle sampleCaps;
	private static MethodHandle capsStructure;
	private static MethodHandle structureGetInt;
	private static MethodHandle bufferMap;
	private static MethodHandle bufferUnmap;
	private static MethodHandle capsFromString;
	private static MethodHandle tagGetString;
	private static MethodHandle elementFactory;
	private static MethodHandle objectName;
	private static MethodHandle structureNewEmpty;
	private static MethodHandle structureSetValue;
	private static MethodHandle structureFree;
	private static MethodHandle iterateByFactory;
	private static MethodHandle iteratorNext;
	private static MethodHandle iteratorResync;
	private static MethodHandle iteratorFree;
	private static MethodHandle valueGetObject;
	private static MethodHandle valueReset;
	private static MethodHandle valueUnset;
	private static MethodHandle factoryFind;
	private static MethodHandle objectUnref;
	private static MethodHandle registryGet;
	private static MethodHandle lookupFeature;
	private static MethodHandle setRank;

	private static final int RANK_NONE = 0;
	private static final int RANK_PRIMARY = 256;
	private static final String[] HARDWARE_DECODERS = {
			"vah264dec", "vah265dec", "vavp8dec", "vavp9dec", "vaav1dec", "vampeg2dec", "vah266dec",
			"nvh264dec", "nvh265dec", "nvvp8dec", "nvvp9dec", "nvav1dec", "nvmpeg2videodec"};
	private static final String[] NEVER = {
			"vaapidecodebin", "vaapih264dec", "vaapih265dec", "vaapivp8dec", "vaapivp9dec", "vaapiav1dec",
			"vaapimpeg2dec", "vaapivc1dec", "vaapijpegdec",
			"vulkanh264dec", "vulkanh265dec", "vulkanav1dec", "vulkanvp9dec",
			"qsvh264dec", "qsvh265dec", "qsvvp9dec", "qsvav1dec", "qsvjpegdec",
			"msdkh264dec", "msdkh265dec", "msdkvp9dec", "msdkav1dec", "msdkmpeg2dec", "msdkvc1dec"};

	private Gst() {
	}

	public static synchronized void init() {
		if (initialized) {
			return;
		}
		gst = Native.library("libgstreamer-1.0.so.0");
		app = Native.library("libgstapp-1.0.so.0");
		init = Native.function(gst, "gst_init", null, PTR, PTR);
		MethodHandle segtrap = Native.function(gst, "gst_segtrap_set_enabled", null, INT);
		factoryMake = Native.function(gst, "gst_element_factory_make", PTR, PTR, PTR);
		setState = Native.function(gst, "gst_element_set_state", INT, PTR, INT);
		queryPosition = Native.function(gst, "gst_element_query_position", INT, PTR, INT, PTR);
		queryDuration = Native.function(gst, "gst_element_query_duration", INT, PTR, INT, PTR);
		seekSimple = Native.function(gst, "gst_element_seek_simple", INT, PTR, INT, INT, LONG);
		getBus = Native.function(gst, "gst_element_get_bus", PTR, PTR);
		busPop = Native.function(gst, "gst_bus_timed_pop_filtered", PTR, PTR, LONG, INT);
		miniUnref = Native.function(gst, "gst_mini_object_unref", null, PTR);
		parseError = Native.function(gst, "gst_message_parse_error", null, PTR, PTR, PTR);
		parseBuffering = Native.function(gst, "gst_message_parse_buffering", null, PTR, PTR);
		parseStateChanged = Native.function(gst, "gst_message_parse_state_changed", null, PTR, PTR, PTR, PTR);
		pullSample = Native.function(app, "gst_app_sink_try_pull_sample", PTR, PTR, LONG);
		pullPreroll = Native.function(app, "gst_app_sink_try_pull_preroll", PTR, PTR, LONG);
		sampleBuffer = Native.function(gst, "gst_sample_get_buffer", PTR, PTR);
		sampleCaps = Native.function(gst, "gst_sample_get_caps", PTR, PTR);
		capsStructure = Native.function(gst, "gst_caps_get_structure", PTR, PTR, INT);
		structureGetInt = Native.function(gst, "gst_structure_get_int", INT, PTR, PTR, PTR);
		bufferMap = Native.function(gst, "gst_buffer_map", INT, PTR, PTR, INT);
		bufferUnmap = Native.function(gst, "gst_buffer_unmap", null, PTR, PTR);
		capsFromString = Native.function(gst, "gst_caps_from_string", PTR, PTR);
		tagGetString = Native.function(gst, "gst_tag_list_get_string", INT, PTR, PTR, PTR);
		elementFactory = Native.function(gst, "gst_element_get_factory", PTR, PTR);
		objectName = Native.function(gst, "gst_object_get_name", PTR, PTR);
		structureNewEmpty = Native.function(gst, "gst_structure_new_empty", PTR, PTR);
		structureSetValue = Native.function(gst, "gst_structure_set_value", null, PTR, PTR, PTR);
		structureFree = Native.function(gst, "gst_structure_free", null, PTR);
		iterateByFactory = Native.function(gst, "gst_bin_iterate_all_by_element_factory_name", PTR, PTR, PTR);
		iteratorNext = Native.function(gst, "gst_iterator_next", INT, PTR, PTR);
		iteratorResync = Native.function(gst, "gst_iterator_resync", null, PTR);
		iteratorFree = Native.function(gst, "gst_iterator_free", null, PTR);
		SymbolLookup gobject = Native.library("libgobject-2.0.so.0");
		valueGetObject = Native.function(gobject, "g_value_get_object", PTR, PTR);
		valueReset = Native.function(gobject, "g_value_reset", PTR, PTR);
		valueUnset = Native.function(gobject, "g_value_unset", null, PTR);
		factoryFind = Native.function(gst, "gst_element_factory_find", PTR, PTR);
		objectUnref = Native.function(gst, "gst_object_unref", null, PTR);
		registryGet = Native.function(gst, "gst_registry_get", PTR);
		lookupFeature = Native.function(gst, "gst_registry_lookup_feature", PTR, PTR, PTR);
		setRank = Native.function(gst, "gst_plugin_feature_set_rank", null, PTR, INT);
		try {
			segtrap.invokeExact(0);
			init.invokeExact(MemorySegment.NULL, MemorySegment.NULL);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
		initialized = true;
	}

	public static boolean has(@NonNull String factory) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment found = (MemorySegment) factoryFind.invokeExact(arena.allocateFrom(factory));
			if (Native.isNull(found)) {
				return false;
			}
			objectUnref.invokeExact(found);
			return true;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static synchronized void hardwareDecoding(boolean on) {
		for (String decoder : HARDWARE_DECODERS) {
			rank(decoder, on ? RANK_PRIMARY + 1 : RANK_NONE);
		}
		for (String decoder : NEVER) {
			rank(decoder, RANK_NONE);
		}
	}

	private static void rank(String feature, int rank) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment registry = (MemorySegment) registryGet.invokeExact();
			MemorySegment found = (MemorySegment) lookupFeature.invokeExact(registry, arena.allocateFrom(feature));
			if (Native.isNull(found)) {
				return;
			}
			setRank.invokeExact(found, rank);
			objectUnref.invokeExact(found);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void unrefObject(@NonNull MemorySegment object) {
		if (Native.isNull(object)) {
			return;
		}
		try {
			objectUnref.invokeExact(object);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment make(@NonNull String factory, @NonNull String name) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment element = (MemorySegment) factoryMake.invokeExact(arena.allocateFrom(factory),
					arena.allocateFrom(name));
			if (Native.isNull(element)) {
				throw new IllegalStateException("GStreamer has no " + factory);
			}
			return element;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static boolean setState(@NonNull MemorySegment element, int state) {
		try {
			return (int) setState.invokeExact(element, state) != STATE_CHANGE_FAILURE;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static long position(@NonNull MemorySegment element) {
		return query(queryPosition, element);
	}

	public static long duration(@NonNull MemorySegment element) {
		return query(queryDuration, element);
	}

	private static long query(MethodHandle query, MemorySegment element) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(LONG);
			int ok = (int) query.invokeExact(element, FORMAT_TIME, out);
			return ok != 0 ? out.get(LONG, 0) : -1;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static boolean seek(@NonNull MemorySegment element, long positionNs, boolean accurate) {
		int flags = SEEK_FLAG_FLUSH | (accurate ? 0 : SEEK_FLAG_KEY_UNIT | SEEK_FLAG_SNAP_NEAREST);
		try {
			return (int) seekSimple.invokeExact(element, FORMAT_TIME, flags, positionNs) != 0;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment bus(@NonNull MemorySegment element) {
		try {
			return (MemorySegment) getBus.invokeExact(element);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment pop(@NonNull MemorySegment bus, long timeoutNs, int types) {
		try {
			return (MemorySegment) busPop.invokeExact(bus, timeoutNs, types);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static int messageType(@NonNull MemorySegment message) {
		return Native.at(message, MESSAGE_TYPE_OFFSET + 4).get(INT, MESSAGE_TYPE_OFFSET);
	}

	@NonNull
	public static MemorySegment messageSource(@NonNull MemorySegment message) {
		return Native.at(message, MESSAGE_SRC_OFFSET + 8).get(PTR, MESSAGE_SRC_OFFSET);
	}

	public static void unref(@NonNull MemorySegment miniObject) {
		if (Native.isNull(miniObject)) {
			return;
		}
		try {
			miniUnref.invokeExact(miniObject);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static String errorText(@NonNull MemorySegment message) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment error = arena.allocate(PTR);
			MemorySegment debug = arena.allocate(PTR);
			parseError.invokeExact(message, error, debug);
			String text = GLib.takeError(error);
			String details = Native.string(debug.get(PTR, 0));
			GLib.free(debug.get(PTR, 0));
			return (text != null ? text : "error") + (details != null ? " (" + details + ")" : "");
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static int bufferingPercent(@NonNull MemorySegment message) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment percent = arena.allocate(INT);
			parseBuffering.invokeExact(message, percent);
			return percent.get(INT, 0);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static int newState(@NonNull MemorySegment message) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment states = arena.allocate(INT, 3);
			parseStateChanged.invokeExact(message, states, states.asSlice(4), states.asSlice(8));
			return states.get(INT, 4);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment pullSample(@NonNull MemorySegment appsink, long timeoutNs) {
		try {
			return (MemorySegment) pullSample.invokeExact(appsink, timeoutNs);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static MemorySegment pullPreroll(@NonNull MemorySegment appsink, long timeoutNs) {
		try {
			return (MemorySegment) pullPreroll.invokeExact(appsink, timeoutNs);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@NonNull
	public static int[] sampleSize(@NonNull MemorySegment sample) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment caps = (MemorySegment) sampleCaps.invokeExact(sample);
			if (Native.isNull(caps)) {
				return new int[] {0, 0};
			}
			MemorySegment structure = (MemorySegment) capsStructure.invokeExact(caps, 0);
			MemorySegment value = arena.allocate(INT);
			int width = (int) structureGetInt.invokeExact(structure, arena.allocateFrom("width"), value) != 0
					? value.get(INT, 0) : 0;
			int height = (int) structureGetInt.invokeExact(structure, arena.allocateFrom("height"), value) != 0
					? value.get(INT, 0) : 0;
			return new int[] {width, height};
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void readSample(@NonNull MemorySegment sample, @NonNull SampleReader reader) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = (MemorySegment) sampleBuffer.invokeExact(sample);
			if (Native.isNull(buffer)) {
				return;
			}
			MemorySegment info = arena.allocate(MAP_INFO_SIZE, 8);
			if ((int) bufferMap.invokeExact(buffer, info, MAP_READ) == 0) {
				return;
			}
			try {
				long size = info.get(LONG, MAP_SIZE_OFFSET);
				reader.read(Native.at(info.get(PTR, MAP_DATA_OFFSET), size));
			} finally {
				bufferUnmap.invokeExact(buffer, info);
			}
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public interface SampleReader {
		void read(@NonNull MemorySegment bytes);
	}

	@NonNull
	public static MemorySegment caps(@NonNull String description) {
		try (Arena arena = Arena.ofConfined()) {
			return (MemorySegment) capsFromString.invokeExact(arena.allocateFrom(description));
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@Nullable
	public static String tag(@NonNull MemorySegment tagList, @NonNull String name) {
		if (Native.isNull(tagList)) {
			return null;
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(PTR);
			if ((int) tagGetString.invokeExact(tagList, arena.allocateFrom(name), out) == 0) {
				return null;
			}
			String value = Native.string(out.get(PTR, 0));
			GLib.free(out.get(PTR, 0));
			return value;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	@Nullable
	public static String factoryName(@NonNull MemorySegment element) {
		try {
			MemorySegment factory = (MemorySegment) elementFactory.invokeExact(element);
			if (Native.isNull(factory)) {
				return null;
			}
			MemorySegment name = (MemorySegment) objectName.invokeExact(factory);
			String text = Native.string(name);
			GLib.free(name);
			return text;
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void forEachByFactory(@NonNull MemorySegment bin, @NonNull String factory,
			@NonNull Consumer<MemorySegment> action) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment iterator = (MemorySegment) iterateByFactory.invokeExact(bin, arena.allocateFrom(factory));
			if (Native.isNull(iterator)) {
				return;
			}
			MemorySegment value = arena.allocate(GVALUE_SIZE, 8);
			try {
				for (int i = 0; i < MAX_ITERATIONS; i++) {
					int result = (int) iteratorNext.invokeExact(iterator, value);
					if (result == ITERATOR_OK) {
						try {
							MemorySegment element = (MemorySegment) valueGetObject.invokeExact(value);
							if (!Native.isNull(element)) {
								action.accept(element);
							}
						} finally {
							MemorySegment ignored = (MemorySegment) valueReset.invokeExact(value);
						}
					} else if (result == ITERATOR_RESYNC) {
						iteratorResync.invokeExact(iterator);
					} else {
						break;
					}
				}
			} finally {
				if (value.get(LONG, 0) != 0) {
					valueUnset.invokeExact(value);
				}
				iteratorFree.invokeExact(iterator);
			}
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}

	public static void setHeaders(@NonNull MemorySegment source, @NonNull Map<String, String> headers) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment structure = (MemorySegment) structureNewEmpty.invokeExact(arena.allocateFrom("extra-headers"));
			for (Map.Entry<String, String> h : headers.entrySet()) {
				MemorySegment value = GLib.stringValue(h.getValue(), arena);
				structureSetValue.invokeExact(structure, arena.allocateFrom(h.getKey()), value);
				GLib.unsetValue(value);
			}
			GLib.setPointer(source, "extra-headers", structure);
			structureFree.invokeExact(structure);
		} catch (Throwable t) {
			throw Native.rethrow(t);
		}
	}
}
