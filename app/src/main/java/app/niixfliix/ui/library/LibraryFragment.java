package app.niixfliix.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.download.Downloads;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.player.PlaySession;
import app.niixfliix.player.PlayerLauncher;
import app.niixfliix.player.QueueResolver;
import app.niixfliix.ui.common.Grids;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.details.DetailsActivity;

public final class LibraryFragment extends Fragment implements PosterAdapter.Listener, Downloads.Listener,
		DownloadsAdapter.Listener, QueueAdapter.Listener {

	private static final int TAB_SAVED = 0;
	private static final int TAB_HISTORY = 1;
	private static final int TAB_DOWNLOADS = 2;
	private static final int TAB_QUEUE = 3;
	private static final String STATE_TAB = "tab";

	private final AppGraph graph = AppGraph.get();
	private RecyclerView list;
	private TextView empty;
	private PosterAdapter posters;
	private DownloadsAdapter downloads;
	private QueueAdapter queue;
	private View queueActions;
	@Nullable private QueueResolver resolving;
	@Nullable private AlertDialog resolvingDialog;
	private int tab = TAB_SAVED;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_library, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		list = view.findViewById(R.id.list);
		empty = view.findViewById(R.id.empty);
		posters = new PosterAdapter(true, this);
		downloads = new DownloadsAdapter(this);
		queue = new QueueAdapter(this);
		queueActions = view.findViewById(R.id.queue_actions);
		view.findViewById(R.id.queue_play).setOnClickListener(v -> {
			QueueItem first = graph.queue.peek();
			if (first != null) {
				onQueueClick(first);
			}
		});
		view.findViewById(R.id.queue_clear).setOnClickListener(v -> {
			graph.queue.clear();
			graph.playback().queueChanged();
			show();
		});
		TabLayout tabs = view.findViewById(R.id.tabs);
		if (savedInstanceState != null) {
			tab = savedInstanceState.getInt(STATE_TAB, TAB_SAVED);
		}
		TabLayout.Tab selected = tabs.getTabAt(tab);
		if (selected != null) {
			selected.select();
		}
		tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab t) {
				tab = t.getPosition();
				show();
			}

			@Override
			public void onTabUnselected(TabLayout.Tab t) {
			}

			@Override
			public void onTabReselected(TabLayout.Tab t) {
			}
		});
		graph.downloads().addListener(this);
		show();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_TAB, tab);
	}

	@Override
	public void onDestroyView() {
		stopResolving();
		graph.downloads().removeListener(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		show();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			show();
		}
	}

	@Override
	public void onDownloadsChanged() {
		if (getView() != null && tab == TAB_DOWNLOADS) {
			downloads.submit(graph.downloads().all());
		}
	}

	private void show() {
		if (getView() == null) {
			return;
		}
		queueActions.setVisibility(View.GONE);
		if (tab == TAB_QUEUE) {
			list.setLayoutManager(new LinearLayoutManager(requireContext()));
			list.setAdapter(queue);
			List<QueueItem> items = graph.queue.all();
			queue.submit(items);
			queueActions.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
			setEmpty(items.isEmpty(), R.string.library_queue_empty);
			return;
		}
		if (tab == TAB_DOWNLOADS) {
			list.setLayoutManager(new LinearLayoutManager(requireContext()));
			list.setAdapter(downloads);
			List<DownloadItem> items = graph.downloads().all();
			downloads.submit(items);
			setEmpty(items.isEmpty(), R.string.library_downloads_empty);
			return;
		}
		list.setLayoutManager(new GridLayoutManager(requireContext(), Grids.posterSpan(requireContext())));
		list.setAdapter(posters);
		List<PosterItem> items = new ArrayList<>();
		if (tab == TAB_SAVED) {
			for (LibraryItem item : graph.library.all()) {
				items.add(PosterItem.of(item));
			}
		} else if (tab == TAB_HISTORY) {
			for (HistoryEntry e : graph.history.recent()) {
				items.add(PosterItem.of(e, true));
			}
		}
		posters.submit(items);
		setEmpty(items.isEmpty(), tab == TAB_SAVED ? R.string.library_saved_empty : R.string.library_history_empty);
	}

	private void setEmpty(boolean isEmpty, int text) {
		empty.setText(text);
		empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		QueueMenu.show(requireContext(), item, getString(R.string.action_remove), () -> confirmRemove(item));
		return true;
	}

	private void confirmRemove(PosterItem item) {
		boolean saved = tab == TAB_SAVED;
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.name)
				.setMessage(saved ? R.string.library_remove_saved : R.string.library_remove_history)
				.setPositiveButton(R.string.action_remove, (d, w) -> {
					if (saved) {
						graph.library.remove(item.id);
					} else {
						graph.history.removeMeta(item.id);
					}
					show();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	/** finds a stream the same way auto-play does, then starts it; the item leaves the queue once it plays */
	@Override
	public void onQueueClick(@NonNull QueueItem item) {
		stopResolving();
		resolvingDialog = new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.label())
				.setMessage(R.string.queue_finding)
				.setNegativeButton(R.string.action_cancel, (d, w) -> stopResolving())
				.setOnCancelListener(d -> stopResolving())
				.show();
		resolving = QueueResolver.start(requireContext(), item, null, null, new QueueResolver.Callback() {
			@Override
			public void onReady(@NonNull PlaySession session) {
				stopResolving();
				graph.queue.remove(item.key());
				PlayerLauncher.play(requireActivity(), session);
			}

			@Override
			public void onFailed() {
				stopResolving();
				Toast.makeText(requireContext(), R.string.queue_no_stream, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void onQueueRemove(@NonNull QueueItem item) {
		graph.queue.remove(item.key());
		graph.playback().queueChanged();
		show();
	}

	private void stopResolving() {
		if (resolving != null) {
			resolving.cancel();
			resolving = null;
		}
		if (resolvingDialog != null) {
			resolvingDialog.dismiss();
			resolvingDialog = null;
		}
	}

	@Override
	public void onDownloadClick(@NonNull DownloadItem item) {
		if (item.state == DownloadItem.State.DONE) {
			PlayerLauncher.playLocal(requireActivity(), item);
		}
	}

	@Override
	public void onDownloadToggle(@NonNull DownloadItem item) {
		if (item.isActive()) {
			graph.downloads().pause(item.id);
		} else if (item.state != DownloadItem.State.DONE) {
			graph.downloads().resume(item.id);
		}
	}

	@Override
	public void onDownloadDelete(@NonNull DownloadItem item) {
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.title)
				.setMessage(R.string.download_delete_message)
				.setPositiveButton(R.string.action_delete, (d, w) -> {
					graph.downloads().delete(item.id);
					show();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}
}
