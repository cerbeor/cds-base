package gov.nist.healthcare.cds.service.transformation;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Test;

import gov.nist.healthcare.cds.domain.wrapper.ExportResult;
import gov.nist.healthcare.cds.domain.wrapper.ZipExportSummary;

/**
 * The archive {@link ZipExportSummary} builds has to be readable everywhere it
 * is unzipped, so its entry names must be unique AND legal on every platform.
 *
 * The collision handling used to prepend a {@code *} per duplicate. Java will
 * happily write such an entry, which is why this went unnoticed, but
 * {@code *} is not a legal character in a Windows file name : Explorer and
 * several archive tools skip those entries, so the files simply never appeared
 * in the unpacked folder.
 */
public class ZipExportSummaryTest {

	/** Characters no Windows file name may contain. */
	private static final Pattern ILLEGAL = Pattern.compile("[*?:\"<>|]");

	@Test
	public void duplicateEntryNamesAreNumberedNotMarked() throws Exception {
		List<String> entries = entriesFor(16, "Peds_-_Valid.xml");

		assertEquals(16, entries.size());
		assertEquals("Peds_-_Valid.xml", entries.get(0));
		assertEquals("Peds_-_Valid_2.xml", entries.get(1));
		assertEquals("Peds_-_Valid_16.xml", entries.get(15));
	}

	@Test
	public void noEntryNameUsesACharacterIllegalOnWindows() throws Exception {
		for (String name : entriesFor(32, "Peds_-_Valid.xml")) {
			assertFalse(name + " is not a legal Windows file name", ILLEGAL.matcher(name).find());
		}
	}

	@Test
	public void everyFileSurvivesIntoTheArchive() throws Exception {
		List<String> entries = entriesFor(32, "Peds_-_Valid.xml");
		assertEquals("entries went missing from the archive", 32, new HashSet<String>(entries).size());
	}

	/** The suffix goes before the extension, so the files stay openable. */
	@Test
	public void theExtensionStaysAtTheEnd() throws Exception {
		for (String name : entriesFor(8, "Peds_-_Valid.xml")) {
			assertTrue(name + " lost its .xml extension", name.endsWith(".xml"));
		}
	}

	@Test
	public void anEntryWithoutAnExtensionIsStillMadeUnique() throws Exception {
		List<String> entries = entriesFor(3, "README");
		assertEquals("README", entries.get(0));
		assertEquals("README_2", entries.get(1));
		assertEquals("README_3", entries.get(2));
	}

	/** Distinct names must be left exactly as the formatter produced them. */
	@Test
	public void distinctNamesAreUntouched() throws Exception {
		ExportResult result = new ExportResult();
		result.add("a.xml", content());
		result.add("b.xml", content());
		result.add("c.xml", content());

		assertEquals(java.util.Arrays.asList("a.xml", "b.xml", "c.xml"), zip(result));
	}

	// ------------------------------------------------------------------

	/** Zips {@code count} files all offered under the same name. */
	private static List<String> entriesFor(int count, String name) throws Exception {
		ExportResult result = new ExportResult();
		for (int i = 0; i < count; i++) {
			result.add(name, content());
		}
		return zip(result);
	}

	/** Runs the export through a real archive and reads the entry names back. */
	private static List<String> zip(ExportResult result) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipExportSummary summary = new ZipExportSummary(baos);
		summary.add(result, new ArrayList<String>());
		summary.getOut().close();

		List<String> entries = new ArrayList<String>();
		ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));
		ZipEntry entry;
		while ((entry = in.getNextEntry()) != null) {
			entries.add(entry.getName());
		}
		in.close();
		return entries;
	}

	private static ByteArrayInputStream content() {
		return new ByteArrayInputStream("<TestCase/>".getBytes(StandardCharsets.UTF_8));
	}
}
