package klfr.conlangdb.http;

import java.io.IOException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsWithHeader;

import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.database.SortOrder;

/**
 * An API template which outputs a data list in JSON format to the requestor.
 * This class takes care of parsing the standard query parameters and converting
 * the results to JSON. The customizable part is the QueryGenerator function
 * which is used to execute the query and return a JSON array containing the
 * data to be returned.
 */
public class TkListAPI implements Take {
	private static final Logger log = Logger.getLogger(TkListAPI.class.getCanonicalName());

	/**
	 * A function that is used to build a query to retrieve a data list with
	 * client-specified options. The function recieves a bunch of arguments (read
	 * the
	 * {@link QueryBuilder#execute(Set, SortOrder, String, org.takes.rq.RqHref.Smart)}
	 * documentation for more details) and generates a
	 * {@link klfr.conlangdb.database.DatabaseCommand} that is executed by the
	 * database access system. This command returns the finished JSON array in the
	 * standard data list API format, which will be sent to the server. QueryBuilder
	 * implementations can choose to
	 */
	@FunctionalInterface
	public static interface QueryBuilder {
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

	protected final QueryBuilder executor;
	private final Set<String> standardFields;
	private final String standardOrdering;

	@FunctionalInterface
	private static interface SQLValueSettingFunction {
		@FunctionalInterface
		public static interface SQLValueSetterCurried {
			public void apply(JSONObject obj, ResultSet rset) throws SQLException;
		}

		public SQLValueSetterCurried f(String key);
	}

	/**
	 * Map for statically preparing simple functions for setting sql return values
	 * onto JSON objects. The functions are curried in that they first recieve the
	 * value's key / the column name (constant for any one query) and then the JSON
	 * object for the row and the result set pointing to the correct row (both
	 * change for every row).
	 */
	private static final Map<Integer, SQLValueSettingFunction> sqlTypesSetters = new HashMap<>(20, 0.5f);
	static {
		sqlTypesSetters.put(java.sql.Types.BIGINT, key -> (json, rset) -> json.put(key, rset.getLong(key)));
		sqlTypesSetters.put(java.sql.Types.INTEGER, key -> (json, rset) -> json.put(key, rset.getInt(key)));
		sqlTypesSetters.put(java.sql.Types.SMALLINT, key -> (json, rset) -> json.put(key, rset.getShort(key)));
		sqlTypesSetters.put(java.sql.Types.TINYINT, key -> (json, rset) -> json.put(key, rset.getByte(key)));
		sqlTypesSetters.put(java.sql.Types.BOOLEAN, key -> (json, rset) -> json.put(key, rset.getBoolean(key)));
		sqlTypesSetters.put(java.sql.Types.BIT, key -> (json, rset) -> json.put(key, rset.getBoolean(key)));
		sqlTypesSetters.put(java.sql.Types.CHAR, key -> (json, rset) -> json.put(key, rset.getString(key)));
		sqlTypesSetters.put(java.sql.Types.VARCHAR, key -> (json, rset) -> json.put(key, rset.getString(key)));
		sqlTypesSetters.put(java.sql.Types.DOUBLE, key -> (json, rset) -> json.put(key, rset.getDouble(key)));
		sqlTypesSetters.put(java.sql.Types.FLOAT, key -> (json, rset) -> json.put(key, rset.getFloat(key)));
		sqlTypesSetters.put(java.sql.Types.DECIMAL,
				key -> (json, rset) -> json.put(key, rset.getBigDecimal(key).toPlainString()));
		sqlTypesSetters.put(java.sql.Types.DATE, key -> (json, rset) -> json.put(key, rset.getDate(key)));
		sqlTypesSetters.put(java.sql.Types.TIME, key -> (json, rset) -> json.put(key, rset.getTime(key)));
		sqlTypesSetters.put(java.sql.Types.TIMESTAMP, key -> (json, rset) -> json.put(key, rset.getTimestamp(key)));
		sqlTypesSetters.put(java.sql.Types.ARRAY, key -> (json, rset) -> {
			final Array sqlarr = rset.getArray(key);
			final var jsarr = new JSONArray();
			final var arrayset = sqlarr.getResultSet();
			while (arrayset.next()) {
				jsarr.put(arrayset.getObject(2));
			}
			json.put(key, jsarr);
		});
	}
	/** Default setter for unrecognized types. */
	private static final SQLValueSettingFunction defaultSetter = key -> (json, rset) -> json.put(key,
			String.valueOf(rset.getObject(key)));

	/**
	 * Creates a new data list API that will use the specified query executor
	 * function.
	 * 
	 * @param executor         The function that is called with the request
	 *                         parameters to generate a JSON result.
	 * @param standardFields   Field names that should always be requested from the
	 *                         database and are always included in the field set the
	 *                         executor function gets passed. This means that the
	 *                         executor function does not need to know about such
	 *                         standards.
	 * @param standardOrdering The name of the ordering that should be used by
	 *                         default if no ordering is given by the client.
	 */
	public TkListAPI(QueryBuilder executor, Collection<String> standardFields, String standardOrdering) {
		this.executor = executor;
		this.standardFields = Set.copyOf(standardFields);
		this.standardOrdering = standardOrdering;
	}

	@Override
	public Response act(Request request) {
		final var queryParams = new RqHref.Smart(request);
		try {
			// extract requested fields and add standard fields.
			final Set<String> fields = new TreeSet<>();
			fields.addAll(Arrays.asList(queryParams.single("fields", "").split("\\,")));
			fields.addAll(standardFields);
			fields.remove("");

			// extract ordering name and order "direction"
			final var orderParts = queryParams.single("order", "").split("\\s+");
			final var ordering = orderParts[0].length() == 0 ? standardOrdering : orderParts[0];
			final var order = orderParts.length > 1 ? SortOrder.fromString(orderParts[1]) : SortOrder.Ascending;

			// extract offset and limit
			final var limitStr = queryParams.single("ipp", "20");
			final var pageStr = queryParams.single("page", "0");
			Integer limit = -1, offset = -1;
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

			log.fine("Query params: fields %s order %s %s limit %d offset %d".formatted(fields, ordering, order, limit,
					offset));
			final String query = executor.execute(fields, order, ordering, queryParams, offset, limit);
			log.fine("Resulting query: %s".formatted(query));

			final DatabaseCommand<JSONArray> dbcommand = DatabaseCommand.from(con -> {
				try {
					final var stmt = con.createStatement();
					final var result = stmt.execute(query);
					if (!result)
						return Optional.empty();
					final var rset = stmt.getResultSet();
					final var meta = rset.getMetaData();

					final JSONArray arr = new JSONArray();
					// These functions will each process one column of the result. They access the
					// result set and modify the JSON object. As the functions do not care about
					// order or indices, we can use sets, because no two functions can be equal in
					// the Java RE.
					final Set<SQLValueSettingFunction.SQLValueSetterCurried> processors = new HashSet<>(
							meta.getColumnCount(), 0.9f);
					for (int i = 1; i <= meta.getColumnCount(); ++i) {
						final int ctype = meta.getColumnType(i);
						final String cname = meta.getColumnName(i);
						log.finer(ctype + " " + cname);
						// Use the prepared list of type-aware JSON object setters.
						// These are curried, i.e. "preloaded" with the column name which is constant.
						// also, escape the column name to enable non-identifier column names
						processors.add(sqlTypesSetters.getOrDefault(ctype, defaultSetter).f(cname));
					}
					while (rset.next()) {
						final var jsonObj = new JSONObject();
						// Each processor already knows the column name, we do not need to pass it.
						processors.forEach(processor -> {
							try {
								processor.apply(jsonObj, rset);
							} catch (SQLException e) {
								log.log(Level.SEVERE, "Exception while retrieving single column value.", e);
							}
						});
						arr.put(jsonObj);
					}

					return Optional.of(arr);
				} catch (SQLException e) {
					log.log(Level.SEVERE, "SQL exception in data list fetch.", e);
					return Optional.empty();
				}
			});
			DatabaseCommunicator.submitCommand(dbcommand);

			final var json = dbcommand.get().orElse(new JSONArray());

			return new RsWithHeader(new RsCWrap(new RsJSON(json)), "Cache-Control", "public, max-age=10");

		} catch (IllegalArgumentException e1) {
			log.log(Level.WARNING, "Illegal arguments to data list API.", e1);
			return new RsCWrap(new RsEmpty(), HttpStatusCode.BAD_REQUEST);
		} catch (ExecutionException e2) {
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
	public static final QueryBuilder languageQueryBuilder = (fields, order, orderingName, queryParameters, offset,
			limit) -> {
		final Function<String, String> fieldMapper = elt -> {
			// only valid elements are not nulled
			switch (elt.toLowerCase()) {
				case "id":
				case "name":
				case "description":
				case "isconlang":
				case "config":
				case "fonturl":
					return elt.toLowerCase();
				case "name-en":
					return "Name_En as \"name-en\"";
				case "description-en":
					return "Description_En as \"description-en\"";
				default:
					return null;
			}
		};
		var properOrdering = fieldMapper.apply(orderingName);
		if (properOrdering == null)
			properOrdering = "ID";
		return "SELECT "
				+ String.join(", ", fields.stream().map(fieldMapper).filter(x -> x != null).collect(Collectors.toSet()))
				+ " FROM TLanguage " + " ORDER BY " + properOrdering + " " + order.sql
				+ (limit >= 0 ? (" LIMIT " + limit) : "") + (offset >= 0 ? (" OFFSET " + offset) : "") + ";";
	};

	public static final QueryBuilder wordQueryBuilder = (fields, order, orderingName, queryParameters, offset,
			limit) -> {
		final Function<String, String> fieldMapper = elt -> {
			// only valid elements are not nulled
			switch (elt.toLowerCase()) {
				case "romanized":
				case "fonturl":
					return elt.toLowerCase();
				case "text":
					return "Native as text";
				default:
					return null;
			}
		};
		var properOrdering = fieldMapper.apply(orderingName);
		if (properOrdering == null)
			properOrdering = "ID";
		try {
			String languageToId = queryParameters.single("to", null);
			if (languageToId == null) {
				// only query words, do not query translations
				return String.format("SELECT %s FROM TWord ORDER BY %s %s %s;",
						String.join(", ",
								fields.stream().map(fieldMapper).filter(x -> x != null).collect(Collectors.toSet())),
						properOrdering, order.sql,
						(limit >= 0 ? (" LIMIT " + limit) : "") + (offset >= 0 ? (" OFFSET " + offset) : ""));
			}
			return String.format("SELECT %s, (select Native from TWord translation "
					+ "join RelTranslation on (translation.ID=RelTranslation.WIDOne or translation.ID=RelTranslation.WIDTwo) where (TWord.ID=RelTranslation.WIDOne or TWord.ID=RelTranslation.WIDTwo) ) "
					+ "FROM TWord ORDER BY %s %s %s;",
					String.join(", ",
							fields.stream().map(fieldMapper).filter(x -> x != null).collect(Collectors.toSet())),
					properOrdering, order.sql,
					(limit >= 0 ? (" LIMIT " + limit) : "") + (offset >= 0 ? (" OFFSET " + offset) : ""));
		} catch (IOException e) {
			throw new ExecutionException(e);
		}
	};

	/**
	 * A dummy query builder that does not do anything and returns nothing.
	 */
	public static final QueryBuilder empty = (_5, _4, _3, _2, _1, _0) -> "";
	// #endregion Standard query builder implementations

}