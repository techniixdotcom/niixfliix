package app.niixfliix.player;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.app.Constant;

/** The "Up next" card. What's on it and when comes from the PlaybackController. */
final class UpNext {

	private final View card;
	private final TextView label;
	private final TextView title;
	private final LinearProgressIndicator countdown;
	private boolean showing;
	private boolean hiddenForPip;

	UpNext(@NonNull View root, @NonNull Runnable onPlay, @NonNull Runnable onClose) {
		card = root.findViewById(R.id.up_next);
		label = root.findViewById(R.id.up_next_label);
		title = root.findViewById(R.id.up_next_title);
		countdown = root.findViewById(R.id.up_next_countdown);
		card.setOnClickListener(v -> onPlay.run());
		root.findViewById(R.id.up_next_close).setOnClickListener(v -> onClose.run());
	}

	/** counts down to the end of this video; the next one starts the moment it ends. null hides it. */
	void show(@Nullable String next, long remainingMs) {
		showing = next != null;
		if (next != null) {
			title.setText(next);
			long seconds = (remainingMs + 999) / 1000;
			String time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
			label.setText(card.getContext().getString(R.string.player_up_next_in, time));
			long window = Constant.UP_NEXT_SECONDS * 1000L;
			countdown.setProgress((int) (1000 - Math.min(1000, remainingMs * 1000 / window)));
		}
		updateVisibility();
	}

	void setHiddenForPip(boolean inPip) {
		hiddenForPip = inPip;
		updateVisibility();
	}

	private void updateVisibility() {
		card.setVisibility(showing && !hiddenForPip ? View.VISIBLE : View.GONE);
	}
}
