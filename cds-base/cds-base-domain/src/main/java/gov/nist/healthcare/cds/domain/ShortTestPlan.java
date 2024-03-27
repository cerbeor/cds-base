package gov.nist.healthcare.cds.domain;

import gov.nist.healthcare.cds.domain.wrapper.MetaData;

import java.util.List;

public class ShortTestPlan {
	String id;
	String name;
	String description;
	MetaData metaData;
	String user;
	boolean isPublic;
	List<String> viewers;
	int nbTestCases;
	boolean archived;

	public ShortTestPlan() {
	}

	public ShortTestPlan(TestPlan tp) {
		this(
			tp.getId(),
			tp.getName(),
			tp.getDescription(),
			tp.getMetaData(),
			tp.getUser(),
			tp.getViewers(),
			tp.isArchived(),
			tp.isPublic(),
			tp.getNumberOfTestCases()
		);
	}

	public ShortTestPlan(
			String id,
			String name,
			String description,
			MetaData metaData,
			String user,
			List<String> viewers,
			boolean archived,
			boolean isPublic,
			int nbTestCases
	) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.metaData = metaData;
		this.user = user;
		this.viewers = viewers;
		this.archived = archived;
		this.isPublic = isPublic;
		this.nbTestCases = nbTestCases;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public MetaData getMetaData() {
		return metaData;
	}

	public void setMetaData(MetaData metaData) {
		this.metaData = metaData;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public List<String> getViewers() {
		return viewers;
	}

	public void setViewers(List<String> viewers) {
		this.viewers = viewers;
	}

	public int getNbTestCases() {
		return nbTestCases;
	}

	public void setNbTestCases(int nbTestCases) {
		this.nbTestCases = nbTestCases;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean aPublic) {
		isPublic = aPublic;
	}

	public boolean isArchived() {
		return archived;
	}

	public void setArchived(boolean archived) {
		this.archived = archived;
	}
}
