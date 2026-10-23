package gov.nist.healthcare.cds.service.transformation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import gov.nist.healthcare.cds.domain.Date;
import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.FixedDate;
import gov.nist.healthcare.cds.domain.Manufacturer;
import gov.nist.healthcare.cds.domain.Patient;
import gov.nist.healthcare.cds.domain.Product;
import gov.nist.healthcare.cds.domain.RelativeDate;
import gov.nist.healthcare.cds.domain.RelativeDateRule;
import gov.nist.healthcare.cds.domain.StaticDateReference;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.VaccinationEvent;
import gov.nist.healthcare.cds.domain.Vaccine;
import gov.nist.healthcare.cds.domain.VaccineDateReference;
import gov.nist.healthcare.cds.domain.wrapper.MetaData;
import gov.nist.healthcare.cds.enumeration.DatePosition;
import gov.nist.healthcare.cds.enumeration.DateType;
import gov.nist.healthcare.cds.enumeration.EvaluationReason;
import gov.nist.healthcare.cds.enumeration.EvaluationStatus;
import gov.nist.healthcare.cds.enumeration.Gender;
import gov.nist.healthcare.cds.enumeration.RelativeTo;
import gov.nist.healthcare.cds.enumeration.SerieStatus;

/**
 * Shared builders for the NIST format tests. Everything is deterministic so the
 * exported XML can be compared against a golden file.
 */
public final class NISTFormatTestFixtures {

	public static final String CVX_DTAP = "20";
	public static final String CVX_HEPB = "08";
	public static final String MVX_SKB = "SKB";

	private NISTFormatTestFixtures() {
	}

	public static Vaccine vaccine(String cvx, String name) {
		Vaccine v = new Vaccine();
		v.setCvx(cvx);
		v.setName(name);
		v.setDetails(name + " details");
		return v;
	}

	public static Product product(String code, Vaccine vx, String mvx, String mxName, String name) {
		Manufacturer mx = new Manufacturer();
		mx.setMvx(mvx);
		mx.setName(mxName);
		Product p = new Product();
		p.setCode(code);
		p.setVx(vx);
		p.setMx(mx);
		p.setName(name);
		return p;
	}

	public static Vaccine dtap() {
		return vaccine(CVX_DTAP, "DTaP");
	}

	public static Vaccine hepb() {
		return vaccine(CVX_HEPB, "Hep B, adolescent or pediatric");
	}

	public static Product infanrix() {
		return product("20-SKB", dtap(), MVX_SKB, "GlaxoSmithKline", "Infanrix");
	}

	public static MetaData metaData() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		sdf.setTimeZone(java.util.TimeZone.getDefault());
		MetaData md = new MetaData();
		md.setVersion("1.0");
		md.setImported(true);
		md.setDateCreated(sdf.parse("2018-01-01"));
		md.setDateLastUpdated(sdf.parse("2018-06-15"));
		return md;
	}

	public static RelativeDate relativeTo(RelativeTo reference, DatePosition position, int years, int months,
			int weeks, int days) {
		RelativeDate rd = new RelativeDate();
		RelativeDateRule rule = new RelativeDateRule();
		rule.setPosition(position);
		rule.setYear(years);
		rule.setMonth(months);
		rule.setWeek(weeks);
		rule.setDay(days);
		rule.setRelativeTo(new StaticDateReference(reference));
		rd.add(rule);
		return rd;
	}

	public static RelativeDate relativeToVaccination(int eventId, DatePosition position, int years, int months,
			int weeks, int days) {
		RelativeDate rd = new RelativeDate();
		RelativeDateRule rule = new RelativeDateRule();
		rule.setPosition(position);
		rule.setYear(years);
		rule.setMonth(months);
		rule.setWeek(weeks);
		rule.setDay(days);
		rule.setRelativeTo(new VaccineDateReference(eventId));
		rd.add(rule);
		return rd;
	}

	/**
	 * A fully populated, fixed-date test case: 2 events (generic + product), 1
	 * forecast. Event positions are 0-based, matching what FITS actually
	 * exports (see the corpus under src/test/resources/nist/corpus).
	 */
	public static TestCase fixedDateTestCase() throws Exception {
		TestCase tc = new TestCase();
		tc.setName("Fixed Date Test Case");
		tc.setDescription("A complete fixed date test case");
		tc.setDateType(DateType.FIXED);
		tc.setGroupTag("GROUP-A");
		tc.setUid("TC-0001");
		tc.setEvalDate(new FixedDate("06/01/2018"));
		tc.setMetaData(metaData());

		Patient p = new Patient();
		p.setGender(Gender.F);
		p.setDob(new FixedDate("01/15/2016"));
		tc.setPatient(p);

		VaccinationEvent e1 = new VaccinationEvent();
		e1.setPosition(0);
		e1.setDate(new FixedDate("03/15/2016"));
		e1.setAdministred(dtap());
		e1.setEvaluations(evaluations(evaluation(dtap(), EvaluationStatus.VALID, null)));

		VaccinationEvent e2 = new VaccinationEvent();
		e2.setPosition(1);
		e2.setDate(new FixedDate("05/15/2016"));
		e2.setAdministred(infanrix());
		e2.setEvaluations(evaluations(evaluation(dtap(), EvaluationStatus.INVALID, EvaluationReason.D)));

		tc.setEvents(new ArrayList<Event>(Arrays.asList(e1, e2)));

		ExpectedForecast ef = new ExpectedForecast();
		ef.setTarget(dtap());
		ef.setSerieStatus(SerieStatus.D);
		ef.setForecastReason("Due now");
		ef.setDoseNumber("3");
		ef.setEarliest(new FixedDate("07/15/2016"));
		ef.setRecommended(new FixedDate("08/15/2016"));
		ef.setPastDue(new FixedDate("09/15/2016"));
		ef.setComplete(new FixedDate("10/15/2016"));
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(ef)));
		return tc;
	}

	/** A fully populated relative-date test case. */
	public static TestCase relativeDateTestCase() throws Exception {
		TestCase tc = new TestCase();
		tc.setName("Relative Date Test Case");
		tc.setDescription("A complete relative date test case");
		tc.setDateType(DateType.RELATIVE);
		tc.setGroupTag("GROUP-B");
		tc.setUid("TC-0002");
		tc.setEvalDate(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 2, 0, 0, 0));
		tc.setMetaData(metaData());

		Patient p = new Patient();
		p.setGender(Gender.M);
		p.setDob(relativeTo(RelativeTo.EVALDATE, DatePosition.BEFORE, 2, 0, 0, 0));
		tc.setPatient(p);

		VaccinationEvent e1 = new VaccinationEvent();
		e1.setPosition(0);
		e1.setDate(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 0, 2, 0, 0));
		e1.setAdministred(hepb());
		e1.setEvaluations(evaluations(evaluation(hepb(), EvaluationStatus.VALID, null)));

		VaccinationEvent e2 = new VaccinationEvent();
		e2.setPosition(1);
		e2.setDate(relativeToVaccination(0, DatePosition.AFTER, 0, 0, 4, 3));
		e2.setAdministred(hepb());
		e2.setEvaluations(evaluations(evaluation(hepb(), EvaluationStatus.SUBSTANDARD, EvaluationReason.F)));

		tc.setEvents(new ArrayList<Event>(Arrays.asList(e1, e2)));

		ExpectedForecast ef = new ExpectedForecast();
		ef.setTarget(hepb());
		ef.setSerieStatus(SerieStatus.L);
		ef.setForecastReason("Due later");
		ef.setDoseNumber("2");
		ef.setEarliest(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 0, 6, 0, 0));
		ef.setRecommended(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 0, 7, 0, 0));
		ef.setPastDue(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 0, 8, 0, 0));
		ef.setComplete(relativeTo(RelativeTo.DOB, DatePosition.AFTER, 1, 0, 0, 0));
		tc.setForecast(new ArrayList<ExpectedForecast>(Arrays.asList(ef)));
		return tc;
	}

	/** Minimal test case : no group, no uid, no events, no forecasts. */
	public static TestCase minimalTestCase() throws Exception {
		TestCase tc = new TestCase();
		tc.setName("Minimal");
		tc.setDateType(DateType.FIXED);
		tc.setEvalDate(new FixedDate("06/01/2018"));
		tc.setMetaData(metaData());
		Patient p = new Patient();
		p.setGender(Gender.M);
		p.setDob(new FixedDate("01/15/2016"));
		tc.setPatient(p);
		return tc;
	}

	public static ExpectedEvaluation evaluation(Vaccine relatedTo, EvaluationStatus status, EvaluationReason reason) {
		ExpectedEvaluation ee = new ExpectedEvaluation();
		ee.setRelatedTo(relatedTo);
		ee.setStatus(status);
		ee.setReason(reason);
		return ee;
	}

	public static Set<ExpectedEvaluation> evaluations(ExpectedEvaluation... evals) {
		return new LinkedHashSet<ExpectedEvaluation>(Arrays.asList(evals));
	}

	public static List<Date> datesOf(ExpectedForecast ef) {
		return Arrays.asList(ef.getEarliest(), ef.getRecommended(), ef.getPastDue(), ef.getComplete());
	}

	public static String read(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int n;
		while ((n = is.read(buffer)) != -1) {
			baos.write(buffer, 0, n);
		}
		return new String(baos.toByteArray(), StandardCharsets.UTF_8);
	}

	/** Strips the trailing whitespace of every line and normalizes line endings. */
	public static String normalize(String xml) {
		StringBuilder sb = new StringBuilder();
		for (String line : xml.replace("\r\n", "\n").split("\n")) {
			String trimmed = line.replaceAll("\\s+$", "");
			if (!trimmed.isEmpty()) {
				sb.append(trimmed).append('\n');
			}
		}
		return sb.toString();
	}
}
