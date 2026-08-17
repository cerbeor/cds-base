package gov.nist.healthcare.cds.service.transformation;

import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.bytes;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.completeTestCase;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.exportConfig;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.importAll;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.list;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.exception.ConfigurationException;
import gov.nist.healthcare.cds.domain.wrapper.ExportResult;
import gov.nist.healthcare.cds.domain.wrapper.ExportedFileStream;
import gov.nist.healthcare.cds.domain.xml.ErrorModel;
import gov.nist.healthcare.cds.service.impl.transformation.PDFFormatServiceImpl;

/**
 * The 'pdf' format is export only : it renders the XML of the nist format through
 * /stylesheets/testCase.xsl. It reads nothing from the database, so the service needs no
 * collaborator.
 */
public class PDFFormatServiceImplTest {

	private final PDFFormatServiceImpl formatService = new PDFFormatServiceImpl();

	// --- Format ---

	@Test
	public void formatName_isPdf() {
		Assert.assertEquals("pdf", formatService.formatName());
	}

	// --- Export ---

	@Test
	public void exportToFile_writesOnePdfPerTestCase() throws ConfigurationException {
		TestCase second = completeTestCase();
		second.setName("Another Case");

		ExportResult result = formatService.exportToFile(list(completeTestCase(), second), exportConfig());

		Assert.assertEquals(Arrays.asList("Simple-Test-Case.pdf", "Another-Case.pdf"), names(result));
	}

	@Test
	public void exportToFile_writesARenderedPdfDocument() throws ConfigurationException {
		ExportResult result = formatService.exportToFile(list(completeTestCase()), exportConfig());

		byte[] pdf = bytes(result.getReader().get(0).getIn());
		Assert.assertTrue("the export is empty", pdf.length > 0);
		Assert.assertEquals("%PDF", new String(pdf, 0, 4, Charset.forName("US-ASCII")));
	}

	/**
	 * Rendering failures are swallowed one test case at a time : the file is left out of the
	 * export and the other test cases still make it through.
	 */
	@Test
	public void exportToFile_leavesOutTheTestCasesItCannotRender() throws ConfigurationException {
		TestCase unrenderable = completeTestCase();
		unrenderable.setDateType(null);

		ExportResult result = formatService.exportToFile(list(unrenderable, completeTestCase()), exportConfig());

		Assert.assertEquals(Arrays.asList("Simple-Test-Case.pdf"), names(result));
	}

	// --- Import ---

	@Test(expected = ConfigurationException.class)
	public void importFromFile_isNotSupported() throws ConfigurationException {
		formatService.importFromFile(new ByteArrayInputStream(new byte[0]), importAll());
	}

	// --- Pre conditions ---

	@Test
	public void preImport_reportsNothing() {
		Assert.assertTrue(formatService.preImport(new ByteArrayInputStream(new byte[0])).isEmpty());
	}

	@Test
	public void preExport_acceptsARunnableTestCase() {
		Assert.assertTrue(formatService.preExport(completeTestCase()).isEmpty());
	}

	@Test
	public void preExport_reportsAnIncompleteTestCase() {
		TestCase tc = completeTestCase();
		tc.setRunnable(false);

		List<ErrorModel> errors = formatService.preExport(tc);

		Assert.assertEquals(1, errors.size());
		Assert.assertEquals("Simple Test Case", errors.get(0).getLocation());
	}

	// --- Helpers ---

	private List<String> names(ExportResult result) {
		List<String> names = new ArrayList<String>();
		for (ExportedFileStream file : result.getReader()) {
			names.add(file.getName());
		}
		return names;
	}
}
