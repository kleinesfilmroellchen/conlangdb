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
	 * necessary.
	 */
	public static class InitDatabaseCmd extends NoArgumentCmd {

		public InitDatabaseCmd(final Connection conn) {
			super(conn, con -> {
				try {
					final var stmt = con.createStatement();
					stmt.execute("CREATE TABLE if not exists TLanguage ("
							+ "ID          varchar(10) not null unique primary key,"
							+ "Name        varchar(30) not null unique," + "Description text,"
							+ "IsConlang   boolean not null default false,"
							+ "CHECK ( case when IsConlang then ( char_length(Description) > 0 ) else true end )"
							+ ");");
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
						final var pythonCodeScanner = new Scanner(CResources.open("server-functions/" + file + ".py").get());
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
							+ functionCode + "$$ LANGUAGE plpython3u;";
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
