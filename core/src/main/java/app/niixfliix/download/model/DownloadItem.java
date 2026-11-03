package app.niixfliix.download.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DownloadItem {

	public enum State {
		QUEUED,
		RUNNING,
		PAUSED,
		DONE,
		FAILED
	}

	@NonNull public String id = "";
	@NonNull public String title = "";
	@NonNull public String fileName = "";
	@NonNull public State state = State.QUEUED;
	public long bytesDone;
	public long bytesTotal = -1;
	@Nullable public String contentUri;

	public boolean isActive() {
		return state == State.RUNNING || state == State.QUEUED;
	}

	public int percent() {
		if (bytesTotal <= 0) {
			return 0;
		}
		return (int) Math.min(100, bytesDone * 100 / bytesTotal);
	}
}
