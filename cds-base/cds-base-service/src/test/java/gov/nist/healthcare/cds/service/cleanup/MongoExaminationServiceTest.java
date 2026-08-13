package gov.nist.healthcare.cds.service.cleanup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
import gov.nist.healthcare.cds.service.impl.persist.MongoExaminationService;

public class MongoExaminationServiceTest {

	@InjectMocks
	private MongoExaminationService examinationService;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TestPlanRepository testPlanRepository;

	@Mock
	private SoftwareConfigRepository softwareConfigRepository;

	@Mock
	private ReportRepository reportRepository;

	@Mock
	private ValidationJobRepository validationJobRepository;

	@Mock
	private UserMetadataRepository userMetadataRepository;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	// --- Helpers ---

	private Account createAccount(String username, String email, String organization) {
		Account account = new Account();
		account.setId(username + "-id");
		account.setUsername(username);
		account.setEmail(email);
		account.setOrganization(organization);
		return account;
	}

	private TestPlan createTestPlan(String id, String user, boolean isPublic, String... viewers) {
		TestPlan tp = new TestPlan();
		tp.setId(id);
		tp.setUser(user);
		tp.setPublic(isPublic);
		tp.setViewers(new ArrayList<>(Arrays.asList(viewers)));
		return tp;
	}

	private SoftwareConfig createSoftwareConfig(String id, String user) {
		SoftwareConfig sc = new SoftwareConfig();
		sc.setId(id);
		sc.setUser(user);
		return sc;
	}

	private Report createReport(String id, String user) {
		Report report = new Report();
		report.setId(id);
		report.setUser(user);
		return report;
	}

	private ValidationJob createValidationJob(String id, String initiator) {
		ValidationJob job = new ValidationJob();
		job.setId(id);
		job.setInitiator(initiator);
		return job;
	}

	private UserMetadata createMetadata(String username, Date lastApiCall) {
		UserMetadata um = new UserMetadata();
		um.setUsername(username);
		um.setLastApiCall(lastApiCall);
		return um;
	}

	private Date yearsAgo(int years) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.YEAR, -years);
		return calendar.getTime();
	}

	private List<String> usernames(List<UserContact> contacts) {
		List<String> names = new ArrayList<>();
		for (UserContact contact : contacts) {
			names.add(contact.getUsername());
		}
		Collections.sort(names);
		return names;
	}

	private List<String> emails(List<UserContact> contacts) {
		List<String> emails = new ArrayList<>();
		for (UserContact contact : contacts) {
			emails.add(contact.getEmail());
		}
		return emails;
	}

	private UserContact byUsername(List<UserContact> contacts, String username) {
		for (UserContact contact : contacts) {
			if (username.equals(contact.getUsername())) {
				return contact;
			}
		}
		Assert.fail("no contact for " + username);
		return null;
	}

	// --- Owners of public or shared test plans ---

	@Test
	public void findOwnersOfSharedTestPlans_returnsPublicAndSharedOwners() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("pub", "pub@test.com", "NIST"),
				createAccount("shared", "shared@test.com", "CDC"),
				createAccount("private", "private@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "pub", true),
				createTestPlan("tp2", "shared", false, "someoneElse"),
				createTestPlan("tp3", "private", false)
		));

		List<UserContact> contacts = examinationService.findOwnersOfSharedTestPlans();

		Assert.assertEquals(Arrays.asList("pub", "shared"), usernames(contacts));
	}

	@Test
	public void findOwnersOfSharedTestPlans_carriesEmailAndOrganization() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("pub", "pub@test.com", "NIST")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "pub", true)
		));

		List<UserContact> contacts = examinationService.findOwnersOfSharedTestPlans();

		Assert.assertEquals(1, contacts.size());
		Assert.assertEquals("pub@test.com", contacts.get(0).getEmail());
		Assert.assertEquals("NIST", contacts.get(0).getOrganization());
	}

	// --- Recent activity ---

	@Test
	public void findRecentlyActiveUsers_keepsOnlyUsersWithinTheWindow() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("recent", "recent@test.com", "NIST"),
				createAccount("old", "old@test.com", "CDC"),
				createAccount("never", "never@test.com", "ACME")
		));
		Mockito.when(userMetadataRepository.findAll()).thenReturn(Arrays.asList(
				createMetadata("recent", yearsAgo(1)),
				createMetadata("old", yearsAgo(5)),
				createMetadata("never", null)
		));

		List<UserContact> contacts = examinationService.findRecentlyActiveUsers();

		Assert.assertEquals(Arrays.asList("recent"), usernames(contacts));
	}

	// --- Never active ---

	@Test
	public void findNeverActiveUsers_includesUsersWithoutMetadataAndWithoutApiCall() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("active", "active@test.com", "NIST"),
				createAccount("noCall", "nocall@test.com", "CDC"),
				createAccount("noMetadata", "nometadata@test.com", "ACME")
		));
		Mockito.when(userMetadataRepository.findAll()).thenReturn(Arrays.asList(
				createMetadata("active", yearsAgo(1)),
				createMetadata("noCall", null)
		));

		List<UserContact> contacts = examinationService.findNeverActiveUsers();

		Assert.assertEquals(Arrays.asList("noCall", "noMetadata"), usernames(contacts));
	}

	// --- No content at all ---

	@Test
	public void findUsersWithoutTestPlansNorSoftwareConfigs_excludesOwnersOfEither() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("hasTestPlan", "tp@test.com", "NIST"),
				createAccount("hasConfig", "sc@test.com", "CDC"),
				createAccount("hasNothing", "nothing@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "hasTestPlan", false)
		));
		Mockito.when(softwareConfigRepository.findAll()).thenReturn(Arrays.asList(
				createSoftwareConfig("sc1", "hasConfig")
		));

		List<UserContact> contacts = examinationService.findUsersWithoutTestPlansNorSoftwareConfigs();

		Assert.assertEquals(Arrays.asList("hasNothing"), usernames(contacts));
	}

	// --- The outreach list ---

	@Test
	public void findUsersToContact_keepsOwnersOfDataWhoAreNoLongerActive() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("dormantOwner", "dormant@test.com", "ACME"),
				createAccount("neverActiveOwner", "never@test.com", "ACME"),
				createAccount("activeOwner", "active@test.com", "NIST"),
				createAccount("dormantWithoutData", "empty@test.com", "CDC")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "dormantOwner", false),
				createTestPlan("tp2", "neverActiveOwner", false),
				createTestPlan("tp3", "activeOwner", false)
		));
		Mockito.when(userMetadataRepository.findAll()).thenReturn(Arrays.asList(
				createMetadata("dormantOwner", yearsAgo(5)),
				createMetadata("activeOwner", yearsAgo(1)),
				createMetadata("dormantWithoutData", yearsAgo(8))
		));

		List<UserContact> contacts = examinationService.findUsersToContact();

		Assert.assertEquals(Arrays.asList("dormantOwner", "neverActiveOwner"), usernames(contacts));
	}

	@Test
	public void findUsersToContact_countsSoftwareConfigsAsDataWorthKeeping() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("configOnly", "config@test.com", "ACME")
		));
		Mockito.when(softwareConfigRepository.findAll()).thenReturn(Arrays.asList(
				createSoftwareConfig("sc1", "configOnly"),
				createSoftwareConfig("sc2", "configOnly")
		));

		List<UserContact> contacts = examinationService.findUsersToContact();

		Assert.assertEquals(1, contacts.size());
		Assert.assertEquals("config@test.com", contacts.get(0).getEmail());
		Assert.assertEquals(0, contacts.get(0).getTestPlans());
		Assert.assertEquals(2, contacts.get(0).getSoftwareConfigs());
	}

	@Test
	public void findUsersToContact_countsReportsAndValidationJobsAsDataWorthKeeping() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("reportsOnly", "reports@test.com", "ACME"),
				createAccount("jobsOnly", "jobs@test.com", "CDC"),
				createAccount("nothing", "nothing@test.com", "NIST")
		));
		Mockito.when(reportRepository.reportOwners()).thenReturn(Arrays.asList(
				createReport("r1", "reportsOnly"),
				createReport("r2", "reportsOnly")
		));
		Mockito.when(validationJobRepository.jobInitiators()).thenReturn(Arrays.asList(
				createValidationJob("j1", "jobsOnly")
		));

		List<UserContact> contacts = examinationService.findUsersToContact();

		Assert.assertEquals(Arrays.asList("jobsOnly", "reportsOnly"), usernames(contacts));
		Assert.assertEquals(2, byUsername(contacts, "reportsOnly").getReports());
		Assert.assertEquals(0, byUsername(contacts, "reportsOnly").getValidationJobs());
		Assert.assertEquals(1, byUsername(contacts, "jobsOnly").getValidationJobs());
	}

	@Test
	public void findUsersToContact_ignoresReportsOwnedByADeletedAccount() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("known", "known@test.com", "ACME")
		));
		Mockito.when(reportRepository.reportOwners()).thenReturn(Arrays.asList(
				createReport("r1", "ghost"),
				createReport("r2", null)
		));

		Assert.assertTrue(examinationService.findUsersToContact().isEmpty());
	}

	@Test
	public void findUsersToContact_flagsOwnersOfSharedTestPlans() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("sharing", "sharing@test.com", "ACME"),
				createAccount("notSharing", "notsharing@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "sharing", false),
				createTestPlan("tp2", "sharing", true),
				createTestPlan("tp3", "notSharing", false)
		));

		List<UserContact> contacts = examinationService.findUsersToContact();

		Assert.assertEquals(2, contacts.size());
		UserContact sharing = byUsername(contacts, "sharing");
		Assert.assertTrue(sharing.isOwnsSharedTestPlans());
		Assert.assertEquals("sharing@test.com", sharing.getEmail());
		Assert.assertEquals(2, sharing.getTestPlans());
		Assert.assertFalse(byUsername(contacts, "notSharing").isOwnsSharedTestPlans());
	}

	@Test
	public void findUsersToContact_sortsByOrganizationThenEmail() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("c", "zoe@test.com", "NIST"),
				createAccount("a", "bob@test.com", "ACME"),
				createAccount("d", "orphan@test.com", null),
				createAccount("b", "alice@test.com", "NIST")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "a", false),
				createTestPlan("tp2", "b", false),
				createTestPlan("tp3", "c", false),
				createTestPlan("tp4", "d", false)
		));

		List<UserContact> contacts = examinationService.findUsersToContact();

		Assert.assertEquals(Arrays.asList("bob@test.com", "alice@test.com", "zoe@test.com", "orphan@test.com"),
				emails(contacts));
	}

	// --- Whitelist ---

	@Test
	public void findUsersToContact_dropsWhitelistedUsernamesAndEmails() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("admin", "admin@test.com", "NIST"),
				createAccount("partner", "partner@test.com", "CDC"),
				createAccount("dormant", "dormant@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "admin", false),
				createTestPlan("tp2", "partner", false),
				createTestPlan("tp3", "dormant", false)
		));

		List<UserContact> contacts = examinationService.findUsersToContact(
				new HashSet<>(Arrays.asList("admin")),
				new HashSet<>(Arrays.asList("partner@test.com"))
		);

		Assert.assertEquals(Arrays.asList("dormant"), usernames(contacts));
	}

	@Test
	public void findUsersToContact_emptyWhitelistsChangeNothing() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("dormant", "dormant@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "dormant", false)
		));

		List<UserContact> contacts = examinationService.findUsersToContact(
				Collections.<String>emptySet(),
				Collections.<String>emptySet()
		);

		Assert.assertEquals(Arrays.asList("dormant"), usernames(contacts));
	}

	@Test
	public void findUsersToContact_whitelistHonoursTheInactivityWindow() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("admin", "admin@test.com", "NIST"),
				createAccount("dormant", "dormant@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "admin", false),
				createTestPlan("tp2", "dormant", false)
		));
		Mockito.when(userMetadataRepository.findAll()).thenReturn(Arrays.asList(
				createMetadata("dormant", yearsAgo(4))
		));

		List<UserContact> contacts = examinationService.findUsersToContact(
				3, new HashSet<>(Arrays.asList("admin")), Collections.<String>emptySet());

		Assert.assertEquals(Arrays.asList("dormant"), usernames(contacts));
		Assert.assertTrue(examinationService.findUsersToContact(
				5, new HashSet<>(Arrays.asList("admin")), Collections.<String>emptySet()).isEmpty());
	}

	@Test
	public void findUsersToContact_honoursAnExplicitInactivityWindow() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("owner", "owner@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "owner", false)
		));
		Mockito.when(userMetadataRepository.findAll()).thenReturn(Arrays.asList(
				createMetadata("owner", yearsAgo(4))
		));

		Assert.assertEquals(1, examinationService.findUsersToContact(3).size());
		Assert.assertTrue(examinationService.findUsersToContact(5).isEmpty());
	}

	@Test
	public void noMatch_returnsEmptyList() {
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(
				createAccount("private", "private@test.com", "ACME")
		));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(
				createTestPlan("tp1", "private", false)
		));

		Assert.assertTrue(examinationService.findOwnersOfSharedTestPlans().isEmpty());
	}
}
