package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.mmkv.MMKV;

public final class MmkvStore implements KeyValueStore {

	private final MMKV mmkv;

	public MmkvStore(@NonNull String id) {
		mmkv = MMKV.mmkvWithID(id);
	}

	@Nullable
	@Override
	public String getString(@NonNull String key, @Nullable String def) {
		return mmkv.decodeString(key, def);
	}

	@Override
	public void putString(@NonNull String key, @Nullable String value) {
		if (value == null) {
			mmkv.removeValueForKey(key);
		} else {
			mmkv.encode(key, value);
		}
	}

	@Override
	public boolean getBoolean(@NonNull String key, boolean def) {
		return mmkv.decodeBool(key, def);
	}

	@Override
	public void putBoolean(@NonNull String key, boolean value) {
		mmkv.encode(key, value);
	}

	@Override
	public int getInt(@NonNull String key, int def) {
		return mmkv.decodeInt(key, def);
	}

	@Override
	public void putInt(@NonNull String key, int value) {
		mmkv.encode(key, value);
	}

	@Override
	public void remove(@NonNull String key) {
		mmkv.removeValueForKey(key);
	}
}
