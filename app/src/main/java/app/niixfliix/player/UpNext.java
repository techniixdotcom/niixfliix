package app.niixfliix.player;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

import app.niixfliix.R;

final class UpNext {

	static final long OVERLAY_LAST_MS = 30_000;
	private static final int ABOVE_CONTROLS_DP = 88;
	private static final int CORNER_DP = 16;

	private final View card;
	private final TextView label;
	private final TextView title;
	private final LinearProgressIndicator countdown;
	private boolean showing;
	private long windowMs = 1;
	private boolean hiddenForPip;
	private boolean overlay;
	private boolean controlsVisible;
	private long remainingMs = Long.MAX_VALUE;

	UpNext(@NonNull View root, @NonNull Runnable onPlay, @NonNull Runnable onClose) {
		card = root.findViewById(R.id.up_next);
		label = root.findViewById(R.id.up_next_label);
		title = root.findViewById(R.id.up_next_title);
		countdown = root.findViewById(R.id.up_next_countdown);
		card.setOnClickListener(v -> onPlay.run());
		root.findViewById(R.id.up_next_close).setOnClickListener(v -> onClose.run());
	}

	void show(@Nullable String next, long remainingMs) {
		if (next != null && !showing) {
			windowMs = Math.max(1, remainingMs);
		}
		showing = next != null;
		this.remainingMs = remainingMs;
		if (next != null) {
			title.setText(next);
			long seconds = (remainingMs + 999) / 1000;
			String time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
			label.setText(card.getContext().getString(R.string.player_up_next_in, time));
			countdown.setProgress((int) (1000 - Math.min(1000, remainingMs * 1000 / windowMs)));
		}
		updateVisibility();
	}

	void setHiddenForPip(boolean inPip) {
		hiddenForPip = inPip;
		updateVisibility();
	}

	void setOverlay(boolean overVideo) {
		overlay = overVideo;
		updateVisibility();
	}

	void setControlsVisible(boolean visible) {
		controlsVisible = visible;
		updateVisibility();
	}

	private void updateVisibility() {
		boolean visible = showing && !hiddenForPip
				&& (!overlay || controlsVisible || remainingMs <= OVERLAY_LAST_MS);
		card.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (overlay && card.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
			ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
			float dp = card.getResources().getDisplayMetrics().density;
			int bottom = Math.round((controlsVisible ? ABOVE_CONTROLS_DP : CORNER_DP) * dp);
			if (lp.bottomMargin != bottom) {
				lp.bottomMargin = bottom;
				card.setLayoutParams(lp);
			}
		}
	}
}
