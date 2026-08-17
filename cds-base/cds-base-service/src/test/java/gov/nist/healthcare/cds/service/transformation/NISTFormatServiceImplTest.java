package gov.nist.healthcare.cds.service.transformation;

import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MERCK_MVX;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MMR_CVX;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MMR_NAME;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.completeTestCase;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.createdMetaData;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.exportConfig;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.importAll;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.list;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.product;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.relativeToBirth;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.relativeToVaccination;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.richTestCase;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.text;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.vaccination;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.vaccine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.FixedDate;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.RelativeDate;
import gov.nist.healthcare.cds.domain.RelativeDateRule;
import gov.nist.healthcare.cds.domain.StaticDateReference;
import gov.nist.healthcare.cds.domain.Tag;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
import gov.nist.healthcare.cds.domain.exception.ConfigurationException;
import gov.nist.healthcare.cds.domain.wrapper.ExportResult;
import gov.nist.healthcare.cds.domain.wrapper.ExportedFileStream;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.domain.wrapper.TransformResult;
import gov.nist.healthcare.cds.domain.xml.ErrorModel;
import gov.nist.healthcare.cds.enumeration.DatePosition;
import gov.nist.healthcare.cds.enumeration.DateType;
import gov.nist.healthcare.cds.enumeration.EvaluationReason;
import gov.nist.healthcare.cds.enumeration.EvaluationStatus;
import gov.nist.healthcare.cds.enumeration.Gender;
import gov.nist.healthcare.cds.enumeration.RelativeTo;
import gov.nist.healthcare.cds.enumeration.SerieStatus;
import gov.nist.healthcare.cds.enumeration.WorkflowTag;
import gov.nist.healthcare.cds.repositories.ProductRepository;
import gov.nist.healthcare.cds.repositories.VaccineRepository;
import gov.nist.healthcare.cds.service.MetaDataService;
import gov.nist.healthcare.cds.service.impl.transformation.NISTFormatServiceImpl;

/**
 * Import and export of the 'nist' format : one XML file per test case, validated against
 * /schema/testCase.xsd. Vaccines and products are resolved against the database, so every
 * import test states which codes the repositories know about.
 */
public class NISTFormatServiceImplTest {

	@InjectMocks
	private NISTFormatServiceImpl formatService;

	@Mock
	private VaccineRepository vaccineRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private MetaDataService mdService;

	private Vaccine mmr;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mmr = vaccine(MMR_CVX, MMR_NAME);
		Mockito.when(mdService.create(Mockito.anyBoolean())).thenAnswer(newMetaData(null));
		Mockito.when(mdService.create(Mockito.anyBoolean(), Mockito.anyString())).thenAnswer(newMetaData(1));
	}

	// --- Format ---

	@Test
	public void formatName_isNist() {
		Assert.assertEquals("nist", formatService.formatName());
	}

	// --- Export ---

	@Test
	public void exportToFile_writesOneXmlFilePerTestCase() throws ConfigurationException {
		TestCase first = completeTestCase();
		TestCase second = completeTestCase();
		second.setName("Another Case");

		ExportResult result = formatService.exportToFile(list(first, second), exportConfig());

		Assert.assertEquals(Arrays.asList("Simple_Test_Case.xml", "Another_Case.xml"), names(result));
	}

	@Test
	public void exportToFile_replacesTheCharactersAFileNameCannotHold() throws ConfigurationException {
		TestCase tc = completeTestCase();
		tc.setName("MMR : dose #1 / 2");

		ExportResult result = formatService.exportToFile(list(tc), exportConfig());

		Assert.assertEquals(Arrays.asList("MMR___dose__1___2.xml"), names(result));
	}

	@Test
	public void exportToFile_disambiguatesTestCasesSharingAName() throws ConfigurationException {
		ExportResult result = formatService.exportToFile(
				list(completeTestCase(), completeTestCase(), completeTestCase()), exportConfig());

		Assert.assertEquals(Arrays.asList("Simple_Test_Case.xml", "Simple_Test_Case_1.xml",
				"Simple_Test_Case_1_1.xml"), names(result));
	}

	@Test
	public void exportToFile_writesTheTestCaseAsXml() throws ConfigurationException {
		String xml = exportOne(completeTestCase());

		Assert.assertTrue(xml, xml.contains("UID=\"TC-1\""));
		Assert.assertTrue(xml, xml.contains("<Name>Simple Test Case</Name>"));
		Assert.assertTrue(xml, xml.contains("<Description>A single MMR dose</Description>"));
		Assert.assertTrue(xml, xml.contains("<Group>MMR</Group>"));
		Assert.assertTrue(xml, xml.contains("<DateType>FIXED</DateType>"));
		Assert.assertTrue(xml, xml.contains("<version>1.0</version>"));
		Assert.assertTrue(xml, xml.contains("<changeLog>initial version</changeLog>"));
		Assert.assertTrue(xml, xml.contains("<Gender>Female</Gender>"));
		Assert.assertTrue(xml, xml.contains("<Fixed date=\"01/01/2010\"/>"));
		Assert.assertTrue(xml, xml.contains("<Event type=\"VACCINATION\" ID=\"1\">"));
		Assert.assertTrue(xml, xml.contains("<Administred name=\"MMR\" cvx=\"03\"/>"));
		Assert.assertTrue(xml, xml.contains("<Evaluation status=\"Valid\">"));
		Assert.assertTrue(xml, xml.contains("<SerieStatus code=\"D\" details=\"Due\"/>"));
		Assert.assertTrue(xml, xml.contains("<DoseNumber>2</DoseNumber>"));
		Assert.assertTrue(xml, xml.contains("<ForecastType>Forecast</ForecastType>"));
		Assert.assertTrue(xml, xml.contains("<EvaluationType>Evaluation</EvaluationType>"));
	}

	@Test
	public void exportToFile_writesTheManufacturerOfAdministredProducts() throws ConfigurationException {
		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(vaccination(1, "02/01/2010",
				product("MMR-II", mmr, MERCK_MVX, "M-M-R II"), EvaluationStatus.VALID, null, mmr))));

		String xml = exportOne(tc);

		Assert.assertTrue(xml, xml.contains("<Administred name=\"M-M-R II\" cvx=\"03\" mvx=\"MSD\"/>"));
	}

	@Test
	public void exportToFile_writesTheEvaluationReasonWithItsCodeAndDetails() throws ConfigurationException {
		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(vaccination(1, "02/01/2010", mmr,
				EvaluationStatus.INVALID, EvaluationReason.C, mmr))));

		String xml = exportOne(tc);

		Assert.assertTrue(xml, xml.contains("<Evaluation status=\"Not Valid\">"));
		Assert.assertTrue(xml, xml.contains("<EvaluationReason code=\"C\""));
		Assert.assertTrue(xml, xml.contains(EvaluationReason.C.getDetails()));
	}

	@Test
	public void exportToFile_writesRelativeDatesAsRules() throws ConfigurationException {
		TestCase tc = completeTestCase();
		tc.setDateType(DateType.RELATIVE);
		tc.getEvents().get(0).setDate(relativeToBirth(1, 2, 0, 3));

		String xml = exportOne(tc);

		Assert.assertTrue(xml, xml.contains("<DateType>RELATIVE</DateType>"));
		Assert.assertTrue(xml, xml.contains("years=\"1\""));
		Assert.assertTrue(xml, xml.contains("months=\"2\""));
		Assert.assertTrue(xml, xml.contains("days=\"3\""));
		Assert.assertTrue(xml, xml.contains("<RelativeTo position=\"AFTER\" reference=\"DOB\"/>"));
	}

	@Test
	public void exportToFile_writesTagsAndWorkflowTag() throws ConfigurationException {
		TestCase tc = completeTestCase();
		tc.setTags(Arrays.asList(new Tag("regression"), new Tag("mmr")));
		tc.setWorkflowTag(WorkflowTag.FINAL);

		String xml = exportOne(tc);

		Assert.assertTrue(xml, xml.contains("<WorkflowTag>FINAL</WorkflowTag>"));
		Assert.assertTrue(xml, xml.contains("<Tag>regression</Tag>"));
		Assert.assertTrue(xml, xml.contains("<Tag>mmr</Tag>"));
	}

	// --- Import ---

	@Test
	public void importFromFile_readsBackAnExportedTestCase() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);

		TestCase imported = importOne(exportOne(completeTestCase()));

		Assert.assertEquals("TC-1", imported.getUid());
		Assert.assertEquals("Simple Test Case", imported.getName());
		Assert.assertEquals("A single MMR dose", imported.getDescription());
		Assert.assertEquals("MMR", imported.getGroupTag());
		Assert.assertEquals(DateType.FIXED, imported.getDateType());
		Assert.assertEquals("Evaluation", imported.getEvaluationType());
		Assert.assertEquals("Forecast", imported.getForecastType());
		Assert.assertEquals("06/15/2012", ((FixedDate) imported.getEvalDate()).getDateString());
		Assert.assertEquals(Gender.F, imported.getPatient().getGender());
		Assert.assertEquals("01/01/2010", ((FixedDate) imported.getPatient().getDob()).getDateString());
	}

	@Test
	public void importFromFile_readsBackTheVaccinationsAndTheirEvaluations() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);

		TestCase imported = importOne(exportOne(completeTestCase()));

		Assert.assertEquals(1, imported.getEvents().size());
		VaccinationEvent event = (VaccinationEvent) imported.getEvents().get(0);
		Assert.assertEquals(1, event.getPosition());
		Assert.assertEquals("02/01/2010", ((FixedDate) event.getDate()).getDateString());
		Assert.assertEquals(mmr, event.getAdministred());

		Assert.assertEquals(1, event.getEvaluations().size());
		ExpectedEvaluation evaluation = event.getEvaluations().iterator().next();
		Assert.assertEquals(EvaluationStatus.VALID, evaluation.getStatus());
		Assert.assertEquals(mmr, evaluation.getRelatedTo());
	}

	@Test
	public void importFromFile_readsBackTheForecastAndItsDates() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);

		TestCase imported = importOne(exportOne(completeTestCase()));

		Assert.assertEquals(1, imported.getForecast().size());
		ExpectedForecast forecast = imported.getForecast().get(0);
		Assert.assertEquals(mmr, forecast.getTarget());
		Assert.assertEquals(SerieStatus.D, forecast.getSerieStatus());
		Assert.assertEquals("2", forecast.getDoseNumber());
		Assert.assertEquals("01/01/2011", ((FixedDate) forecast.getEarliest()).getDateString());
		Assert.assertEquals("02/01/2011", ((FixedDate) forecast.getRecommended()).getDateString());
		Assert.assertEquals("03/01/2011", ((FixedDate) forecast.getPastDue()).getDateString());
		Assert.assertNull("no Latest date was exported", forecast.getComplete());
	}

	@Test
	public void importFromFile_readsBackRelativeDates() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);
		TestCase tc = completeTestCase();
		tc.setDateType(DateType.RELATIVE);
		tc.getEvents().get(0).setDate(relativeToBirth(1, 2, 0, 3));

		TestCase imported = importOne(exportOne(tc));

		Assert.assertEquals(DateType.RELATIVE, imported.getDateType());
		RelativeDate date = (RelativeDate) imported.getEvents().get(0).getDate();
		Assert.assertEquals(1, date.getRules().size());
		RelativeDateRule rule = date.getRules().get(0);
		Assert.assertEquals(1, rule.getYear());
		Assert.assertEquals(2, rule.getMonth());
		Assert.assertEquals(3, rule.getDay());
		Assert.assertEquals(DatePosition.AFTER, rule.getPosition());
		Assert.assertEquals(RelativeTo.DOB, ((StaticDateReference) rule.getRelativeTo()).getId());
	}

	@Test
	public void importFromFile_resolvesAdministredProductsByCvxAndMvx() throws Exception {
		Product mmrII = product("MMR-II", mmr, MERCK_MVX, "M-M-R II");
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);
		Mockito.when(productRepository.getProduct(MERCK_MVX, MMR_CVX)).thenReturn(mmrII);

		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(
				vaccination(1, "02/01/2010", mmrII, EvaluationStatus.VALID, null, mmr))));

		TestCase imported = importOne(exportOne(tc));

		Assert.assertEquals(mmrII, ((VaccinationEvent) imported.getEvents().get(0)).getAdministred());
	}

	@Test
	public void importFromFile_countsTheTestCaseItRead() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);

		TransformResult result = importXml(exportOne(completeTestCase()));

		Assert.assertEquals(1, result.getTotalTC());
		Assert.assertTrue(result.getErrors().isEmpty());
	}

	// --- Round trip fidelity ---

	/**
	 * The whole point of the format : what goes out has to come back. Everything a test case is
	 * made of is compared, so a field the export forgets to write - or the import forgets to
	 * read - shows up here rather than in production.
	 *
	 * Two things do not survive the trip :
	 *
	 * - the metadata dates : the export writes them, but the import stamps the test case with
	 *   the date of the import instead of reading them back ;
	 * - the dose number of a vaccination : the XML has no place for it, so it comes back as the
	 *   default of 1.
	 */
	@Test
	public void exportToFile_thenImportFromFile_changesNothingButTheMetaDataDatesAndTheDoseNumbers()
			throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);
		Mockito.when(productRepository.getProduct(MERCK_MVX, MMR_CVX))
				.thenReturn(product("MMR-II", mmr, MERCK_MVX, "M-M-R II"));
		TestCase original = richTestCase();

		TestCase imported = importOne(exportOne(original));

		Assert.assertEquals(Arrays.asList(
				"metaData.dateCreated: 2018-01-01 -> 2020-09-30",
				"metaData.dateLastUpdated: 2018-06-01 -> 2020-09-30",
				"events[1].doseNumber: 2 -> 1"),
				TestCaseDiff.differences(original, imported));
	}

	/**
	 * Same check for a test case whose dates are all relative, so that the rules and their
	 * references are covered too. The metadata is stamped with the date of the import up front,
	 * to leave the known loss above out of the way and keep this test about the dates.
	 */
	@Test
	public void exportToFile_thenImportFromFile_changesNothingOfARelativeDatedTestCase() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);
		TestCase original = completeTestCase();
		original.setDateType(DateType.RELATIVE);
		original.setEvalDate(relativeToBirth(6, 0, 0, 0));
		original.getEvents().get(0).setDate(relativeToBirth(1, 2, 3, 4));
		original.getForecast().get(0).setEarliest(relativeToBirth(2, 0, 0, 0));
		original.getForecast().get(0).setRecommended(relativeToVaccination(0, 6, 0, 0, 1));
		original.getForecast().get(0).setPastDue(null);
		original.setMetaData(createdMetaData("1.0"));

		TestCase imported = importOne(exportOne(original));

		Assert.assertEquals(Collections.<String>emptyList(), TestCaseDiff.differences(original, imported));
	}

	// --- Import errors ---

	@Test
	public void importFromFile_reportsAVaccineTheDatabaseDoesNotKnow() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(null);

		TransformResult result = importXml(exportOne(completeTestCase()));

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(1, result.getErrors().size());
		Assert.assertEquals("Vaccine", result.getErrors().get(0).getLocation());
		Assert.assertTrue(result.getErrors().get(0).getMessage().contains(MMR_CVX));
	}

	@Test
	public void importFromFile_reportsAProductTheDatabaseDoesNotKnow() throws Exception {
		Mockito.when(vaccineRepository.findOne(MMR_CVX)).thenReturn(mmr);
		Mockito.when(productRepository.getProduct(MERCK_MVX, MMR_CVX)).thenReturn(null);

		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(vaccination(1, "02/01/2010",
				product("MMR-II", mmr, MERCK_MVX, "M-M-R II"), EvaluationStatus.VALID, null, mmr))));

		TransformResult result = importXml(exportOne(tc));

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(1, result.getErrors().size());
		Assert.assertEquals("Product", result.getErrors().get(0).getLocation());
		Assert.assertTrue(result.getErrors().get(0).getMessage().contains(MERCK_MVX));
	}

	@Test
	public void importFromFile_reportsAStreamThatIsNotATestCase() throws Exception {
		TransformResult result = importXml("this is not the XML you are looking for");

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(1, result.getErrors().size());
		Assert.assertEquals("File Format", result.getErrors().get(0).getLocation());
	}

	// --- Pre conditions ---

	@Test
	public void preImport_acceptsAnExportedTestCase() throws Exception {
		List<ErrorModel> errors = formatService.preImport(stream(exportOne(completeTestCase())));

		Assert.assertEquals(new ArrayList<ErrorModel>(), errors);
	}

	@Test
	public void preImport_reportsWhatTheSchemaRejects() throws Exception {
		String xml = exportOne(completeTestCase()).replace("<Name>Simple Test Case</Name>", "");

		List<ErrorModel> errors = formatService.preImport(stream(xml));

		Assert.assertFalse("a test case without a name is not schema valid", errors.isEmpty());
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

	private String exportOne(TestCase tc) throws ConfigurationException {
		ExportResult result = formatService.exportToFile(list(tc), exportConfig());
		Assert.assertEquals(1, result.getReader().size());
		return text(result.getReader().get(0).getIn());
	}

	private TransformResult importXml(String xml) throws Exception {
		return formatService.importFromFile(stream(xml), importAll());
	}

	private TestCase importOne(String xml) throws Exception {
		TransformResult result = importXml(xml);
		Assert.assertEquals("import reported " + messages(result.getErrors()), 0, result.getErrors().size());
		Assert.assertEquals(1, result.getTestCases().size());
		return result.getTestCases().get(0);
	}

	private InputStream stream(String xml) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(xml.getBytes("UTF-8"));
	}

	private List<String> names(ExportResult result) {
		List<String> names = new ArrayList<String>();
		for (ExportedFileStream file : result.getReader()) {
			names.add(file.getName());
		}
		return names;
	}

	private List<String> messages(List<ErrorModel> errors) {
		List<String> messages = new ArrayList<String>();
		for (ErrorModel error : errors) {
			messages.add(error.getLocation() + " : " + error.getMessage());
		}
		return messages;
	}

	/**
	 * Stands in for SimpleMetaDataService : a fresh instance per call, stamped with the moment
	 * of the import, holding the version passed at the given argument index if there is one.
	 */
	private Answer<MetaData> newMetaData(final Integer versionArgument) {
		return new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) {
				return createdMetaData(versionArgument == null ? "1.0"
						: (String) invocation.getArguments()[versionArgument.intValue()]);
			}
		};
	}
}
