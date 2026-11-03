package app.niixfliix.desktop.ui;

import androidx.annotation.Nullable;

import app.niixfliix.account.StremioAccount;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.Entry;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.i18n.I18n;

public final class SignInDialog {

	private static final int WIDTH = 420;

	private SignInDialog() {
	}

	public static void show(@Nullable Runnable onSignedIn) {
		Entry email = Entry.plain(I18n.t("account_email"));
		Entry password = Entry.password(I18n.t("account_password"));
		Label error = Label.wrapping(null, "error");
		error.visible(false);
		Progress progress = Progress.spinner(24);
		progress.visible(false);
		Box body = Box.column(12, email, password, error, progress);
		Button cancel = Dialogs.button(I18n.t("action_cancel"), false);
		Button go = Dialogs.button(I18n.t("account_sign_in"), true);
		Dialog dialog = Dialogs.custom(I18n.t("account_sign_in"), body, WIDTH, cancel, go);
		dialog.followsContent(true);
		dialog.focus(email);
		cancel.onClick(dialog::close);
		Runnable signIn = () -> {
			String address = email.text().trim();
			String secret = password.text();
			if (address.isEmpty() || secret.isEmpty()) {
				return;
			}
			error.visible(false);
			go.sensitive(false);
			progress.visible(true);
			DesktopGraph.get().account.signIn(address, secret, new StremioAccount.SignInCallback() {
				@Override
				public void onSignedIn(int addons, int titles) {
					if (dialog.isClosed()) {
						return;
					}
					dialog.close();
					Toast.show(I18n.t("account_done"));
					if (onSignedIn != null) {
						onSignedIn.run();
					}
				}

				@Override
				public void onFailed(boolean wrongLogin) {
					if (dialog.isClosed()) {
						return;
					}
					go.sensitive(true);
					progress.visible(false);
					error.text(I18n.t(wrongLogin ? "account_error_login" : "account_error_network"));
					error.visible(true);
				}
			});
		};
		go.onClick(signIn);
		email.onActivate(password::grabFocus);
		password.onActivate(signIn);
	}
}
