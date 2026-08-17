package gov.nist.healthcare.cds.service.transformation;

import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MERCK_MVX;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MMR_CVX;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.MMR_NAME;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.bytes;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.completeTestCase;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.createdMetaData;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.exportConfig;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.fixed;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.forecast;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.importAll;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.importLines;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.list;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.metaData;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.product;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.relativeToBirth;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.richTestCase;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.vaccination;
import static gov.nist.healthcare.cds.service.transformation.FormatServiceFixtures.vaccine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import gov.nist.healthcare.cds.domain.Injection;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
import gov.nist.healthcare.cds.domain.VaccineGroup;
import gov.nist.healthcare.cds.domain.VaccineMapping;
import gov.nist.healthcare.cds.domain.exception.ConfigurationException;
import gov.nist.healthcare.cds.domain.exception.ProductNotFoundException;
import gov.nist.healthcare.cds.domain.exception.VaccineNotFoundException;
import gov.nist.healthcare.cds.domain.wrapper.ExportResult;
import gov.nist.healthcare.cds.domain.wrapper.ImportConfig;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.domain.wrapper.ModelError;
import gov.nist.healthcare.cds.domain.wrapper.TransformResult;
import gov.nist.healthcare.cds.domain.wrapper.VaccineRef;
import gov.nist.healthcare.cds.domain.xml.ErrorModel;
import gov.nist.healthcare.cds.enumeration.DateType;
import gov.nist.healthcare.cds.enumeration.EvaluationReason;
import gov.nist.healthcare.cds.enumeration.EvaluationStatus;
import gov.nist.healthcare.cds.enumeration.Gender;
import gov.nist.healthcare.cds.enumeration.SerieStatus;
import gov.nist.healthcare.cds.repositories.VaccineGroupRepository;
import gov.nist.healthcare.cds.repositories.VaccineMappingRepository;
import gov.nist.healthcare.cds.repositories.VaccineRepository;
import gov.nist.healthcare.cds.service.DateService;
import gov.nist.healthcare.cds.service.MetaDataService;
import gov.nist.healthcare.cds.service.NameTranslationService;
import gov.nist.healthcare.cds.service.VaccineService;
import gov.nist.healthcare.cds.service.impl.transformation.CSSFormatServiceImpl;

/**
 * Import and export of the 'cdc' format : one xlsx spreadsheet holding one row per expected
 * forecast, with the columns of the CDC test case sheet. The column numbers below mirror the
 * ones the service uses, so a change of layout shows up here.
 */
public class CSSFormatServiceImplTest {

	// -- Column layout of the CDC sheet, as laid out by CSSFormatServiceImpl --
	private static final int UID = 0;
	private static final int NAME = 1;
	private static final int DOB = 2;
	private static final int GENDER = 3;
	private static final int SERIESTATUS = 7;
	private static final int START_EVENTS = 8;
	private static final int ADMIN_DATE = 0;
	private static final int VA_NAME = 1;
	private static final int CVX = 2;
	private static final int MVX = 3;
	private static final int EVAL = 4;
	private static final int EVAL_REASON = 5;
	private static final int EVENT_WIDTH = 6;
	private static final int DOSE_N = 50;
	private static final int EARLIEST = 51;
	private static final int RECOMMENDED = 52;
	private static final int PAST_DUE = 53;
	private static final int TARGET = 54;
	private static final int EVAL_DATE = 55;
	private static final int EVAL_TYPE = 56;
	private static final int DATE_ADD = 57;
	private static final int DATE_UP = 58;
	private static final int FORECAST_TYPE = 59;
	private static final int CHANGE = 60;
	private static final int VERSION = 61;
	private static final int DESCRIPTION = 62;

	@InjectMocks
	private CSSFormatServiceImpl formatService;

	@Mock
	private VaccineRepository vaccineRepository;

	@Mock
	private VaccineGroupRepository vaccineGrRepository;

	@Mock
	private VaccineMappingRepository vaccineMpRepository;

	@Mock
	private MetaDataService mdService;

	@Mock
	private VaccineService vaxService;

	@Mock
	private DateService dateService;

	@Mock
	private NameTranslationService transform;

	private Vaccine mmr;

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		mmr = vaccine(MMR_CVX, MMR_NAME);

		// stands in for SimpleMetaDataService : a fresh instance stamped with the import date
		Mockito.when(mdService.create(Mockito.anyBoolean())).thenAnswer(new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) {
				return createdMetaData("1.0");
			}
		});
		// the MMR cvx is a group of its own, and is known by that name on both sides
		Mockito.when(vaccineMpRepository.findMapping(MMR_CVX)).thenReturn(mapping(mmr, group(MMR_CVX, MMR_NAME)));
		Mockito.when(transform.nameToRep(MMR_NAME)).thenReturn(MMR_NAME);
		Mockito.when(vaxService.findGroup(MMR_NAME)).thenReturn(mmr);
		Mockito.when(vaxService.getVax(Mockito.any(VaccineRef.class), Mockito.anyBoolean())).thenReturn(mmr);
		Mockito.when(vaxService.isGroupOf(MMR_CVX, MMR_CVX)).thenReturn(true);
		Mockito.when(vaxService.asVaccine(Mockito.any(Injection.class))).thenReturn(mmr);
	}

	// --- Format ---

	@Test
	public void formatName_isCdc() {
		Assert.assertEquals("cdc", formatService.formatName());
	}

	// --- Export ---

	@Test
	public void exportToFile_writesASingleSpreadsheet() throws Exception {
		ExportResult result = formatService.exportToFile(list(completeTestCase()), exportConfig());

		Assert.assertEquals(1, result.getReader().size());
		Assert.assertEquals("cds-spreadsheet.xlsx", result.getReader().get(0).getName());
	}

	@Test
	public void exportToFile_writesAHeaderRowAndOneRowPerTestCase() throws Exception {
		TestCase second = completeTestCase();
		second.setName("Another Case");

		Sheet sheet = exportSheet(completeTestCase(), second);

		Assert.assertEquals(3, sheet.getLastRowNum() + 1);
		Assert.assertEquals("CDC_Test_ID", string(sheet, 0, UID));
		Assert.assertEquals("Vaccine_Group", string(sheet, 0, TARGET));
		Assert.assertEquals("General_Description", string(sheet, 0, DESCRIPTION));
		Assert.assertEquals("Simple Test Case", string(sheet, 1, NAME));
		Assert.assertEquals("Another Case", string(sheet, 2, NAME));
	}

	@Test
	public void exportToFile_writesTheTestCaseColumns() throws Exception {
		Sheet sheet = exportSheet(completeTestCase());

		Assert.assertEquals("TC-1", string(sheet, 1, UID));
		Assert.assertEquals("Simple Test Case", string(sheet, 1, NAME));
		Assert.assertEquals("A single MMR dose", string(sheet, 1, DESCRIPTION));
		Assert.assertEquals("F", string(sheet, 1, GENDER));
		Assert.assertEquals(date("01/01/2010"), date(sheet, 1, DOB));
		Assert.assertEquals(date("06/15/2012"), date(sheet, 1, EVAL_DATE));
		Assert.assertEquals("Evaluation", string(sheet, 1, EVAL_TYPE));
		Assert.assertEquals("Forecast", string(sheet, 1, FORECAST_TYPE));
		Assert.assertEquals("initial version", string(sheet, 1, CHANGE));
		Assert.assertEquals("1.0", string(sheet, 1, VERSION));
		Assert.assertEquals(date("01/01/2018"), date(sheet, 1, DATE_ADD));
		Assert.assertEquals(date("06/01/2018"), date(sheet, 1, DATE_UP));
	}

	@Test
	public void exportToFile_writesTheVaccinationColumns() throws Exception {
		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(
				vaccination(1, "02/01/2010", mmr, EvaluationStatus.VALID, null, mmr),
				vaccination(2, "03/01/2010", mmr, EvaluationStatus.INVALID, EvaluationReason.C, mmr))));

		Sheet sheet = exportSheet(tc);

		Assert.assertEquals(date("02/01/2010"), date(sheet, 1, START_EVENTS + ADMIN_DATE));
		Assert.assertEquals(MMR_NAME, string(sheet, 1, START_EVENTS + VA_NAME));
		Assert.assertEquals(MMR_CVX, string(sheet, 1, START_EVENTS + CVX));
		Assert.assertEquals("", string(sheet, 1, START_EVENTS + MVX));
		Assert.assertEquals("Valid", string(sheet, 1, START_EVENTS + EVAL));

		int second = START_EVENTS + EVENT_WIDTH;
		Assert.assertEquals(date("03/01/2010"), date(sheet, 1, second + ADMIN_DATE));
		Assert.assertEquals("Not Valid", string(sheet, 1, second + EVAL));
		Assert.assertEquals("Age: Too Young", string(sheet, 1, second + EVAL_REASON));
	}

	@Test
	public void exportToFile_writesTheManufacturerOfAdministredProducts() throws Exception {
		TestCase tc = completeTestCase();
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(vaccination(1, "02/01/2010",
				product("MMR-II", mmr, MERCK_MVX, "M-M-R II"),
				EvaluationStatus.VALID, null, mmr))));

		Sheet sheet = exportSheet(tc);

		Assert.assertEquals("M-M-R II", string(sheet, 1, START_EVENTS + VA_NAME));
		Assert.assertEquals(MMR_CVX, string(sheet, 1, START_EVENTS + CVX));
		Assert.assertEquals(MERCK_MVX, string(sheet, 1, START_EVENTS + MVX));
	}

	@Test
	public void exportToFile_writesTheForecastColumns() throws Exception {
		Sheet sheet = exportSheet(completeTestCase());

		Assert.assertEquals("Due", string(sheet, 1, SERIESTATUS));
		Assert.assertEquals("2", string(sheet, 1, DOSE_N));
		Assert.assertEquals(date("01/01/2011"), date(sheet, 1, EARLIEST));
		Assert.assertEquals(date("02/01/2011"), date(sheet, 1, RECOMMENDED));
		Assert.assertEquals(date("03/01/2011"), date(sheet, 1, PAST_DUE));
		Assert.assertEquals(MMR_NAME, string(sheet, 1, TARGET));
	}

	@Test
	public void exportToFile_writesOneRowPerForecastAndSuffixesTheExtraUids() throws Exception {
		TestCase tc = completeTestCase();
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(
				forecast(mmr, SerieStatus.D, "2", "01/01/2011", "02/01/2011", "03/01/2011"),
				forecast(mmr, SerieStatus.C, "3", null, null, null))));

		Sheet sheet = exportSheet(tc);

		Assert.assertEquals(3, sheet.getLastRowNum() + 1);
		Assert.assertEquals("TC-1", string(sheet, 1, UID));
		Assert.assertEquals("TC-1-2", string(sheet, 2, UID));
		Assert.assertEquals("Due", string(sheet, 1, SERIESTATUS));
		Assert.assertEquals("Complete", string(sheet, 2, SERIESTATUS));
	}

	@Test
	public void exportToFile_resolvesRelativeDatesBeforeWriting() throws Exception {
		final TestCase tc = completeTestCase();
		tc.setDateType(DateType.RELATIVE);
		tc.getEvents().get(0).setDate(relativeToBirth(1, 0, 0, 0));
		// the service hands the test case over to the date service, which fixes it in place
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) {
				tc.getEvents().get(0).setDate(fixed("01/01/2011"));
				return null;
			}
		}).when(dateService).toFixed(Mockito.same(tc), Mockito.any(LocalDate.class));

		Sheet sheet = exportSheet(tc);

		Mockito.verify(dateService).toFixed(Mockito.same(tc), Mockito.any(LocalDate.class));
		Assert.assertEquals(date("01/01/2011"), date(sheet, 1, START_EVENTS + ADMIN_DATE));
	}

	@Test
	public void exportToFile_leavesFixedDatedTestCasesAlone() throws Exception {
		exportSheet(completeTestCase());

		Mockito.verify(dateService, Mockito.never()).toFixed(Mockito.any(TestCase.class),
				Mockito.any(LocalDate.class));
	}

	@Test
	public void exportToFile_namesTheTargetAfterTheVaccineWhenItsCvxMapsToSeveralGroups() throws Exception {
		Vaccine mmrv = vaccine(MMR_CVX, "MMRV");
		Mockito.when(vaccineMpRepository.findMapping(MMR_CVX))
				.thenReturn(mapping(mmrv, group("03", "Measles"), group("21", "Varicella")));
		Mockito.when(transform.nameToRep("MMRV")).thenReturn("MMRV");

		Sheet sheet = exportSheet(completeTestCase());

		Assert.assertEquals("MMRV", string(sheet, 1, TARGET));
	}

	// --- Import ---

	@Test
	public void importFromFile_readsOneTestCasePerLine() throws Exception {
		TransformResult result = importRows(importAll(), row(), row("TC-2", "Second Case"));

		Assert.assertEquals(2, result.getTotalTC());
		Assert.assertEquals(2, result.getTestCases().size());
		Assert.assertTrue(messages(result.getErrors()).toString(), result.getErrors().isEmpty());
		Assert.assertEquals("TC-1", result.getTestCases().get(0).getUid());
		Assert.assertEquals("Second Case", result.getTestCases().get(1).getName());
	}

	@Test
	public void importFromFile_readsTheTestCaseColumns() throws Exception {
		TestCase tc = importOne(row());

		Assert.assertEquals("TC-1", tc.getUid());
		Assert.assertEquals("Simple Test Case", tc.getName());
		Assert.assertEquals("A single MMR dose", tc.getDescription());
		Assert.assertEquals(DateType.FIXED, tc.getDateType());
		Assert.assertEquals("06/15/2012", ((FixedDate) tc.getEvalDate()).getDateString());
		Assert.assertEquals(Gender.F, tc.getPatient().getGender());
		Assert.assertEquals("01/01/2010", ((FixedDate) tc.getPatient().getDob()).getDateString());
		Assert.assertEquals("Evaluation", tc.getEvaluationType());
		Assert.assertEquals("Forecast", tc.getForecastType());
		Assert.assertEquals(MMR_NAME, tc.getGroupTag());
	}

	@Test
	public void importFromFile_readsTheMetaDataColumns() throws Exception {
		TestCase tc = importOne(row());

		Assert.assertEquals("initial version", tc.getMetaData().getChangeLog());
		Assert.assertEquals(date("01/01/2018"), tc.getMetaData().getDateCreated());
		Assert.assertEquals(date("06/01/2018"), tc.getMetaData().getDateLastUpdated());
		Assert.assertEquals("2.0", tc.getMetaData().getVersion());
	}

	@Test
	public void importFromFile_readsTheVaccinationsAndTheirEvaluation() throws Exception {
		Map<Integer, Object> row = row();
		putEvent(row, 1, "03/01/2010", MMR_NAME, MMR_CVX, "", "Not Valid", "Age: Too Young");

		TestCase tc = importOne(row);

		Assert.assertEquals(2, tc.getEvents().size());
		VaccinationEvent first = (VaccinationEvent) tc.getEvents().get(0);
		Assert.assertEquals(0, first.getPosition());
		Assert.assertEquals("02/01/2010", ((FixedDate) first.getDate()).getDateString());
		Assert.assertEquals(mmr, first.getAdministred());
		Assert.assertEquals(EvaluationStatus.VALID, evaluationOf(first).getStatus());

		VaccinationEvent second = (VaccinationEvent) tc.getEvents().get(1);
		Assert.assertEquals(1, second.getPosition());
		Assert.assertEquals("03/01/2010", ((FixedDate) second.getDate()).getDateString());
		Assert.assertEquals(EvaluationStatus.INVALID, evaluationOf(second).getStatus());
		Assert.assertEquals(EvaluationReason.C, evaluationOf(second).getReason());
	}

	@Test
	public void importFromFile_relatesTheEvaluationToTheForecastTargetWhenTheVaccineBelongsToItsGroup()
			throws Exception {
		TestCase tc = importOne(row());

		Assert.assertEquals(mmr, evaluationOf((VaccinationEvent) tc.getEvents().get(0)).getRelatedTo());
	}

	@Test
	public void importFromFile_relatesTheEvaluationToTheAdministredVaccineOutsideOfTheTargetGroup()
			throws Exception {
		Vaccine hepB = vaccine("08", "Hep B");
		Mockito.when(vaxService.getVax(Mockito.any(VaccineRef.class), Mockito.anyBoolean())).thenReturn(hepB);
		Mockito.when(vaxService.isGroupOf("08", MMR_CVX)).thenReturn(false);
		Mockito.when(vaxService.asVaccine(hepB)).thenReturn(hepB);

		TestCase tc = importOne(row());

		Assert.assertEquals(hepB, evaluationOf((VaccinationEvent) tc.getEvents().get(0)).getRelatedTo());
	}

	@Test
	public void importFromFile_readsTheForecast() throws Exception {
		TestCase tc = importOne(row());

		Assert.assertEquals(1, tc.getForecast().size());
		ExpectedForecast forecast = tc.getForecast().get(0);
		Assert.assertEquals(SerieStatus.D, forecast.getSerieStatus());
		Assert.assertEquals("2", forecast.getDoseNumber());
		Assert.assertEquals("01/01/2011", ((FixedDate) forecast.getEarliest()).getDateString());
		Assert.assertEquals("02/01/2011", ((FixedDate) forecast.getRecommended()).getDateString());
		Assert.assertEquals("03/01/2011", ((FixedDate) forecast.getPastDue()).getDateString());
		Assert.assertEquals(mmr, forecast.getTarget());
	}

	@Test
	public void importFromFile_readsADoseNumberHeldAsANumber() throws Exception {
		Map<Integer, Object> row = row();
		row.put(DOSE_N, Double.valueOf(3));

		Assert.assertEquals("3", importOne(row).getForecast().get(0).getDoseNumber());
	}

	@Test
	public void importFromFile_readsOnlyTheSelectedLines() throws Exception {
		TransformResult result = importRows(importLines("1;3-4"),
				row("TC-1", "First"), row("TC-2", "Second"), row("TC-3", "Third"), row("TC-4", "Fourth"));

		Assert.assertEquals(3, result.getTotalTC());
		Assert.assertEquals(Arrays.asList("First", "Third", "Fourth"), names(result));
	}

	@Test
	public void importFromFile_stopsAtTheFirstEmptyLine() throws Exception {
		TransformResult result = importRows(importAll(),
				row("TC-1", "First"), new LinkedHashMap<Integer, Object>(), row("TC-3", "Third"));

		Assert.assertEquals(Arrays.asList("First"), names(result));
	}

	@Test
	public void importFromFile_readsTheSheetGivenByTheConfiguration() throws Exception {
		byte[] spreadsheet = spreadsheet(2, Arrays.asList(row("TC-2", "On the second sheet")));
		ImportConfig secondSheet = importAll();
		secondSheet.setPosition(2);

		TransformResult result = formatService.importFromFile(new ByteArrayInputStream(spreadsheet), secondSheet);

		Assert.assertEquals(Arrays.asList("On the second sheet"), names(result));
	}

	// --- Import errors ---

	@Test(expected = ConfigurationException.class)
	public void importFromFile_rejectsALineSelectionItCannotParse() throws Exception {
		importRows(importLines("one;two"), row());
	}

	@Test
	public void importFromFile_reportsALineMissingMandatoryData() throws Exception {
		Map<Integer, Object> incomplete = row();
		incomplete.remove(NAME);

		TransformResult result = importRows(importAll(), row(), incomplete);

		Assert.assertEquals(1, result.getTestCases().size());
		Assert.assertEquals(2, result.getTotalTC());
		Assert.assertEquals(1, result.getErrors().size());
		ErrorModel error = result.getErrors().get(0);
		Assert.assertEquals("TestCase Data", error.getLocation());
		Assert.assertEquals("the second data line", 2, error.getLine());
		Assert.assertEquals("the name column", NAME, error.getColumn());
	}

	@Test
	public void importFromFile_reportsAVaccineTheDatabaseDoesNotKnow() throws Exception {
		Mockito.when(vaxService.getVax(Mockito.any(VaccineRef.class), Mockito.anyBoolean()))
				.thenThrow(new VaccineNotFoundException(MMR_CVX));

		TransformResult result = importRows(importAll(), row());

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(1, result.getErrors().size());
		Assert.assertEquals("Vaccine", result.getErrors().get(0).getLocation());
		Assert.assertTrue(result.getErrors().get(0).getMessage().contains(MMR_CVX));
	}

	@Test
	public void importFromFile_reportsAProductTheDatabaseDoesNotKnow() throws Exception {
		Mockito.when(vaxService.getVax(Mockito.any(VaccineRef.class), Mockito.anyBoolean()))
				.thenThrow(new ProductNotFoundException(MMR_CVX, MERCK_MVX));

		Map<Integer, Object> row = row();
		row.put(START_EVENTS + MVX, MERCK_MVX);

		TransformResult result = importRows(importAll(), row);

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(1, result.getErrors().size());
		Assert.assertEquals("Product", result.getErrors().get(0).getLocation());
		Assert.assertTrue(result.getErrors().get(0).getMessage().contains(MERCK_MVX));
	}

	@Test
	public void importFromFile_returnsAnEmptyResultOnAStreamThatIsNotASpreadsheet() throws Exception {
		InputStream stream = new ByteArrayInputStream("this is not a spreadsheet".getBytes("UTF-8"));

		TransformResult result = formatService.importFromFile(stream, importAll());

		Assert.assertTrue(result.getTestCases().isEmpty());
		Assert.assertEquals(0, result.getTotalTC());
	}

	// --- Round trip ---

	@Test
	public void exportToFile_thenImportFromFile_keepsTheTestCase() throws Exception {
		ExportResult exported = formatService.exportToFile(list(completeTestCase()), exportConfig());
		InputStream spreadsheet = new ByteArrayInputStream(bytes(exported.getReader().get(0).getIn()));

		TransformResult result = formatService.importFromFile(spreadsheet, importAll());

		Assert.assertTrue(messages(result.getErrors()).toString(), result.getErrors().isEmpty());
		TestCase tc = result.getTestCases().get(0);
		Assert.assertEquals("TC-1", tc.getUid());
		Assert.assertEquals("Simple Test Case", tc.getName());
		Assert.assertEquals("A single MMR dose", tc.getDescription());
		Assert.assertEquals(Gender.F, tc.getPatient().getGender());
		Assert.assertEquals("01/01/2010", ((FixedDate) tc.getPatient().getDob()).getDateString());
		Assert.assertEquals("06/15/2012", ((FixedDate) tc.getEvalDate()).getDateString());
		Assert.assertEquals(1, tc.getEvents().size());
		Assert.assertEquals(mmr, ((VaccinationEvent) tc.getEvents().get(0)).getAdministred());
		Assert.assertEquals(EvaluationStatus.VALID,
				evaluationOf((VaccinationEvent) tc.getEvents().get(0)).getStatus());
		Assert.assertEquals(SerieStatus.D, tc.getForecast().get(0).getSerieStatus());
		Assert.assertEquals("2", tc.getForecast().get(0).getDoseNumber());
		Assert.assertEquals("01/01/2011", ((FixedDate) tc.getForecast().get(0).getEarliest()).getDateString());
	}

	/**
	 * The export writes the version as text while the import only reads it from a number cell,
	 * so a version does not survive a round trip : it comes back as the default 1.0.
	 */
	@Test
	public void exportToFile_thenImportFromFile_losesTheVersion() throws Exception {
		TestCase original = completeTestCase();
		original.setMetaData(metaData("7.3", "initial version"));
		ExportResult exported = formatService.exportToFile(list(original), exportConfig());
		InputStream spreadsheet = new ByteArrayInputStream(bytes(exported.getReader().get(0).getIn()));

		TransformResult result = formatService.importFromFile(spreadsheet, importAll());

		Assert.assertEquals("1.0", result.getTestCases().get(0).getMetaData().getVersion());
	}

	/**
	 * What the CDC layout costs, spelled out : the sheet has no column for a good part of a test
	 * case, so a round trip through it is lossy by construction. The list below is the whole of
	 * it for a fully populated test case, and anything new that goes missing shows up here.
	 *
	 * Only the losses are surprising, not their presence : this test is here so that the price
	 * of the format stays a known, reviewed list rather than a discovery made in production.
	 */
	@Test
	public void exportToFile_thenImportFromFile_dropsWhatTheCdcSheetHasNoColumnFor() throws Exception {
		Mockito.when(vaxService.getVax(Mockito.any(VaccineRef.class), Mockito.anyBoolean()))
				.thenAnswer(administredInjection());

		TestCase original = richTestCase();
		ExportResult exported = formatService.exportToFile(list(original), exportConfig());
		InputStream spreadsheet = new ByteArrayInputStream(bytes(exported.getReader().get(0).getIn()));

		TransformResult result = formatService.importFromFile(spreadsheet, importAll());

		Assert.assertTrue(messages(result.getErrors()).toString(), result.getErrors().isEmpty());
		Assert.assertEquals(Arrays.asList(
				"workflowTag: FINAL -> <none>",
				"tags: [regression, mmr] -> <none>",
				"metaData.version: 7.3 -> 1.0",
				"events[0].position: 1 -> 0",
				"events[0].doseNumber: 1 -> 0",
				"events[1].position: 2 -> 1",
				"events[1].doseNumber: 2 -> 0",
				"forecast[0].forecastReason: Series in progress -> <none>",
				"forecast[0].complete: 04/01/2011 -> <none>"),
				TestCaseDiff.differences(original, result.getTestCases().get(0)));
	}

	/** Resolves the administred injection from the cvx and mvx the sheet holds, as the real service does. */
	private Answer<Injection> administredInjection() {
		final Product mmrII = product("MMR-II", mmr, MERCK_MVX, "M-M-R II");
		return new Answer<Injection>() {
			@Override
			public Injection answer(InvocationOnMock invocation) {
				VaccineRef ref = (VaccineRef) invocation.getArguments()[0];
				return MERCK_MVX.equals(ref.getMvx()) ? mmrII : mmr;
			}
		};
	}

	// --- Pre conditions ---

	@Test
	public void preImport_reportsNothing() throws Exception {
		Assert.assertTrue(formatService.preImport(new ByteArrayInputStream(new byte[0])).isEmpty());
	}

	@Test
	public void preExport_acceptsARunnableTestCase() {
		Assert.assertTrue(formatService.preExport(completeTestCase()).isEmpty());
	}

	@Test
	public void preExport_reportsTheErrorsOfAnIncompleteTestCase() {
		TestCase tc = completeTestCase();
		tc.setRunnable(false);
		tc.setErrors(Arrays.asList(new ModelError("patient.dob", "Patient Date of Birth is required")));

		List<ErrorModel> errors = formatService.preExport(tc);

		Assert.assertEquals(1, errors.size());
		Assert.assertEquals("patient.dob", errors.get(0).getLocation());
		Assert.assertEquals("Patient Date of Birth is required", errors.get(0).getMessage());
	}

	// --- Helpers : the spreadsheet the service reads ---

	/** A complete CDC line : the spreadsheet counterpart of {@link FormatServiceFixtures#completeTestCase()}. */
	private Map<Integer, Object> row() {
		return row("TC-1", "Simple Test Case");
	}

	private Map<Integer, Object> row(String uid, String name) {
		Map<Integer, Object> row = new LinkedHashMap<Integer, Object>();
		row.put(UID, uid);
		row.put(NAME, name);
		row.put(DESCRIPTION, "A single MMR dose");
		row.put(DOB, date("01/01/2010"));
		row.put(GENDER, "F");
		row.put(EVAL_DATE, date("06/15/2012"));
		row.put(EVAL_TYPE, "Evaluation");
		row.put(FORECAST_TYPE, "Forecast");
		row.put(CHANGE, "initial version");
		row.put(VERSION, Double.valueOf(2));
		row.put(DATE_ADD, date("01/01/2018"));
		row.put(DATE_UP, date("06/01/2018"));

		row.put(SERIESTATUS, "Due");
		row.put(DOSE_N, "2");
		row.put(EARLIEST, date("01/01/2011"));
		row.put(RECOMMENDED, date("02/01/2011"));
		row.put(PAST_DUE, date("03/01/2011"));
		row.put(TARGET, MMR_NAME);

		putEvent(row, 0, "02/01/2010", MMR_NAME, MMR_CVX, "", "Valid", null);
		return row;
	}

	private void putEvent(Map<Integer, Object> row, int position, String administredOn, String name,
			String cvx, String mvx, String evaluation, String reason) {
		int start = START_EVENTS + position * EVENT_WIDTH;
		row.put(start + ADMIN_DATE, date(administredOn));
		row.put(start + VA_NAME, name);
		row.put(start + CVX, cvx);
		row.put(start + MVX, mvx);
		row.put(start + EVAL, evaluation);
		if (reason != null) {
			row.put(start + EVAL_REASON, reason);
		}
	}

	private TransformResult importRows(ImportConfig config, Map<Integer, Object>... rows) throws Exception {
		byte[] spreadsheet = spreadsheet(1, Arrays.asList(rows));
		return formatService.importFromFile(new ByteArrayInputStream(spreadsheet), config);
	}

	private TestCase importOne(Map<Integer, Object> row) throws Exception {
		TransformResult result = importRows(importAll(), row);
		Assert.assertTrue(messages(result.getErrors()).toString(), result.getErrors().isEmpty());
		Assert.assertEquals(1, result.getTestCases().size());
		return result.getTestCases().get(0);
	}

	/**
	 * A workbook whose sheet number 'sheet' holds the header row and the given lines. The
	 * sheets before it are left empty, so the position of the ImportConfig can be exercised.
	 */
	private byte[] spreadsheet(int sheet, List<Map<Integer, Object>> rows) throws IOException {
		XSSFWorkbook workbook = new XSSFWorkbook();
		for (int i = 1; i < sheet; i++) {
			workbook.createSheet("Empty " + i);
		}
		Sheet data = workbook.createSheet("TestCases");
		CellStyle dateStyle = workbook.createCellStyle();
		dateStyle.setDataFormat(workbook.createDataFormat().getFormat("mm/dd/yyyy"));

		formatService.createHeader(data.createRow(0));
		int r = 1;
		for (Map<Integer, Object> row : rows) {
			write(data.createRow(r++), row, dateStyle);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		workbook.write(out);
		workbook.close();
		return out.toByteArray();
	}

	/** The service reads the first cell to know where the lines stop, so it is always created. */
	private void write(Row row, Map<Integer, Object> cells, CellStyle dateStyle) {
		row.createCell(UID);
		for (Map.Entry<Integer, Object> entry : cells.entrySet()) {
			Cell cell = row.createCell(entry.getKey().intValue());
			Object value = entry.getValue();
			if (value instanceof java.util.Date) {
				cell.setCellValue((java.util.Date) value);
				cell.setCellStyle(dateStyle);
			} else if (value instanceof Double) {
				cell.setCellValue(((Double) value).doubleValue());
			} else {
				cell.setCellValue((String) value);
			}
		}
	}

	// --- Helpers : the spreadsheet the service writes ---

	private Sheet exportSheet(TestCase... testCases) throws Exception {
		ExportResult result = formatService.exportToFile(list(testCases), exportConfig());
		byte[] spreadsheet = bytes(result.getReader().get(0).getIn());
		Workbook workbook = new XSSFWorkbook(OPCPackage.open(new ByteArrayInputStream(spreadsheet)));
		return workbook.getSheetAt(0);
	}

	private String string(Sheet sheet, int row, int column) {
		Cell cell = sheet.getRow(row).getCell(column);
		Assert.assertNotNull("no cell at " + row + "," + column, cell);
		return cell.getStringCellValue();
	}

	private java.util.Date date(Sheet sheet, int row, int column) {
		Cell cell = sheet.getRow(row).getCell(column);
		Assert.assertNotNull("no cell at " + row + "," + column, cell);
		return cell.getDateCellValue();
	}

	// --- Helpers : the rest ---

	private VaccineGroup group(String cvx, String name) {
		VaccineGroup group = new VaccineGroup(cvx);
		group.setName(name);
		return group;
	}

	private VaccineMapping mapping(Vaccine vaccine, VaccineGroup... groups) {
		VaccineMapping mapping = new VaccineMapping();
		mapping.setVx(vaccine);
		mapping.setGroups(new HashSet<VaccineGroup>(Arrays.asList(groups)));
		return mapping;
	}

	private ExpectedEvaluation evaluationOf(VaccinationEvent event) {
		Assert.assertEquals(1, event.getEvaluations().size());
		return event.getEvaluations().iterator().next();
	}

	private List<String> names(TransformResult result) {
		List<String> names = new ArrayList<String>();
		for (TestCase tc : result.getTestCases()) {
			names.add(tc.getName());
		}
		return names;
	}

	private List<String> messages(List<ErrorModel> errors) {
		List<String> messages = new ArrayList<String>();
		for (ErrorModel error : errors) {
			messages.add(error.getLine() + ":" + error.getColumn() + " " + error.getLocation() + " : "
					+ error.getMessage());
		}
		return messages;
	}

	private static java.util.Date date(String mmddyyyy) {
		try {
			return new SimpleDateFormat("MM/dd/yyyy").parse(mmddyyyy);
		} catch (ParseException e) {
			throw new IllegalArgumentException(mmddyyyy, e);
		}
	}
}
