package gov.nist.healthcare.cds.service;

import java.util.Date;

/**
 * Contact information for a user identified by the MongoExaminationService,
 * to be used to reach out about the retention of their data.
 */
public class UserContact {

	private String username;
	private String email;
	private String organization;
	private String fullName;
	private Date lastApiCall;
	private int testPlans;
	private int softwareConfigs;
	private int reports;
	private int validationJobs;
	private boolean ownsSharedTestPlans;

	public UserContact() {

	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public Date getLastApiCall() {
		return lastApiCall;
	}

	public void setLastApiCall(Date lastApiCall) {
		this.lastApiCall = lastApiCall;
	}

	public int getTestPlans() {
		return testPlans;
	}

	public void setTestPlans(int testPlans) {
		this.testPlans = testPlans;
	}

	public int getSoftwareConfigs() {
		return softwareConfigs;
	}

	public void setSoftwareConfigs(int softwareConfigs) {
		this.softwareConfigs = softwareConfigs;
	}

	public int getReports() {
		return reports;
	}

	public void setReports(int reports) {
		this.reports = reports;
	}

	public int getValidationJobs() {
		return validationJobs;
	}

	public void setValidationJobs(int validationJobs) {
		this.validationJobs = validationJobs;
	}

	/**
	 * True when this user owns any test plan, software configuration, report or
	 * validation job : there is something of theirs that a cleanup would drop.
	 */
	public boolean ownsData() {
		return testPlans > 0 || softwareConfigs > 0 || reports > 0 || validationJobs > 0;
	}

	/**
	 * True when at least one of the test plans this user owns is public or shared :
	 * dropping their data would impact other users.
	 */
	public boolean isOwnsSharedTestPlans() {
		return ownsSharedTestPlans;
	}

	public void setOwnsSharedTestPlans(boolean ownsSharedTestPlans) {
		this.ownsSharedTestPlans = ownsSharedTestPlans;
	}

	@Override
	public String toString() {
		return username + " <" + email + "> (" + organization + ")";
	}
}
