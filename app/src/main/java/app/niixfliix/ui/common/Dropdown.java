package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

/** Reads the choice back from the visible text, which survives rotation (an index field wouldn't). */
public final class Dropdown {

	public interface Listener {
		void onSelected(int index);
	}

	private final MaterialAutoCompleteTextView input;
	private final String[] labels;
	private final int initial;

	public Dropdown(@NonNull MaterialAutoCompleteTextView input, @NonNull String[] labels, int selected) {
		this.input = input;
		this.labels = labels;
		this.initial = selected >= 0 && selected < labels.length ? selected : 0;
		input.setSimpleItems(labels);
		if (labels.length > 0) {
			input.setText(labels[initial], false);
		}
	}

	public void setListener(@Nullable Listener listener) {
		input.setOnItemClickListener(listener == null ? null
				: (parent, view, position, id) -> listener.onSelected(position));
	}

	public int selected() {
		String text = input.getText() != null ? input.getText().toString() : "";
		for (int i = 0; i < labels.length; i++) {
			if (labels[i].equals(text)) {
				return i;
			}
		}
		return initial;
	}
}
