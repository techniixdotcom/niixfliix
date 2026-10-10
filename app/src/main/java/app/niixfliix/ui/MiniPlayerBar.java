package app.niixfliix.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import app.niixfliix.R;
import app.niixfliix.app.AppGraph;
import app.niixfliix.player.PlaybackController;
import app.niixfliix.player.PlayerLauncher;

/** The bar at the bottom of the app while a video plays in the background: tap to go back, play/pause, next, close. */
final class MiniPlayerBar implements PlaybackController.Listener, Player.Listener {

	private final PlaybackController playback = AppGraph.get().playback();
	private final View bar;
	private final ImageView poster;
	private final TextView title;
	private final TextView subtitle;
	private final ImageButton play;
	private final View next;
	@Nullable private Player bound;

	MiniPlayerBar(@NonNull Activity activity, @NonNull View bar) {
		this.bar = bar;
		poster = bar.findViewById(R.id.mini_poster);
		title = bar.findViewById(R.id.mini_title);
		subtitle = bar.findViewById(R.id.mini_subtitle);
		play = bar.findViewById(R.id.mini_play);
		next = bar.findViewById(R.id.mini_next);
		next.setOnClickListener(v -> {
			if (bound != null) {
				bound.seekToNext();
			}
		});
		bar.findViewById(R.id.mini_card).setOnClickListener(v -> PlayerLauncher.resume(activity));
		play.setOnClickListener(v -> {
			Player p = bound;
			if (p != null && p.isPlaying()) {
				p.pause();
			} else if (p != null) {
				p.play();
			}
		});
		bar.findViewById(R.id.mini_close).setOnClickListener(v -> playback.stop());
	}

	void start() {
		playback.addListener(this);
		onNowPlayingChanged();
	}

	void stop() {
		playback.removeListener(this);
		bind(null);
	}

	@Override
	public void onNowPlayingChanged() {
		Player p = playback.inBackground();
		bind(p);
		bar.setVisibility(p != null ? View.VISIBLE : View.GONE);
		if (p != null) {
			showMetadata(p.getMediaMetadata());
			showPlaying(p.isPlaying());
			showNext(p);
		}
	}

	@Override
	public void onAvailableCommandsChanged(@NonNull Player.Commands commands) {
		if (bound != null) {
			showNext(bound);
		}
	}

	private void showNext(Player p) {
		next.setVisibility(p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onIsPlayingChanged(boolean isPlaying) {
		showPlaying(isPlaying);
	}

	@Override
	public void onMediaMetadataChanged(@NonNull MediaMetadata metadata) {
		showMetadata(metadata);
	}

	private void bind(@Nullable Player p) {
		if (bound == p) {
			return;
		}
		if (bound != null) {
			bound.removeListener(this);
		}
		bound = p;
		if (p != null) {
			p.addListener(this);
		}
	}

	private void showMetadata(MediaMetadata metadata) {
		title.setText(metadata.title);
		subtitle.setText(metadata.artist);
		subtitle.setVisibility(metadata.artist != null ? View.VISIBLE : View.GONE);
		Images.poster(poster, metadata.artworkUri != null ? metadata.artworkUri.toString() : null);
	}

	private void showPlaying(boolean playing) {
		play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
	}
}
