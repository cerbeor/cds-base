package gov.nist.healthcare.cds.service.transformation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import gov.nist.healthcare.cds.domain.Date;
import gov.nist.healthcare.cds.domain.DateReference;
import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.FixedDate;
import gov.nist.healthcare.cds.domain.Injection;
import gov.nist.healthcare.cds.domain.Manufacturer;
import gov.nist.healthcare.cds.domain.Patient;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.RelativeDate;
import gov.nist.healthcare.cds.domain.RelativeDateRule;
import gov.nist.healthcare.cds.domain.StaticDateReference;
import gov.nist.healthcare.cds.domain.Tag;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
import gov.nist.healthcare.cds.domain.VaccineDateReference;
import gov.nist.healthcare.cds.domain.wrapper.ExportConfig;
import gov.nist.healthcare.cds.domain.wrapper.ImportConfig;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.enumeration.DatePosition;
import gov.nist.healthcare.cds.enumeration.DateType;
import gov.nist.healthcare.cds.enumeration.EvaluationReason;
import gov.nist.healthcare.cds.enumeration.EvaluationStatus;
import gov.nist.healthcare.cds.enumeration.Gender;
import gov.nist.healthcare.cds.enumeration.RelativeTo;
import gov.nist.healthcare.cds.enumeration.SerieStatus;
import gov.nist.healthcare.cds.enumeration.WorkflowTag;

/**
 * Test cases and building blocks shared by the {@link gov.nist.healthcare.cds.service.FormatService}
 * tests. The same test case is handed to every format so the three suites stay comparable.
 */
final class FormatServiceFixtures {

	static final String MMR_CVX = "03";
	static final String MMR_NAME = "MMR";
	static final String MERCK_MVX = "MSD";

	/** Stands in for the 'now' a real MetaDataService stamps on an imported test case. */
	static final java.util.Date IMPORTED_ON = utilDate("09/30/2020");

	private FormatServiceFixtures() {
	}

	/**
	 * A complete, runnable test case using fixed dates only : one vaccination evaluated as
	 * valid, one forecast due with all its dates. Every format can export it as is.
	 */
	static TestCase completeTestCase() {
		Vaccine mmr = vaccine(MMR_CVX, MMR_NAME);

		TestCase tc = new TestCase();
		tc.setUid("TC-1");
		tc.setName("Simple Test Case");
		tc.setDescription("A single MMR dose");
		tc.setDateType(DateType.FIXED);
		tc.setEvalDate(fixed("06/15/2012"));
		tc.setGroupTag(MMR_NAME);
		tc.setEvaluationType("Evaluation");
		tc.setForecastType("Forecast");
		tc.setPatient(patient("01/01/2010", Gender.F));
		tc.setMetaData(metaData("1.0", "initial version"));
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(
				vaccination(1, "02/01/2010", mmr, EvaluationStatus.VALID, null, mmr))));
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(
				forecast(mmr, SerieStatus.D, "2", "01/01/2011", "02/01/2011", "03/01/2011"))));
		return tc;
	}

	/**
	 * A test case with every field a format could carry filled in with a distinctive value :
	 * two vaccinations, one of them a product, an evaluation reason, a forecast with all four
	 * dates and a reason, tags and a workflow tag. Handed to the round trip tests so that
	 * anything a format drops on the way shows up.
	 *
	 * Dates are fixed so that every format can export it as is.
	 */
	static TestCase richTestCase() {
		Vaccine mmr = vaccine(MMR_CVX, MMR_NAME);
		Product mmrII = product("MMR-II", mmr, MERCK_MVX, "M-M-R II");

		TestCase tc = new TestCase();
		tc.setUid("TC-42");
		tc.setName("Rich Test Case");
		tc.setDescription("Two doses, one of them a product");
		tc.setDateType(DateType.FIXED);
		tc.setEvalDate(fixed("06/15/2012"));
		tc.setGroupTag(MMR_NAME);
		tc.setEvaluationType("Evaluation");
		tc.setForecastType("Forecast");
		tc.setTags(new ArrayList<Tag>(Arrays.asList(new Tag("regression"), new Tag("mmr"))));
		tc.setWorkflowTag(WorkflowTag.FINAL);
		tc.setPatient(patient("01/01/2010", Gender.M));
		tc.setMetaData(metaData("7.3", "reviewed after the 2018 schedule"));

		VaccinationEvent first = vaccination(1, "02/01/2010", mmr, EvaluationStatus.VALID, null, mmr);
		first.setDoseNumber(1);
		VaccinationEvent second = vaccination(2, "03/01/2010", mmrII, EvaluationStatus.INVALID,
				EvaluationReason.C, mmr);
		second.setDoseNumber(2);
		tc.setEvents(new ArrayList<Event>(Arrays.<Event>asList(first, second)));

		ExpectedForecast forecast = forecast(mmr, SerieStatus.D, "3",
				"01/01/2011", "02/01/2011", "03/01/2011");
		forecast.setComplete(fixed("04/01/2011"));
		forecast.setForecastReason("Series in progress");
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(forecast)));
		return tc;
	}

	/**
	 * What {@link gov.nist.healthcare.cds.service.impl.persist.SimpleMetaDataService} hands an
	 * import : the version it was given, and the moment of the import as both dates.
	 */
	static MetaData createdMetaData(String version) {
		MetaData md = new MetaData();
		md.setVersion(version);
		md.setImported(true);
		md.setDateCreated(IMPORTED_ON);
		md.setDateLastUpdated(IMPORTED_ON);
		return md;
	}

	static Vaccine vaccine(String cvx, String name) {
		Vaccine vaccine = new Vaccine();
		vaccine.setCvx(cvx);
		vaccine.setName(name);
		vaccine.setDetails(name);
		return vaccine;
	}

	static Product product(String code, Vaccine vx, String mvx, String name) {
		Manufacturer mx = new Manufacturer();
		mx.setMvx(mvx);
		mx.setName(mvx);

		Product product = new Product();
		product.setCode(code);
		product.setVx(vx);
		product.setMx(mx);
		product.setName(name);
		return product;
	}

	static FixedDate fixed(String mmddyyyy) {
		return new FixedDate(mmddyyyy);
	}

	/** A date expressed as an offset from the patient's date of birth. */
	static RelativeDate relativeToBirth(int years, int months, int weeks, int days) {
		return relative(years, months, weeks, days, new StaticDateReference(RelativeTo.DOB));
	}

	/** A date expressed as an offset from the vaccination event holding the given id. */
	static RelativeDate relativeToVaccination(int years, int months, int weeks, int days, int eventId) {
		return relative(years, months, weeks, days, new VaccineDateReference(eventId));
	}

	/**
	 * The six argument constructor of RelativeDateRule drops the number of weeks, so the rule is
	 * built through its setters to make sure the fixture holds what it was asked for.
	 */
	private static RelativeDate relative(int years, int months, int weeks, int days, DateReference relativeTo) {
		RelativeDateRule rule = new RelativeDateRule();
		rule.setPosition(DatePosition.AFTER);
		rule.setYear(years);
		rule.setMonth(months);
		rule.setWeek(weeks);
		rule.setDay(days);
		rule.setRelativeTo(relativeTo);

		RelativeDate date = new RelativeDate();
		date.add(rule);
		return date;
	}

	static Patient patient(String dob, Gender gender) {
		Patient patient = new Patient();
		patient.setDob(fixed(dob));
		patient.setGender(gender);
		return patient;
	}

	static MetaData metaData(String version, String changeLog) {
		MetaData md = new MetaData();
		md.setVersion(version);
		md.setChangeLog(changeLog);
		md.setImported(false);
		md.setDateCreated(utilDate("01/01/2018"));
		md.setDateLastUpdated(utilDate("06/01/2018"));
		return md;
	}

	static VaccinationEvent vaccination(int position, String date, Injection administred,
			EvaluationStatus status, EvaluationReason reason, Vaccine relatedTo) {
		ExpectedEvaluation evaluation = new ExpectedEvaluation();
		evaluation.setStatus(status);
		evaluation.setReason(reason);
		evaluation.setRelatedTo(relatedTo);

		VaccinationEvent event = new VaccinationEvent();
		event.setPosition(position);
		event.setDate(fixed(date));
		event.setAdministred(administred);
		event.setEvaluations(new HashSet<ExpectedEvaluation>(Arrays.asList(evaluation)));
		return event;
	}

	static ExpectedForecast forecast(Vaccine target, SerieStatus status, String doseNumber,
			String earliest, String recommended, String pastDue) {
		ExpectedForecast forecast = new ExpectedForecast();
		forecast.setTarget(target);
		forecast.setSerieStatus(status);
		forecast.setDoseNumber(doseNumber);
		forecast.setEarliest(nullable(earliest));
		forecast.setRecommended(nullable(recommended));
		forecast.setPastDue(nullable(pastDue));
		return forecast;
	}

	static ExportConfig exportConfig() {
		return new ExportConfig();
	}

	/** Import every test case of the first sheet. */
	static ImportConfig importAll() {
		ImportConfig config = new ImportConfig();
		config.setPosition(1);
		config.setAll(true);
		return config;
	}

	/** Import only the lines of the first sheet listed as '1;3-4'. */
	static ImportConfig importLines(String lines) {
		ImportConfig config = new ImportConfig();
		config.setPosition(1);
		config.setAll(false);
		config.setLines(lines);
		return config;
	}

	static List<TestCase> list(TestCase... testCases) {
		return new ArrayList<TestCase>(Arrays.asList(testCases));
	}

	static byte[] bytes(InputStream in) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("cannot read the exported stream", e);
		}
	}

	static String text(InputStream in) {
		return new String(bytes(in), Charset.forName("UTF-8"));
	}

	private static Date nullable(String mmddyyyy) {
		return mmddyyyy == null ? null : fixed(mmddyyyy);
	}

	private static java.util.Date utilDate(String mmddyyyy) {
		try {
			return new SimpleDateFormat("MM/dd/yyyy").parse(mmddyyyy);
		} catch (ParseException e) {
			throw new IllegalArgumentException(mmddyyyy, e);
		}
	}
}