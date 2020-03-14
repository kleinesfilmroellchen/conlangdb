package klfr.conlangdb;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import klfr.conlangdb.ServerMain.Arguments;

import static klfr.conlangdb.CObject.*;

/**
 * Thread that will communicate with the database and recieve commands from the
 * database handler.
 */
public class DatabaseManagerThread extends Thread {

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

	private BlockingQueue<DatabaseCommand> commandQueue;

	public Connection getConnection() {
		return databaseConnection;
	}

	private final Lock signal;
	private final Condition cond;

	/**
	 * Constructs the database manager thread with its arguments and sets important
	 * properties on the parent.
	 */
	public DatabaseManagerThread(final Arguments args, Lock signal, Condition cond) {
		super(Thread.currentThread().getThreadGroup(), "DatabaseManager");
		super.setDaemon(true);
		log.fine(f("CONSTRUCT %s", this.getClass().getCanonicalName()));
		this.args = args;
		this.signal = signal;
		this.cond = cond;
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
		final String url = new URI("jdbc:postgresql://localhost/" + args.databaseName).toASCIIString();
		return DriverManager.getConnection(url, props);
	}

	public void run() {
		log.entering(this.getClass().getCanonicalName(), "run");
		try {
			signal.lock();
			try {
				databaseConnection = connect(args);
				log.finer(databaseConnection.toString());
				cond.signalAll();
			} finally {
				signal.unlock();
			}
			try {
				DatabaseCommand nextCommand;
				while (true) {
					// blocks while queue is empty
					nextCommand = commandQueue.take();
					log.fine(f("RUN COMMAND %s", nextCommand));
					nextCommand.run();
					if (nextCommand.isDone() && !nextCommand.isCancelled())
						try {
							nextCommand.get();
						} catch (ExecutionException e) {
							log.log(Level.SEVERE, "Command execution caused exception.", e);
						}
					else {
						// restart the command - is this a good idea?
						commandQueue.add(nextCommand.clone(databaseConnection));
					}
				}
			} catch (InterruptedException e) {
				log.warning("Interrupted on main loop");
				return;
			}
		} catch (URISyntaxException e) {
			log.log(Level.SEVERE, f("Invalid URL resulted from database name %s.", args.databaseName), e);
			// kill the database manager
			Thread.currentThread().interrupt();
		} catch (SQLException e) {
			log.log(Level.SEVERE, f("SQL exception occurred while opening database %s: %s (%d).", args.databaseName,
					e.getMessage(), e.getErrorCode()), e);
			Thread.currentThread().interrupt();
		}
	}

	public void setQueue(BlockingQueue<DatabaseCommand> queue) {
		commandQueue = queue;
	}

}