package app.niixfliix.player;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.FlagSet;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The player as the screen, the notification, the lock screen and headset buttons see it. Its "next" is the
 * next episode or the next thing in the queue, so the usual next button skips to that.
 */
@OptIn(markerClass = UnstableApi.class)
final class NextPlayer extends ForwardingPlayer {

	private final Runnable onNext;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private boolean hasNext;

	NextPlayer(@NonNull Player player, @NonNull Runnable onNext) {
		super(player);
		this.onNext = onNext;
	}

	void setHasNext(boolean hasNext) {
		if (this.hasNext == hasNext) {
			return;
		}
		this.hasNext = hasNext;
		Commands commands = getAvailableCommands();
		Events events = new Events(new FlagSet.Builder().add(EVENT_AVAILABLE_COMMANDS_CHANGED).build());
		for (Listener l : listeners) {
			l.onAvailableCommandsChanged(commands);
			l.onEvents(this, events);
		}
	}

	@Override
	public void addListener(@NonNull Listener listener) {
		super.addListener(listener);
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener(@NonNull Listener listener) {
		super.removeListener(listener);
		listeners.remove(listener);
	}

	@Override
	public boolean isCommandAvailable(int command) {
		if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
			return hasNext;
		}
		return super.isCommandAvailable(command);
	}

	@NonNull
	@Override
	public Commands getAvailableCommands() {
		Commands.Builder commands = super.getAvailableCommands().buildUpon();
		if (hasNext) {
			commands.addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
		} else {
			commands.removeAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
		}
		return commands.build();
	}

	@Override
	public boolean hasNextMediaItem() {
		return hasNext;
	}

	@Override
	public void seekToNext() {
		if (hasNext) {
			onNext.run();
		}
	}

	@Override
	public void seekToNextMediaItem() {
		seekToNext();
	}
}
