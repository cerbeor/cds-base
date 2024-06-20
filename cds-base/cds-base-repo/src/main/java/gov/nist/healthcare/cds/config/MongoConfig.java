package gov.nist.healthcare.cds.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

@Configuration
@EnableMongoRepositories(value="gov.nist.healthcare.cds")
public class MongoConfig extends AbstractMongoConfiguration {

	@Value("${fits.data.mongodb.host}")
	private String host;

	@Value("${fits.data.mongodb.port}")
	private String port;

	@Value("${fits.data.mongodb.database}")
	private String db;

	@Override
	protected String getDatabaseName() {
		return db;
	}

	@Override
	public Mongo mongo() throws Exception {
		return new MongoClient(new ServerAddress(host, Integer.parseInt(port)));
	}

	@Override
	protected String getMappingBasePackage() {
		return "gov.nist.healthcare.cds";
	}

}