package gov.nist.healthcare.cds.service.transformation;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Backward-compatibility regression over a corpus of real exported test cases
 * (<code>src/test/resources/nist/corpus</code>, a legacy FITS export of 61
 * files).
 *
 * This is the strongest guard for the format upgrade : any change that alters
 * the serialized form of an existing test case, or that stops one from
 * importing, fails here with the offending file named.
 */
@RunWith(Parameterized.class)
public class NISTFormatCorpusTest extends AbstractNISTFormatCorpusTest {

	private static final String DIRECTORY = "/nist/corpus";

	public NISTFormatCorpusTest(String fileName) {
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
