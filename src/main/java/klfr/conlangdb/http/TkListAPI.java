package klfr.conlangdb.http;

import java.io.IOException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsWithHeader;

import klfr.conlangdb.CObject;
import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.database.SortOrder;
import klfr.conlangdb.database.commands.EmptyCmd;
import klfr.conlangdb.http.util.HttpStatusCode;
import klfr.conlangdb.http.util.RsJSON;
import klfr.conlangdb.util.Range;

/**
 * An API template which outputs a data list in JSON format to the requestor.
 * This class takes care of parsing the standard query parameters and converting
 * the results to JSON. The customizable part is the QueryGenerator function
 * which is used to execute the query and return a JSON array containing the
 * data to be returned.
 */
public class TkListAPI extends CObject implements Take {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(TkListAPI.class.getCanonicalName());

	/**
	 * A function that is used to build a query to retrieve a data list with
	 * client-specified options. The function recieves a bunch of arguments (read
	 * the
	 * {@link SimpleQueryBuilder#execute(Set, SortOrder, String, org.takes.rq.RqHref.Smart)}
	 * documentation for more details) and generates a string query that is executed
	 * by the database system. The returned result set's data structure is directly
	 * converted to JSON and sent to the client.
	 */
	@FunctionalInterface
	public static interface SimpleQueryBuilder {
		/**
		 * Execute this query builder function. Query builder functions should always
		 * use their main method body to process and check the multitude of arguments
		 * given, and then return a compact database command which should not do much
		 * more than querying the database and converting the result to JSON format.
		 * This reduces overhead on the database access queue.
		 * 
		 * @param fields          The fields that should be included in the result.
		 *                        These should NEVER be actual table column names and
		 *                        should never in any form be directly used in the
		 *                        actual query.
		 * @param order           Sort order, ascending or descending.
		 * @param orderingName    Name of the ordering that should be used. Most often,
		 *                        this will be a field name, but many lists allow
		 *                        special more complex orders with special names.
		 * @param queryParameters A takes Request decorator that allows easy access to
		 *                        the other query parameters of the request. This is
		 *                        provided because many lists allow for special query
		 *                        parameters that control their behavior. The query
		 *                        builder is discouraged from using any other attributes
		 *                        of the request and accessing the standard query
		 *                        parameters, which have already been parsed.
		 * @param offset          {@code OFFSET} argument to the final SQL query, as
		 *                        computed from the request parameters. If negative, the
		 *                        clause should be omitted.
		 * @param limit           {@code LIMIT} argument to the final SQL query, as
		 *                        computed from the request parameters. If negative, the
		 *                        clause should be omitted.
		 * @throws IllegalArgumentException If at least one illegal field name was used
		 *                                  or the ordering name is unrecognized. This
		 *                                  will result in a 400 error being sent to the
		 *                                  client.
		 * @throws ExecutionException       If some error occurred that the query
		 *                                  builder could not handle. This will result
		 *                                  in a 500 error being sent to the client as
		 *                                  well as appropriate exception logging.
		 * @return A string containing a single SELECT query to be executed. The columns
		 *         of the result should match the field names specified.
		 */
		public String execute(Set<String> fields, SortOrder order, String orderingName, RqHref.Smart queryParameters,
				int offset, int limit) throws IllegalArgumentException, ExecutionException;
	}

	/**
	 * A function that is used to build a query to retrieve a data list with
	 * client-specified options. The function recieves a bunch of arguments (read
	 * the
	 * {@link QueryBuilder#execute(Set, SortOrder, String, org.takes.rq.RqHref.Smart)}
	 * documentation for more details) and generates a
	 * {@link klfr.conlangdb.database.DatabaseCommand} that is executed by the
	 * database access system. This command returns a result set, whose data
	 * structure is directly converted to JSON and sent to the client.
	 */
	@FunctionalInterface
	public static interface QueryBuilder {

		/**
		 * Helper method that wraps the simple query builder given into an advanced
		 * query builder. The command of the advanced query builder simply executes the
		 * query with {@code executeQuery} and returns its result set.
		 */
		public static QueryBuilder simpleToAdvanced(final SimpleQueryBuilder builder) {
			return (fields, order, oname, qpms, offset, limit) -> {
				final var query = builder.execute(fields, order, oname, qpms, offset, limit);
				log.fine("Resulting query: %s".formatted(query));
				return DatabaseCommand.from(con -> {
					final var stmt = con.createStatement();
					return Optional.of(stmt.executeQuery(query));
				});
			};
		}

		/**
		 * Execute this query builder function. Query builder functions should always
		 * use their main method body to process and check the multitude of arguments
		 * given, and then return a compact database command which should not do much
		 * more than querying the database and converting the result to JSON format.
		 * This reduces overhead on the database access queue.
		 * 
		 * @param fields          The fields that should be included in the result.
		 *                        These should NEVER be actual table column names and
		 *                        should never in any form be directly used in the
		 *                        actual query.
		 * @param order           Sort order, ascending or descending.
		 * @param orderingName    Name of the ordering that should be used. Most often,
		 *                        this will be a field name, but many lists allow
		 *                        special more complex orders with special names.
		 * @param queryParameters A takes Request decorator that allows easy access to
		 *                        the other query parameters of the request. This is
		 *                        provided because many lists allow for special query
		 *                        parameters that control their behavior. The query
		 *                        builder is discouraged from using any other attributes
		 *                        of the request and accessing the standard query
		 *                        parameters, which have already been parsed.
		 * @param offset          {@code OFFSET} argument to the final SQL query, as
		 *                        computed from the request parameters. If negative, the
		 *                        clause should be omitted.
		 * @param limit           {@code LIMIT} argument to the final SQL query, as
		 *                        computed from the request parameters. If negative, the
		 *                        clause should be omitted.
		 * @throws IllegalArgumentException If at least one illegal field name was used
		 *                                  or the ordering name is unrecognized. This
		 *                                  will result in a 400 error being sent to the
		 *                                  client.
		 * @throws ExecutionException       If some error occurred that the query
		 *                                  builder could not handle. This will result
		 *                                  in a 500 error being sent to the client as
		 *                                  well as appropriate exception logging.
		 * @return A database command to be executed, which returns a ResultSet or
		 *         Nothing if an error occurred. The result set is directly converted to
		 *         JSON and sent to the client.
		 */
		public DatabaseCommand<ResultSet> execute(Set<String> fields, SortOrder order, String orderingName,
				RqHref.Smart queryParameters, int offset, int limit)
				throws IllegalArgumentException, ExecutionException;
	}

	/**
	 * This function creates and executes a query created from request parameters.
	 * This is useful for advanced functionality.
	 */
	protected final QueryBuilder builder;
	private final Set<String> standardFields;
	private final String standardOrdering;

	/**
	 * Creates a new data list API that will use the specified query builder
	 * function.
	 * 
	 * @param builder          The function that is called with the request
	 *                         parameters to generate a JSON result.
	 * @param standardFields   Field names that should always be requested from the
	 *                         database and are always included in the field set the
	 *                         builder function gets passed. This means that the
	 *                         builder function does not need to know about such
	 *                         standards.
	 * @param standardOrdering The name of the ordering that should be used by
	 *                         default if no ordering is given by the client.
	 */
	public TkListAPI(QueryBuilder builder, Collection<String> standardFields, String standardOrdering) {
		this.builder = builder;
		this.standardFields = Set.copyOf(standardFields);
		this.standardOrdering = standardOrdering;
	}

	public TkListAPI(SimpleQueryBuilder builder, Collection<String> standardFields, String standardOrdering) {
		this.builder = QueryBuilder.simpleToAdvanced(builder);
		this.standardFields = Set.copyOf(standardFields);
		this.standardOrdering = standardOrdering;
	}

	@Override
	public Response act(Request request) {
		final var queryParams = new RqHref.Smart(request);
		try {
			// extract requested fields and add standard fields.
			// LinkedHashSet retains the insertion order, so query builders can exploit this
			// behavior and expect their column lists in the SELECT to have a certain order.
			final Set<String> fields = new LinkedHashSet<>();
			fields.addAll(standardFields);
			fields.addAll(Arrays.asList(queryParams.single("fields", "").split("\\,")));
			fields.remove("");

			// extract ordering name and order "direction"
			final var orderParts = queryParams.single("order", "").split("\\s+");
			final var ordering = orderParts[0].length() == 0 ? standardOrdering : orderParts[0];
			final var order = orderParts.length > 1 ? SortOrder.fromString(orderParts[1]) : SortOrder.Ascending;

			// extract offset and limit
			final var limitStr = queryParams.single("ipp", "-1");
			final var pageStr = queryParams.single("page", "0");
			int limit = -1, offset = -1;
			try {
				limit = Integer.parseInt(limitStr);
			} catch (NumberFormatException e) {
			}
			try {
				offset = Integer.parseInt(pageStr) * limit;
			} catch (NumberFormatException e) {
			}
			if (offset < 0)
				offset = -1;

			log.fine("Query params: fields=%s, order=%s %s, limit=%d, offset=%d".formatted(fields, ordering, order,
					limit, offset));
			final var command = builder.execute(fields, order, ordering, queryParams, offset, limit);

			final var maybeRset = DatabaseCommunicator.submitCommand(command).get();
			if (maybeRset.isEmpty())
				return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
			final var rset = maybeRset.get();

			final var meta = rset.getMetaData();
			final JSONArray arr = new JSONArray();
			final var columns = new Range(1, meta.getColumnCount() + 1);
			while (rset.next()) {
				final var jsonObj = new JSONObject();
				for (int i : columns) {
					// The result set will convert its data to normal java data types such as
					// Integer, Boolean and String, JSONObject's put(Object) detects primitive
					// wrapper types (Integer, Float etc.) as well as complicated objects and
					// represents them accordingly. The only exception is the SQL array type, which
					// is more like a pointer to the server data than anything. The utility method
					// detects SQL arrays and retrieves all of their data into a simple
					// List<Object>.
					final var data = DatabaseCommunicator.javaType(rset.getObject(i));
					jsonObj.put(meta.getColumnName(i), data.orElse(null));
				}
				arr.put(jsonObj);
			}
			rset.close();

			return new RsWithHeader(new RsCWrap(new RsJSON(arr)), "Cache-Control", "public, max-age=10");
		} catch (IllegalArgumentException e1) {
			log.log(Level.WARNING, "Illegal arguments to data list API.", e1);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.BAD_REQUEST);
		} catch (ExecutionException | SQLException e2) {
			log.log(Level.SEVERE, "Database processing exception in data list API.", e2);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.INTERNAL_SERVER_ERROR);
		} catch (IOException e) {
			log.log(Level.SEVERE, "IO exception in data list API.", e);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.INTERNAL_SERVER_ERROR);
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Database processing exception in data list API.", e);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.INTERNAL_SERVER_ERROR);
		}
	}

	// #region Standard query builder implementations

	private static final Map<String, String> languageFieldMap = new TreeMap<>();
	static {
		for (var key : List.of("id", "name", "description", "isconlang", "config", "fonturl"))
			languageFieldMap.computeIfAbsent(key, k -> k);
		languageFieldMap.put("name-en", "name_en as \"name-en\"");
		languageFieldMap.put("description-en", "description_en as \"description-en\"");
	}
	public static final SimpleQueryBuilder languageQueryBuilder = (fields, order, orderingName, queryParameters, offset,
			limit) -> {
		// SQL-injection safe, because the ordering name needs to be part of a safe list
		// that cannot escape the quoting and change the query
		final var properOrdering = languageFieldMap.containsKey(orderingName) ? ("\"" + orderingName + "\"") : "id";
		return "SELECT "
				+ String.join(", ",
						fields.stream().map(f -> languageFieldMap.getOrDefault(f, null)).filter(x -> x != null)
								.collect(Collectors.toSet()))
				+ " FROM TLanguage " + " ORDER BY " + properOrdering + " " + order.sql
				+ (limit >= 0 ? (" LIMIT " + limit) : "") + (offset >= 0 ? (" OFFSET " + offset) : "") + ";";
	};

	private static final Map<String, String> wordFieldMap = new TreeMap<>();
	static {
		wordFieldMap.put("romanized", "romanized");
		wordFieldMap.put("text", "native as \"text\"");
		wordFieldMap.put("translations", "ARRAY(select romanized from TWord translation "
				+ "join RelTranslation on (translation.ID=RelTranslation.WIDOne or translation.ID=RelTranslation.WIDTwo) "
				+ "where ( (TWord.ID=RelTranslation.WIDOne or TWord.ID=RelTranslation.WIDTwo) "
				+ "and translation.LID=? ) ) as translations");
		wordFieldMap.put("definitions", "ARRAY(select definition from tdefinition where TWord.ID=TDefinition.WID) as definitions");
		wordFieldMap.put("types", "ARRAY(select symbol from TWordAttribute join RelAttributeForWord on (RelAttributeForWord.AID=TWordAttribute.ID) where TWord.ID=RelAttributeForWord.WID ) as types");
	}
	/**
	 * List API that recieves data from the word list. The additional query
	 * parameter that the user can give is "to" for the target language.
	 */
	public static final QueryBuilder wordQueryBuilder = (fields, order, orderingName, queryParameters, offset,
			limit) -> {
		var properOrdering = wordFieldMap.containsKey(orderingName) ? "\"" + orderingName + "\"" : "id";
		try {
			final String languageFromId = queryParameters.single("from", "");
			if (languageFromId.isBlank())
				return new EmptyCmd<>();
			final String languageToId = queryParameters.single("to", "");
			if (languageToId.isBlank()) {
				fields.remove("translations");
			}
			final String query = String.format("SELECT %s FROM TWord WHERE LID=? ORDER BY %s %s %s %s;",
					String.join(", ",
							fields.stream().map(f -> wordFieldMap.getOrDefault(f, null)).filter(x -> x != null)
									.collect(Collectors.toSet())),
					properOrdering, order.sql, (limit >= 0 ? ("LIMIT " + limit) : ""),
					(offset >= 0 ? ("OFFSET " + offset) : ""));
			return DatabaseCommand.from(con -> {
				try {
					final var stmt = con.prepareStatement(query);
					var paramIdx = 1;
					if (fields.contains("translations"))
						stmt.setString(paramIdx++, languageToId);
					stmt.setString(paramIdx, languageFromId);
					log.finer(stmt.toString());
					return Just(stmt.executeQuery());
				} catch (SQLException e) {
					log.log(Level.SEVERE, "SQL exception", e);
					return Nothing();
				}
			});
		} catch (IOException e) {
			throw new ExecutionException(e);
		}
	};

	/**
	 * A dummy query builder that does not do anything and returns nothing.
	 */
	public static final SimpleQueryBuilder empty = (_5, _4, _3, _2, _1, _0) -> "";
	// #endregion Standard query builder implementations

	@Override
	public CObject clone() {
		return new TkListAPI(builder, standardFields, standardOrdering);
	}

}