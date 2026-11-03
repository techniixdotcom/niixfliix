package app.niixfliix.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

public final class Network {

	private Network() {
	}

	public static boolean isUnmetered(@NonNull Context context) {
		ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
		if (cm == null) {
			return false;
		}
		NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
		return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
	}
}
