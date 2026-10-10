package app.niixfliix.ui.streams;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.StreamSearch;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.app.AppGraph;
import app.niixfliix.net.Network;
import app.niixfliix.player.PlaySession;
import app.niixfliix.player.PlayerLauncher;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.setup.Choices;
import app.niixfliix.ui.setup.QualityDialog;

public final class StreamsSheet extends BottomSheetDialogFragment implements StreamSearch.Listener,
		StreamsAdapter.Listener {

	/** full meta is needed later for the next episode */
	public interface Host {
		@Nullable
		Meta streamsMeta();
	}

	private static final String TAG = "streams";
	private static final String ARG_TYPE = "type";
	private static final String ARG_META_ID = "meta_id";
	private static final String ARG_POSTER = "poster";
	private static final String ARG_VIDEO = "video";
	private static final String ARG_TITLE = "title";
	private static final String ARG_SUBTITLE = "subtitle";
	private static final int AUTOPLAY_SECONDS = 3;

	private final AppGraph graph = AppGraph.get();
	private final List<StreamInfo> all = new ArrayList<>();
	/** everything, kept for the player's "try the next stream" */
	private List<StreamInfo> sorted = new ArrayList<>();
	/** what the list shows: one per quality */
	private List<StreamInfo> best = new ArrayList<>();
	@Nullable private StreamSearch search;
	private boolean finished;
	@Nullable private StreamInfo pendingDownload;
	@Nullable private Runnable countdown;
	private int secondsLeft;
	private boolean autoplayStopped;

	private TextView status;
	private View progress;
	private TextView empty;
	private TextView qualityLine;
	private View autoplayCard;
	private TextView autoplayText;
	private StreamsAdapter adapter;

	private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), granted -> {
				StreamInfo s = pendingDownload;
				pendingDownload = null;
				if (s == null) {
					return;
				}
				if (granted) {
					startDownload(s);
				} else {
					Toast.makeText(requireContext(), R.string.download_needs_storage, Toast.LENGTH_LONG).show();
				}
			});

	private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), granted -> {
				// download runs either way
			});

	public static void show(@NonNull FragmentManager fm, @NonNull Meta meta, @NonNull String videoId,
			@NonNull String title, @Nullable String subtitle) {
		if (fm.findFragmentByTag(TAG) != null || meta.id == null || meta.type == null) {
			return;
		}
		StreamsSheet sheet = new StreamsSheet();
		Bundle args = new Bundle();
		args.putString(ARG_TYPE, meta.type);
		args.putString(ARG_META_ID, meta.id);
		args.putString(ARG_POSTER, meta.poster);
		args.putString(ARG_VIDEO, videoId);
		args.putString(ARG_TITLE, title);
		args.putString(ARG_SUBTITLE, subtitle);
		sheet.setArguments(args);
		sheet.show(fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
		BottomSheetBehavior<?> behavior = dialog.getBehavior();
		behavior.setSkipCollapsed(true);
		behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.sheet_streams, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		Bundle args = requireArguments();
		((TextView) view.findViewById(R.id.sheet_title)).setText(args.getString(ARG_TITLE));
		TextView subtitle = view.findViewById(R.id.sheet_subtitle);
		String sub = args.getString(ARG_SUBTITLE);
		subtitle.setText(sub);
		subtitle.setVisibility(sub == null ? View.GONE : View.VISIBLE);
		status = view.findViewById(R.id.sheet_status);
		progress = view.findViewById(R.id.sheet_progress);
		empty = view.findViewById(R.id.sheet_empty);
		qualityLine = view.findViewById(R.id.sheet_quality);
		autoplayCard = view.findViewById(R.id.autoplay_card);
		autoplayText = view.findViewById(R.id.autoplay_text);
		RecyclerView list = view.findViewById(R.id.streams);
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		adapter = new StreamsAdapter(this, fullTitle());
		list.setAdapter(adapter);
		view.findViewById(R.id.autoplay_cancel).setOnClickListener(v -> stopAutoplay());
		qualityLine.setOnClickListener(v -> {
			stopAutoplay();
			QualityDialog.show(getParentFragmentManager());
		});
		getParentFragmentManager().setFragmentResultListener(QualityDialog.RESULT_KEY, getViewLifecycleOwner(),
				(key, result) -> {
					showQualityLine();
					autoplayStopped = false;
					maybeAutoplay();
				});
		showQualityLine();
		if (!graph.playerPrefs.qualityChosen()) {
			QualityDialog.show(getParentFragmentManager());
		}
		start();
	}

	@Override
	public void onDestroyView() {
		cancelCountdown();
		if (search != null) {
			search.cancel();
		}
		super.onDestroyView();
	}

	/** host's meta if loaded; right after a rotation it may not be, then a stub from the args */
	@NonNull
	private Meta meta() {
		Meta full = requireActivity() instanceof Host ? ((Host) requireActivity()).streamsMeta() : null;
		if (full != null) {
			return full;
		}
		Bundle args = requireArguments();
		Meta stub = new Meta();
		stub.type = args.getString(ARG_TYPE);
		stub.id = args.getString(ARG_META_ID);
		stub.name = args.getString(ARG_TITLE);
		stub.poster = args.getString(ARG_POSTER);
		return stub;
	}

	/** "Show · S1E2" for an episode */
	@NonNull
	private String fullTitle() {
		Bundle args = requireArguments();
		String title = args.getString(ARG_TITLE, "");
		String sub = args.getString(ARG_SUBTITLE);
		return sub == null ? title : title + " · " + sub;
	}

	private String videoId() {
		return requireArguments().getString(ARG_VIDEO, "");
	}

	private void showQualityLine() {
		boolean wifi = Network.isUnmetered(requireContext());
		Quality quality = Choices.QUALITIES.get(Choices.qualityIndex(graph.playerPrefs.quality(wifi)));
		qualityLine.setText(getString(wifi ? R.string.streams_quality_wifi : R.string.streams_quality_mobile,
				quality.title));
	}

	private void start() {
		Meta meta = meta();
		if (meta.type == null) {
			return;
		}
		cancelCountdown();
		autoplayStopped = false;
		all.clear();
		sorted = new ArrayList<>();
		best = new ArrayList<>();
		finished = false;
		adapter.submit(best);
		empty.setVisibility(View.GONE);
		progress.setVisibility(View.VISIBLE);
		search = StreamSearch.start(graph.addons, graph.addonClient, graph.io, graph.main, meta.type, videoId(), this);
		updateStatus();
	}

	@Override
	public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams) {
		for (StreamInfo s : streams) {
			if (s.kind != StreamInfo.Kind.UNSUPPORTED) {
				all.add(s);
			}
		}
		sorted = StreamRanker.sort(all);
		best = StreamRanker.bestPerQuality(sorted);
		adapter.submit(best);
		updateStatus();
	}

	@Override
	public void onAddonFailed(@NonNull InstalledAddon addon) {
		// which add-on didn't answer isn't shown
	}

	@Override
	public void onFinished() {
		finished = true;
		progress.setVisibility(View.GONE);
		updateStatus();
		if (best.isEmpty()) {
			int text;
			if (search != null && search.addonCount() == 0) {
				text = R.string.streams_no_addons;
			} else if (sorted.isEmpty() || hasShownQuality(sorted)) {
				// in range but all dead (zero seeders) reads the same as nothing at all
				text = R.string.streams_none_found;
			} else {
				text = R.string.streams_none_in_range;
			}
			empty.setText(text);
			empty.setVisibility(View.VISIBLE);
		} else {
			maybeAutoplay();
		}
	}

	/** after all add-ons answer, counts down then plays the preferred quality (or the nearest) */
	private void maybeAutoplay() {
		if (!finished || autoplayStopped || countdown != null || getView() == null
				|| !graph.playerPrefs.qualityChosen()) {
			return;
		}
		Quality preferred = graph.playerPrefs.quality(Network.isUnmetered(requireContext()));
		StreamInfo pick = StreamRanker.pick(best, preferred, null);
		if (pick == null) {
			return;
		}
		secondsLeft = AUTOPLAY_SECONDS;
		autoplayCard.setVisibility(View.VISIBLE);
		countdown = new Runnable() {
			@Override
			public void run() {
				if (secondsLeft == 0) {
					countdown = null;
					play(pick);
					return;
				}
				autoplayText.setText(getString(R.string.streams_autoplay, pick.quality.title, secondsLeft));
				secondsLeft--;
				autoplayCard.postDelayed(this, 1000);
			}
		};
		countdown.run();
	}

	private void stopAutoplay() {
		autoplayStopped = true;
		cancelCountdown();
	}

	private void cancelCountdown() {
		if (countdown != null) {
			autoplayCard.removeCallbacks(countdown);
			countdown = null;
		}
		autoplayCard.setVisibility(View.GONE);
	}

	private static boolean hasShownQuality(List<StreamInfo> streams) {
		for (StreamInfo s : streams) {
			if (StreamRanker.SHOWN.contains(s.quality)) {
				return true;
			}
		}
		return false;
	}

	private void updateStatus() {
		if (getView() == null) {
			return;
		}
		status.setText(finished
				? getResources().getQuantityString(R.plurals.streams_found, best.size(), best.size())
				: getString(R.string.queue_finding));
	}

	@Override
	public void onStreamClick(@NonNull StreamInfo s) {
		stopAutoplay();
		switch (s.kind) {
			case DIRECT:
			case TORRENT:
				play(s);
				break;
			case EXTERNAL:
				Stream stream = s.stream;
				Links.open(requireContext(), stream.externalUrl != null ? stream.externalUrl
						: stream.ytId != null ? Links.youtube(stream.ytId) : null);
				break;
			default:
				Toast.makeText(requireContext(), R.string.streams_unsupported, Toast.LENGTH_SHORT).show();
				break;
		}
	}

	private void play(StreamInfo s) {
		Meta meta = meta();
		if (meta.id == null || meta.type == null) {
			return;
		}
		String videoId = videoId();
		PlaySession session = new PlaySession(meta, meta.type, meta.id, meta.nameOrEmpty(),
				meta.poster, videoId, meta.video(videoId), sorted, s);
		PlayerLauncher.play(requireActivity(), session);
		dismiss();
	}

	@Override
	public void onStreamLongClick(@NonNull StreamInfo s) {
		stopAutoplay();
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		if (s.kind == StreamInfo.Kind.DIRECT && s.stream.url != null) {
			String url = s.stream.url;
			labels.add(getString(R.string.streams_copy_link));
			actions.add(() -> copy(url));
			labels.add(getString(R.string.streams_open_external));
			actions.add(() -> openExternal(url));
			if (!s.isUncachedDebrid()) {
				labels.add(getString(R.string.streams_download));
				actions.add(() -> requestDownload(s));
			}
		}
		if (labels.isEmpty()) {
			return;
		}
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(s.quality.title)
				.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
				.show();
	}

	private void copy(String text) {
		ClipboardManager clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager.class);
		if (clipboard == null) {
			return;
		}
		ClipData clip = ClipData.newPlainText(getString(R.string.app_name), text);
		// may contain a debrid key, keep it out of the clipboard preview (constant gets inlined, ignored < 13)
		PersistableBundle extras = new PersistableBundle();
		extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
		clip.getDescription().setExtras(extras);
		clipboard.setPrimaryClip(clip);
		if (Build.VERSION.SDK_INT < 33) {
			Toast.makeText(requireContext(), R.string.streams_copied, Toast.LENGTH_SHORT).show();
		}
	}

	private void openExternal(String url) {
		Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*");
		try {
			startActivity(Intent.createChooser(intent, getString(R.string.streams_open_external)));
		} catch (ActivityNotFoundException e) {
			Toast.makeText(requireContext(), R.string.error_no_app_for_link, Toast.LENGTH_SHORT).show();
		}
	}

	private void requestDownload(StreamInfo s) {
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
				Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
		}
		if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(requireContext(),
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			pendingDownload = s;
			storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			return;
		}
		startDownload(s);
	}

	private void startDownload(StreamInfo s) {
		if (s.stream.url == null) {
			return;
		}
		String title = fullTitle();
		boolean ok = graph.downloads().enqueue(s.stream.url, s.stream.requestHeaders(), title, fileName(s, title));
		Toast.makeText(requireContext(), ok ? R.string.download_started : R.string.download_could_not_start,
				Toast.LENGTH_SHORT).show();
	}

	/** release file name if known, else title + extension from the url */
	private static String fileName(StreamInfo s, String title) {
		String name = s.fileName != null ? s.fileName : s.releaseName;
		if (name.isEmpty()) {
			name = title;
		}
		if (name.toLowerCase(Locale.ROOT).matches(".*\\.(mkv|mp4|m4v|webm|avi)$")) {
			return name;
		}
		String url = s.stream.url != null ? s.stream.url.toLowerCase(Locale.ROOT) : "";
		int query = url.indexOf('?');
		String path = query >= 0 ? url.substring(0, query) : url;
		for (String ext : new String[] {".mp4", ".webm", ".m4v", ".avi"}) {
			if (path.endsWith(ext)) {
				return name + ext;
			}
		}
		return name + ".mkv";
	}
}
