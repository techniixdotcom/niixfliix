package app.niixfliix.ui.setup;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import app.niixfliix.R;
import app.niixfliix.account.StremioAccount;
import app.niixfliix.app.AppGraph;

/** Signs in to an existing Stremio account. Optional, and there's no sign-up here. */
public final class SignInDialog extends DialogFragment {

	public static final String RESULT_KEY = "signed_in";
	private static final String TAG = "sign_in";

	public static void show(@NonNull FragmentManager fm) {
		if (fm.findFragmentByTag(TAG) == null) {
			new SignInDialog().show(fm, TAG);
		}
	}

	@NonNull
	@Override
	@SuppressLint("InflateParams")
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		View view = getLayoutInflater().inflate(R.layout.dialog_sign_in, null);
		EditText email = view.findViewById(R.id.email);
		EditText password = view.findViewById(R.id.password);
		TextInputLayout passwordLayout = view.findViewById(R.id.password_layout);
		View progress = view.findViewById(R.id.progress);
		AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.account_sign_in)
				.setView(view)
				.setPositiveButton(R.string.account_sign_in, null)
				.setNegativeButton(R.string.action_cancel, null)
				.create();
		// set after show() so a failed attempt keeps the dialog open
		dialog.setOnShowListener(d -> {
			Button go = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			go.setOnClickListener(v -> {
				String address = email.getText().toString().trim();
				String secret = password.getText().toString();
				if (address.isEmpty() || secret.isEmpty()) {
					return;
				}
				passwordLayout.setError(null);
				go.setEnabled(false);
				progress.setVisibility(View.VISIBLE);
				AppGraph.get().account.signIn(address, secret, new StremioAccount.SignInCallback() {
					@Override
					public void onSignedIn(int addons, int titles) {
						if (!isAdded()) {
							return;
						}
						Toast.makeText(requireContext(), R.string.account_done, Toast.LENGTH_LONG).show();
						getParentFragmentManager().setFragmentResult(RESULT_KEY, new Bundle());
						dismiss();
					}

					@Override
					public void onFailed(boolean wrongLogin) {
						if (!isAdded()) {
							return;
						}
						go.setEnabled(true);
						progress.setVisibility(View.INVISIBLE);
						passwordLayout.setError(getString(wrongLogin
								? R.string.account_error_login : R.string.account_error_network));
					}
				});
			});
		});
		return dialog;
	}
}
