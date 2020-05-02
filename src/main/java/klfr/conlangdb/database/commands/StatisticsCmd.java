package klfr.conlangdb.database.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Stream;

import klfr.conlangdb.database.DatabaseCommand;

/**
 * Command for accessing database statistics. The arguments are given as a
 * dictionary of requested statistics, with each entry specifying the Set of all
 * groups that this statistic should be requested for. These groups may vary for
 * different statistics, but for the language-related statistics, they can
 * specify "all" for all data or a language id for the data associated with that
 * language.<br>
 * <br>
 * The following statistics are supported, other map keys will be ignored:
 * <ul>
 * <li>{@code language-count} for the number of languages in the database. This
 * only supports "all" for all languages, "constructed" for all constructed
 * languages and "natural" for all natural languages. Each language is
 * categorized according to its {@code IsConlang} column computed from an ISO
 * official language code list.</li>
 * <li>{@code word-count} for the number of words in a certain language, or all
 * words in the database.</li>
 * <li>{@code definition-count} for the number of definitions in a certain
 * language, or all definitions in the database.</li>
 * <li>{@code wordattribute-count} for the number of word attributes in a
 * certain language, or all word attributes in the database.</li>
 * </ul>
 * The command returns a nested Map that contains all the statistic types and
 * for every one, the requested groups, which are the keys to accessing the
 * values in the inner maps.
 */
public class StatisticsCmd extends DatabaseCommand<Map<String, Map<String, Object>>> {
	private static final long serialVersionUID = 1L;

	private final Map<String, Set<String>> requested;

	public StatisticsCmd(final Map<String, Set<String>> requested) {
		super((con) -> {
			// retrieve all counting statistics requested
			final Set<String> languageCounting = requested.get("language-count"),
					definitionCounting = requested.get("definition-count"),
					wordCounting = requested.get("word-count"),
					wordAttributeCounting = requested.get("wordattribute-count");
			// prepare statistics return object
			final var statistics = new HashMap<String, Map<String, Object>>();
			statistics.put("language-count", new TreeMap<>());

			try {
				// prepare single language counting statements
				final PreparedStatement wordQuery = con
						.prepareStatement("select count(*) from TWord where TWord.LID=?;"),
						definitionQuery = con.prepareStatement(
								"select count(*) from TDefinition join TWord on TDefinition.WID=TWord.ID where TWord.LID=?;"),
						wordAttributeQuery = con.prepareStatement(
								"select count(*) from TWordAttribute where TWordAttribute.LID=?;"),
						// conlang/natural language count query
						conlangQuery = con
								.prepareStatement("select count(*) from TLanguage where TLanguage.IsConlang=?;");

				// Count words, definitions, word attributes (all very similar)
				statistics.put("wordattribute-count",
						doCountStatistics("TWordAttribute", wordAttributeCounting, wordAttributeQuery, con));
				statistics.put("definition-count",
						doCountStatistics("TDefinition", definitionCounting, definitionQuery, con));
				statistics.put("word-count", doCountStatistics("TWord", wordCounting, wordQuery, con));

				// Count languages (different because only three possible options)
				final var langCountDict = statistics.get("language-count");
				for (final String langKey : languageCounting) {
					// special case
					if (langKey.equals("all")) {
						final var qry = con.createStatement().executeQuery("select count(*) from TLanguage;");
						qry.next();
						final var result = qry.getInt(1);
						langCountDict.put(langKey, result);
					} else if (langKey.equals("constructed") || langKey.equals("natural")) {
						conlangQuery.setBoolean(1, langKey.equals("constructed"));
						final var rset = conlangQuery.executeQuery();
						rset.next();
						final var result = rset.getInt(1);
						langCountDict.put(langKey, result);
					}
				}

				// clean up and return
				wordQuery.close();
				definitionQuery.close();
				wordAttributeQuery.close();
				conlangQuery.close();
				return Just(statistics);
			} catch (final SQLException e) {
				log.log(Level.SEVERE, "Unexpected SQL exception in prepared statement", e);
				return Optional.empty();
			}
		});
		this.requested = requested;
	}

	/**
	 * Helper funciton that executes the counting statistics procedure on a given
	 * table and given languages.
	 * 
	 * @param tableName                   Which table to count the contents of.
	 *                                    <strong>This is directly interpolated into
	 *                                    the query and therefore subject to SQL
	 *                                    injections. Do not use user input for this
	 *                                    argument!</strong>
	 * @param whichToCount                Which languages to count. For the special
	 *                                    key 'all', the simple query "SELECT
	 *                                    COUNT(*) FROM TABLENAME;" is used.
	 * @param statementForSingleLanguages A prepared statement whose first argument
	 *                                    is a string specifiying the language and
	 *                                    first return column in first result is an
	 *                                    Integer specifying the count, as in
	 *                                    "select count(*)". This statement is used
	 *                                    when counting single languages.
	 * @param con                         The connection to use.
	 * @return A map which has an integer count for every language that was
	 *         requested to be counted.
	 * @throws SQLException These are simply propagated and the caller should deal
	 *                      with them.
	 */
	private static Map<String, Object> doCountStatistics(final String tableName, final Set<String> whichToCount,
			final PreparedStatement statementForSingleLanguages, final Connection con) throws SQLException {
		final var countDict = new TreeMap<String, Object>();
		for (final String langKey : whichToCount) {
			// special case
			if (langKey.equals("all")) {
				final var qry = con.createStatement().executeQuery("select count(*) from " + tableName + ";");
				qry.next();
				final var result = qry.getInt(1);
				countDict.put(langKey, result);
			} else {
				statementForSingleLanguages.setString(1, langKey);
				final var qry = statementForSingleLanguages.executeQuery();
				qry.next();
				countDict.put(langKey, qry.getInt(1));
			}
		}
		return countDict;
	}

	@Override
	public Stream<Object> getArguments() {
		return Stream.of(requested);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getArgument(final int index) {
		return index == 0 ? (Optional<T>) Optional.of(requested) : Optional.empty();
	}

	@Override
	public DatabaseCommand<Map<String, Map<String, Object>>> clone() {
		return new StatisticsCmd(requested);
	}

}