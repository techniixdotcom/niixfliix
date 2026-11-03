package app.niixfliix.player;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import app.niixfliix.app.AppGraph;

public final class PlaybackService extends MediaSessionService {

	@Nullable private static Player current;
	@Nullable private static PlaybackService running;
	@Nullable private MediaSession session;

	@MainThread
	static void attach(@Nullable Player player) {
		current = player;
		PlaybackService service = running;
		if (service != null) {
			service.use(player);
		}
	}

	@MainThread
	static boolean isServing(@NonNull Player player) {
		PlaybackService service = running;
		return service != null && service.session != null && service.session.getPlayer() == player;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		running = this;
		Player player = current;
		if (player == null) {
			stopSelf();
			return;
		}
		use(player);
	}

	@MainThread
	private void use(@Nullable Player player) {
		if (player == null) {
			releaseSession();
			stopSelf();
			return;
		}
		if (session == null) {
			Intent open = new Intent(this, PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent activity = PendingIntent.getActivity(this, 0, open,
					PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
			session = new MediaSession.Builder(this, player).setSessionActivity(activity).build();
			addSession(session);
		} else if (session.getPlayer() != player) {
			session.setPlayer(player);
		}
	}

	private void releaseSession() {
		MediaSession s = session;
		session = null;
		if (s != null) {
			removeSession(s);
			s.release();
		}
	}

	@Nullable
	@Override
	public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
		if (session == null && current != null) {
			use(current);
		}
		return session;
	}

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
		if (running == this) {
			running = null;
		}
		releaseSession();
		super.onDestroy();
	}
}
