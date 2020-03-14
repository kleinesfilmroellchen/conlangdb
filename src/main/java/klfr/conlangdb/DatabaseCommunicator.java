package klfr.conlangdb;

import java.sql.*;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import klfr.conlangdb.ServerMain.Arguments;

/**
 * Static class used to communicate with the database managing thread. This
 * class provides a lot of high-level features and stores SQL queries.
 */
public class DatabaseCommunicator extends CObject {
	private static final long serialVersionUID = 1L;
	protected static final Logger log = Logger.getLogger(DatabaseCommunicator.class.getCanonicalName());

	/** Database manager thread that is used by the communicators */
	private static DatabaseManagerThread dbmanagerT;
	private static BlockingQueue<DatabaseCommand> queue;
	private static volatile Connection con;

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
		dbmanagerT = new DatabaseManagerThread(args, signal, cond);
		queue = new LinkedBlockingQueue<DatabaseCommand>();
		dbmanagerT.setQueue(queue);

		signal.lock();

		dbmanagerT.start();
		log.info("Database thread up");

		// wait on the thread to establish the connection
		try {
			cond.await();
			con = dbmanagerT.getConnection();
		} catch (InterruptedException e) { } finally {
			signal.unlock();
		}
		log.info("Database connection up");

		// initialize the database
		try {
			queue.put(new DatabaseCommand.InitDatabaseCmd(con));
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Interrupted while initializing database", e);
			dbmanagerT.interrupt();
		}
	}

}