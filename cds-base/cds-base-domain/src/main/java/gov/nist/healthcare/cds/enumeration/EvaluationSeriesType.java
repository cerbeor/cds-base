package gov.nist.healthcare.cds.enumeration;

/**
 * Provides the Series Type (Standard or Risk) where the Evaluation Status was determined.
 */
public enum EvaluationSeriesType {
	STANDARD("standard"), RISK("risk");

	private final String label;

	private EvaluationSeriesType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
