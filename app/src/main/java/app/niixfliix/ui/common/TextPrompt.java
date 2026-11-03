package app.niixfliix.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;

import app.niixfliix.R;
import app.niixfliix.ui.SecureScreens;

public final class TextPrompt {

	private TextPrompt() {
	}

	@SuppressLint("InflateParams")
	public static void show(@NonNull Context context, @StringRes int title, @StringRes int message,
			@StringRes int positive, @NonNull Consumer<String> onText) {
		View view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null);
		TextInputLayout layout = view.findViewById(R.id.input_layout);
		layout.setHint(R.string.addons_add_hint);
		EditText input = view.findViewById(R.id.input);
		AlertDialog dialog = new MaterialAlertDialogBuilder(context)
				.setTitle(title)
				.setMessage(message)
				.setView(view)
				.setPositiveButton(positive, (d, w) -> onText.accept(input.getText().toString()))
				.setNegativeButton(R.string.action_cancel, null)
				.create();
		SecureScreens.protect(dialog, context);
		dialog.show();
	}
}
