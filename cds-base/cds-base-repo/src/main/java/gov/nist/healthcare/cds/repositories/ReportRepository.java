package gov.nist.healthcare.cds.repositories;

import gov.nist.healthcare.cds.domain.wrapper.Report;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface ReportRepository extends MongoRepository<Report, String>{ 
	
	@Query("{ 'tc' : ?0 }")
	public List<Report> reportsForTestCase(String tcId);
	
	@Query("{ 'tc' : ?0 , 'user' : ?1 }")
	public List<Report> reportsForTestCase(String tcId, String user);
	
	public List<Report> findByUser(String user);

	/** Every report, carrying only its owner : used to count reports per user without loading them. */
	@Query(value = "{ 'user' : { $exists : true } }", fields = "{ 'user' : 1 }")
	public List<Report> reportOwners();

	/**
	 * Removes reports server-side and returns the number deleted. Nothing is mapped back into
	 * Report, which matters because the LocalDate fields of stored reports cannot be read by this
	 * version of Spring Data (no JSR-310 converters), so any query returning Report blows up.
	 */
	@Query(value = "{ 'tc' : ?0 }", delete = true)
	public Long deleteReportsForTestCase(String tcId);

	@Query(value = "{ 'user' : ?0 }", delete = true)
	public Long deleteReportsForUser(String user);
}
