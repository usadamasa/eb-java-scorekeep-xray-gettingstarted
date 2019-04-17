package scorekeep;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameModel {
	/** AWS SDK credentials. */
	private AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
			.build();
	private DynamoDBMapper mapper = new DynamoDBMapper(client);
	private final SessionModel sessionModel = new SessionModel();

	public void saveGame(Game game) throws SessionNotFoundException {
		Subsegment subsegment = AWSXRay.beginSubsegment("## GameModel.saveGame");

		try {
			// check session
			String sessionId = game.getSession();
			if (sessionModel.loadSession(sessionId) == null) {
				throw new SessionNotFoundException(sessionId);
			}
			Segment segment = AWSXRay.getCurrentSegment();
			subsegment.putMetadata("resources", "game", game);
			segment.putAnnotation("gameid", game.getId());
			mapper.save(game);
		} catch (Exception e) {
			subsegment.addException(e);
			throw e;
		} finally {
			AWSXRay.endSubsegment();
		}
	}

	public Game loadGame(String gameId) throws GameNotFoundException {
		Game game = mapper.load(Game.class, gameId);
		if (game == null) {
			throw new GameNotFoundException(gameId);
		}
		return game;
	}

	public List<Game> loadGames(String sessionId) throws SessionNotFoundException {
		if (sessionModel.loadSession(sessionId) == null) {
			throw new SessionNotFoundException(sessionId);
		}
		Map<String, AttributeValue> eav = new HashMap<String, AttributeValue>();
		eav.put(":val1", new AttributeValue().withS(sessionId));

		Map<String, String> ean = new HashMap<String, String>();
		ean.put("#key1", "session");

		DynamoDBQueryExpression<Game> queryExpression = new DynamoDBQueryExpression<Game>()
				.withIndexName("session-index")
				.withExpressionAttributeValues(eav)
				.withExpressionAttributeNames(ean)
				.withKeyConditionExpression("#key1 = :val1")
				.withConsistentRead(false);

		List<Game> sessionGames = mapper.query(Game.class, queryExpression);
		return sessionGames;
	}

	public void deleteGame(String sessionId, String gameId) throws GameNotFoundException {
		Game game = mapper.load(Game.class, gameId);
		if (game == null) {
			throw new GameNotFoundException(gameId);
		}
		mapper.delete(game);
		//delete game from session
		Session session = mapper.load(Session.class, sessionId);
		Set<String> sessionGames = session.getGames();
		sessionGames.remove(gameId);
		if (sessionGames.size() == 0) {
			session.clearGames();
		} else {
			session.setGames(sessionGames);
		}
		mapper.save(session);
	}
}
