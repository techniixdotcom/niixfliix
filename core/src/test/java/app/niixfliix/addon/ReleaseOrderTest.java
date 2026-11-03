package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

public class ReleaseOrderTest {

	private static Meta meta(String id, String released, String releaseInfo) {
		Meta m = new Meta();
		m.id = id;
		m.type = Meta.TYPE_MOVIE;
		m.name = id;
		m.released = released;
		m.releaseInfo = releaseInfo;
		return m;
	}

	private static List<String> ids(List<Meta> metas) {
		List<String> out = new ArrayList<>();
		for (Meta m : metas) {
			out.add(m.id);
		}
		return out;
	}

	@Test
	public void newestFirstWithUndatedLast() {
		List<Meta> sorted = ReleaseOrder.newestFirst(Arrays.asList(
				meta("old", "1999-03-31T00:00:00.000Z", "1999"),
				meta("none", null, null),
				meta("new", "2024-07-12T00:00:00.000Z", "2024"),
				meta("yearOnly", null, "2010"),
				meta("mid", "2015-01-01", null)));
		assertEquals(Arrays.asList("new", "mid", "yearOnly", "old", "none"), ids(sorted));
	}

	@Test
	public void equalDatesKeepTheAddonsOrder() {
		List<Meta> sorted = ReleaseOrder.newestFirst(Arrays.asList(
				meta("a", null, "2020"), meta("b", null, "2020"), meta("c", null, "2020")));
		assertEquals(Arrays.asList("a", "b", "c"), ids(sorted));
	}

	@Test
	public void seriesYearRangesUseTheFirstYear() {
		assertEquals(ReleaseOrder.parseYear("2008"), ReleaseOrder.parse(null, "2008–2013"));
		assertEquals(ReleaseOrder.parseYear("2019"), ReleaseOrder.parse("", "2019-"));
		assertEquals(0, ReleaseOrder.parse("soon", "TBA"));
	}

	@Test
	public void aSeriesInTheLibraryIsDatedByItsLatestEpisodeOut() {
		Meta m = meta("show", "2010-01-01T00:00:00.000Z", "2010–");
		m.type = Meta.TYPE_SERIES;
		Video old = new Video();
		old.released = "2010-01-01T00:00:00.000Z";
		Video latest = new Video();
		latest.released = "2024-05-01T00:00:00.000Z";
		Video future = new Video();
		future.released = "2099-01-01T00:00:00.000Z";
		m.videos = Arrays.asList(old, latest, future);
		long now = ReleaseOrder.parseDate("2025-01-01");
		assertEquals(ReleaseOrder.parseDate("2024-05-01"), ReleaseOrder.latestOut(m, now));
		m.videos = new ArrayList<>();
		assertEquals(ReleaseOrder.of(m), ReleaseOrder.latestOut(m, now));
		assertTrue(ReleaseOrder.of(m) > 0);
	}
}
