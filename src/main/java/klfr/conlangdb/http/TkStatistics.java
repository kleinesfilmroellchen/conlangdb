package klfr.conlangdb.http;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsWithHeader;

import klfr.conlangdb.http.util.HttpStatusCode;
import klfr.conlangdb.http.util.RsJSON;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.database.commands.StatisticsCmd;

public class TkStatistics implements Take {

	private static final Logger log = Logger.getLogger(TkStatistics.class.getCanonicalName());

	@Override
	public Response act(Request request) {
		try {
			// convert http query params -> java set-map
			final var requestedStatistics = requestParamsToMap(request);

			// execute the StatisticsCmd and retrieve its result
			final var future = DatabaseCommunicator.submitCommand(new StatisticsCmd(requestedStatistics));
			final var resultMaybe = future.get();
			if (resultMaybe.isEmpty()) {
				// command threw error
				return new RsCWrap(new RsEmpty(), HttpStatusCode.BAD_REQUEST);
			}

			// convert java set-map -> json
			final var results = resultMaybe.get();
			final var responseObject = responseToJSON(results);
			// return the response
			return new RsWithHeader(new RsCWrap(new RsJSON(responseObject)), "Cache-Control", "public, max-age=60");
		} catch (JSONException e) {
			log.log(Level.WARNING, "JSON exception in statistics", e);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.BAD_REQUEST);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Uncaught exception.", e);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Puts a JSON array in the JSON object into the Set dictionary. Both the JSON
	 * array as well as the final set in the Map/Dictionary are identified using the
	 * same given key.
	 * 
	 * @param map    A Set Map/Dictionary.
	 * @param key    The key to locate the JSON array and store the Set in the
	 *               Dictionary.
	 * @param origin The JSON object which may or may not contain a JSON array with
	 *               the specified key.
	 * @return The given, modified map.
	 */
	// private static Map<String, Set<String>> putJSONArray(final Map<String,
	// Set<String>> map, final String key,
	// final JSONObject origin) {
	// map.put(key,
	// Set.<String>copyOf(origin.optJSONArray(key).toList().stream().map(x ->
	// x.toString())
	// .collect(Collectors.toUnmodifiableList())));
	// return map;
	// }

	/**
	 * Converts the requests's query parameters to the java-internal format that the
	 * statistics database command can read.
	 * 
	 * @param request A request with the query parameters appropriate for a
	 *                statistics request.
	 * @return The map that represents the same data as the query parameters of the
	 *         request
	 * @throws IOException
	 */
	private static Map<String, Set<String>> requestParamsToMap(final Request request) throws IOException {
		final var queryParamsRetriever = new RqHref.Smart(request);
		final String[] languages = queryParamsRetriever.single("language-count", "").split("\\,"),
				words = queryParamsRetriever.single("word-count", "").split("\\,"),
				definitions = queryParamsRetriever.single("definition-count", "").split("\\,"),
				wordAttributes = queryParamsRetriever.single("wordattribute-count", "").split("\\,");
		log.fine(() -> Arrays.toString(languages) + Arrays.toString(words) + Arrays.toString(definitions)
				+ Arrays.toString(wordAttributes));
		final var requestedStatistics = new HashMap<String, Set<String>>();
		requestedStatistics.put("language-count",
				(languages.length == 1 && languages[0].length() == 0) ? new TreeSet<>()
						: new TreeSet<>(Arrays.asList(languages)));
		requestedStatistics.put("word-count",
				(words.length == 1 && words[0].length() == 0) ? new TreeSet<>() : new TreeSet<>(Arrays.asList(words)));
		requestedStatistics.put("definition-count",
				(definitions.length == 1 && definitions[0].length() == 0) ? new TreeSet<>()
						: new TreeSet<>(Arrays.asList(definitions)));
		requestedStatistics.put("wordattribute-count",
				(wordAttributes.length == 1 && wordAttributes[0].length() == 0) ? new TreeSet<>()
						: new TreeSet<>(Arrays.asList(wordAttributes)));
		return requestedStatistics;
	}

	private static JSONObject responseToJSON(final Map<String, Map<String, Object>> results) {
		final var responseObject = new JSONObject();
		// create response json object
		for (final var statistic : results.entrySet()) {
			final var json = new JSONObject();
			for (final var category : statistic.getValue().entrySet())
				json.put(category.getKey(), category.getValue());
			responseObject.put(statistic.getKey(), json);
		}
		return responseObject;
	}
}
