package app.niixfliix.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SecureScreens {

	private static final Map<Activity, Integer> HOLDS = new WeakHashMap<>();
	private static final Set<Activity> WHOLE = Collections.newSetFromMap(new WeakHashMap<>());

	private SecureScreens() {
	}

	@MainThread
	public static void protect(@NonNull Activity activity) {
		WHOLE.add(activity);
		Window window = activity.getWindow();
		secureWindow(window);
		filterTouches(window);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			activity.setRecentsScreenshotEnabled(false);
		}
	}

	@MainThread
	public static void protect(@NonNull DialogFragment fragment, @NonNull Dialog dialog) {
		secureWindow(dialog.getWindow());
		fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
			@Nullable private Activity held;

			@Override
			public void onStart(@NonNull LifecycleOwner owner) {
				filterTouches(dialog.getWindow());
				if (held == null) {
					held = fragment.getActivity();
					hold(held);
				}
			}

			@Override
			public void onDestroy(@NonNull LifecycleOwner owner) {
				release(held);
				held = null;
			}
		});
	}

	@MainThread
	public static void protect(@NonNull Dialog dialog, @NonNull Context context) {
		secureWindow(dialog.getWindow());
		Activity activity = activityOf(context);
		dialog.setOnShowListener(d -> {
			filterTouches(dialog.getWindow());
			hold(activity);
		});
		dialog.setOnDismissListener(d -> release(activity));
	}

	private static void secureWindow(@Nullable Window window) {
		if (window == null) {
			return;
		}
		window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			window.setHideOverlayWindows(true);
		}
	}

	private static void filterTouches(@Nullable Window window) {
		View decor = window != null ? window.peekDecorView() : null;
		if (decor != null) {
			decor.setFilterTouchesWhenObscured(true);
		}
	}

	private static void hold(@Nullable Activity activity) {
		if (activity == null) {
			return;
		}
		Integer count = HOLDS.get(activity);
		int now = count != null ? count + 1 : 1;
		HOLDS.put(activity, now);
		if (now == 1 && !WHOLE.contains(activity)) {
			activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				activity.setRecentsScreenshotEnabled(false);
			}
		}
	}

	private static void release(@Nullable Activity activity) {
		if (activity == null) {
			return;
		}
		Integer count = HOLDS.get(activity);
		if (count == null) {
			return;
		}
		if (count > 1) {
			HOLDS.put(activity, count - 1);
			return;
		}
		HOLDS.remove(activity);
		if (!WHOLE.contains(activity)) {
			activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				activity.setRecentsScreenshotEnabled(true);
			}
		}
	}

	@Nullable
	private static Activity activityOf(@NonNull Context context) {
		Context c = context;
		while (c instanceof ContextWrapper) {
			if (c instanceof Activity) {
				return (Activity) c;
			}
			c = ((ContextWrapper) c).getBaseContext();
		}
		return null;
	}
}
