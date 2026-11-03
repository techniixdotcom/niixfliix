package app.niixfliix.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

public final class AndroidMainQueue implements MainQueue {

	private final Handler handler = new Handler(Looper.getMainLooper());

	@Override
	public void post(@NonNull Runnable task) {
		handler.post(task);
	}

	@Override
	public void postDelayed(@NonNull Runnable task, long delayMs) {
		handler.postDelayed(task, delayMs);
	}

	@Override
	public void removeCallbacks(@NonNull Runnable task) {
		handler.removeCallbacks(task);
	}
}
