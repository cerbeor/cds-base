package gov.nist.healthcare.cds.service.impl.persist;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import gov.nist.healthcare.cds.auth.domain.Account;
import gov.nist.healthcare.cds.auth.repo.AccountRepository;
import gov.nist.healthcare.cds.domain.SoftwareConfig;
import gov.nist.healthcare.cds.domain.TestPlan;
import gov.nist.healthcare.cds.domain.UserMetadata;
import gov.nist.healthcare.cds.domain.ValidationJob;
import gov.nist.healthcare.cds.domain.wrapper.Report;
import gov.nist.healthcare.cds.repositories.ReportRepository;
import gov.nist.healthcare.cds.repositories.SoftwareConfigRepository;
import gov.nist.healthcare.cds.repositories.TestPlanRepository;
import gov.nist.healthcare.cds.repositories.UserMetadataRepository;
import gov.nist.healthcare.cds.repositories.ValidationJobRepository;
import gov.nist.healthcare.cds.service.UserContact;

/**
 * Read-only examination of the database, used to build the list of people to
 * contact before any cleanup happens. Nothing here writes to the database.
 *
 * Ownership is resolved the same way {@link SimpleDatabaseCleanupService} resolves
 * it : a document belongs to the account whose username is stored in its 'user'
 * field ('initiator' for validation jobs).
 *
 * Every method takes a fresh snapshot of the database, so calling several of them
 * in a row re-reads the collections. Reports and validation jobs are read through
 * projections that only carry their owner, never the full documents.
 */
@Service
public class MongoExaminationService {

	private static final Logger logger = LoggerFactory.getLogger(MongoExaminationService.class);

	/** Number of years of inactivity after which a user is no longer considered active. */
	public static final int ACTIVITY_WINDOW_YEARS = 3;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TestPlanRepository testPlanRepository;

	@Autowired
	private SoftwareConfigRepository softwareConfigRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private ValidationJobRepository validationJobRepository;

	@Autowired
	private UserMetadataRepository userMetadataRepository;

	/**
	 * The outreach list : emails and organizations to contact to assert whether they
	 * want to keep their data. A user is on it when both are true :
	 *
	 * - they own something a cleanup would drop, that is at least one test plan,
	 *   software configuration, report or validation job ;
	 * - they made no API call in the last {@value #ACTIVITY_WINDOW_YEARS} years, so their
	 *   intent cannot be inferred from recent usage.
	 *
	 * Entries flagged with {@link UserContact#isOwnsSharedTestPlans()} should be handled
	 * first : their data is public or shared, so dropping it also impacts other users.
	 *
	 * The list is sorted by organization then email so it can be handed over as is.
	 */
	public List<UserContact> findUsersToContact() {
		return findUsersToContact(ACTIVITY_WINDOW_YEARS);
	}

	/**
	 * Same as {@link #findUsersToContact()} with an explicit inactivity window.
	 */
	public List<UserContact> findUsersToContact(int inactiveForYears) {
		Date cutoff = yearsAgo(inactiveForYears);

		List<UserContact> contacts = new ArrayList<>();
		for (UserContact contact : snapshot()) {
			if (contact.ownsData() && !isActiveSince(contact, cutoff)) {
				contacts.add(contact);
			}
		}

		logger.info("Examination: {} users to contact (owning data, inactive for {} years)",
				contacts.size(), inactiveForYears);
		return sorted(contacts);
	}

	/**
	 * Users owning at least one test plan that is public or shared with other users.
	 * Their data cannot be dropped without impacting somebody else.
	 */
	public List<UserContact> findOwnersOfSharedTestPlans() {
		List<UserContact> contacts = new ArrayList<>();
		for (UserContact contact : snapshot()) {
			if (contact.isOwnsSharedTestPlans()) {
				contacts.add(contact);
			}
		}

		logger.info("Examination: {} users own a public or shared test plan", contacts.size());
		return sorted(contacts);
	}

	/**
	 * Users who made at least one API call within the last {@value #ACTIVITY_WINDOW_YEARS} years.
	 */
	public List<UserContact> findRecentlyActiveUsers() {
		return findUsersActiveSince(yearsAgo(ACTIVITY_WINDOW_YEARS));
	}

	/**
	 * Users who made at least one API call on or after the given date.
	 */
	public List<UserContact> findUsersActiveSince(Date since) {
		List<UserContact> contacts = new ArrayList<>();
		for (UserContact contact : snapshot()) {
			if (isActiveSince(contact, since)) {
				contacts.add(contact);
			}
		}

		logger.info("Examination: {} users active since {}", contacts.size(), since);
		return sorted(contacts);
	}

	/**
	 * Users who never made a single API call since their account was created, either
	 * because they have no metadata document at all or because no call was ever recorded.
	 */
	public List<UserContact> findNeverActiveUsers() {
		List<UserContact> contacts = new ArrayList<>();
		for (UserContact contact : snapshot()) {
			if (contact.getLastApiCall() == null) {
				contacts.add(contact);
			}
		}

		logger.info("Examination: {} users never made an API call", contacts.size());
		return sorted(contacts);
	}

	/**
	 * Users owning neither a test plan nor a software configuration. They may still own
	 * reports or validation jobs : see {@link UserContact#ownsData()}.
	 */
	public List<UserContact> findUsersWithoutTestPlansNorSoftwareConfigs() {
		List<UserContact> contacts = new ArrayList<>();
		for (UserContact contact : snapshot()) {
			if (contact.getTestPlans() == 0 && contact.getSoftwareConfigs() == 0) {
				contacts.add(contact);
			}
		}

		logger.info("Examination: {} users own no test plan nor software configuration", contacts.size());
		return sorted(contacts);
	}

	/**
	 * Contact information for every account, whatever its activity or content.
	 */
	public List<UserContact> findAllUsers() {
		return sorted(snapshot());
	}

	/**
	 * Reads every collection once and builds one fully populated entry per account.
	 * Documents owned by a username with no matching account are ignored : there is no
	 * email nor organization to reach out to.
	 */
	private List<UserContact> snapshot() {
		Map<String, UserContact> byUsername = new LinkedHashMap<>();
		for (Account account : accountRepository.findAll()) {
			UserContact contact = new UserContact();
			contact.setUsername(account.getUsername());
			contact.setEmail(account.getEmail());
			contact.setOrganization(account.getOrganization());
			contact.setFullName(account.getFullName());
			byUsername.put(account.getUsername(), contact);
		}

		Map<String, Integer> testPlans = new HashMap<>();
		Map<String, Boolean> sharedTestPlans = new HashMap<>();
		for (TestPlan tp : testPlanRepository.findAll()) {
			increment(testPlans, tp.getUser());
			if (isShared(tp) && tp.getUser() != null) {
				sharedTestPlans.put(tp.getUser(), Boolean.TRUE);
			}
		}

		Map<String, Integer> softwareConfigs = new HashMap<>();
		for (SoftwareConfig sc : softwareConfigRepository.findAll()) {
			increment(softwareConfigs, sc.getUser());
		}

		Map<String, Integer> reports = new HashMap<>();
		for (Report report : reportRepository.reportOwners()) {
			increment(reports, report.getUser());
		}

		Map<String, Integer> validationJobs = new HashMap<>();
		for (ValidationJob job : validationJobRepository.jobInitiators()) {
			increment(validationJobs, job.getInitiator());
		}

		Map<String, Date> lastApiCalls = new HashMap<>();
		for (UserMetadata um : userMetadataRepository.findAll()) {
			if (um.getLastApiCall() != null) {
				lastApiCalls.put(um.getUsername(), um.getLastApiCall());
			}
		}

		for (Map.Entry<String, UserContact> entry : byUsername.entrySet()) {
			String username = entry.getKey();
			UserContact contact = entry.getValue();
			contact.setTestPlans(count(testPlans, username));
			contact.setSoftwareConfigs(count(softwareConfigs, username));
			contact.setReports(count(reports, username));
			contact.setValidationJobs(count(validationJobs, username));
			contact.setOwnsSharedTestPlans(sharedTestPlans.containsKey(username));
			contact.setLastApiCall(lastApiCalls.get(username));
		}

		return new ArrayList<>(byUsername.values());
	}

	private boolean isShared(TestPlan tp) {
		return tp.isPublic() || (tp.getViewers() != null && !tp.getViewers().isEmpty());
	}

	private boolean isActiveSince(UserContact contact, Date since) {
		return contact.getLastApiCall() != null && !contact.getLastApiCall().before(since);
	}

	private void increment(Map<String, Integer> counts, String username) {
		if (username == null) {
			return;
		}
		Integer count = counts.get(username);
		counts.put(username, count == null ? 1 : count + 1);
	}

	private int count(Map<String, Integer> counts, String username) {
		Integer count = counts.get(username);
		return count == null ? 0 : count;
	}

	private List<UserContact> sorted(List<UserContact> contacts) {
		Collections.sort(contacts, BY_ORGANIZATION_THEN_EMAIL);
		return contacts;
	}

	private Date yearsAgo(int years) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.YEAR, -years);
		return calendar.getTime();
	}

	/** Sorts by organization then email, missing values last, so the list reads as an outreach sheet. */
	private static final Comparator<UserContact> BY_ORGANIZATION_THEN_EMAIL = new Comparator<UserContact>() {
		@Override
		public int compare(UserContact left, UserContact right) {
			int byOrganization = compareNullsLast(left.getOrganization(), right.getOrganization());
			if (byOrganization != 0) {
				return byOrganization;
			}
			return compareNullsLast(left.getEmail(), right.getEmail());
		}

		private int compareNullsLast(String left, String right) {
			if (left == null) {
				return right == null ? 0 : 1;
			}
			if (right == null) {
				return -1;
			}
			return left.compareToIgnoreCase(right);
		}
	};
}
