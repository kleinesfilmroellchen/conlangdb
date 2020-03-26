package klfr.conlangdb.database;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import klfr.conlangdb.CResources;

/**
 * DatabaseCommands are executable objects that operate on a database and
 * execute a specified command. They may take command arguments and return a
 * value.
 */
public abstract class DatabaseCommand extends FutureTask<Optional<Object>> implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;

	protected static final Logger log = Logger.getLogger(DatabaseCommand.class.getCanonicalName());

	/**
	 * Creates the database command and sets the connection to be used in the
	 * command execution.
	 * 
	 * @param con
	 */
	private DatabaseCommand(final Connection con, final Function<Connection, Optional<Object>> toExecute) {
		super(() -> toExecute.apply(con));
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
	 * @param <T> the type of arguments to return
	 * @return an iterator of the arguments of the given types, in correct order.
	 */
	public <T> Stream<T> onlyArguments(final Class<T> clazz) {
		return getArguments().filter(arg -> clazz.isInstance(arg)).map(arg -> (T) arg);
	}

	/**
	 * Return the argument at index {@code index}, or Nothing if that index is
	 * invalid.
	 */
	public abstract <T> Optional<T> getArgument(int index);

	public String toString() {
		return getReadableName() + "("
				+ getArguments().reduce("", (orig, b) -> orig + ", " + b.toString(), (a, b) -> a + ", " + b) + ")";
	}

	/**
	 * Copy the command.
	 */
	public abstract DatabaseCommand clone(Connection con);

	// #region Implementations

	/**
	 * Wrapper for argumentless commands.
	 */
	public static abstract class NoArgumentCmd extends DatabaseCommand {
		private static final long serialVersionUID = 1L;

		public NoArgumentCmd(final Connection con, final Function<Connection, Optional<Object>> toExecute) {
			super(con, toExecute);
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.empty();
		}

		@Override
		public <T> Optional<T> getArgument(final int index) {
			return Optional.empty();
		}

		@Override
		public DatabaseCommand clone(final Connection con) {
			return new InitDatabaseCmd(con);
		}
	}

	/**
	 * Command for initializing the database: Setting up all the tables, if
	 * necessary, creating PL/PgSQL triggers for defaults, generated columns and
	 * constraints.<br><br>
	 * Master comand for deleting the database:
	 * {@code drop table tlanguage cascade; drop table tword cascade; drop table reltranslation cascade; drop table relattributeforword cascade; drop table tdefinition cascade; drop table twordattribute cascade;}
	 */
	public static class InitDatabaseCmd extends NoArgumentCmd {

		public InitDatabaseCmd(final Connection conn) {
			super(conn, con -> {
				try {
					final var oldCommit = con.getAutoCommit();
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
							+ "Name_En        varchar(30) not null unique,"
							+ "Description    text,"
							+ "Description_En text,"
							+ "IsConlang   boolean generated always as (not py_is_real_language(ID)) stored,"
							+ "CONSTRAINT ConlangHasDescription CHECK ( case when IsConlang then ( char_length(Description) > 0 ) else true end )"
						+ ");");
					
					stmt.addBatch("CREATE TABLE IF NOT EXISTS TWord ("
							+ "ID         bigserial  not null primary key,"
							+ "LID        varchar(3) not null,"
							+ "Native     text                unique,"
							+ "Romanized  text       not null unique,"
							+ "TextSearch tsvector," // generated by trigger, see below
							+ "FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT"
						+ ");");
					// Special PL/PgSQL function that creates the text search vector for a word,
					// using the collation defined by the word's language. This cannot be a simple
					// index with expression, because it requires language collation lookup with a
					// SELECT query, an unallowed expressiion in index expressions.
					stmt.addBatch("CREATE OR REPLACE FUNCTION pgsql_create_tword_tsvector() RETURNS Trigger AS $$"
						+ "BEGIN "
						+ "IF OLD IS NULL THEN"
						+ "    OLD := NEW;"
						+ "END IF;"
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
					stmt.addBatch("CREATE TRIGGER TrigCreateSearchVectorForTWordInsert "
						+ "AFTER INSERT ON TWord "
						+ "FOR EACH ROW "
						+ "EXECUTE FUNCTION pgsql_create_tword_tsvector();");
					// hypothetical index: not possible
					// stmt.addBatch(
					// 		"CREATE INDEX IF NOT EXISTS IdxTWordTextSearch ON TWord "+
					// 		"USING GIN (to_tsvector((SELECT config FROM TLanguage WHERE TLanguage.ID=LID), Romanized || ' ' || Native));");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordRomanized ON TWord (Romanized);");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordNative ON TWord (Native);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS TDefinition ("
							+ "ID         bigserial not null primary key,"
							+ "WID        bigint    not null,"
							+ "Definition text      not null,"
							+ "FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE"
						+ ");");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTDefinitionDefinition ON TDefinition (Definition);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS RelTranslation ("
							+ "WIDFrom     bigint not null,"
							+ "WIDTo       bigint not null,"
							+ "Description text,"
							+ "PRIMARY KEY ( WIDFrom, WIDTo ),"
							+ "CONSTRAINT  NoSelfTranslation CHECK ( WIDFrom <> WIDTo ),"
							+ "FOREIGN KEY (WIDFrom) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,"
							+ "FOREIGN KEY (WIDTo) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE"
						+ ");");
					
					stmt.addBatch("CREATE TABLE IF NOT EXISTS TWordAttribute ("
							+ "ID          bigserial   not null primary key,"
							+ "LID         varchar(3)  not null,"
							+ "Name        varchar(30) not null,"
							+ "Description text,"
							+ "Symbol      varchar(5),"
							+ "FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT"
						+ ");");
					// Trigger that acts as a default setter of the Symbol column
					stmt.addBatch("CREATE OR REPLACE FUNCTION pgsql_default_symbol_wordattribute() RETURNS Trigger AS $$"
						+ "BEGIN "
						+ "IF NEW IS NOT NULL THEN"
						+ "    NEW.Symbol := lower(substring(NEW.Name from 0 for 3));"
						+ "END IF;"
						+ "END;$$ LANGUAGE plpgsql;");
					stmt.addBatch("DROP TRIGGER IF EXISTS TrigSymbolDefaultTWordAttribute On TWordAttribute;");
					stmt.addBatch("CREATE TRIGGER TrigSymbolDefaultTWordAttribute "
						+ "AFTER INSERT ON TWordAttribute "
						+ "FOR EACH ROW "
						+ "EXECUTE FUNCTION pgsql_default_symbol_wordattribute();");
					stmt.addBatch("CREATE INDEX IF NOT EXISTS IdxTWordAttributeSymbol ON TWordAttribute (Symbol) INCLUDE (Name);");

					stmt.addBatch("CREATE TABLE IF NOT EXISTS RelAttributeForWord ("
							+ "WID bigint not null,"
							+ "AID bigint not null,"
							+ "PRIMARY KEY ( WID, AID ),"
							+ "FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,"
							+ "FOREIGN KEY (AID) References TWordAttribute (ID) ON UPDATE CASCADE ON DELETE CASCADE"
						+ ");");

					stmt.executeBatch();
					con.setAutoCommit(oldCommit);
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
	public static class CreateServerFunctionsCmd extends NoArgumentCmd {

		/**
		 * Layout of the first line of each python file that specifies the SQL code to
		 * be run when creating the function. As this is a valid Python comment line, it
		 * can safely be left in the final code.
		 */
		public static final Pattern functionDefinitionLine = Pattern
				.compile("^\\p{IsWhite_Space}*\\#\\p{IsWhite_Space}*(.+)$");

		public CreateServerFunctionsCmd(final Connection conn) {
			super(conn, con -> {
				try {
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
							log.fine(qry);
							stmt.execute(qry);
						} catch (SQLException e) {
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
	 * Command for directly executing an SQL query. Should be used with caution.
	 */
	public static class SQLCmd extends DatabaseCommand {
		private static final long serialVersionUID = 1L;
		private final String sql;

		public SQLCmd(final Connection conn, final String sql) {
			super(conn, con -> {
				try {
					final var stmt = con.createStatement();
					stmt.execute(sql);
					return Optional.of(stmt.getResultSet());
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
		public <T> Optional<T> getArgument(final int index) {
			return index == 0 ? (Optional<T>) Optional.of(sql) : Optional.empty();
		}

		@Override
		public <T> Stream<T> onlyArguments(final Class<T> clazz) {
			return clazz.isInstance(sql) ? Stream.of((T) sql) : Stream.empty();
		}

		@Override
		public DatabaseCommand clone(final Connection con) {
			return new SQLCmd(con, sql);
		}
	}

	// #endregion Implementations
}
