package app.niixfliix.ui.setup;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.app.AppGraph;
import app.niixfliix.debrid.DebridKeyCheck;
import app.niixfliix.debrid.DebridService;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.common.Dropdown;

/** Shared by the setup popup and the Torrentio screen. A saved key is never shown; empty field = keep it. */
public final class DebridFields {

	private final View root;
	private final boolean allowNone;
	private final Dropdown serviceInput;
	private final EditText keyInput;
	private final TextView status;
	private final View testButton;
	private final View getKey;
	/** saved key is only ever used with this service */
	@Nullable private final DebridService savedService;
	@Nullable private final String savedKey;
	private int checks;

	public DebridFields(@NonNull View root, @Nullable DebridService current, @Nullable String savedKey,
			boolean allowNone) {
		this.root = root;
		this.allowNone = allowNone;
		this.savedService = savedKey != null ? current : null;
		this.savedKey = savedKey;
		keyInput = root.findViewById(R.id.debrid_key);
		status = root.findViewById(R.id.debrid_status);
		testButton = root.findViewById(R.id.debrid_test);
		getKey = root.findViewById(R.id.debrid_get_key);
		TextInputLayout keyLayout = root.findViewById(R.id.debrid_key_layout);

		List<String> labels = new ArrayList<>();
		if (allowNone) {
			labels.add(root.getContext().getString(R.string.debrid_none));
		}
		labels.addAll(Arrays.asList(root.getResources().getStringArray(R.array.debrid_services)));
		DebridService initial = current != null ? current : allowNone ? null : DebridService.REAL_DEBRID;
		MaterialAutoCompleteTextView input = root.findViewById(R.id.debrid_service);
		serviceInput = new Dropdown(input, labels.toArray(new String[0]), indexOf(initial));
		serviceInput.setListener(index -> {
			status.setText(null);
			updateEnabled();
		});
		if (savedKey != null) {
			keyLayout.setHelperText(root.getContext().getString(R.string.debrid_key_saved, DebridService.mask(savedKey)));
		}
		testButton.setOnClickListener(v -> test());
		getKey.setOnClickListener(v -> {
			DebridService s = service();
			if (s != null) {
				Links.open(root.getContext(), s.keyPage);
			}
		});
		updateEnabled();
	}

	/** false (and shows why) if the key looks wrong; no key clears the debrid from the config */
	public boolean applyTo(@NonNull TorrentioConfig config) {
		DebridService service = service();
		String key = key();
		if (service == null || key == null || key.isEmpty()) {
			config.debrid = null;
			config.debridKey = null;
			return true;
		}
		if (!DebridService.isPlausibleKey(key)) {
			showInvalidKey();
			return false;
		}
		config.debrid = service;
		config.debridKey = key;
		return true;
	}

	@Nullable
	private DebridService service() {
		int index = serviceInput.selected() - (allowNone ? 1 : 0);
		return index >= 0 ? DebridService.values()[index] : null;
	}

	/** typed key, or the saved one if the field is empty and the service didn't change */
	@Nullable
	private String key() {
		String typed = typedKey();
		if (!typed.isEmpty()) {
			return typed;
		}
		return service() == savedService ? savedKey : null;
	}

	@NonNull
	private String typedKey() {
		return keyInput.getText() != null ? keyInput.getText().toString().trim() : "";
	}

	private void showInvalidKey() {
		status.setText(R.string.debrid_key_invalid_format);
	}

	private int indexOf(@Nullable DebridService s) {
		if (s == null) {
			return 0;
		}
		return s.ordinal() + (allowNone ? 1 : 0);
	}

	private void updateEnabled() {
		boolean on = service() != null;
		keyInput.setEnabled(on);
		testButton.setEnabled(on);
		getKey.setEnabled(on);
	}

	private void test() {
		DebridService s = service();
		if (s == null) {
			return;
		}
		String key = key();
		if (!DebridService.isPlausibleKey(key)) {
			showInvalidKey();
			return;
		}
		int check = ++checks;
		status.setText(R.string.debrid_testing);
		AppGraph graph = AppGraph.get();
		graph.io.execute(() -> {
			DebridKeyCheck.Result result = DebridKeyCheck.check(graph.http, s, key);
			graph.main.post(() -> {
				if (check != checks || !root.isAttachedToWindow()) {
					return;
				}
				switch (result) {
					case VALID:
						status.setText(R.string.debrid_key_valid);
						break;
					case INVALID:
						status.setText(R.string.debrid_key_invalid);
						break;
					case UNKNOWN:
					default:
						status.setText(R.string.debrid_key_unknown);
						break;
				}
			});
		});
	}
}
