package app.niixfliix.addon;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.model.ManifestResource;

/** Lenient Gson: bad list entries are skipped, bad fields come back null. */
public final class AddonJson {

	private static final String MODEL_PACKAGE = "app.niixfliix.addon.model.";

	public static final class BadJsonException extends IOException {
		BadJsonException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private AddonJson() {
	}

	@NonNull
	public static Gson create() {
		return new GsonBuilder()
				.registerTypeAdapterFactory(new ForgivingListFactory())
				.registerTypeAdapterFactory(new ForgivingObjectFactory())
				.registerTypeAdapter(ManifestResource.class, new ManifestResourceAdapter())
				.create();
	}

	@NonNull
	public static <T> T parse(@NonNull Gson gson, @NonNull String json, @NonNull Class<T> type) throws IOException {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				throw new BadJsonException("Expected a JSON object", null);
			}
			T value = gson.fromJson(root, type);
			if (value == null) {
				throw new BadJsonException("Empty JSON", null);
			}
			return value;
		} catch (JsonParseException | IllegalStateException e) {
			throw new BadJsonException("Not valid JSON for " + type.getSimpleName(), e);
		}
	}

	private static final class ForgivingListFactory implements TypeAdapterFactory {
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
			if (typeToken.getRawType() != List.class) {
				return null;
			}
			Type type = typeToken.getType();
			Type elementType = type instanceof ParameterizedType
					? ((ParameterizedType) type).getActualTypeArguments()[0]
					: Object.class;
			TypeAdapter<?> elementAdapter = gson.getAdapter(TypeToken.get(elementType));
			TypeAdapter<T> delegate = gson.getDelegateAdapter(this, typeToken);
			@SuppressWarnings("unchecked")
			TypeAdapter<T> adapter = (TypeAdapter<T>) new ForgivingListAdapter<>(elementAdapter, (TypeAdapter<List<?>>) delegate);
			return adapter;
		}
	}

	private static final class ForgivingObjectFactory implements TypeAdapterFactory {
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
			if (!typeToken.getRawType().getName().startsWith(MODEL_PACKAGE)) {
				return null;
			}
			TypeAdapter<T> delegate = gson.getDelegateAdapter(this, typeToken);
			return new TypeAdapter<T>() {
				@Override
				public void write(JsonWriter out, T value) throws IOException {
					delegate.write(out, value);
				}

				@Override
				public T read(JsonReader in) throws IOException {
					JsonElement tree = JsonParser.parseReader(in);
					try {
						return delegate.fromJsonTree(tree);
					} catch (JsonParseException | IllegalStateException e) {
						return null;
					}
				}
			};
		}
	}

	private static final class ForgivingListAdapter<E> extends TypeAdapter<List<E>> {
		private final TypeAdapter<E> elementAdapter;
		private final TypeAdapter<List<?>> delegate;

		ForgivingListAdapter(TypeAdapter<E> elementAdapter, TypeAdapter<List<?>> delegate) {
			this.elementAdapter = elementAdapter;
			this.delegate = delegate;
		}

		@Override
		public void write(JsonWriter out, List<E> value) throws IOException {
			delegate.write(out, value);
		}

		@Override
		public List<E> read(JsonReader in) throws IOException {
			if (in.peek() != JsonToken.BEGIN_ARRAY) {
				in.skipValue();
				return null;
			}
			JsonArray array = JsonParser.parseReader(in).getAsJsonArray();
			List<E> out = new ArrayList<>(array.size());
			for (JsonElement element : array) {
				try {
					E value = elementAdapter.fromJsonTree(element);
					if (value != null) {
						out.add(value);
					}
				} catch (JsonParseException | IllegalStateException e) {
					// skip it, keep the rest
				}
			}
			return out;
		}
	}

	private static final class ManifestResourceAdapter extends TypeAdapter<ManifestResource> {
		@Override
		public void write(JsonWriter out, ManifestResource value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name("name").value(value.name);
			writeList(out, "types", value.types);
			writeList(out, "idPrefixes", value.idPrefixes);
			out.endObject();
		}

		private static void writeList(JsonWriter out, String name, List<String> list) throws IOException {
			if (list == null) {
				return;
			}
			out.name(name).beginArray();
			for (String s : list) {
				out.value(s);
			}
			out.endArray();
		}

		@Override
		public ManifestResource read(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			if (token == JsonToken.STRING) {
				return new ManifestResource(in.nextString());
			}
			if (token != JsonToken.BEGIN_OBJECT) {
				in.skipValue();
				return null;
			}
			JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
			ManifestResource r = new ManifestResource(stringOrNull(obj.get("name")));
			r.types = stringList(obj.get("types"));
			r.idPrefixes = stringList(obj.get("idPrefixes"));
			return r;
		}

		private static String stringOrNull(JsonElement e) {
			return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
		}

		private static List<String> stringList(JsonElement e) {
			if (e == null || !e.isJsonArray()) {
				return null;
			}
			List<String> out = new ArrayList<>();
			for (JsonElement item : e.getAsJsonArray()) {
				if (item.isJsonPrimitive()) {
					out.add(item.getAsString());
				}
			}
			return out;
		}
	}
}
