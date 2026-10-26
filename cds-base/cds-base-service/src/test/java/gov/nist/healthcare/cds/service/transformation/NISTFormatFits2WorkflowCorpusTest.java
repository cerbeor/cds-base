package gov.nist.healthcare.cds.service.transformation;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Compatibility regression over a FITS v2 export that actually exercises the
 * v2-only parts of the format (<code>src/test/resources/nist/fits2-workflow</code>,
 * 61 files).
 *
 * Unlike the earlier v2 export covered by {@link NISTFormatFits2CorpusTest} --
 * which turned out to be the legacy corpus with nothing but a different
 * timestamp serialization -- every document here carries a
 * {@code WorkflowTag} and a {@code Tags} element, and one carries a
 * {@code changeLog}. That makes this the first corpus whose green result says
 * something about v2 support rather than about the legacy format.
 */
@RunWith(Parameterized.class)
public class NISTFormatFits2WorkflowCorpusTest extends AbstractNISTFormatCorpusTest {

	private static final String DIRECTORY = "/nist/fits2-workflow";

	public NISTFormatFits2WorkflowCorpusTest(String fileName) {
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
