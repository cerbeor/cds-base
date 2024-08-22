package gov.nist.healthcare.cds.config;

import com.mongodb.MongoCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

import java.net.UnknownHostException;
import java.util.Collections;

@Configuration
@EnableMongoRepositories(value="gov.nist.healthcare.cds")
public class MongoConfig extends AbstractMongoConfiguration {

	@Value("${fits.data.mongodb.host}")
	private String HOST;

	@Value("${fits.data.mongodb.port}")
	private String PORT;

	@Value("${fits.data.mongodb.database}")
	private String NAME;

	@Value("${fits.data.mongodb.username}")
	private String USERNAME;

	@Value("${fits.data.mongodb.password}")
	private String PASSWORD;

	@Value("${fits.data.mongodb.auth.source}")
	private String AUTH_SOURCE;

	@Override
	protected String getDatabaseName() {
		return NAME;
	}

	@Override
	public Mongo mongo() throws UnknownHostException {
		if(USERNAME != null && PASSWORD != null && !USERNAME.isEmpty() && !PASSWORD.isEmpty()) {
			MongoCredential credential = MongoCredential.createPlainCredential(USERNAME, AUTH_SOURCE, PASSWORD.toCharArray());
			return new MongoClient(
					new ServerAddress(
							HOST,
							Integer.parseInt(PORT)
					),
					Collections.singletonList(credential)
			);
		}
		return new MongoClient(new ServerAddress(HOST, Integer.parseInt(PORT)));
	}

	@Override
	protected String getMappingBasePackage() {
		return "gov.nist.healthcare.cds";
	}

}