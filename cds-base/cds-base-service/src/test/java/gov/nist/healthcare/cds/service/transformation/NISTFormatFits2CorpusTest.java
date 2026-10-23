package gov.nist.healthcare.cds.service.transformation;

import static gov.nist.healthcare.cds.service.transformation.NISTFormatTestFixtures.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
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
 * Forward-compatibility regression over the corpus actually produced by
 * FITS v2 (<code>src/test/resources/nist/fits2</code>, 61 files exported by
 * <code>gov.nist.hit.fits.service.file.NISTFormatServiceImpl</code>).
 *
 * The companion {@link NISTFormatCorpusTest} runs the same three checks over
 * the legacy corpus and guards backward compatibility ; this one guards the
 * other direction : whatever FITS v2 writes must be readable here, and
 * re-exporting it must not lose anything.
 *
 * These 61 documents carry the same wire shape as the legacy corpus except for
 * the MetaData date serialization, so they are expected to pass today. The
 * genuinely new parts of the v2 format -- seriesType, changeLog, Tags,
 * WorkflowTag, ForecastType, EvaluationType -- are covered separately by
 * {@link NISTFormatFits2FeaturesTest}.
 */
@RunWith(Parameterized.class)
public class NISTFormatFits2CorpusTest {

	/** CVX -> name, harvested from the FITS v2 corpus. */
	private static final Map<String, String> CVX = new LinkedHashMap<String, String>();
	static {
		CVX.put("03", "MMR");
		CVX.put("10", "IPV");
		CVX.put("17", "Hib, unspecified formulation");
		CVX.put("21", "varicella");
		CVX.put("45", "Hep B, unspecified formulation");
		CVX.put("85", "Hep A, unspecified formulation");
		CVX.put("89", "polio, unspecified formulation");
		CVX.put("107", "DTaP, unspecified formulation");
		CVX.put("108", "meningococcal ACWY, unspecified formulation");
		CVX.put("109", "pneumococcal, unspecified formulation");
		CVX.put("113", "Td (adult), 5 Lf tetanus toxoid, preservative free, adsorbed");
		CVX.put("115", "Tdap");
		CVX.put("122", "rotavirus, unspecified formulation");
		CVX.put("137", "HPV, unspecified formulation");
		CVX.put("139", "Td(adult) unspecified formulation");
		CVX.put("152", "Pneumococcal Conjugate, unspecified formulation");
		CVX.put("187", "zoster recombinant");
		CVX.put("188", "zoster, unspecified formulation");
		CVX.put("215", "Pneumococcal conjugate PCV15, polysaccharide CRM197 conjugate, adjuvant, PF");
	}

	private static final String SHINGRIX_CVX = "187";
	private static final String SHINGRIX_MVX = "SKB";

	@Mock
	private VaccineRepository vaccineRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private MetaDataService mdService;

	private NISTFormatServiceImpl service;

	private final String fileName;

	public NISTFormatFits2CorpusTest(String fileName) {
		this.fileName = fileName;
	}

	@Parameters(name = "{0}")
	public static List<Object[]> corpus() throws Exception {
		URL dir = NISTFormatFits2CorpusTest.class.getResource("/nist/fits2");
		assertNotNull("missing FITS v2 corpus directory /nist/fits2", dir);
		File[] files = new File(dir.toURI()).listFiles(new java.io.FilenameFilter() {
			@Override
			public boolean accept(File d, String name) {
				return name.endsWith(".xml");
			}
		});
		assertNotNull("FITS v2 corpus directory is not readable", files);
		assertTrue("FITS v2 corpus is empty", files.length > 0);

		List<String> names = new ArrayList<String>();
		for (File f : files) {
			names.add(f.getName());
		}
		Collections.sort(names);

		List<Object[]> params = new ArrayList<Object[]>();
		for (String name : names) {
			params.add(new Object[] { name });
		}
		return params;
	}

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		service = new NISTFormatServiceImpl();
		inject(service, "vaccineRepository", vaccineRepository);
		inject(service, "productRepository", productRepository);
		inject(service, "mdService", mdService);

		when(vaccineRepository.findOne(anyString())).thenAnswer(new Answer<Vaccine>() {
			@Override
			public Vaccine answer(InvocationOnMock invocation) throws Throwable {
				String cvx = (String) invocation.getArguments()[0];
				String name = CVX.get(cvx);
				return name == null ? null : vaccine(cvx, name);
			}
		});

		when(productRepository.getProduct(anyString(), anyString())).thenAnswer(new Answer<Product>() {
			@Override
			public Product answer(InvocationOnMock invocation) throws Throwable {
				String mvx = (String) invocation.getArguments()[0];
				String cvx = (String) invocation.getArguments()[1];
				if (SHINGRIX_MVX.equals(mvx) && SHINGRIX_CVX.equals(cvx)) {
					return product("187-SKB", vaccine(cvx, CVX.get(cvx)), mvx, "GlaxoSmithKline", "SHINGRIX");
				}
				return null;
			}
		});

		when(mdService.create(anyBoolean(), anyString())).thenAnswer(new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) throws Throwable {
				MetaData md = stampedMetaData();
				md.setVersion((String) invocation.getArguments()[1]);
				return md;
			}
		});
		when(mdService.create(anyBoolean())).thenAnswer(new Answer<MetaData>() {
			@Override
			public MetaData answer(InvocationOnMock invocation) throws Throwable {
				return stampedMetaData();
			}
		});
	}

	// ------------------------------------------------------------------

	/** Every document FITS v2 writes must pass the schema shipped here. */
	@Test
	public void validatesAgainstTheShippedSchema() throws Exception {
		List<ErrorModel> errors = service.preImport(stream(corpusFile(fileName)));
		assertNotNull("schema could not be loaded", errors);
		assertTrue(fileName + " : " + messages(errors), errors.isEmpty());
	}

	/** ... and must import into a usable domain test case. */
	@Test
	public void importsWithoutError() throws Exception {
		TransformResult result = service.importFromFile(stream(corpusFile(fileName)), new ImportConfig());
		assertTrue(fileName + " : " + messages(result.getErrors()), result.getErrors().isEmpty());
		assertEquals(fileName + " : expected exactly one test case", 1, result.getTestCases().size());

		TestCase tc = result.getTestCases().get(0);
		assertNotNull(fileName + " : name", tc.getName());
		assertFalse(fileName + " : name is blank", tc.getName().trim().isEmpty());
		assertNotNull(fileName + " : dateType", tc.getDateType());
		assertNotNull(fileName + " : evalDate", tc.getEvalDate());
		assertNotNull(fileName + " : patient", tc.getPatient());
		assertNotNull(fileName + " : patient gender", tc.getPatient().getGender());
		assertNotNull(fileName + " : patient dob", tc.getPatient().getDob());
	}

	/** Importing then re-exporting a v2 document must reproduce it. */
	@Test
	public void reExportReproducesTheOriginalDocument() throws Exception {
		String original = corpusFile(fileName);
		TransformResult result = service.importFromFile(stream(original), new ImportConfig());
		assertTrue(fileName + " : " + messages(result.getErrors()), result.getErrors().isEmpty());

		String reExported = read(service.export(result.getTestCases().get(0), new ExportConfig()));
		assertEquals(fileName + " : re-export differs from the FITS v2 original",
				normalize(canonicalize(original)), normalize(canonicalize(reExported)));
	}

	/** Every vaccine and product referenced by the v2 corpus must resolve. */
	@Test
	public void allReferencedCodesResolve() throws Exception {
		TransformResult result = service.importFromFile(stream(corpusFile(fileName)), new ImportConfig());
		assertTrue(fileName + " : " + messages(result.getErrors()), result.getErrors().isEmpty());

		TestCase tc = result.getTestCases().get(0);
		if (tc.getEvents() != null) {
			for (Event e : tc.getEvents()) {
				VaccinationEvent ve = (VaccinationEvent) e;
				assertNotNull(fileName + " : unresolved administred vaccine", ve.getAdministred());
				assertNotNull(fileName + " : administred without cvx", ve.getAdministred().getCvx());
			}
		}
		for (ExpectedForecast ef : tc.getForecast()) {
			assertNotNull(fileName + " : forecast without target", ef.getTarget());
			assertNotNull(fileName + " : forecast target without cvx", ef.getTarget().getCvx());
		}
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static MetaData stampedMetaData() {
		MetaData md = new MetaData();
		md.setImported(true);
		md.setDateCreated(new java.util.Date());
		md.setDateLastUpdated(new java.util.Date());
		return md;
	}

	private static void inject(Object target, String field, Object value) throws Exception {
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static String corpusFile(String name) throws Exception {
		InputStream is = NISTFormatFits2CorpusTest.class.getResourceAsStream("/nist/fits2/" + name);
		assertNotNull("missing FITS v2 corpus file " + name, is);
		return read(is);
	}

	/**
	 * Same normalization as {@link NISTFormatCorpusTest} : import regenerates
	 * the MetaData dates rather than preserving them, and empty elements may be
	 * written either as &lt;X&gt;&lt;/X&gt; or &lt;X/&gt;.
	 */
	private static String canonicalize(String xml) {
		String out = xml.replaceAll("<dateCreated>[^<]*</dateCreated>", "<dateCreated/>");
		out = out.replaceAll("<dateLastUpdated>[^<]*</dateLastUpdated>", "<dateLastUpdated/>");
		out = out.replaceAll("<([A-Za-z][A-Za-z0-9]*)></\\1>", "<$1/>");
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
