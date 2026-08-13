package gov.nist.healthcare.cds.service.impl.persist;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import gov.nist.healthcare.cds.auth.domain.Account;
import gov.nist.healthcare.cds.auth.domain.AccountPasswordReset;
import gov.nist.healthcare.cds.auth.repo.AccountPasswordResetRepository;
import gov.nist.healthcare.cds.auth.repo.AccountRepository;
import gov.nist.healthcare.cds.domain.TestCase;
import gov.nist.healthcare.cds.domain.TestCaseGroup;
import gov.nist.healthcare.cds.domain.TestPlan;
import gov.nist.healthcare.cds.domain.wrapper.Report;
import gov.nist.healthcare.cds.repositories.ReportRepository;
import gov.nist.healthcare.cds.repositories.SoftwareConfigRepository;
import gov.nist.healthcare.cds.repositories.TestCaseRepository;
import gov.nist.healthcare.cds.repositories.TestPlanRepository;
import gov.nist.healthcare.cds.repositories.UserMetadataRepository;
import gov.nist.healthcare.cds.repositories.ValidationJobRepository;
import gov.nist.healthcare.cds.service.DatabaseCleanupService;

@Service
public class SimpleDatabaseCleanupService implements DatabaseCleanupService {

	private static final Logger logger = LoggerFactory.getLogger(SimpleDatabaseCleanupService.class);

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountPasswordResetRepository accountPasswordResetRepository;

	@Autowired
	private TestPlanRepository testPlanRepository;

	@Autowired
	private TestCaseRepository testCaseRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private SoftwareConfigRepository softwareConfigRepository;

	@Autowired
	private ValidationJobRepository validationJobRepository;

	@Autowired
	private UserMetadataRepository userMetadataRepository;

	@Override
	public void cleanDatabase(Set<String> whitelistedUsernames, Set<String> whitelistedEmails) {
		List<Account> allAccounts = accountRepository.findAll();

		List<Account> accountsToDelete = allAccounts.stream()
				.filter(account -> !(whitelistedUsernames.contains(account.getUsername()) || whitelistedEmails.contains(account.getEmail())))
				.collect(Collectors.toList());

		Set<String> usernamesToDelete = accountsToDelete.stream()
				.map(Account::getUsername)
				.collect(Collectors.toSet());

		logger.info("Database cleanup: {} accounts to delete, {} whitelisted",
				accountsToDelete.size(), whitelistedUsernames.size());

		for (String username : usernamesToDelete) {
			deleteUserData(username);
		}

		cleanViewerLists(usernamesToDelete);

		accountRepository.delete(accountsToDelete);
		logger.info("Database cleanup complete");
	}

	private void deleteUserData(String username) {
		logger.info("Deleting data for user: {}", username);

		List<TestPlan> testPlans = testPlanRepository.findByUser(username);
		for (TestPlan tp : testPlans) {
			if (!tp.isPublic()) {
				deleteTestPlanCascade(tp);
			}
		}

		softwareConfigRepository.delete(softwareConfigRepository.findByUser(username));
		validationJobRepository.delete(validationJobRepository.findByInitiator(username));

		List<Report> remainingReports = reportRepository.findByUser(username);
		if (!remainingReports.isEmpty()) {
			reportRepository.delete(remainingReports);
		}

		if (userMetadataRepository.exists(username)) {
			userMetadataRepository.delete(username);
		}

		AccountPasswordReset passwordReset = accountPasswordResetRepository.findByUsername(username);
		if (passwordReset != null) {
			accountPasswordResetRepository.delete(passwordReset);
		}
	}

	private void deleteTestPlanCascade(TestPlan tp) {
		if (tp.getTestCases() != null) {
			for (TestCase tc : tp.getTestCases()) {
				deleteReportsForTestCase(tc);
			}
			testCaseRepository.delete(tp.getTestCases());
		}

		for (TestCaseGroup tcg : tp.getTestCaseGroups()) {
			if (tcg.getTestCases() != null) {
				for (TestCase tc : tcg.getTestCases()) {
					deleteReportsForTestCase(tc);
				}
				testCaseRepository.delete(tcg.getTestCases());
			}
		}

		testPlanRepository.delete(tp);
	}

	private void deleteReportsForTestCase(TestCase tc) {
		List<Report> reports = reportRepository.reportsForTestCase(tc.getId());
		if (!reports.isEmpty()) {
			reportRepository.delete(reports);
		}
	}

	private void cleanViewerLists(Set<String> deletedUsernames) {
		List<TestPlan> allTestPlans = testPlanRepository.findAll();
		for (TestPlan tp : allTestPlans) {
			List<String> viewers = tp.getViewers();
			if (viewers != null && viewers.removeAll(deletedUsernames)) {
				testPlanRepository.save(tp);
			}
		}
	}
}


