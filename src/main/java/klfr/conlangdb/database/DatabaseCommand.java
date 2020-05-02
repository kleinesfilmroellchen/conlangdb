package klfr.conlangdb.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import klfr.conlangdb.CObject;
import klfr.conlangdb.database.commands.NoArgumentCmd;

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
	protected DatabaseCommand(final Function<Connection, Optional<T>> toExecute) {
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
		return this.getClass().getSimpleName() + "(" + String.join(", ", this.getArguments().map(x -> x.toString()).collect(Collectors.toList())) + ")";
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
}
