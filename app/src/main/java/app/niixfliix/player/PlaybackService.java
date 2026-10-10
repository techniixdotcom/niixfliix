package app.niixfliix.player;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import app.niixfliix.app.AppGraph;

/** Wraps the PlaybackController's player in a MediaSession (notification, lock screen, headset buttons). */
public final class PlaybackService extends MediaSessionService {

	@Nullable private static Player current;
	@Nullable private MediaSession session;

	static void attach(@Nullable Player player) {
		current = player;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Player player = current;
		if (player == null) {
			stopSelf();
			return;
		}
		Intent open = new Intent(this, PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent activity = PendingIntent.getActivity(this, 0, open,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		session = new MediaSession.Builder(this, player).setSessionActivity(activity).build();
	}

	@Nullable
	@Override
	public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
		return session;
	}

	/** app swiped away from recents: keeps playing if it was, otherwise everything stops */
	@Override
	public void onTaskRemoved(@Nullable Intent rootIntent) {
		Player player = session != null ? session.getPlayer() : null;
		if (player == null || !player.getPlayWhenReady()) {
			AppGraph.get().playback().stop();
			stopSelf();
		}
	}

	@Override
	public void onDestroy() {
		if (session != null) {
			session.release();
			session = null;
		}
		super.onDestroy();
	}
}
