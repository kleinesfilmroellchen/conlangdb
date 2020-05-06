package klfr.conlangdb.database;

import static klfr.conlangdb.CObject.f;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import klfr.conlangdb.ServerMain.Arguments;

/**
 * Thread that will communicate with the database and recieve commands from the
 * database handler.
 */
class DatabaseManagerThread extends Thread {

	private static final Logger log = Logger.getLogger(DatabaseManagerThread.class.getCanonicalName());
	/**
	 * The argument object that contains the username and password to use. Is never
	 * modified by this class.
	 */
	private final Arguments args;

	/**
	 * The connection to the database that is established by the thread.
	 */
	private Connection databaseConnection;

	private final BlockingQueue<DatabaseCommand<Object>> commandQueue;

	public Connection getConnection() {
		return databaseConnection;
	}

	private final Lock signal;
	private final Condition cond;

	/**
	 * Constructs the database manager thread with its arguments and sets important
	 * properties on the parent.
	 */
	public DatabaseManagerThread(final Arguments args, final Lock signal, final Condition cond,
			final BlockingQueue<DatabaseCommand<Object>> queue) {
		super(Thread.currentThread().getThreadGroup(), "DBManagr");
		super.setDaemon(true);
		log.fine(f("CONSTRUCT %s", this.getClass().getCanonicalName()));
		this.args = args;
		this.signal = signal;
		this.cond = cond;
		this.commandQueue = queue;
	}

	/**
	 * Causes this DatabaseManager thread to initiate a connection to the specified
	 * database, using the username and password contained in the Arguments.
	 * 
	 * @param args The argument object that contains the database, username and
	 *             password to use. Is never modified by this class.
	 * @return the connection established
	 * @throws SQLException       If the database connection fails.
	 * @throws URISyntaxException If the database name in combination with the
	 *                            server name yields an invalid URI.
	 */
	public static Connection connect(final Arguments args) throws SQLException, URISyntaxException {
		final var props = new Properties();
		props.setProperty("user", args.databaseUser);
		props.setProperty("password", args.databasePassword);
		// props.setProperty("ssl", "true");
		// use postgresql database on localhost with given database
		final String url = new URI("jdbc:postgresql://localhost:5431/" + args.databaseName).toASCIIString();
		return DriverManager.getConnection(url, props);
	}

	public void run() {
		log.entering(this.getClass().getCanonicalName(), "run");
		try {
			signal.lock();
			try {
				databaseConnection = connect(args);
				cond.signalAll();
			} finally {
				signal.unlock();
			}
			try {
				DatabaseCommand<Object> nextCommand;
				while (true) {
					// blocks while queue is empty
					nextCommand = commandQueue.take();
					final var nc = nextCommand;
					log.fine(() -> f("RUN COMMAND %s", nc));
					databaseConnection.beginRequest();
					final var executable = nextCommand.getTask(databaseConnection);
					executable.run();
					databaseConnection.endRequest();
					if (executable.isDone() && !executable.isCancelled())
						try {
							executable.get();
						} catch (final ExecutionException e) {
							log.log(Level.SEVERE, "Command execution caused exception.", e);
						}
					else {
						// TODO: restart the command without cloning - is this a good idea?
						commandQueue.add(nextCommand);
					}
				}
			} catch (final InterruptedException e) {
				log.warning("Interrupted on main loop");
				return;
			}
		} catch (final URISyntaxException e) {
			log.log(Level.SEVERE, f("Invalid URL resulted from database name %s.", args.databaseName), e);
			// kill the database manager
			Thread.currentThread().interrupt();
		} catch (final SQLException e) {
			log.log(Level.SEVERE, f("SQL exception occurred while opening database %s: %s (%d).", args.databaseName,
					e.getMessage(), e.getErrorCode()), e);
			Thread.currentThread().interrupt();
		}
	}

}