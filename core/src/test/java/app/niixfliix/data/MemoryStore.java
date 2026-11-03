package app.niixfliix.data;

import java.util.HashMap;
import java.util.Map;

final class MemoryStore implements KeyValueStore {
	private final Map<String, Object> values = new HashMap<>();

	@Override
	public String getString(String key, String def) {
		return values.containsKey(key) ? (String) values.get(key) : def;
	}

	@Override
	public void putString(String key, String value) {
		values.put(key, value);
	}

	@Override
	public boolean getBoolean(String key, boolean def) {
		return values.containsKey(key) ? (Boolean) values.get(key) : def;
	}

	@Override
	public void putBoolean(String key, boolean value) {
		values.put(key, value);
	}

	@Override
	public int getInt(String key, int def) {
		return values.containsKey(key) ? (Integer) values.get(key) : def;
	}

	@Override
	public void putInt(String key, int value) {
		values.put(key, value);
	}

	@Override
	public void remove(String key) {
		values.remove(key);
	}
}
