package gov.nist.healthcare.cds.service.transformation;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Forward-compatibility regression over a FITS v2 export
 * (<code>src/test/resources/nist/fits2</code>, 61 files written by
 * <code>gov.nist.hit.fits.service.file.NISTFormatServiceImpl</code>).
 *
 * These 61 documents turned out to be the legacy corpus with one difference --
 * the MetaData timestamps carry a timezone offset rather than {@code Z} -- so
 * this class mainly pins that v2's date serialization parses. The v2-only
 * constructs are covered by {@link NISTFormatFits2WorkflowCorpusTest} (a real
 * export) and {@link NISTFormatFits2FeaturesTest} (hand-written fixtures).
 */
@RunWith(Parameterized.class)
public class NISTFormatFits2CorpusTest extends AbstractNISTFormatCorpusTest {

	private static final String DIRECTORY = "/nist/fits2";

	public NISTFormatFits2CorpusTest(String fileName) {
		super(fileName);
	}

	@Override
	protected String corpusDirectory() {
		return DIRECTORY;
	}

	@Parameters(name = "{0}")
	public static List<Object[]> corpus() throws Exception {
		return corpusOf(DIRECTORY);
	}
}
