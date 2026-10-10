package app.niixfliix.ui.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.app.AppGraph;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.ui.AddonErrors;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.ui.common.TextPrompt;

public final class AddonsActivity extends BaseActivity implements AddonRepository.Listener {

	private final AppGraph graph = AppGraph.get();
	private final List<InstalledAddon> items = new ArrayList<>();
	private Adapter adapter;
	private ItemTouchHelper dragger;
	@Nullable private InstalledAddon configuring;
	private boolean dragging;

	private final ActivityResultLauncher<Intent> configure = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> {
				InstalledAddon old = configuring;
				configuring = null;
				Intent data = result.getData();
				String url = data != null ? data.getStringExtra(ConfigurePageActivity.EXTRA_RESULT_URL) : null;
				if (old != null && url != null) {
					replace(old, url);
				}
			});

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_addons);
		WindowPadding.apply(findViewById(R.id.root), WindowPadding.LEFT | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(R.string.settings_manage_addons);
		toolbar.setNavigationOnClickListener(v -> finish());
		toolbar.inflateMenu(R.menu.addons);
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_reset) {
				confirmReset();
				return true;
			}
			return false;
		});

		RecyclerView list = findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(this));
		adapter = new Adapter();
		list.setAdapter(adapter);
		dragger = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
			private int from = -1;
			private int to = -1;

			@Override
			public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
					@NonNull RecyclerView.ViewHolder b) {
				int start = a.getBindingAdapterPosition();
				int end = b.getBindingAdapterPosition();
				if (start == RecyclerView.NO_POSITION || end == RecyclerView.NO_POSITION) {
					return false;
				}
				dragging = true;
				if (from < 0) {
					from = start;
				}
				to = end;
				items.add(end, items.remove(start));
				adapter.notifyItemMoved(start, end);
				return true;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
			}

			@Override
			public boolean isLongPressDragEnabled() {
				return false;
			}

			@Override
			public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
				super.clearView(rv, holder);
				if (from >= 0 && to >= 0 && from != to) {
					graph.addons.move(from, to);
				}
				from = -1;
				to = -1;
				dragging = false;
				reload();
			}
		});
		dragger.attachToRecyclerView(list);
		findViewById(R.id.add).setOnClickListener(v -> TextPrompt.show(this, R.string.addons_add,
				R.string.addons_add_message, R.string.action_install, this::install));
		graph.addons.addListener(this);
		reload();
	}

	@Override
	protected void onDestroy() {
		graph.addons.removeListener(this);
		super.onDestroy();
	}

	@Override
	public void onAddonsChanged() {
		reload();
	}

	private void reload() {
		if (dragging) {
			// not mid-drag, clearView reloads
			return;
		}
		items.clear();
		items.addAll(graph.addons.all());
		adapter.notifyDataSetChanged();
	}

	private void install(String input) {
		String url = StremioUrl.toManifestUrl(input);
		if (url != null && TorrentioConfig.isTorrentio(url) && graph.addons.torrentio() != null) {
			// no duplicate Torrentio, replace it
			graph.addons.setTorrentioUrl(url);
			Toast.makeText(this, R.string.torrentio_saved, Toast.LENGTH_SHORT).show();
			return;
		}
		Toast.makeText(this, R.string.addons_installing, Toast.LENGTH_SHORT).show();
		graph.addons.add(input, AddonErrors.toastCallback(this));
	}

	private void replace(InstalledAddon old, String url) {
		if (TorrentioConfig.isTorrentio(old.transportUrl)) {
			graph.addons.setTorrentioUrl(url);
			return;
		}
		graph.addons.add(url, new AddonRepository.AddCallback() {
			@Override
			public void onAdded(@NonNull InstalledAddon addon) {
				List<InstalledAddon> all = graph.addons.all();
				int target = all.indexOf(old);
				graph.addons.remove(old);
				if (target >= 0) {
					graph.addons.move(graph.addons.all().indexOf(addon), target);
				}
			}

			@Override
			public void onFailed(@NonNull AddonRepository.AddError error) {
				Toast.makeText(AddonsActivity.this, AddonErrors.message(error), Toast.LENGTH_LONG).show();
			}
		});
	}

	private void confirmReset() {
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.addons_reset)
				.setMessage(R.string.addons_reset_message)
				.setPositiveButton(R.string.addons_reset_confirm, (d, w) -> graph.addons.resetToDefaults())
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private void showMenu(View anchor, InstalledAddon addon) {
		PopupMenu menu = new PopupMenu(this, anchor);
		menu.inflate(R.menu.addon_item);
		Manifest m = addon.manifest;
		boolean torrentio = TorrentioConfig.isTorrentio(addon.transportUrl);
		menu.getMenu().findItem(R.id.action_configure)
				.setVisible(torrentio || m != null && (m.isConfigurable() || m.needsConfiguration()));
		menu.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_configure) {
				if (torrentio) {
					startActivity(new Intent(this, TorrentioSetupActivity.class));
				} else {
					configuring = addon;
					configure.launch(ConfigurePageActivity.intent(this,
							StremioUrl.baseOf(addon.transportUrl) + "/configure"));
				}
				return true;
			}
			if (item.getItemId() == R.id.action_remove) {
				new MaterialAlertDialogBuilder(this)
						.setTitle(addon.displayName())
						.setMessage(R.string.addons_remove_message)
						.setPositiveButton(R.string.action_remove, (d, w) -> graph.addons.remove(addon))
						.setNegativeButton(R.string.action_cancel, null)
						.show();
				return true;
			}
			return false;
		});
		menu.show();
	}

	private final class Adapter extends RecyclerView.Adapter<Holder> {
		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_addon, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int position) {
			holder.bind(items.get(position));
		}

		@Override
		public int getItemCount() {
			return items.size();
		}
	}

	private final class Holder extends RecyclerView.ViewHolder {
		private final ImageView logo;
		private final TextView name;
		private final TextView detail;
		private final TextView url;
		private final View down;
		@Nullable private InstalledAddon addon;

		@SuppressLint("ClickableViewAccessibility")
		Holder(@NonNull View view) {
			super(view);
			logo = view.findViewById(R.id.addon_logo);
			name = view.findViewById(R.id.addon_name);
			detail = view.findViewById(R.id.addon_detail);
			url = view.findViewById(R.id.addon_url);
			down = view.findViewById(R.id.addon_down);
			view.findViewById(R.id.addon_drag).setOnTouchListener((v, event) -> {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					dragger.startDrag(this);
				}
				return false;
			});
			view.findViewById(R.id.addon_more).setOnClickListener(v -> {
				if (addon != null) {
					showMenu(v, addon);
				}
			});
		}

		void bind(InstalledAddon a) {
			addon = a;
			Manifest m = a.manifest;
			name.setText(a.displayName());
			String version = m != null && m.version != null ? getString(R.string.addons_version, m.version) : "";
			String description = m != null && m.description != null ? m.description : "";
			String text = version.isEmpty() ? description : description.isEmpty() ? version : version + " · " + description;
			detail.setText(text);
			detail.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
			// might contain a key, show the host only
			url.setText(StremioUrl.masked(a.transportUrl));
			down.setVisibility(graph.addons.isUnreachable(a) ? View.VISIBLE : View.GONE);
			Images.icon(logo, m != null ? m.logo : null);
		}
	}
}
