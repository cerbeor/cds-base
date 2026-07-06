package gov.nist.healthcare.cds.repositories;


import gov.nist.healthcare.cds.domain.ValidationJob;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationJobRepository extends MongoRepository<ValidationJob, String>{

	List<ValidationJob> findByInitiator(String initiator);

}
