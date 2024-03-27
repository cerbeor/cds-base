package gov.nist.healthcare.cds.repositories;

import com.mongodb.*;
import com.mongodb.AggregationOptions;
import gov.nist.healthcare.cds.domain.ShortTestPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ShortTestPlanRepository {
	@Autowired
	MongoTemplate mongoTemplate;


	private List<ShortTestPlan> getTestPlans(BasicDBObject matchStage, boolean populateNumberOfTestCases) {
		DBCollection col = mongoTemplate.getCollection("testPlan");
		BasicDBObject project = new BasicDBObject();
		project.put("_id", "$_id");
		if(populateNumberOfTestCases) {
			BasicDBObject nestedTestCasesSum = new BasicDBObject(
					"$sum",
					new BasicDBObject(
							"$map",
							new BasicDBObject()
									.append(
											"input", "$testCaseGroups"
									).append(
											"in", new BasicDBObject("$size", "$$this.testCases")
									)
					)
			);
			BasicDBList addList =  new BasicDBList();
			BasicDBObject testCasesSize = new BasicDBObject("$size", "$testCases");
			addList.add(nestedTestCasesSum);
			addList.add(testCasesSize);
			project.put("nbTestCases", new BasicDBObject("$add", addList));
		}
		project.put("name", "$name");
		project.put("description", "$description");
		project.put("metaData", "$metaData");
		project.put("user", "$user");
		project.put("isPublic", "$isPublic");
		project.put("viewers", "$viewers");
		project.put("archived", "$archived");
		BasicDBObject projectStage = new BasicDBObject("$project", project);

		List<DBObject> pipeline = Arrays.asList(
				matchStage,
				projectStage
		);

		List<ShortTestPlan> testPlans = new ArrayList<>();
		try(Cursor cursor = col.aggregate(pipeline, AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).allowDiskUse(true).batchSize(1000000000).build())) {
			while(cursor.hasNext()) {
				testPlans.add(mongoTemplate.getConverter().read(ShortTestPlan.class, cursor.next()));
				if(populateNumberOfTestCases) {

				}
			}
			return testPlans;
		}
	}

	public List<ShortTestPlan> getUserNotArchivedTestPlans(String user) {
		BasicDBObject match = new BasicDBObject();
		BasicDBList andList = new BasicDBList();
		andList.add(new BasicDBObject("user", user));
		BasicDBList orList = new BasicDBList();
		orList.add(new BasicDBObject("archived", false));
		orList.add(new BasicDBObject("archived", null));
		andList.add(new BasicDBObject("$or", orList));
		match.put("$and", andList);
		BasicDBObject matchStage = new BasicDBObject("$match", match);
		return this.getTestPlans(matchStage, true);
	}

	public List<ShortTestPlan> getUserArchivedTestPlans(String user) {
		BasicDBObject match = new BasicDBObject();
		BasicDBList andList = new BasicDBList();
		andList.add(new BasicDBObject("user", user));
		andList.add(new BasicDBObject("archived", true));
		match.put("$and", andList);
		BasicDBObject matchStage = new BasicDBObject("$match", match);
		return this.getTestPlans(matchStage, true);
	}

	public List<ShortTestPlan> getSharedWithUserTestPlans(String user) {
		BasicDBObject match = new BasicDBObject();
		BasicDBList andList = new BasicDBList();
		andList.add(
				new BasicDBObject(
						"user",
						new BasicDBObject(
								"$ne",
								user
						)
				)
		);
		BasicDBList orList = new BasicDBList();
		orList.add(new BasicDBObject("viewers", user));
		orList.add(new BasicDBObject("isPublic", true));
		andList.add(new BasicDBObject("$or", orList));
		match.put("$and", andList);
		BasicDBObject matchStage = new BasicDBObject("$match", match);
		return this.getTestPlans(matchStage, false);
	}
}
