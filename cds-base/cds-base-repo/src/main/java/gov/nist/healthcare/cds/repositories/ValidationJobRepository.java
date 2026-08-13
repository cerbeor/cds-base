package gov.nist.healthcare.cds.repositories;


import gov.nist.healthcare.cds.domain.ValidationJob;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationJobRepository extends MongoRepository<ValidationJob, String>{

	List<ValidationJob> findByInitiator(String initiator);

	/** Every job, carrying only its initiator : used to count jobs per user without loading them. */
	@Query(value = "{ 'initiator' : { $exists : true } }", fields = "{ 'initiator' : 1 }")
	List<ValidationJob> jobInitiators();

}
