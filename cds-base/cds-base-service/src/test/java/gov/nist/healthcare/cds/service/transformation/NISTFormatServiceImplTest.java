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
import java.util.Iterator;
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

import gov.nist.healthcare.cds.domain.Date;
import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.FixedDate;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.RelativeDate;
import gov.nist.healthcare.cds.domain.RelativeDateRule;
import gov.nist.healthcare.cds.domain.StaticDateReference;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
import gov.nist.healthcare.cds.domain.VaccineDateReference;
import gov.nist.healthcare.cds.domain.wrapper.ExportConfig;
import gov.nist.healthcare.cds.domain.wrapper.ExportResult;
import gov.nist.healthcare.cds.domain.wrapper.ExportedFileStream;
import gov.nist.healthcare.cds.domain.wrapper.ImportConfig;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.domain.wrapper.TransformResult;
import gov.nist.healthcare.cds.domain.xml.ErrorModel;
import gov.nist.healthcare.cds.domain.xml.beans.DateType;
import gov.nist.healthcare.cds.domain.xml.beans.FixedDateType;
import gov.nist.healthcare.cds.domain.xml.beans.RelativeDateType;
import gov.nist.healthcare.cds.domain.xml.beans.RelativeToType;
import gov.nist.healthcare.cds.domain.xml.beans.RuleType;
import gov.nist.healthcare.cds.domain.xml.beans.StatusType;
import gov.nist.healthcare.cds.enumeration.DatePosition;
import gov.nist.healthcare.cds.enumeration.EvaluationReason;
import gov.nist.healthcare.cds.enumeration.EvaluationStatus;
import gov.nist.healthcare.cds.enumeration.Gender;
import gov.nist.healthcare.cds.enumeration.RelativeTo;
import gov.nist.healthcare.cds.enumeration.SerieStatus;
import gov.nist.healthcare.cds.repositories.ProductRepository;
import gov.nist.healthcare.cds.repositories.VaccineRepository;
import gov.nist.healthcare.cds.service.MetaDataService;
import gov.nist.healthcare.cds.service.impl.transformation.NISTFormatServiceImpl;

/**
 * Characterization tests for {@link NISTFormatServiceImpl}. They pin down the
 * current behaviour of the NIST XML format (export, import, round-trip,
 * schema validation and error handling) so a future version of the format /
 * service can be checked for backward compatibility.
 */
@RunWith(MockitoJUnitRunner.class)
public class NISTFormatServiceImplTest {

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
		when(vaccineRepository.findOne(CVX_DTAP)).thenReturn(dtap());
		when(vaccineRepository.findOne(CVX_HEPB)).thenReturn(hepb());
		when(productRepository.getProduct(MVX_SKB, CVX_DTAP)).thenReturn(infanrix());

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
	// format name
	// ------------------------------------------------------------------

	@Test
	public void formatNameIsNist() {
		assertEquals("nist", service.formatName());
	}

	// ------------------------------------------------------------------
	// export
	// ------------------------------------------------------------------

	@Test
	public void exportFixedDateTestCaseProducesExpectedXml() throws Exception {
		String xml = read(service.export(fixedDateTestCase(), new ExportConfig()));

		assertTrue(xml.contains("TestCase xmlns:ns2=\"https://fits.nist.gov/xml/\" UID=\"TC-0001\""));
		assertTrue(xml.contains("<Name>Fixed Date Test Case</Name>"));
		assertTrue(xml.contains("<Description>A complete fixed date test case</Description>"));
		assertTrue(xml.contains("<Group>GROUP-A</Group>"));
		assertTrue(xml.contains("<DateType>FIXED</DateType>"));
		assertTrue(xml.contains("<version>1.0</version>"));
		// assessment date
		assertTrue(xml.contains("<AssessmentDate>"));
		assertTrue(xml.contains("date=\"06/01/2018\""));
		// patient
		assertTrue(xml.contains("<Gender>Female</Gender>"));
		assertTrue(xml.contains("date=\"01/15/2016\""));
		// events
		assertTrue(xml.contains("type=\"VACCINATION\""));
		assertTrue(xml.contains("ID=\"0\""));
		assertTrue(xml.contains("ID=\"1\""));
		assertTrue(xml.contains("cvx=\"" + CVX_DTAP + "\""));
		assertTrue(xml.contains("mvx=\"" + MVX_SKB + "\""));
		// evaluations
		assertTrue(xml.contains("status=\"Valid\""));
		assertTrue(xml.contains("status=\"Not Valid\""));
		assertTrue(xml.contains("code=\"D\""));
		assertTrue(xml.contains("value=\"" + EvaluationReason.D.getDetails() + "\""));
		// forecast
		assertTrue(xml.contains("<DoseNumber>3</DoseNumber>"));
		assertTrue(xml.contains("<ForecastReason>Due now</ForecastReason>"));
		assertTrue(xml.contains("details=\"" + SerieStatus.D.getDetails() + "\""));
		assertTrue(xml.contains("date=\"07/15/2016\""));
		assertTrue(xml.contains("date=\"10/15/2016\""));
	}

	@Test
	public void exportRelativeDateTestCaseProducesExpectedXml() throws Exception {
		String xml = read(service.export(relativeDateTestCase(), new ExportConfig()));

		assertTrue(xml.contains("<DateType>RELATIVE</DateType>"));
		assertTrue(xml.contains("<Gender>Male</Gender>"));
		assertTrue(xml.contains("<Relative>"));
		assertTrue(xml.contains("position=\"AFTER\""));
		assertTrue(xml.contains("position=\"BEFORE\""));
		assertTrue(xml.contains("reference=\"DOB\""));
		assertTrue(xml.contains("reference=\"EVALDATE\""));
		assertTrue(xml.contains("reference=\"VACCINATION\" id=\"0\""));
		assertTrue(xml.contains("weeks=\"4\""));
		assertTrue(xml.contains("days=\"3\""));
		assertFalse(xml.contains("<Fixed"));
	}

	@Test
	public void exportedXmlIsInTheNistNamespace() throws Exception {
		String xml = read(service.export(minimalTestCase(), new ExportConfig()));
		assertTrue(xml.contains("https://fits.nist.gov/xml/"));
	}

	@Test
	public void exportOmitsEmptyGroupAndUid() throws Exception {
		TestCase tc = minimalTestCase();
		tc.setGroupTag("");
		tc.setUid("");
		String xml = read(service.export(tc, new ExportConfig()));
		assertFalse(xml.contains("<Group>"));
		assertFalse(xml.contains("UID=\""));
	}

	@Test
	public void exportOmitsEventsAndForecastsWhenEmpty() throws Exception {
		String xml = read(service.export(minimalTestCase(), new ExportConfig()));
		assertFalse(xml.contains("<Events>"));
		assertFalse(xml.contains("<Forecasts>"));
	}

	@Test
	public void exportReturnsNullWhenTestCaseIsUnusable() {
		// evalDate is null -> NPE inside export is swallowed and null returned
		TestCase tc = new TestCase();
		tc.setName("broken");
		assertNull(service.export(tc, new ExportConfig()));
	}

	// ------------------------------------------------------------------
	// exportToFile
	// ------------------------------------------------------------------

	@Test
	public void exportToFileSanitizesNamesAndDeduplicates() throws Exception {
		TestCase a = minimalTestCase();
		a.setName("Test Case #1");
		TestCase b = minimalTestCase();
		b.setName("Test Case #1");
		TestCase c = minimalTestCase();
		c.setName("Keep-This.1");

		ExportResult result = service.exportToFile(Arrays.asList(a, b, c), new ExportConfig());
		List<String> names = new ArrayList<String>();
		for (ExportedFileStream efs : result.getReader()) {
			names.add(efs.getName());
		}
		assertEquals(Arrays.asList("Test_Case__1.xml", "Test_Case__1_1.xml", "Keep-This.1.xml"), names);
	}

	@Test
	public void findNameAppendsSuffixUntilUnique() {
		List<String> names = new ArrayList<String>(Arrays.asList("a", "a_1"));
		assertEquals("a_1_1", service.findName("a", names));
		assertTrue(names.contains("a_1_1"));
	}


	// ------------------------------------------------------------------
	// golden file : the exact serialized form of the NIST format
	// ------------------------------------------------------------------

	@Test
	public void exportFixedDateTestCaseMatchesGoldenFile() throws Exception {
		assertEquals(normalize(stripTimeZone(golden("fixed-date-testcase.xml"))),
				normalize(stripTimeZone(read(service.export(fixedDateTestCase(), new ExportConfig())))));
	}

	@Test
	public void exportRelativeDateTestCaseMatchesGoldenFile() throws Exception {
		assertEquals(normalize(stripTimeZone(golden("relative-date-testcase.xml"))),
				normalize(stripTimeZone(read(service.export(relativeDateTestCase(), new ExportConfig())))));
	}

	@Test
	public void importGoldenFixedDateFileYieldsTheSameTestCase() throws Exception {
		TransformResult result = service.importFromFile(stream(golden("fixed-date-testcase.xml")),
				new ImportConfig());
		assertTrue(messages(result.getErrors()), result.getErrors().isEmpty());
		TestCase tc = result.getTestCases().get(0);
		assertEquals("Fixed Date Test Case", tc.getName());
		assertEquals(2, tc.getEvents().size());
		assertEquals(1, tc.getForecast().size());
	}

	@Test
	public void importGoldenRelativeDateFileYieldsTheSameTestCase() throws Exception {
		TransformResult result = service.importFromFile(stream(golden("relative-date-testcase.xml")),
				new ImportConfig());
		assertTrue(messages(result.getErrors()), result.getErrors().isEmpty());
		TestCase tc = result.getTestCases().get(0);
		assertEquals("Relative Date Test Case", tc.getName());
		assertEquals(gov.nist.healthcare.cds.enumeration.DateType.RELATIVE, tc.getDateType());
	}

	@Test
	public void goldenFilesAreSchemaValid() throws Exception {
		for (String file : new String[] { "fixed-date-testcase.xml", "relative-date-testcase.xml" }) {
			List<ErrorModel> errors = service.preImport(stream(golden(file)));
			assertNotNull(errors);
			assertTrue(file + " : " + messages(errors), errors.isEmpty());
		}
	}


	// ------------------------------------------------------------------
	// SerieStatus / XSD coverage gap
	//
	// SerieStatus declares 16 codes but testCase.xsd's SerieStatusType only
	// enumerates 14 : U ("Other") and R ("Not Recommended") are missing. A
	// test case carrying either one exports to schema-invalid XML, and cannot
	// be imported through a schema-validating consumer.
	//
	// These tests assert the DESIRED behaviour and therefore FAIL against the
	// current XSD. They should start passing once the new format adds the two
	// missing enumeration values.
	// ------------------------------------------------------------------

	/** Every SerieStatus the domain can hold must be expressible in the schema. */
	@Test
	public void everySerieStatusExportsToSchemaValidXml() throws Exception {
		List<String> invalid = new ArrayList<String>();
		for (SerieStatus status : SerieStatus.values()) {
			List<ErrorModel> errors = service.preImport(service.export(testCaseWithSerieStatus(status),
					new ExportConfig()));
			assertNotNull("schema could not be loaded", errors);
			if (!errors.isEmpty()) {
				invalid.add(status.name() + " (" + status.getDetails() + ") -> " + messages(errors));
			}
		}
		assertTrue("SerieStatus codes missing from testCase.xsd : " + invalid, invalid.isEmpty());
	}

	/** The schema enumeration and the SerieStatus enum must not drift apart. */
	@Test
	public void xsdSerieStatusEnumerationCoversTheWholeEnum() throws Exception {
		Set<String> declared = serieStatusCodesDeclaredInXsd();
		Set<String> expected = new TreeSet<String>();
		for (SerieStatus status : SerieStatus.values()) {
			expected.add(status.name());
		}

		Set<String> missing = new TreeSet<String>(expected);
		missing.removeAll(declared);
		Set<String> extra = new TreeSet<String>(declared);
		extra.removeAll(expected);

		assertTrue("codes in SerieStatus but not in testCase.xsd : " + missing, missing.isEmpty());
		assertTrue("codes in testCase.xsd but not in SerieStatus : " + extra, extra.isEmpty());
	}

	/** Round-tripping must preserve the status for every code, U and R included. */
	@Test
	public void everySerieStatusSurvivesARoundTrip() throws Exception {
		for (SerieStatus status : SerieStatus.values()) {
			TransformResult result = service.importFromFile(
					service.export(testCaseWithSerieStatus(status), new ExportConfig()), new ImportConfig());
			assertTrue(status + " : " + messages(result.getErrors()), result.getErrors().isEmpty());
			assertEquals("round trip lost the status for " + status, status,
					result.getTestCases().get(0).getForecast().get(0).getSerieStatus());
		}
	}

	// ------------------------------------------------------------------
	// preExport
	// ------------------------------------------------------------------

	@Test
	public void preExportAcceptsRunnableTestCase() throws Exception {
		assertTrue(service.preExport(fixedDateTestCase()).isEmpty());
	}

	@Test
	public void preExportRejectsNonRunnableTestCase() throws Exception {
		TestCase tc = fixedDateTestCase();
		tc.setRunnable(false);
		List<ErrorModel> errors = service.preExport(tc);
		assertEquals(1, errors.size());
		assertEquals(tc.getName(), errors.get(0).getLocation());
		assertEquals(" TestCase is not complete", errors.get(0).getMessage());
	}

	// ------------------------------------------------------------------
	// import
	// ------------------------------------------------------------------

	@Test
	public void importFixedDateTestCase() throws Exception {
		TestCase original = fixedDateTestCase();
		TransformResult result = service.importFromFile(service.export(original, new ExportConfig()),
				new ImportConfig());

		assertTrue(result.getErrors().isEmpty());
		assertEquals(1, result.getTotalTC());
		assertEquals(1, result.getTestCases().size());

		TestCase tc = result.getTestCases().get(0);
		assertEquals("Fixed Date Test Case", tc.getName());
		assertEquals("A complete fixed date test case", tc.getDescription());
		assertEquals("GROUP-A", tc.getGroupTag());
		assertEquals("TC-0001", tc.getUid());
		assertEquals(gov.nist.healthcare.cds.enumeration.DateType.FIXED, tc.getDateType());
		assertEquals("06/01/2018", ((FixedDate) tc.getEvalDate()).getDateString());
		assertEquals("1.0", tc.getMetaData().getVersion());

		assertEquals(Gender.F, tc.getPatient().getGender());
		assertEquals("01/15/2016", ((FixedDate) tc.getPatient().getDob()).getDateString());

		assertEquals(2, tc.getEvents().size());
		VaccinationEvent e1 = (VaccinationEvent) tc.getEvents().get(0);
		assertEquals(0, e1.getPosition());
		assertEquals("03/15/2016", ((FixedDate) e1.getDate()).getDateString());
		assertTrue(e1.getAdministred() instanceof Vaccine);
		assertEquals(CVX_DTAP, e1.getAdministred().getCvx());
		assertEquals(1, e1.getEvaluations().size());
		ExpectedEvaluation ev1 = e1.getEvaluations().iterator().next();
		assertEquals(EvaluationStatus.VALID, ev1.getStatus());
		assertNull(ev1.getReason());
		assertEquals(CVX_DTAP, ev1.getRelatedTo().getCvx());

		VaccinationEvent e2 = (VaccinationEvent) tc.getEvents().get(1);
		assertEquals(1, e2.getPosition());
		assertTrue(e2.getAdministred() instanceof Product);
		assertEquals(MVX_SKB, ((Product) e2.getAdministred()).getMvx());
		ExpectedEvaluation ev2 = e2.getEvaluations().iterator().next();
		assertEquals(EvaluationStatus.INVALID, ev2.getStatus());
		assertEquals(EvaluationReason.D, ev2.getReason());

		assertEquals(1, tc.getForecast().size());
		ExpectedForecast ef = tc.getForecast().get(0);
		assertEquals(CVX_DTAP, ef.getTarget().getCvx());
		assertEquals(SerieStatus.D, ef.getSerieStatus());
		assertEquals("Due now", ef.getForecastReason());
		assertEquals("3", ef.getDoseNumber());
		assertEquals("07/15/2016", ((FixedDate) ef.getEarliest()).getDateString());
		assertEquals("08/15/2016", ((FixedDate) ef.getRecommended()).getDateString());
		assertEquals("09/15/2016", ((FixedDate) ef.getPastDue()).getDateString());
		assertEquals("10/15/2016", ((FixedDate) ef.getComplete()).getDateString());
	}

	@Test
	public void importRelativeDateTestCase() throws Exception {
		TransformResult result = service.importFromFile(
				service.export(relativeDateTestCase(), new ExportConfig()), new ImportConfig());

		assertTrue(result.getErrors().isEmpty());
		TestCase tc = result.getTestCases().get(0);
		assertEquals(gov.nist.healthcare.cds.enumeration.DateType.RELATIVE, tc.getDateType());

		RelativeDateRule evalRule = ((RelativeDate) tc.getEvalDate()).getRules().get(0);
		assertEquals(DatePosition.AFTER, evalRule.getPosition());
		assertEquals(2, evalRule.getYear());
		assertEquals(RelativeTo.DOB, ((StaticDateReference) evalRule.getRelativeTo()).getId());

		RelativeDateRule dobRule = ((RelativeDate) tc.getPatient().getDob()).getRules().get(0);
		assertEquals(DatePosition.BEFORE, dobRule.getPosition());
		assertEquals(RelativeTo.EVALDATE, ((StaticDateReference) dobRule.getRelativeTo()).getId());

		VaccinationEvent e2 = (VaccinationEvent) tc.getEvents().get(1);
		RelativeDateRule evtRule = ((RelativeDate) e2.getDate()).getRules().get(0);
		assertTrue(evtRule.getRelativeTo() instanceof VaccineDateReference);
		assertEquals(0, ((VaccineDateReference) evtRule.getRelativeTo()).getId());
		assertEquals(4, evtRule.getWeek());
		assertEquals(3, evtRule.getDay());
	}

	@Test
	public void importMinimalTestCaseSetsEmptyUidAndNoEvents() throws Exception {
		TransformResult result = service.importFromFile(service.export(minimalTestCase(), new ExportConfig()),
				new ImportConfig());

		TestCase tc = result.getTestCases().get(0);
		assertEquals("", tc.getUid());
		assertNull(tc.getGroupTag());
		assertTrue(tc.getEvents().isEmpty());
		assertTrue(tc.getForecast().isEmpty());
	}

	@Test
	public void importReportsUnknownVaccine() throws Exception {
		when(vaccineRepository.findOne(CVX_DTAP)).thenReturn(null);

		TransformResult result = service.importFromFile(service.export(fixedDateTestCase(), new ExportConfig()),
				new ImportConfig());

		assertTrue(result.getTestCases().isEmpty());
		assertEquals(1, result.getErrors().size());
		ErrorModel error = result.getErrors().get(0);
		assertEquals("Vaccine", error.getLocation());
		assertEquals("CVX not found in database : " + CVX_DTAP, error.getMessage());
	}

	@Test
	public void importReportsUnknownProduct() throws Exception {
		when(productRepository.getProduct(MVX_SKB, CVX_DTAP)).thenReturn(null);

		TransformResult result = service.importFromFile(service.export(fixedDateTestCase(), new ExportConfig()),
				new ImportConfig());

		assertTrue(result.getTestCases().isEmpty());
		assertEquals(1, result.getErrors().size());
		ErrorModel error = result.getErrors().get(0);
		assertEquals("Product", error.getLocation());
		assertEquals("Product not found in database cvx : " + CVX_DTAP + " mvx : " + MVX_SKB, error.getMessage());
	}

	@Test
	public void importReportsInvalidFileFormat() throws Exception {
		TransformResult result = service.importFromFile(stream("this is not xml at all"), new ImportConfig());

		assertTrue(result.getTestCases().isEmpty());
		assertEquals(1, result.getErrors().size());
		assertEquals("File Format", result.getErrors().get(0).getLocation());
		assertEquals("Invalid File Format", result.getErrors().get(0).getMessage());
		assertEquals(1, result.getTotalTC());
	}

	// ------------------------------------------------------------------
	// round trip
	// ------------------------------------------------------------------

	@Test
	public void roundTripIsStableAcrossTwoExports() throws Exception {
		String first = read(service.export(fixedDateTestCase(), new ExportConfig()));
		TestCase imported = service.importFromFile(stream(first), new ImportConfig()).getTestCases().get(0);
		String second = read(service.export(imported, new ExportConfig()));
		assertEquals(normalize(first), normalize(second));
	}

	@Test
	public void roundTripIsStableForRelativeDates() throws Exception {
		String first = read(service.export(relativeDateTestCase(), new ExportConfig()));
		TestCase imported = service.importFromFile(stream(first), new ImportConfig()).getTestCases().get(0);
		String second = read(service.export(imported, new ExportConfig()));
		assertEquals(normalize(first), normalize(second));
	}

	// ------------------------------------------------------------------
	// preImport (schema validation)
	// ------------------------------------------------------------------

	@Test
	public void preImportAcceptsExportedDocument() throws Exception {
		List<ErrorModel> errors = service.preImport(service.export(fixedDateTestCase(), new ExportConfig()));
		assertNotNull(errors);
		assertTrue("unexpected schema errors : " + messages(errors), errors.isEmpty());
	}

	@Test
	public void preImportAcceptsExportedRelativeDocument() throws Exception {
		List<ErrorModel> errors = service.preImport(service.export(relativeDateTestCase(), new ExportConfig()));
		assertNotNull(errors);
		assertTrue("unexpected schema errors : " + messages(errors), errors.isEmpty());
	}

	@Test
	public void preImportRejectsDocumentMissingRequiredElements() throws Exception {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<TestCase xmlns=\"https://fits.nist.gov/xml/\"><Name>no date type</Name></TestCase>";
		List<ErrorModel> errors = service.preImport(stream(xml));
		assertNotNull(errors);
		assertFalse(errors.isEmpty());
	}

	@Test
	public void preImportRejectsUnknownRootElement() throws Exception {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Unknown xmlns=\"https://fits.nist.gov/xml/\"/>";
		List<ErrorModel> errors = service.preImport(stream(xml));
		assertNotNull(errors);
		assertFalse(errors.isEmpty());
	}

	// ------------------------------------------------------------------
	// date conversions
	// ------------------------------------------------------------------

	@Test
	public void fixedDateToXmlAndBack() throws Exception {
		DateType dt = service.date((Date) new FixedDate("12/25/2019"));
		assertNotNull(dt.getFixed());
		assertNull(dt.getRelative());
		assertEquals("12/25/2019", dt.getFixed().getDate());

		Date back = service.date(dt);
		assertTrue(back instanceof FixedDate);
		assertEquals("12/25/2019", ((FixedDate) back).getDateString());
	}

	@Test
	public void relativeDateToXmlAndBack() throws Exception {
		RelativeDate rd = relativeTo(RelativeTo.DOB, DatePosition.AFTER, 1, 2, 3, 4);
		DateType dt = service.date((Date) rd);
		assertNull(dt.getFixed());
		assertEquals(1, dt.getRelative().getRule().size());
		RuleType rt = dt.getRelative().getRule().get(0);
		assertEquals(Integer.valueOf(1), rt.getYears());
		assertEquals(Integer.valueOf(2), rt.getMonths());
		assertEquals(Integer.valueOf(3), rt.getWeeks());
		assertEquals(Integer.valueOf(4), rt.getDays());
		assertEquals("AFTER", rt.getRelativeTo().getPosition());
		assertEquals("DOB", rt.getRelativeTo().getReference());

		RelativeDateRule back = ((RelativeDate) service.date(dt)).getRules().get(0);
		assertEquals(DatePosition.AFTER, back.getPosition());
		assertEquals(1, back.getYear());
		assertEquals(2, back.getMonth());
		assertEquals(3, back.getWeek());
		assertEquals(4, back.getDay());
		assertEquals(RelativeTo.DOB, ((StaticDateReference) back.getRelativeTo()).getId());
	}

	@Test
	public void vaccineReferenceIsExportedAsVaccinationWithId() throws Exception {
		DateType dt = service.date((Date) relativeToVaccination(7, DatePosition.BEFORE, 0, 0, 0, 10));
		RelativeToType rtt = dt.getRelative().getRule().get(0).getRelativeTo();
		assertEquals("VACCINATION", rtt.getReference());
		assertEquals(Integer.valueOf(7), rtt.getId());
		assertEquals("BEFORE", rtt.getPosition());

		RelativeDateRule back = ((RelativeDate) service.date(dt)).getRules().get(0);
		assertEquals(7, ((VaccineDateReference) back.getRelativeTo()).getId());
	}

	@Test
	public void multipleRulesArePreserved() throws Exception {
		RelativeDate rd = relativeTo(RelativeTo.DOB, DatePosition.AFTER, 1, 0, 0, 0);
		RelativeDateRule second = new RelativeDateRule();
		second.setPosition(DatePosition.BEFORE);
		second.setDay(5);
		second.setRelativeTo(new StaticDateReference(RelativeTo.EVALDATE));
		rd.add(second);

		DateType dt = service.date((Date) rd);
		assertEquals(2, dt.getRelative().getRule().size());
		assertEquals(2, ((RelativeDate) service.date(dt)).getRules().size());
	}

	@Test
	public void emptyDateTypeConvertsToNull() throws Exception {
		assertNull(service.date(new DateType()));
	}

	@Test
	public void unknownDateSubclassProducesEmptyDateType() throws Exception {
		DateType dt = service.date((Date) null);
		assertNull(dt.getFixed());
		assertNull(dt.getRelative());
	}

	@Test
	public void utilDateIsConvertedToXmlGregorianCalendar() throws Exception {
		MetaData md = metaData();
		assertNotNull(service.date(md.getDateCreated()));
		assertEquals(2018, service.date(md.getDateCreated()).getYear());
	}

	// ------------------------------------------------------------------
	// status mapping
	// ------------------------------------------------------------------

	@Test
	public void statusMappingIsSymmetric() {
		for (EvaluationStatus es : EvaluationStatus.values()) {
			StatusType st = service.toXMLStatus(es);
			assertNotNull("no xml status for " + es, st);
			assertEquals(es, service.fromXMLStatus(st));
		}
		assertEquals(StatusType.VALID, service.toXMLStatus(EvaluationStatus.VALID));
		assertEquals(StatusType.INVALID, service.toXMLStatus(EvaluationStatus.INVALID));
		assertEquals(StatusType.SUBSTANDARD, service.toXMLStatus(EvaluationStatus.SUBSTANDARD));
		assertEquals(StatusType.EXTRANEOUS, service.toXMLStatus(EvaluationStatus.EXTRANEOUS));
	}

	@Test
	public void xmlStatusValuesMatchTheSchemaWording() {
		assertEquals("Valid", StatusType.VALID.value());
		assertEquals("Not Valid", StatusType.INVALID.value());
		assertEquals("Substandard", StatusType.SUBSTANDARD.value());
		assertEquals("Extraneous", StatusType.EXTRANEOUS.value());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static InputStream stream(String xml) {
		return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}

	private static TestCase testCaseWithSerieStatus(SerieStatus status) throws Exception {
		TestCase tc = minimalTestCase();
		ExpectedForecast ef = new ExpectedForecast();
		ef.setTarget(dtap());
		ef.setSerieStatus(status);
		ef.setDoseNumber("1");
		ef.setForecastReason(status.getDetails());
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(ef)));
		return tc;
	}

	/** Reads the SerieStatusType enumeration straight out of the shipped XSD. */
	private static Set<String> serieStatusCodesDeclaredInXsd() throws Exception {
		InputStream is = NISTFormatServiceImpl.class.getResourceAsStream("/schema/testCase.xsd");
		assertNotNull("missing /schema/testCase.xsd", is);
		String xsd = read(is);

		int start = xsd.indexOf("<complexType name=\"SerieStatusType\">");
		assertTrue("SerieStatusType not found in testCase.xsd", start >= 0);
		int end = xsd.indexOf("</complexType>", start);
		String block = xsd.substring(start, end);

		Set<String> codes = new TreeSet<String>();
		Matcher m = Pattern.compile("<enumeration value=\"([^\"]+)\"").matcher(block);
		while (m.find()) {
			codes.add(m.group(1));
		}
		return codes;
	}

	private static String golden(String name) throws Exception {
		InputStream is = NISTFormatServiceImplTest.class.getResourceAsStream("/nist/" + name);
		assertNotNull("missing golden file /nist/" + name, is);
		return read(is);
	}

	/** Timezone offsets in MetaData dates depend on the JVM default timezone. */
	private static String stripTimeZone(String xml) {
		return xml.replaceAll("([0-9]{4}-[0-9]{2}-[0-9]{2})(Z|[+-][0-9]{2}:[0-9]{2})", "$1");
	}

	private static String messages(List<ErrorModel> errors) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<ErrorModel> it = errors.iterator(); it.hasNext();) {
			sb.append(it.next().getMessage());
			if (it.hasNext()) {
				sb.append(" | ");
			}
		}
		return sb.toString();
	}
}
