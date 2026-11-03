package app.niixfliix.util;

import androidx.annotation.NonNull;

public interface MainQueue {

	void post(@NonNull Runnable task);

	void postDelayed(@NonNull Runnable task, long delayMs);

	void removeCallbacks(@NonNull Runnable task);
}
