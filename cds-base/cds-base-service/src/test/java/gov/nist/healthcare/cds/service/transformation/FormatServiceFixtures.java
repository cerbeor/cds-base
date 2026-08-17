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
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
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

/**
 * Test cases and building blocks shared by the {@link gov.nist.healthcare.cds.service.FormatService}
 * tests. The same test case is handed to every format so the three suites stay comparable.
 */
final class FormatServiceFixtures {

	static final String MMR_CVX = "03";
	static final String MMR_NAME = "MMR";
	static final String MERCK_MVX = "MSD";

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
		RelativeDate date = new RelativeDate();
		date.add(new RelativeDateRule(DatePosition.AFTER, years, months, weeks, days,
				new StaticDateReference(RelativeTo.DOB)));
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