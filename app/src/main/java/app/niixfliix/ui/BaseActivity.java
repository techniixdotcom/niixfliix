package app.niixfliix.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import app.niixfliix.R;

public abstract class BaseActivity extends AppCompatActivity {

	/** only on screens whose layout has the mini player slot */
	@Nullable private MiniPlayerBar miniPlayer;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		// always dark, so light bar icons whatever the system uses
		SystemBarStyle style = SystemBarStyle.dark(Color.TRANSPARENT);
		EdgeToEdge.enable(this, style, style);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStart() {
		super.onStart();
		View bar = findViewById(R.id.mini_player);
		if (bar != null && miniPlayer == null) {
			miniPlayer = new MiniPlayerBar(this, bar);
		}
		if (miniPlayer != null) {
			miniPlayer.start();
		}
	}

	@Override
	protected void onStop() {
		if (miniPlayer != null) {
			miniPlayer.stop();
		}
		super.onStop();
	}
}
