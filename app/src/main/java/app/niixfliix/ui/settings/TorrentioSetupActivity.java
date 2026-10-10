package app.niixfliix.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import app.niixfliix.R;
import app.niixfliix.addon.DefaultAddons;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.app.AppGraph;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.ui.common.Dropdown;
import app.niixfliix.ui.common.TextPrompt;
import app.niixfliix.ui.setup.DebridFields;

public final class TorrentioSetupActivity extends BaseActivity {

	private static final int[] LIMITS = {0, 1, 2, 3, 5, 10};

	/** Torrentio language names -> tags so they show up translated */
	private static final Map<String, String> LANGUAGE_TAGS = new HashMap<>();

	static {
		String[][] pairs = {
				{"japanese", "ja"}, {"russian", "ru"}, {"italian", "it"}, {"portuguese", "pt"}, {"spanish", "es"},
				{"latino", "es-419"}, {"korean", "ko"}, {"chinese", "zh"}, {"taiwanese", "zh-TW"}, {"french", "fr"},
				{"german", "de"}, {"dutch", "nl"}, {"hindi", "hi"}, {"telugu", "te"}, {"tamil", "ta"},
				{"polish", "pl"}, {"lithuanian", "lt"}, {"latvian", "lv"}, {"estonian", "et"}, {"czech", "cs"},
				{"slovakian", "sk"}, {"slovenian", "sl"}, {"hungarian", "hu"}, {"romanian", "ro"},
				{"bulgarian", "bg"}, {"serbian", "sr"}, {"croatian", "hr"}, {"ukrainian", "uk"}, {"greek", "el"},
				{"danish", "da"}, {"finnish", "fi"}, {"swedish", "sv"}, {"norwegian", "no"}, {"turkish", "tr"},
				{"arabic", "ar"}, {"persian", "fa"}, {"hebrew", "he"}, {"vietnamese", "vi"},
				{"indonesian", "id"}, {"malay", "ms"}, {"thai", "th"}};
		for (String[] pair : pairs) {
			LANGUAGE_TAGS.put(pair[0], pair[1]);
		}
	}

	private final AppGraph graph = AppGraph.get();
	private final List<String> languageOrder = new ArrayList<>();
	private TorrentioConfig config;
	private DebridFields debrid;
	private Dropdown sort;
	private Dropdown limit;

	private final ActivityResultLauncher<Intent> configurePage = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> {
				Intent data = result.getData();
				String url = data != null ? data.getStringExtra(ConfigurePageActivity.EXTRA_RESULT_URL) : null;
				if (url != null) {
					applyLink(url);
				}
			});

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_torrentio);
		View root = findViewById(R.id.root);
		WindowPadding.apply(root, WindowPadding.LEFT | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(R.string.torrentio_title);
		toolbar.setNavigationOnClickListener(v -> finish());

		InstalledAddon torrentio = graph.addons.torrentio();
		config = TorrentioConfig.parse(torrentio != null ? torrentio.transportUrl : null);
		debrid = new DebridFields(root, config.debrid, config.hasDebrid() ? config.debridKey : null, true);

		sort = new Dropdown(findViewById(R.id.sort), getResources().getStringArray(R.array.torrentio_sorts),
				config.sort.ordinal());

		String[] limits = new String[LIMITS.length];
		limits[0] = getString(R.string.torrentio_no_limit);
		int limitIndex = 0;
		for (int i = 1; i < LIMITS.length; i++) {
			limits[i] = String.valueOf(LIMITS[i]);
			if (LIMITS[i] == config.limitPerQuality) {
				limitIndex = i;
			}
		}
		limit = new Dropdown(findViewById(R.id.limit), limits, limitIndex);

		String[] filterLabels = getResources().getStringArray(R.array.torrentio_quality_filters);
		ChipGroup filters = findViewById(R.id.quality_filter);
		for (int i = 0; i < TorrentioConfig.QUALITY_FILTERS.size(); i++) {
			String value = TorrentioConfig.QUALITY_FILTERS.get(i);
			addChip(filters, filterLabels[i], config.qualityFilter.contains(value), on -> {
				if (on) {
					config.qualityFilter.add(value);
				} else {
					config.qualityFilter.remove(value);
				}
			});
		}

		languageOrder.addAll(config.languages);
		ChipGroup languages = findViewById(R.id.languages);
		for (String language : TorrentioConfig.LANGUAGES) {
			addChip(languages, languageName(language), languageOrder.contains(language), on -> {
				// order switched on = priority
				languageOrder.remove(language);
				if (on) {
					languageOrder.add(language);
				}
			});
		}

		ChipGroup providers = findViewById(R.id.providers);
		for (String provider : TorrentioConfig.PROVIDERS) {
			addChip(providers, provider, config.providers.contains(provider), on -> {
				if (on) {
					config.providers.add(provider);
				} else {
					config.providers.remove(provider);
				}
			});
		}

		findViewById(R.id.open_configure).setOnClickListener(v -> configurePage.launch(
				ConfigurePageActivity.intent(this, DefaultAddons.TORRENTIO_CONFIGURE_PAGE)));
		findViewById(R.id.paste_link).setOnClickListener(v -> TextPrompt.show(this, R.string.configure_paste_link,
				R.string.configure_paste_message, R.string.action_ok, this::applyLink));
		findViewById(R.id.save).setOnClickListener(v -> save());
	}

	private void addChip(ChipGroup group, String label, boolean checked, Consumer<Boolean> listener) {
		Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, group, false);
		chip.setText(label);
		chip.setChecked(checked);
		chip.setOnCheckedChangeListener((button, on) -> listener.accept(on));
		group.addView(chip);
	}

	@NonNull
	private static String languageName(@NonNull String torrentioName) {
		String tag = LANGUAGE_TAGS.get(torrentioName);
		if (tag == null) {
			return torrentioName;
		}
		String name = Locale.forLanguageTag(tag).getDisplayName();
		return name.isEmpty() ? torrentioName : Formats.capitalize(name, Locale.getDefault());
	}

	private void save() {
		if (!debrid.applyTo(config)) {
			return;
		}
		config.sort = TorrentioConfig.Sort.values()[sort.selected()];
		config.limitPerQuality = LIMITS[limit.selected()];
		config.languages.clear();
		config.languages.addAll(languageOrder);
		store(config.toManifestUrl());
	}

	private void applyLink(String link) {
		String url = StremioUrl.toManifestUrl(link);
		if (url == null || !TorrentioConfig.isTorrentio(url)) {
			Toast.makeText(this, R.string.torrentio_not_a_link, Toast.LENGTH_LONG).show();
			return;
		}
		store(url);
	}

	private void store(String url) {
		if (!graph.addons.setTorrentioUrl(url)) {
			Toast.makeText(this, R.string.addon_error_storage, Toast.LENGTH_LONG).show();
			return;
		}
		Toast.makeText(this, R.string.torrentio_saved, Toast.LENGTH_SHORT).show();
		finish();
	}
}
