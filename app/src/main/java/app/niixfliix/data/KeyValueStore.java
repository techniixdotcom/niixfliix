package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** MMKV behind an interface so tests can swap it out. */
public interface KeyValueStore {

	@Nullable
	String getString(@NonNull String key, @Nullable String def);

	void putString(@NonNull String key, @Nullable String value);

	boolean getBoolean(@NonNull String key, boolean def);

	void putBoolean(@NonNull String key, boolean value);

	int getInt(@NonNull String key, int def);

	void putInt(@NonNull String key, int value);

	void remove(@NonNull String key);
}
