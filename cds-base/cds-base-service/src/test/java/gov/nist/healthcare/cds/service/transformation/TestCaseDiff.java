package gov.nist.healthcare.cds.service.transformation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import gov.nist.healthcare.cds.domain.Date;
import gov.nist.healthcare.cds.domain.DateReference;
import gov.nist.healthcare.cds.domain.Event;
import gov.nist.healthcare.cds.domain.ExpectedEvaluation;
import gov.nist.healthcare.cds.domain.ExpectedForecast;
import gov.nist.healthcare.cds.domain.FixedDate;
import gov.nist.healthcare.cds.domain.Injection;
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
import gov.nist.healthcare.cds.domain.wrapper.MetaData;

/**
 * Compares the test case handed to a {@link gov.nist.healthcare.cds.service.FormatService} with
 * the one that comes back after a round trip through it, and reports every value the trip
 * changed or dropped. A format that loses nothing produces an empty list.
 *
 * Only what a test case is made of is compared. Left out on purpose :
 *
 * - id, user and testPlan : where the test case is stored, not what it says ;
 * - runnable and errors : the outcome of validating it, recomputed on the way in ;
 * - metaData.imported : provenance, which every import deliberately turns on.
 */
final class TestCaseDiff {

	private static final String NONE = "<none>";

	private final List<String> differences = new ArrayList<String>();

	private TestCaseDiff() {
	}

	/** Every difference between the two test cases, as 'path: before -> after' lines. */
	static List<String> differences(TestCase before, TestCase after) {
		TestCaseDiff diff = new TestCaseDiff();
		diff.compare(before, after);
		return diff.differences;
	}

	private void compare(TestCase before, TestCase after) {
		compare("name", before.getName(), after.getName());
		compare("uid", before.getUid(), after.getUid());
		compare("description", before.getDescription(), after.getDescription());
		compare("dateType", before.getDateType(), after.getDateType());
		compare("evaluationType", before.getEvaluationType(), after.getEvaluationType());
		compare("forecastType", before.getForecastType(), after.getForecastType());
		compare("groupTag", before.getGroupTag(), after.getGroupTag());
		compare("workflowTag", before.getWorkflowTag(), after.getWorkflowTag());
		compare("tags", tags(before.getTags()), tags(after.getTags()));
		compare("evalDate", date(before.getEvalDate()), date(after.getEvalDate()));

		comparePatients(before.getPatient(), after.getPatient());
		compareMetaData(before.getMetaData(), after.getMetaData());
		compareEvents(before.getEvents(), after.getEvents());
		compareForecasts(before.getForecast(), after.getForecast());
	}

	private void comparePatients(Patient before, Patient after) {
		if (before == null || after == null) {
			compare("patient", before == null ? null : "set", after == null ? null : "set");
			return;
		}
		compare("patient.gender", before.getGender(), after.getGender());
		compare("patient.dob", date(before.getDob()), date(after.getDob()));
	}

	private void compareMetaData(MetaData before, MetaData after) {
		if (before == null || after == null) {
			compare("metaData", before == null ? null : "set", after == null ? null : "set");
			return;
		}
		compare("metaData.version", before.getVersion(), after.getVersion());
		compare("metaData.changeLog", before.getChangeLog(), after.getChangeLog());
		compare("metaData.dateCreated", day(before.getDateCreated()), day(after.getDateCreated()));
		compare("metaData.dateLastUpdated", day(before.getDateLastUpdated()), day(after.getDateLastUpdated()));
	}

	private void compareEvents(List<Event> before, List<Event> after) {
		int common = compareSizes("events", before, after);
		for (int i = 0; i < common; i++) {
			String path = "events[" + i + "]";
			Event beforeEvent = before.get(i);
			Event afterEvent = after.get(i);
			compare(path + ".date", date(beforeEvent.getDate()), date(afterEvent.getDate()));

			if (!(beforeEvent instanceof VaccinationEvent) || !(afterEvent instanceof VaccinationEvent)) {
				compare(path + ".type", beforeEvent.getClass().getSimpleName(),
						afterEvent.getClass().getSimpleName());
				continue;
			}
			VaccinationEvent beforeVaccination = (VaccinationEvent) beforeEvent;
			VaccinationEvent afterVaccination = (VaccinationEvent) afterEvent;
			compare(path + ".position", beforeVaccination.getPosition(), afterVaccination.getPosition());
			compare(path + ".doseNumber", beforeVaccination.getDoseNumber(), afterVaccination.getDoseNumber());
			compare(path + ".administred", injection(beforeVaccination.getAdministred()),
					injection(afterVaccination.getAdministred()));
			compare(path + ".evaluations", evaluations(beforeVaccination.getEvaluations()),
					evaluations(afterVaccination.getEvaluations()));
		}
	}

	private void compareForecasts(List<ExpectedForecast> before, List<ExpectedForecast> after) {
		int common = compareSizes("forecast", before, after);
		for (int i = 0; i < common; i++) {
			String path = "forecast[" + i + "]";
			ExpectedForecast beforeForecast = before.get(i);
			ExpectedForecast afterForecast = after.get(i);
			compare(path + ".target", injection(beforeForecast.getTarget()), injection(afterForecast.getTarget()));
			compare(path + ".serieStatus", beforeForecast.getSerieStatus(), afterForecast.getSerieStatus());
			compare(path + ".doseNumber", beforeForecast.getDoseNumber(), afterForecast.getDoseNumber());
			compare(path + ".forecastReason", beforeForecast.getForecastReason(),
					afterForecast.getForecastReason());
			compare(path + ".earliest", date(beforeForecast.getEarliest()), date(afterForecast.getEarliest()));
			compare(path + ".recommended", date(beforeForecast.getRecommended()),
					date(afterForecast.getRecommended()));
			compare(path + ".pastDue", date(beforeForecast.getPastDue()), date(afterForecast.getPastDue()));
			compare(path + ".complete", date(beforeForecast.getComplete()), date(afterForecast.getComplete()));
		}
	}

	/** Reports a size mismatch and returns how many entries can still be compared one to one. */
	private int compareSizes(String path, List<?> before, List<?> after) {
		int beforeSize = before == null ? 0 : before.size();
		int afterSize = after == null ? 0 : after.size();
		compare(path + ".size", beforeSize, afterSize);
		return Math.min(beforeSize, afterSize);
	}

	private void compare(String path, Object before, Object after) {
		String left = render(before);
		String right = render(after);
		if (!left.equals(right)) {
			differences.add(path + ": " + left + " -> " + right);
		}
	}

	// --- Rendering : every compared value is turned into a readable string first ---

	private static String render(Object value) {
		return value == null ? NONE : value.toString();
	}

	private static String tags(List<Tag> tags) {
		if (tags == null || tags.isEmpty()) {
			return NONE;
		}
		List<String> texts = new ArrayList<String>();
		for (Tag tag : tags) {
			texts.add(tag.getText());
		}
		return texts.toString();
	}

	private static String date(Date date) {
		if (date == null) {
			return NONE;
		}
		if (date instanceof FixedDate) {
			return ((FixedDate) date).getDateString();
		}
		if (date instanceof RelativeDate) {
			List<String> rules = new ArrayList<String>();
			for (RelativeDateRule rule : ((RelativeDate) date).getRules()) {
				rules.add(rule.getPosition() + " " + rule.getYear() + "y" + rule.getMonth() + "m"
						+ rule.getWeek() + "w" + rule.getDay() + "d of " + reference(rule.getRelativeTo()));
			}
			return "relative" + rules;
		}
		return date.getClass().getSimpleName();
	}

	private static String reference(DateReference reference) {
		if (reference instanceof StaticDateReference) {
			return String.valueOf(((StaticDateReference) reference).getId());
		}
		if (reference instanceof VaccineDateReference) {
			return "VACCINATION#" + ((VaccineDateReference) reference).getId();
		}
		return NONE;
	}

	private static String day(java.util.Date date) {
		return date == null ? NONE : new SimpleDateFormat("yyyy-MM-dd").format(date);
	}

	private static String injection(Injection injection) {
		if (injection == null) {
			return NONE;
		}
		if (injection instanceof Product) {
			Product product = (Product) injection;
			return "cvx=" + product.getCvx() + " mvx=" + product.getMvx() + " name=" + product.getName();
		}
		Vaccine vaccine = (Vaccine) injection;
		return "cvx=" + vaccine.getCvx() + " name=" + vaccine.getName();
	}

	/** Evaluations are held in a set, so they are sorted to compare like with like. */
	private static String evaluations(Set<ExpectedEvaluation> evaluations) {
		if (evaluations == null || evaluations.isEmpty()) {
			return NONE;
		}
		List<String> rendered = new ArrayList<String>();
		for (ExpectedEvaluation evaluation : evaluations) {
			rendered.add(evaluation.getStatus() + "/" + evaluation.getReason() + "/"
					+ injection(evaluation.getRelatedTo()));
		}
		Collections.sort(rendered);
		return rendered.toString();
	}
}
