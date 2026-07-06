package gov.nist.healthcare.cds.service;

import java.util.Set;

public interface DatabaseCleanupService {

	void cleanDatabase(Set<String> whitelistedUsernames, Set<String> whitelistedEmails);

}
