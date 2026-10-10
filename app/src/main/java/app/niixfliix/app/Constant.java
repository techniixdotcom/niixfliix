package app.niixfliix.app;

public final class Constant {

	public static final int ADDON_TIMEOUT_MS = 10_000;
	public static final int CONNECT_TIMEOUT_MS = 10_000;

	public static final long MANIFEST_MAX_BYTES = 1L << 20;
	public static final long CATALOG_MAX_BYTES = 5L << 20;
	public static final long META_MAX_BYTES = 2L << 20;
	public static final long STREAMS_MAX_BYTES = 2L << 20;
	public static final long SUBTITLES_MAX_BYTES = 2L << 20;
	public static final long UPDATE_MAX_BYTES = 200L << 20;
	public static final long UPDATE_INFO_MAX_BYTES = 1L << 20;

	public static final int SEARCH_DEBOUNCE_MS = 350;
	public static final int RECENT_SEARCHES = 20;
	public static final int HISTORY_LIMIT = 500;

	public static final float WATCHED_FRACTION = 0.9f;

	public static final int MAX_INTENT_TEXT = 4096;

	public static final int FILE_NAME_MAX_BYTES = 179;

	public static final int UP_NEXT_SECONDS = 5 * 60;
	public static final int SEEK_STEP_MS = 10_000;

	private Constant() {
	}
}
