package scorekeep;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;

import java.util.List;

public class UserModel {
	/** AWS SDK credentials. */
	private AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
			.build();
	private DynamoDBMapper mapper = new DynamoDBMapper(client);

	public void saveUser(User user) {
		// Wrap in subsegment
		Subsegment subsegment = AWSXRay.beginSubsegment("## UserModel.saveUser");
		try {
			mapper.save(user);
		} catch (Exception e) {
			subsegment.addException(e);
			throw e;
		} finally {
			AWSXRay.endSubsegment();
		}
	}

	public User loadUser(String userId) throws UserNotFoundException {
		User user = mapper.load(User.class, userId);
		if (user == null) {
			throw new UserNotFoundException(userId);
		}
		return user;
	}

	public List<User> loadUsers() {
		DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
		List<User> scanResult = mapper.scan(User.class, scanExpression);
		return scanResult;
	}

	public void deleteUser(String userId) throws UserNotFoundException {
		User user = mapper.load(User.class, userId);
		if (user == null) {
			throw new UserNotFoundException(userId);
		}
		mapper.delete(user);
	}
}
