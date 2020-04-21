package klfr.conlangdb.database;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;

/**
 * DatabaseCommands are objects that operate on a database and execute a
 * specified command. They may take command arguments and return a value. They
 * are executed by calling the {@code getTask} method, which returns a
 * FutureTask that does the job of the command. As only this method needs to
 * obtain the connection, it is safely encapsulated and only the executor, the
 * DatabaseManagerThread, needs to know about it. Other users of the Database
 * commands can even define their own commands without needing access to the
 * database at the time of command creation.<br>
 * <br>
 * DatabaseCommand implements Future, and its implementation of that interface
 * delegate to the last task that was created by this database command. This
 * allows the command to be used like any other future, i.e. as a value provider
 * that becomes available after an unspecified amount of time.<br>
 * <br>
 * Because DatabaseCommands recieve connections, they can (and are allowed to)
 * set connection properties like auto-commit mode. Therefore, no command should
 * rely on any particular property having a certain value. If the command needs
 * a certain property to be configured in a certain way, it should
 * unconditionally do so.
 * 
 * @param <T> The type parameter represents the return type of the command.
 *            Commands that do not return anything, such as the NoArgumentCmd
 *            and its children, will set this to Object. Most commands bind this
 *            parameter to a specific type.
 */
public abstract class DatabaseCommand<T> extends CObject implements Future<Optional<T>> {
	private static final long serialVersionUID = 1L;

	protected static final Logger log = Logger.getLogger(DatabaseCommand.class.getCanonicalName());

	/**
	 * Maximum number of seconds a command will wait in its get() method until the
	 * command was actually started by the database manager and the lastTask becomes
	 * available.
	 */
	private static final long CMD_START_WAITTIME_SECONDS = 10;

	/**
	 * The actual code that this command will execute. It recieves a database
	 * connection to operate on and returns a result whose type is given by the
	 * generic type argument of the class. As some commands do not return anything
	 * and null is evil, this is an Optional which is equivalent to an FP Maybe.
	 */
	protected final Function<Connection, Optional<T>> toExecute;

	/**
	 * Last task that was initialized by this command, possibly nothing. Volatile
	 * because at least two threads are expected to access this object during the
	 * command's lifetime.
	 */
	private volatile Optional<FutureTask<Optional<T>>> lastTask = Nothing();

	/**
	 * Creates the database command and sets the function that the command executes.
	 * This constructor is used by subclasses which pass their own, mostly static
	 * functions to this constructor.
	 * 
	 * @param toExecute A function retrieving the Connection the command should
	 *                  operate on and optionally returning a return value, if the
	 *                  command needs to. This defines the actual behavior of the
	 *                  command.
	 */
	private DatabaseCommand(final Function<Connection, Optional<T>> toExecute) {
		this.toExecute = toExecute;
	}

	/** Interface for simple database command functions. */
	@FunctionalInterface
	public interface DatabaseFunction<U> {
		public Optional<U> execute(Connection c) throws SQLException;
	}

	/**
	 * Create a simple no argument database command from a function that defines
	 * what action will be executed by the command. This is the preferred method of
	 * creating database commands on-the-fly.
	 * 
	 * @param toExecute A function taking a database connection and optionally
	 *                  returning a value of any type. It is recommended to set the
	 *                  return type to Object if nothing is ever returned.
	 * @return A new database command that will simply execute the given function
	 *         when it is processed.
	 */
	public static <U> DatabaseCommand<U> from(final DatabaseFunction<U> toExecute) {
		return new NoArgumentCmd<U>(toExecute) {
			private static final long serialVersionUID = -8785363225098048576L;

			public String getReadableName() {
				return "SimpleExternalCmd";
			}
		};
	}

	/**
	 * Create a new FutureTask with the specified connection. Also stores the
	 * returned task so that callers can retrieve its value
	 * 
	 * @param con
	 * @return
	 */
	public synchronized FutureTask<Optional<T>> getTask(final Connection con) {
		lastTask = Just(new FutureTask<Optional<T>>(() -> toExecute.apply(con)));
		return lastTask.get();
	}

	/**
	 * Retrieves the result of the computation. The special behavior of this
	 * implementation is that it will first block up to MAX_CMD_RESULT_WAIT_SECONDS
	 * while there has not been a task started.
	 */
	@Override
	public Optional<T> get() throws InterruptedException, ExecutionException {
		// wait until the task becomes available or the maximum wait time passes
		final Instant endTime = Instant.now().plusSeconds(CMD_START_WAITTIME_SECONDS);
		while (lastTask.isEmpty() && Instant.now().isBefore(endTime))
			Thread.onSpinWait();

		// throw a custom exception if necessary
		return lastTask.orElseThrow(() -> new ExecutionException(
				new IllegalStateException("No task from this DatabaseCommand yet started."))).get();
	}

	@Override
	public boolean cancel(final boolean interrupt) {
		if (lastTask.isEmpty())
			return false;
		return lastTask.get().cancel(interrupt);
	}

	@Override
	public Optional<T> get(final long timeout, final TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		// wait until the task becomes available or the maximum wait time passes
		final Instant endTime = Instant.now().plusSeconds(CMD_START_WAITTIME_SECONDS);
		while (lastTask.isEmpty() && Instant.now().isBefore(endTime))
			Thread.onSpinWait();

		return lastTask
				.orElseThrow(() -> new ExecutionException(
						new IllegalStateException("No task from this DatabaseCommand yet started.")))
				.get(timeout, unit);
	}

	@Override
	public boolean isCancelled() {
		if (lastTask.isEmpty())
			return false;
		return lastTask.get().isCancelled();
	}

	@Override
	public boolean isDone() {
		if (lastTask.isEmpty())
			return false;
		return lastTask.get().isDone();
	}

	/**
	 * Return the human-readable name of the command. The default is the
	 * {@code class.getSimpleName()}.
	 * 
	 * @return the human-readable name of the command.
	 */
	public String getReadableName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Returns this command's arguments, or an empty stream if there are none.
	 */
	public abstract Stream<Object> getArguments();

	/**
	 * Returns only this command's arguments that match the given type, possibly
	 * none. This class provides an inefficient default implementation.
	 * 
	 * @param <U> the type of arguments to return
	 * @return an iterator of the arguments of the given types, in correct order.
	 */
	@SuppressWarnings("unchecked")
	public <U> Stream<U> onlyArguments(final Class<U> clazz) {
		return getArguments().filter(arg -> clazz.isInstance(arg)).map(arg -> (U) arg);
	}

	/**
	 * Return the argument at index {@code index}, or Nothing if that index is
	 * invalid.
	 */
	public abstract <U> Optional<U> getArgument(int index);

	public String toString() {
		return getReadableName() + "(" + getArguments().collect(() -> new StringBuilder(),
				(sb, b) -> sb.append(", ").append(b.toString()), (a, b) -> a.append(", ").append(b)) + ")";
	}

	/**
	 * Copy the command.
	 */
	@Override
	public abstract DatabaseCommand<T> clone();

	// #region Implementations

	/**
	 * Wrapper for argumentless commands.
	 */
	public static class NoArgumentCmd<T> extends DatabaseCommand<T> {
		private static final long serialVersionUID = 1L;
		private final DatabaseFunction<T> realToExecute;

		public NoArgumentCmd(final DatabaseFunction<T> toExecute) {
			super(con -> {
				try {
					return toExecute.execute(con);
				} catch (SQLException e) {
					log.log(Level.SEVERE, "SQL exception in NoArgumentCommand", e);
					return Nothing();
				}
			});
			this.realToExecute = toExecute;
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.empty();
		}

		@Override
		public <U> Optional<U> getArgument(final int index) {
			return Optional.empty();
		}

		@Override
		public DatabaseCommand<T> clone() {
			return new NoArgumentCmd<T>(this.realToExecute);
		}
	}

	/**
	 * Command for initializing the database: Setting up all the tables, if
	 * necessary, creating PL/PgSQL triggers for defaults, generated columns and
	 * constraints.<br>
	 * <br>
	 * Master comand for deleting the database:
	 * {@code drop table tlanguage cascade; drop table tword cascade; drop table reltranslation cascade; drop table relattributeforword cascade; drop table tdefinition cascade; drop table twordattribute cascade;}
	 */
	public static class InitDatabaseCmd extends NoArgumentCmd<Object> {

		public InitDatabaseCmd() {
			super(con -> {
				try {
					con.setAutoCommit(true);
					final var stmt = con.createStatement();
					/*
					 * Database nomenclature and standards:
					 * 
					 * Single tables that represent data start with T.
					 * 
					 * Tables that represent relations with dependencies on other tables (i.e.
					 * DELETEs cascade to them) start with Rel.
					 * 
					 * View tables and temporary tables start with View.
					 * 
					 * Trigger start with Trig.
					 * 
					 * Routines start with the language name and use C nomenclature (underscore
					 * separation)
					 * 
					 * Indices for single columns have the form IdxTableColumn.
					 * 
					 * Identifier columns are named ID in all tables.
					 * 
					 * ID columns that are foreign keys are named XID, where X is a single letter
					 * associated with the referenced table, such as "W" for the word table.
					 * 
					 * "Short Text" columns, i.e. columns that contain names or descriptors, are
					 * VARCHAR(30) i.e. no more than 30 characters.
					 */
					// Use batch processing to submit all statements to the server at once, which
					// prevents half-finished database structures on errors.
					stmt.addBatch("CREATE TABLE if not exists TLanguage ("
							+ "ID             varchar(3)  not null primary key,"
							+ "Name           varchar(30) not null unique,"
							+ "Name_En        varchar(30) not null unique," + "Description    text,"
							+ "Description_En text,"
							+ "IsConlang      boolean generated always as (not py_is_real_language(ID)) stored,"
							+ "Config         varchar(30) not null," + "FontUrl        varchar(40),"
							+ "CONSTRAINT ConlangHasDescription CHECK ( case when IsConlang then ( char_length(Description) > 0 ) else true end )"
							+ ");");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS TWord (" + "ID         bigserial  not null primary key,"
							+ "LID        varchar(3) not null," + "Native     text                unique,"
							+ "Romanized  text       not null unique," + "TextSearch tsvector," // generated by trigger,
																								// see below
							+ "FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT"
							+ ");");
					// Special PL/PgSQL function that creates the text search vector for a word,
					// using the collation defined by the word's language. This cannot be a simple
					// index with expression, because it requires language collation lookup with a
					// SELECT query, an unallowed expressiion in index expressions.
					stmt.addBatch("CREATE OR REPLACE FUNCTION pgsql_create_tword_tsvector() RETURNS Trigger AS $$"
							+ "BEGIN " + "IF OLD IS NULL THEN" + "    OLD := NEW;" + "END IF;"
							+ "NEW.TextSearch := to_tsvector( (SELECT config FROM TLanguage WHERE TLanguage.ID=OLD.LID), OLD.Romanized || ' ' || OLD.Native );"
							+ "END;$$ LANGUAGE plpgsql;");
					// 1st Trigger on update
					stmt.addBatch("DROP TRIGGER IF EXISTS TrigCreateSearchVectorForTWordUpdate On TWord;");
					stmt.addBatch("CREATE TRIGGER TrigCreateSearchVectorForTWordUpdate "
							// The trigger only depends on the two content columns
							+ "AFTER UPDATE OF Romanized, Native, LID ON TWord "
					// Only fire on actual change
							+ "FOR EACH ROW WHEN ( (OLD.Romanized is distinct from NEW.Romanized) or (OLD.Native is distinct from NEW.Native) ) "
							+ "EXECUTE FUNCTION pgsql_create_tword_tsvector();");
					// 2nd Trigger on insert (without change condition)
					stmt.addBatch("DROP TRIGGER IF EXISTS TrigCreateSearchVectorForTWordInsert On TWord;");
					stmt.addBatch("CREATE TRIGGER TrigCreateSearchVectorForTWordInsert " + "AFTER INSERT ON TWord "
							+ "FOR EACH ROW " + "EXECUTE FUNCTION pgsql_create_tword_tsvector();");
					// hypothetical index: not possible
					// stmt.addBatch(
					// "CREATE INDEX IF NOT EXISTS IdxTWordTextSearch ON TWord "+
					// "USING GIN (to_tsvector((SELECT config FROM TLanguage WHERE
					// TLanguage.ID=LID), Romanized || ' ' || Native));");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordRomanized ON TWord (Romanized);");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordNative ON TWord (Native);");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordTS ON TWord (TextSearch);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS TDefinition ("
							+ "ID         bigserial not null primary key," + "WID        bigint    not null,"
							+ "Definition text      not null,"
							+ "FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE" + ");");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTDefinitionDefinition ON TDefinition (Definition);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS RelTranslation (" + "WIDOne     bigint not null,"
							+ "WIDTwo       bigint not null," + "Description text," + "PRIMARY KEY ( WIDOne, WIDTwo ),"
							+ "CONSTRAINT  NoSelfTranslation CHECK ( WIDOne <> WIDTwo ),"
							+ "FOREIGN KEY (WIDOne) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,"
							+ "FOREIGN KEY (WIDTwo) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE" + ");");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS TWordAttribute ("
							+ "ID          bigserial   not null primary key," + "LID         varchar(3)  not null,"
							+ "Name        varchar(30) not null," + "Description text," + "Symbol      varchar(5),"
							+ "FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT"
							+ ");");
					// Trigger that acts as a default setter of the Symbol column
					stmt.addBatch(
							"CREATE OR REPLACE FUNCTION pgsql_default_symbol_wordattribute() RETURNS Trigger AS $$"
									+ "BEGIN " + "IF NEW IS NOT NULL THEN"
									+ "    NEW.Symbol := lower(substring(NEW.Name from 0 for 3));" + "END IF;"
									+ "END;$$ LANGUAGE plpgsql;");
					stmt.addBatch("DROP TRIGGER IF EXISTS TrigSymbolDefaultTWordAttribute On TWordAttribute;");
					stmt.addBatch("CREATE TRIGGER TrigSymbolDefaultTWordAttribute " + "AFTER INSERT ON TWordAttribute "
							+ "FOR EACH ROW " + "EXECUTE FUNCTION pgsql_default_symbol_wordattribute();");
					stmt.addBatch(
							"CREATE INDEX IF NOT EXISTS IdxTWordAttributeSymbol ON TWordAttribute (Symbol) INCLUDE (Name);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS RelAttributeForWord (" + "WID bigint not null,"
							+ "AID bigint not null," + "PRIMARY KEY ( WID, AID ),"
							+ "FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,"
							+ "FOREIGN KEY (AID) References TWordAttribute (ID) ON UPDATE CASCADE ON DELETE CASCADE"
							+ ");");

					stmt.executeBatch();
				} catch (final SQLException e) {
					throw new RuntimeException(e);
				}
				return Optional.empty();
			});

		}

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Command for creating all Python server functions defined in the
	 * server-functions folder of the resources.<br>
	 * <br>
	 * Each of these files has to contain only the code that should appear inside
	 * the function. The first line needs to be commented and contains (in the
	 * comment) the full SQL function signature to be used in the CREATE FUNCTION
	 * statement. It <strong>must not contain the name of the function, it must
	 * start with the parameter list enclosed in parenthesis.</strong> It is
	 * recommended that comments inside the scripts describe their behavior and
	 * arguments. Finally, each file should have a file name that corresponds to its
	 * function name, with {@code py_} appended before it.<br>
	 * <br>
	 * The list of server functions to be created (and replaced) is contained in a
	 * special file named "functions.txt" On each line of this file, one name of a
	 * function file without the .py ending relative to the server function
	 * directory is given.
	 */
	public static class CreateServerFunctionsCmd extends NoArgumentCmd<Object> {

		/**
		 * Layout of the first line of each python file that specifies the SQL code to
		 * be run when creating the function. As this is a valid Python comment line, it
		 * can safely be left in the final code.
		 */
		public static final Pattern functionDefinitionLine = Pattern
				.compile("^\\p{IsWhite_Space}*\\#\\p{IsWhite_Space}*(.+)$");

		public CreateServerFunctionsCmd() {
			super(con -> {
				try {
					con.setAutoCommit(true);
					final var stmt = con.createStatement();
					final var files = new Scanner(CResources.open("server-functions/functions.txt").get());
					while (files.hasNextLine()) {
						final var file = files.nextLine();
						final var pythonCode = CResources.open("server-functions/" + file + ".py").get();
						final var pythonCodeScanner = new Scanner(
								CResources.open("server-functions/" + file + ".py").get());
						final var flm = functionDefinitionLine.matcher(pythonCodeScanner.nextLine());
						pythonCodeScanner.close();
						if (!flm.matches())
							continue;
						final var functionLayout = flm.group(1);
						// TODO: magic number??
						final var out = new StringWriter(1024);
						pythonCode.transferTo(out);
						final var functionCode = out.toString();
						pythonCodeScanner.close();

						// create the function, replace if necessary
						try {
							final var qry = "CREATE OR REPLACE FUNCTION py_" + file + " " + functionLayout + " AS $$\n"
									+ functionCode + "$$ LANGUAGE plpython3u IMMUTABLE;";
							log.finer(qry);
							stmt.execute(qry);
						} catch (final SQLException e) {
							log.log(Level.SEVERE, String.format(
									"SQL exception while creating function py_%s. Check resource server-functions/%s.py for correct first line and general Python syntax.",
									file, file), e);
						}
					}
				} catch (NoSuchElementException | IOException | SQLException e) {
					throw new RuntimeException(e);
				}
				return Optional.empty();
			});

		}

		private static final long serialVersionUID = 1L;
	}

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
	public static class StatisticsCmd extends DatabaseCommand<Map<String, Map<String, Object>>> {
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

	/**
	 * Simple general command that retrieves entries from a table. The user can
	 * specify what table, which fields to recieve and the limits of the query.
	 */
	public static class TableEntriesCmd extends DatabaseCommand<ResultSet> {
		private static final long serialVersionUID = 1L;

		private final String table;
		private final Iterable<String> fields;
		private final Integer offset;
		private final Integer count;

		/**
		 * Creates a TableEntriesCmd that retrieves entries from a table. Note that
		 * except for the limits, all parameters are not sanitized and interpolated
		 * directly into the query. <strong>Never pass user strings directly to this
		 * constructor!</strong>
		 * 
		 * @param table  The table's name that should be accessed. Follows normal SQL
		 *               conventions and is case insensitive.
		 * @param fields A list of field names to be retrieved from the table. As these
		 *               are directly interpolated, they may be computed fields
		 *               ({@code fielda + fieldb}) or summary fields ({@code count(*)}).
		 * @param offset The first element's index of the table to be retrieved. The
		 *               elements are auto-sorted to the first given field name.
		 *               {@code offset} is zero-based and so 0 will give a result table
		 *               starting at the first element in the entire table.
		 * @param count  How many elements to return. May be zero, in this case, all
		 *               elements are returned. Use with caution, the query might take
		 *               long to process and return a large result set.
		 */
		public TableEntriesCmd(String table, Iterable<String> fields, int offset, int count) {
			super(con -> {
				final String fieldString = String.join(", ", fields);
				final String query = "select " + fieldString + " from " + table + " limit "
						+ (count > 0 ? Integer.toString(count) : "all") + " offset " + Integer.toString(offset) + ";";
				log.fine(query);
				try {
					final var stmt = con.createStatement();
					return Just(stmt.executeQuery(query));
				} catch (SQLException e) {
					log.log(Level.SEVERE, "SQL exception in single table SELECT.", e);
					return Nothing();
				}
			});
			this.table = table;
			this.fields = fields;
			this.offset = offset;
			this.count = count;
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.of(table, fields, offset, count);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <U> Optional<U> getArgument(int index) {
			switch (index) {
				case 0:
					return (Optional<U>) Just(table);
				case 1:
					return (Optional<U>) Just(fields);
				case 2:
					return (Optional<U>) Just(offset);
				case 3:
					return (Optional<U>) Just(count);
			}
			return Nothing();
		}

		@Override
		public DatabaseCommand<ResultSet> clone() {
			return new TableEntriesCmd(table, fields, offset, count);
		}

	}

	/**
	 * Command for directly executing an SQL query. Should be used with caution.
	 */
	public static class SQLCmd extends DatabaseCommand<ResultSet> {
		private static final long serialVersionUID = 1L;
		private final String sql;

		public SQLCmd(final String sql) {
			super(con -> {
				try {
					con.setAutoCommit(true);
					final var stmt = con.createStatement();
					stmt.execute(sql);
					return Optional.ofNullable(stmt.getResultSet());
				} catch (final SQLException e) {
					throw new RuntimeException(e);
				}
			});
			this.sql = sql;
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.of(sql);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Optional<T> getArgument(final int index) {
			return index == 0 ? (Optional<T>) Optional.of(sql) : Optional.empty();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Stream<T> onlyArguments(final Class<T> clazz) {
			return clazz.isInstance(sql) ? Stream.<T>of((T) sql) : Stream.empty();
		}

		@Override
		public DatabaseCommand<ResultSet> clone() {
			return new SQLCmd(sql);
		}
	}

	// #endregion Implementations
}
