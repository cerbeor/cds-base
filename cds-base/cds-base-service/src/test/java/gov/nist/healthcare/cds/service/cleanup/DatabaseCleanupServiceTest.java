package gov.nist.healthcare.cds.service.cleanup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import gov.nist.healthcare.cds.auth.domain.Account;
import gov.nist.healthcare.cds.auth.domain.AccountPasswordReset;
import gov.nist.healthcare.cds.auth.repo.AccountPasswordResetRepository;
import gov.nist.healthcare.cds.auth.repo.AccountRepository;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.TestCaseGroup;
import gov.nist.healthcare.cds.domain.TestPlan;
import gov.nist.healthcare.cds.domain.SoftwareConfig;
import gov.nist.healthcare.cds.domain.ValidationJob;
import gov.nist.healthcare.cds.repositories.ReportRepository;
import gov.nist.healthcare.cds.repositories.SoftwareConfigRepository;
import gov.nist.healthcare.cds.repositories.TestCaseRepository;
import gov.nist.healthcare.cds.repositories.TestPlanRepository;
import gov.nist.healthcare.cds.repositories.UserMetadataRepository;
import gov.nist.healthcare.cds.repositories.ValidationJobRepository;
import gov.nist.healthcare.cds.service.impl.persist.SimpleDatabaseCleanupService;

public class DatabaseCleanupServiceTest {

	@InjectMocks
	private SimpleDatabaseCleanupService cleanupService;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountPasswordResetRepository accountPasswordResetRepository;

	@Mock
	private TestPlanRepository testPlanRepository;

	@Mock
	private TestCaseRepository testCaseRepository;

	@Mock
	private ReportRepository reportRepository;

	@Mock
	private SoftwareConfigRepository softwareConfigRepository;

	@Mock
	private ValidationJobRepository validationJobRepository;

	@Mock
	private UserMetadataRepository userMetadataRepository;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	// --- Helpers ---

	private Account createAccount(String username, String email) {
		Account account = new Account();
		account.setId(username + "-id");
		account.setUsername(username);
		account.setEmail(email);
		return account;
	}

	private TestPlan createTestPlan(String id, String user, boolean isPublic) {
		TestPlan tp = new TestPlan();
		tp.setId(id);
		tp.setUser(user);
		tp.setPublic(isPublic);
		return tp;
	}

	private TestCase createTestCase(String id) {
		TestCase tc = new TestCase();
		tc.setId(id);
		return tc;
	}

	// --- Whitelisted accounts are unaffected ---

	@Test
	public void whitelistedByUsername_dataUntouched() {
		Account whitelisted = createAccount("admin", "admin@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(whitelisted));

		cleanupService.cleanDatabase(
				new HashSet<>(Arrays.asList("admin")),
				Collections.<String>emptySet()
		);

		Mockito.verify(testPlanRepository, Mockito.never()).findByUser("admin");
		Mockito.verify(accountRepository).delete(Collections.<Account>emptyList());
	}

	@Test
	public void whitelistedByEmail_dataUntouched() {
		Account whitelisted = createAccount("admin", "admin@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(whitelisted));

		cleanupService.cleanDatabase(
				Collections.<String>emptySet(),
				new HashSet<>(Arrays.asList("admin@test.com"))
		);

		Mockito.verify(testPlanRepository, Mockito.never()).findByUser("admin");
		Mockito.verify(accountRepository).delete(Collections.<Account>emptyList());
	}

	// --- Non-whitelisted private data is fully deleted ---

	@Test
	public void nonWhitelisted_privateTestPlanCascadeDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		TestCase tc1 = createTestCase("tc1");
		TestCase tc2 = createTestCase("tc2");
		TestPlan privatePlan = createTestPlan("tp1", "bob", false);
		privatePlan.setTestCases(Arrays.asList(tc1, tc2));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Arrays.asList(privatePlan));
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(true);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(reportRepository).deleteReportsForTestCase("tc1");
		Mockito.verify(reportRepository).deleteReportsForTestCase("tc2");
		Mockito.verify(reportRepository).deleteReportsForUser("bob");
		Mockito.verify(testCaseRepository).delete(Arrays.asList(tc1, tc2));
		Mockito.verify(testPlanRepository).delete(privatePlan);
		Mockito.verify(userMetadataRepository).delete("bob");
		Mockito.verify(accountRepository).delete(Arrays.asList(toDelete));
	}

	@Test
	public void nonWhitelisted_testCasesInGroupsCascadeDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		TestCase tc1 = createTestCase("tc1");
		TestCase tc2 = createTestCase("tc2");
		TestCaseGroup group = new TestCaseGroup();
		group.setId("grp1");
		group.setTestCases(Arrays.asList(tc1, tc2));

		TestPlan plan = createTestPlan("tp1", "bob", false);
		plan.setTestCaseGroups(Arrays.asList(group));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Arrays.asList(plan));
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(reportRepository).deleteReportsForTestCase("tc1");
		Mockito.verify(reportRepository).deleteReportsForTestCase("tc2");
		Mockito.verify(testCaseRepository).delete(Arrays.asList(tc1, tc2));
		Mockito.verify(testPlanRepository).delete(plan);
	}

	@Test
	public void nonWhitelisted_softwareConfigsDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		SoftwareConfig config = new SoftwareConfig();
		config.setId("sc1");
		List<SoftwareConfig> configs = Arrays.asList(config);

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(configs);
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(softwareConfigRepository).delete(configs);
	}

	@Test
	public void nonWhitelisted_validationJobsDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		ValidationJob job = new ValidationJob();
		job.setId("vj1");
		List<ValidationJob> jobs = Arrays.asList(job);

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(jobs);
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(validationJobRepository).delete(jobs);
	}

	@Test
	public void nonWhitelisted_orphanReportsDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(reportRepository).deleteReportsForUser("bob");
	}

	@Test
	public void nonWhitelisted_passwordResetDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		AccountPasswordReset reset = new AccountPasswordReset();
		Mockito.when(accountPasswordResetRepository.findByUsername("bob")).thenReturn(reset);

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(accountPasswordResetRepository).delete(reset);
	}

	// --- Public test plans are preserved ---

	@Test
	public void nonWhitelisted_publicTestPlanNotDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		TestPlan publicPlan = createTestPlan("tp-public", "bob", true);
		publicPlan.setTestCases(Arrays.asList(createTestCase("tc-pub")));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Arrays.asList(publicPlan));
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(testPlanRepository, Mockito.never()).delete(publicPlan);
		Mockito.verify(testCaseRepository, Mockito.never()).delete(Mockito.anyListOf(TestCase.class));
	}

	@Test
	public void nonWhitelisted_mixedPlans_onlyPrivateDeleted() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		TestCase tcPrivate = createTestCase("tc-priv");
		TestPlan privatePlan = createTestPlan("tp-priv", "bob", false);
		privatePlan.setTestCases(Arrays.asList(tcPrivate));

		TestPlan publicPlan = createTestPlan("tp-pub", "bob", true);

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Arrays.asList(privatePlan, publicPlan));
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(testPlanRepository).delete(privatePlan);
		Mockito.verify(testPlanRepository, Mockito.never()).delete(publicPlan);
	}

	// --- Viewer lists are cleaned ---

	@Test
	public void viewerListsCleaned_deletedUsernamesRemoved() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Account whitelisted = createAccount("admin", "admin@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete, whitelisted));

		TestPlan survivingPlan = createTestPlan("tp-surv", "admin", false);
		survivingPlan.setViewers(new java.util.ArrayList<>(Arrays.asList("bob", "charlie")));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(survivingPlan));

		cleanupService.cleanDatabase(
				new HashSet<>(Arrays.asList("admin")),
				Collections.<String>emptySet()
		);

		ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
		Mockito.verify(testPlanRepository).save(captor.capture());
		List<String> viewers = captor.getValue().getViewers();
		Assert.assertFalse(viewers.contains("bob"));
		Assert.assertTrue(viewers.contains("charlie"));
	}

	@Test
	public void viewerList_noDeletedUsers_notSaved() {
		Account toDelete = createAccount("bob", "bob@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(toDelete));

		TestPlan plan = createTestPlan("tp1", "admin", false);
		plan.setViewers(new java.util.ArrayList<>(Arrays.asList("charlie")));

		Mockito.when(testPlanRepository.findByUser("bob")).thenReturn(Collections.<TestPlan>emptyList());
		Mockito.when(softwareConfigRepository.findByUser("bob")).thenReturn(Collections.<SoftwareConfig>emptyList());
		Mockito.when(validationJobRepository.findByInitiator("bob")).thenReturn(Collections.<ValidationJob>emptyList());
		Mockito.when(userMetadataRepository.exists("bob")).thenReturn(false);
		Mockito.when(testPlanRepository.findAll()).thenReturn(Arrays.asList(plan));

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(testPlanRepository, Mockito.never()).save(Mockito.any(TestPlan.class));
	}

	// --- Edge cases ---

	@Test
	public void emptyDatabase_noErrors() {
		Mockito.when(accountRepository.findAll()).thenReturn(Collections.<Account>emptyList());
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(Collections.<String>emptySet(), Collections.<String>emptySet());

		Mockito.verify(accountRepository).delete(Collections.<Account>emptyList());
	}

	@Test
	public void allAccountsWhitelisted_nothingDeleted() {
		Account a1 = createAccount("admin", "admin@test.com");
		Account a2 = createAccount("hossam", "hossam@test.com");
		Mockito.when(accountRepository.findAll()).thenReturn(Arrays.asList(a1, a2));
		Mockito.when(testPlanRepository.findAll()).thenReturn(Collections.<TestPlan>emptyList());

		cleanupService.cleanDatabase(
				new HashSet<>(Arrays.asList("admin", "hossam")),
				Collections.<String>emptySet()
		);

		Mockito.verify(testPlanRepository, Mockito.never()).findByUser(Mockito.anyString());
		Mockito.verify(accountRepository).delete(Collections.<Account>emptyList());
	}
}
