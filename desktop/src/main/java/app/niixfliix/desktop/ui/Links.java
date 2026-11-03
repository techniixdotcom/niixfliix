package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.app.Constant;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrentio.TorrentioConfig;

public final class Links {

	@Nullable private static InstalledAddon configuring;

	private Links() {
	}

	public static void handle(@NonNull String link) {
		if (link.length() > Constant.MAX_INTENT_TEXT) {
			return;
		}
		String lower = link.trim().toLowerCase(Locale.ROOT);
		if (lower.startsWith("stremio:")) {
			confirmInstall(link.trim());
		} else if (lower.startsWith("magnet:")) {
			Magnet magnet = Magnet.parse(link.trim());
			if (magnet == null) {
				Toast.show(I18n.t("error_bad_magnet"));
			} else {
				Launch.playMagnet(magnet);
			}
		}
	}

	public static void configuring(@Nullable InstalledAddon addon) {
		configuring = addon;
	}

	private static void confirmInstall(String link) {
		String url = StremioUrl.toManifestUrl(link);
		if (url == null) {
			Toast.show(I18n.t("error_unsupported_link"));
			return;
		}
		Dialogs.confirm(I18n.t("addon_install_title"), I18n.t("addon_install_message", StremioUrl.masked(url)),
				I18n.t("action_install"), () -> install(url));
	}

	public static void install(@NonNull String input) {
		DesktopGraph graph = DesktopGraph.get();
		String url = StremioUrl.toManifestUrl(input);
		if (url == null) {
			Toast.show(I18n.t("addon_error_invalid_url"));
			return;
		}
		if (TorrentioConfig.isTorrentio(url) && graph.addons.torrentio() != null) {
			Toast.show(I18n.t(graph.addons.setTorrentioUrl(url) ? "torrentio_saved" : "addon_error_storage"));
			return;
		}
		InstalledAddon old = configuring;
		String host = StremioUrl.hostOf(url);
		if (old != null && host != null && host.equals(StremioUrl.hostOf(old.transportUrl))
				&& graph.addons.all().contains(old)) {
			configuring = null;
			replace(old, url);
			return;
		}
		Toast.show(I18n.t("addons_installing"));
		graph.addons.add(url, toastCallback());
	}

	private static void replace(InstalledAddon old, String url) {
		DesktopGraph graph = DesktopGraph.get();
		graph.addons.add(url, new AddonRepository.AddCallback() {
			@Override
			public void onAdded(@NonNull InstalledAddon addon) {
				List<InstalledAddon> all = graph.addons.all();
				int target = all.indexOf(old);
				graph.addons.remove(old);
				if (target >= 0) {
					graph.addons.move(graph.addons.all().indexOf(addon), target);
				}
				Toast.show(I18n.t("addon_installed", addon.displayName()));
			}

			@Override
			public void onFailed(@NonNull AddonRepository.AddError error) {
				Toast.show(addonError(error));
			}
		});
	}

	@NonNull
	public static AddonRepository.AddCallback toastCallback() {
		return new AddonRepository.AddCallback() {
			@Override
			public void onAdded(@NonNull InstalledAddon addon) {
				Toast.show(I18n.t("addon_installed", addon.displayName()));
			}

			@Override
			public void onFailed(@NonNull AddonRepository.AddError error) {
				Toast.show(addonError(error));
			}
		};
	}

	@NonNull
	public static String addonError(@NonNull AddonRepository.AddError error) {
		switch (error) {
			case INVALID_URL:
				return I18n.t("addon_error_invalid_url");
			case ALREADY_INSTALLED:
				return I18n.t("addon_error_already_installed");
			case INVALID_MANIFEST:
				return I18n.t("addon_error_invalid_manifest");
			case STORAGE:
				return I18n.t("addon_error_storage");
			case UNREACHABLE:
			default:
				return I18n.t("addon_error_unreachable");
		}
	}
}
