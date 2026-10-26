package gov.nist.healthcare.cds.service.transformation;

import static gov.nist.healthcare.cds.service.transformation.NISTFormatTestFixtures.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.wrapper.ExportConfig;
import gov.nist.healthcare.cds.domain.wrapper.ImportConfig;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.domain.wrapper.TransformResult;
import gov.nist.healthcare.cds.domain.xml.ErrorModel;
import gov.nist.healthcare.cds.repositories.ProductRepository;
import gov.nist.healthcare.cds.repositories.VaccineRepository;
import gov.nist.healthcare.cds.service.MetaDataService;
import gov.nist.healthcare.cds.service.impl.transformation.NISTFormatServiceImpl;

/**
 * Forward-compatibility specification : the parts of the NIST XML format that
 * FITS v2 ({@code gov.nist.hit.fits.service.file.NISTFormatServiceImpl}) can
 * write but this service does not yet understand.
 *
 * Each test states the DESIRED behaviour, so most of them FAIL today. They are
 * the to-do list for the format upgrade, and they pin it precisely :
 *
 * <ol>
 * <li>{@code MetaData/changeLog} -- a free-text element after the two dates,</li>
 * <li>{@code Evaluation/@seriesType} -- "Standard" or "Risk",</li>
 * <li>{@code ForecastType}, {@code EvaluationType}, {@code WorkflowTag} and
 *     {@code Tags/Tag} -- the trailer elements after {@code Forecasts},</li>
 * <li>{@code SerieStatus} codes {@code U} and {@code R}, which the domain enum
 *     already has (also asserted by {@link NISTFormatServiceImplTest}).</li>
 * </ol>
 *
 * For every one of those the format contract is the same three steps : the
 * shipped XSD accepts the document, the import keeps the information, and a
 * re-export writes it back unchanged. Each feature therefore gets a schema
 * test, an import test and a round-trip test rather than a single blanket one,
 * so a partial implementation shows up as a partial pass.
 *
 * The fixtures under {@code src/test/resources/nist/fits2-features} were
 * written against, and validate cleanly with, the FITS v2 XSD
 * ({@code fits-model/src/main/resources/xsd/testCase.xsd}).
 */
@RunWith(MockitoJUnitRunner.class)
public class NISTFormatFits2FeaturesTest {

	private static final String CVX_HEPB_UNSPEC = "45";
	private static final String CVX_POLIO_UNSPEC = "89";
	private static final String CVX_OPV = "02";
	private static final String CVX_MMR = "03";

	@Mock
	private VaccineRepository vaccineRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private MetaDataService mdService;

	@InjectMocks
	private NISTFormatServiceImpl service;

	@Before
	public void setUp() throws Exception {
		when(vaccineRepository.findOne(CVX_HEPB_UNSPEC))
				.thenReturn(vaccine(CVX_HEPB_UNSPEC, "Hep B, unspecified formulation"));
		when(vaccineRepository.findOne(CVX_POLIO_UNSPEC))
				.thenReturn(vaccine(CVX_POLIO_UNSPEC, "polio, unspecified formulation"));
		when(vaccineRepository.findOne(CVX_OPV)).thenReturn(vaccine(CVX_OPV, "OPV, trivalent"));
		when(vaccineRepository.findOne(CVX_MMR)).thenReturn(vaccine(CVX_MMR, "MMR"));

		when(mdService.create(anyBoolean())).thenAnswer(new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) throws Throwable {
				MetaData md = metaData();
				md.setVersion(null);
				return md;
			}
		});
		when(mdService.create(anyBoolean(), anyString())).thenAnswer(new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) throws Throwable {
				MetaData md = metaData();
				md.setVersion((String) invocation.getArguments()[1]);
				return md;
			}
		});
	}

	// ------------------------------------------------------------------
	// MetaData/changeLog
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsMetaDataChangeLog() throws Exception {
		assertSchemaValid("metadata-change-log.xml");
	}

	@Test
	public void importKeepsMetaDataChangeLog() throws Exception {
		TestCase tc = importFeature("metadata-change-log.xml");
		assertEquals("Updated the expected forecast after the 2019 schedule change",
				tc.getMetaData().getChangeLog());
	}

	@Test
	public void changeLogSurvivesARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("metadata-change-log.xml");
	}

	// ------------------------------------------------------------------
	// Evaluation/@seriesType
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsEvaluationSeriesType() throws Exception {
		assertSchemaValid("evaluation-series-type.xml");
	}

	@Test
	public void importKeepsEvaluationSeriesType() throws Exception {
		TestCase tc = importFeature("evaluation-series-type.xml");
		assertEquals(2, tc.getEvents().size());

		Set<String> seriesTypes = new TreeSet<String>();
		for (gov.nist.healthcare.cds.domain.Event e : tc.getEvents()) {
			for (ExpectedEvaluation evaluation : ((VaccinationEvent) e).getEvaluations()) {
				seriesTypes.add(String.valueOf(evaluation.getSeriesType()));
			}
		}
		assertEquals("seriesType was dropped on import", new TreeSet<String>(Arrays.asList("RISK", "STANDARD")),
				seriesTypes);
	}

	/**
	 * An absent seriesType must stay absent rather than defaulting to
	 * "Standard" : the legacy corpus has no seriesType at all, and inventing
	 * one would change every one of those documents on re-export.
	 */
	@Test
	public void anAbsentSeriesTypeStaysAbsent() throws Exception {
		TestCase tc = importFeature("metadata-change-log.xml");
		VaccinationEvent event = (VaccinationEvent) tc.getEvents().get(0);
		for (ExpectedEvaluation evaluation : event.getEvaluations()) {
			assertNull("seriesType was invented on import", evaluation.getSeriesType());
		}
		assertFalse("seriesType was invented on export",
				read(service.export(tc, new ExportConfig())).contains("seriesType"));
	}

	@Test
	public void evaluationSeriesTypeSurvivesARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("evaluation-series-type.xml");
	}

	// ------------------------------------------------------------------
	// ForecastType / EvaluationType / WorkflowTag / Tags
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsTheTestCaseTrailerElements() throws Exception {
		assertSchemaValid("testcase-trailer-elements.xml");
	}

	@Test
	public void importKeepsForecastTypeAndEvaluationType() throws Exception {
		TestCase tc = importFeature("testcase-trailer-elements.xml");
		assertEquals("CDC", tc.getForecastType());
		assertEquals("ACIP", tc.getEvaluationType());
	}

	@Test
	public void importKeepsWorkflowTag() throws Exception {
		TestCase tc = importFeature("testcase-trailer-elements.xml");
		assertEquals(gov.nist.healthcare.cds.enumeration.WorkflowTag.FINAL, tc.getWorkflowTag());
	}

	@Test
	public void importKeepsTags() throws Exception {
		TestCase tc = importFeature("testcase-trailer-elements.xml");
		assertNotNull("tags were dropped on import", tc.getTags());

		List<String> texts = new ArrayList<String>();
		for (gov.nist.healthcare.cds.domain.Tag tag : tc.getTags()) {
			texts.add(tag.getText());
		}
		assertEquals(Arrays.asList("regression", "hepb"), texts);
	}

	@Test
	public void trailerElementsSurviveARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("testcase-trailer-elements.xml");
	}

	// ------------------------------------------------------------------
	// Forecast with the optional date elements omitted
	//
	// Earliest / Recommended / PastDue / Latest are all minOccurs="0" in the
	// FITS v2 schema, and v2 happily writes a forecast without any of them
	// (an aged-out series, for instance). Importing one here throws an NPE
	// inside export(), which the catch-all swallows into a null document.
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsAForecastWithoutDates() throws Exception {
		assertSchemaValid("forecast-without-dates.xml");
	}

	@Test
	public void importAcceptsAForecastWithoutDates() throws Exception {
		TestCase tc = importFeature("forecast-without-dates.xml");
		assertEquals(1, tc.getForecast().size());
		assertEquals(gov.nist.healthcare.cds.enumeration.SerieStatus.X,
				tc.getForecast().get(0).getSerieStatus());
	}

	@Test
	public void aForecastWithoutDatesSurvivesARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("forecast-without-dates.xml");
	}

	// ------------------------------------------------------------------
	// Patient conditions
	//
	// The newest FITS v2 schema adds a <Conditions> block after <Forecasts> :
	// a list of PatientConditionType, each wrapping a Condition (id / code /
	// name / type) plus exactly one typed value -- DateValue, NumericValue or
	// YesNoValue -- and an optional AdministrativeGuidance.
	//
	// Nothing of this exists here yet : no ConditionsType in our XSD, no JAXB
	// beans, no domain classes. These tests state the target behaviour and so
	// FAIL today. The fixture is a real FITS v2 export and validates cleanly
	// against fits-model/src/main/resources/xsd/testCase.xsd.
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsPatientConditions() throws Exception {
		assertSchemaValid("patient-conditions.xml");
	}

	@Test
	public void xsdDeclaresTheConditionTypes() throws Exception {
		String xsd = xsd();
		List<String> missing = new ArrayList<String>();
		for (String type : Arrays.asList("ConditionsType", "PatientConditionType", "ConditionType",
				"ConditionValueTypeType")) {
			if (!xsd.contains("name=\"" + type + "\"")) {
				missing.add(type);
			}
		}
		assertTrue("types missing from testCase.xsd : " + missing, missing.isEmpty());
	}

	/**
	 * A relative date inside a condition may carry several rules, as the
	 * fixture's "Begin Date of antiviral therapy" does (20 weeks + 5 days
	 * after DOB), so the date machinery has to be reused rather than
	 * special-cased.
	 */
	@Test
	public void importKeepsPatientConditions() throws Exception {
		TestCase tc = importFeature("patient-conditions.xml");
		assertNotNull("conditions were dropped on import", conditionsOf(tc));
		assertEquals(3, conditionsOf(tc).size());
	}

	@Test
	public void patientConditionsSurviveARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("patient-conditions.xml");
	}

	/**
	 * Reads the conditions off a test case, tolerating their absence today so
	 * the failure reads as "dropped on import" rather than a compile error.
	 */
	private static java.util.Collection<?> conditionsOf(TestCase tc) {
		try {
			Object value = TestCase.class.getMethod("getConditions").invoke(tc);
			return (java.util.Collection<?>) value;
		} catch (NoSuchMethodException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------
	// everything at once
	// ------------------------------------------------------------------

	@Test
	public void schemaAcceptsADocumentUsingEveryV2Addition() throws Exception {
		assertSchemaValid("all-v2-additions.xml");
	}

	@Test
	public void importAcceptsADocumentUsingEveryV2Addition() throws Exception {
		TestCase tc = importFeature("all-v2-additions.xml");
		assertEquals(2, tc.getEvents().size());
		assertEquals(2, tc.getForecast().size());
	}

	@Test
	public void aDocumentUsingEveryV2AdditionSurvivesARoundTrip() throws Exception {
		assertRoundTripPreservesDocument("all-v2-additions.xml");
	}

	// ------------------------------------------------------------------
	// the XSD itself must grow the v2 constructs
	// ------------------------------------------------------------------

	@Test
	public void xsdDeclaresTheMetaDataChangeLogElement() throws Exception {
		assertTrue("testCase.xsd has no changeLog element in MetaDataType",
				xsd().contains("<element name=\"changeLog\""));
	}

	@Test
	public void xsdDeclaresTheEvaluationSeriesTypeAttribute() throws Exception {
		String xsd = xsd();
		assertTrue("testCase.xsd has no seriesType attribute on EvaluationType",
				xsd.contains("<attribute name=\"seriesType\""));
		assertTrue("testCase.xsd has no seriesTypeType simple type",
				xsd.contains("<simpleType name=\"seriesTypeType\">"));
	}

	@Test
	public void xsdDeclaresTheTestCaseTrailerElements() throws Exception {
		String xsd = xsd();
		List<String> missing = new ArrayList<String>();
		for (String element : Arrays.asList("ForecastType", "EvaluationType", "WorkflowTag", "Tags")) {
			if (!xsd.contains("<element name=\"" + element + "\"")) {
				missing.add(element);
			}
		}
		assertTrue("elements missing from testCase.xsd : " + missing, missing.isEmpty());
	}

	/**
	 * The XSD shipped here and the one FITS v2 exports against must declare the
	 * same {@code SerieStatus} codes, otherwise a v2 document is rejected on
	 * import for a code the domain model can hold.
	 */
	@Test
	public void xsdSerieStatusEnumerationMatchesTheFits2Schema() throws Exception {
		Set<String> declared = serieStatusCodesIn(xsd());
		Set<String> expected = new TreeSet<String>();
		for (gov.nist.healthcare.cds.enumeration.SerieStatus status
				: gov.nist.healthcare.cds.enumeration.SerieStatus.values()) {
			expected.add(status.name());
		}

		Set<String> missing = new TreeSet<String>(expected);
		missing.removeAll(declared);
		assertTrue("SerieStatus codes a FITS v2 document may carry but testCase.xsd rejects : " + missing,
				missing.isEmpty());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private void assertSchemaValid(String file) throws Exception {
		List<ErrorModel> errors = service.preImport(stream(feature(file)));
		assertNotNull("schema could not be loaded", errors);
		assertTrue(file + " is rejected by the shipped testCase.xsd : " + messages(errors), errors.isEmpty());
	}

	private TestCase importFeature(String file) throws Exception {
		TransformResult result = service.importFromFile(stream(feature(file)), new ImportConfig());
		assertTrue(file + " : " + messages(result.getErrors()), result.getErrors().isEmpty());
		assertEquals(file + " : expected exactly one test case", 1, result.getTestCases().size());
		return result.getTestCases().get(0);
	}

	private void assertRoundTripPreservesDocument(String file) throws Exception {
		String original = feature(file);
		TestCase tc = importFeature(file);
		InputStream exported = service.export(tc, new ExportConfig());
		assertNotNull(file + " : export returned null", exported);
		assertEquals(file + " : re-export lost or changed something",
				normalize(canonicalize(original)), normalize(canonicalize(read(exported))));
	}

	private static Set<String> serieStatusCodesIn(String xsd) {
		int start = xsd.indexOf("<complexType name=\"SerieStatusType\">");
		assertTrue("SerieStatusType not found in testCase.xsd", start >= 0);
		String block = xsd.substring(start, xsd.indexOf("</complexType>", start));

		Set<String> codes = new TreeSet<String>();
		Matcher m = Pattern.compile("<enumeration value=\"([^\"]+)\"").matcher(block);
		while (m.find()) {
			codes.add(m.group(1));
		}
		return codes;
	}

	private static String xsd() throws Exception {
		InputStream is = NISTFormatServiceImpl.class.getResourceAsStream("/schema/testCase.xsd");
		assertNotNull("missing /schema/testCase.xsd", is);
		return read(is);
	}

	private static String feature(String name) throws Exception {
		InputStream is = NISTFormatFits2FeaturesTest.class.getResourceAsStream("/nist/fits2-features/" + name);
		assertNotNull("missing fixture /nist/fits2-features/" + name, is);
		return read(is);
	}

	/**
	 * Import regenerates the MetaData dates; empty elements may be collapsed.
	 *
	 * An optional forecast date carries no information whether it is written
	 * empty (&lt;Latest/&gt;, which the legacy corpus is full of and which
	 * export still produces) or left out altogether, so both forms are erased
	 * here and the two are compared as equal.
	 */
	private static String canonicalize(String xml) {
		String out = xml.replaceAll("<dateCreated>[^<]*</dateCreated>", "<dateCreated/>");
		out = out.replaceAll("<dateLastUpdated>[^<]*</dateLastUpdated>", "<dateLastUpdated/>");
		out = out.replaceAll("<([A-Za-z][A-Za-z0-9]*)></\\1>", "<$1/>");
		out = out.replaceAll("\\s*<(Earliest|Recommended|PastDue|Latest)/>", "");
		return out;
	}

	private static InputStream stream(String xml) {
		return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}

	private static String messages(List<ErrorModel> errors) {
		if (errors == null || errors.isEmpty()) {
			return "no errors";
		}
		StringBuilder sb = new StringBuilder();
		for (ErrorModel e : errors) {
			if (sb.length() > 0) {
				sb.append(" | ");
			}
			sb.append(e.getLocation()).append(" : ").append(e.getMessage());
		}
		return sb.toString();
	}
}
