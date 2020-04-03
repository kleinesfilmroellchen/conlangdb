package klfr.conlangdb.database;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import klfr.conlangdb.CObject;
import klfr.conlangdb.ServerMain.Arguments;

/**
 * Static class used to communicate with the database managing thread. This
 * class provides a lot of high-level features and stores SQL queries.
 */
public class DatabaseCommunicator extends CObject {
	private static final long serialVersionUID = 1L;
	protected static final Logger log = Logger.getLogger(DatabaseCommunicator.class.getCanonicalName());

	/**
	 * Maximum number of times the communicator will try to submit any command to the queue.
	 */
	public static final int MAX_COMMAND_RETRIES = 10;

	/** Database manager thread that is used by the communicators */
	private static DatabaseManagerThread dbmanagerT;
	private static BlockingQueue<DatabaseCommand<Object>> queue;

	@Override
	public CObject clone() {
		return new DatabaseCommunicator();
	}

	/**
	 * Starts the database manager thread and makes it connect to the database
	 * itself.
	 */
	public static synchronized void setupDatabaseConnection(Arguments args) {
		Lock signal = new ReentrantLock();
		Condition cond = signal.newCondition();
		queue = new LinkedBlockingQueue<DatabaseCommand<Object>>();
		dbmanagerT = new DatabaseManagerThread(args, signal, cond, queue);

		signal.lock();

		dbmanagerT.start();
		log.info("Database thread up");

		// wait on the thread to establish the connection
		try {
			cond.await();
		} catch (InterruptedException e) {
		} finally {
			signal.unlock();
		}
		log.info("Database connection up");

		// initialize the database
		try {
			queue.put(new DatabaseCommand.CreateServerFunctionsCmd());
			queue.put(new DatabaseCommand.InitDatabaseCmd());
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Interrupted while initializing database", e);
			dbmanagerT.interrupt();
		}
	}

	/**
	 * Submit the command to the database command queue to be executed at some
	 * point.
	 * 
	 * @param cmd The database command to execute.
	 * @return The command itself, which implements the Future interface. This is
	 *         for avoiding typecasting.
	 */
	public static synchronized <T extends Object> Future<Optional<T>> submitCommand(DatabaseCommand<T> cmd) {
		return submitCommand(cmd, 0);
	}

	/**
	 * command submitter with retry count that limits how often commands are
	 * re-submitted
	 */
	private static synchronized <T extends Object> Future<Optional<T>> submitCommand(DatabaseCommand<T> cmd, int retryCount) {
		try {
			queue.put((DatabaseCommand<Object>) cmd);
		} catch (InterruptedException e) {
			log.log(Level.WARNING,
					f("Interrupted while submitting command to queue, try %d of %d.", retryCount, MAX_COMMAND_RETRIES),
					e);
			if (retryCount > MAX_COMMAND_RETRIES) {
				log.log(Level.SEVERE, f("Maximum number of resubmission tries (%d) reached. Dropping command %s.",
						MAX_COMMAND_RETRIES, cmd), e);
				return new FutureTask<>(() -> Nothing());
			}
			submitCommand(cmd, retryCount + 1);
		}
		return cmd;
	}

}