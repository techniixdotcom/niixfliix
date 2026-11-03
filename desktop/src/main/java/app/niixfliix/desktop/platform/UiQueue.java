package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import app.niixfliix.util.MainQueue;

public final class UiQueue implements MainQueue {

	private final Executor ui;
	private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "main-queue-timer");
		t.setDaemon(true);
		return t;
	});
	private final Map<Runnable, Set<Ticket>> pending = new HashMap<>();

	public UiQueue(@NonNull Executor ui) {
		this.ui = ui;
	}

	@Override
	public void post(@NonNull Runnable task) {
		dispatch(add(task));
	}

	@Override
	public void postDelayed(@NonNull Runnable task, long delayMs) {
		if (delayMs <= 0) {
			post(task);
			return;
		}
		Ticket ticket = add(task);
		ticket.future = timer.schedule(() -> dispatch(ticket), delayMs, TimeUnit.MILLISECONDS);
	}

	@Override
	public void removeCallbacks(@NonNull Runnable task) {
		Set<Ticket> tickets;
		synchronized (pending) {
			tickets = pending.remove(task);
		}
		if (tickets == null) {
			return;
		}
		for (Ticket t : tickets) {
			t.cancelled = true;
			ScheduledFuture<?> future = t.future;
			if (future != null) {
				future.cancel(false);
			}
		}
	}

	private Ticket add(Runnable task) {
		Ticket ticket = new Ticket(task);
		synchronized (pending) {
			pending.computeIfAbsent(task, k -> new HashSet<>()).add(ticket);
		}
		return ticket;
	}

	private void dispatch(Ticket ticket) {
		ui.execute(ticket);
	}

	private final class Ticket implements Runnable {
		final Runnable task;
		volatile boolean cancelled;
		volatile ScheduledFuture<?> future;

		Ticket(Runnable task) {
			this.task = task;
		}

		@Override
		public void run() {
			synchronized (pending) {
				Set<Ticket> tickets = pending.get(task);
				if (tickets != null) {
					tickets.remove(this);
					if (tickets.isEmpty()) {
						pending.remove(task);
					}
				}
			}
			if (!cancelled) {
				task.run();
			}
		}
	}
}
