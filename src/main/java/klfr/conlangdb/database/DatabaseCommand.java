package klfr.conlangdb.database;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
	private DatabaseCommand(Connection con, Function<Connection, Optional<Object>> toExecute) {
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
	public <T> Stream<T> onlyArguments(Class<T> clazz) {
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
	 * Command for initializing the database: Setting up all the tables, if
	 * necessary.
	 */
	public static class InitDatabaseCmd extends DatabaseCommand {

		public InitDatabaseCmd(Connection conn) {
			super(conn, con -> {
				try {
					var stmt = con.createStatement();
					stmt.execute("CREATE TABLE if not exists TLanguage ("
							+ "ID          varchar(10) not null unique primary key,"
							+ "Name        varchar(30) not null unique," + "Description text,"
							+ "IsConlang   boolean not null default false,"
							+ "CHECK ( case when IsConlang then ( char_length(Description) > 0 ) else true end )"
							+ ");");
					// stmt.execute("CREATE TABLE if not exists TWord (" +
					// "");
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return Optional.empty();
			});

		}

		private static final long serialVersionUID = 1L;

		@Override
		public Stream<Object> getArguments() {
			return Stream.empty();
		}

		@Override
		public <T> Optional<T> getArgument(int index) {
			return Optional.empty();
		}

		@Override
		public DatabaseCommand clone(Connection con) {
			return new InitDatabaseCmd(con);
		}

	}

	/**
	 * Command for directly executing an SQL query. Should be used with caution.
	 */
	public static class SQLCmd extends DatabaseCommand {
		private static final long serialVersionUID = 1L;
		private final String sql;

		public SQLCmd(Connection con, String sql) {
			super(con, c -> {
				try {
					var stmt = con.createStatement();
					stmt.execute(sql);
					return Optional.of(stmt.getResultSet());
				} catch (SQLException e) {
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
		public <T> Optional<T> getArgument(int index) {
			return index == 0 ? (Optional<T>) Optional.of(sql) : Optional.empty();
		}

		@Override
		public <T> Stream<T> onlyArguments(Class<T> clazz) {
			return clazz.isInstance(sql) ? Stream.of((T) sql) : Stream.empty();
		}

		@Override
		public DatabaseCommand clone(Connection con) {
			return new SQLCmd(con, sql);
		}
	}

	// #endregion Implementations
}
