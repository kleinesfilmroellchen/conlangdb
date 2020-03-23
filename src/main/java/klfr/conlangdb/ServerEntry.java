
package klfr.conlangdb;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import klfr.conlangdb.ServerMain.Arguments;
import klfr.conlangdb.database.DatabaseCommunicator;

/**
 * Entry method of the server. Responsible for parsing command line arguments
 * and spawing threads.
 */
public class ServerEntry extends CObject {

	private static final long serialVersionUID = 1L;
	private static final Pattern optionPattern = Pattern.compile("(\\-\\-?)(.+)");
	protected static final Logger log = Logger.getLogger(ServerEntry.class.getCanonicalName());

	public static void main(String[] args) {
		// some startup things
		Thread.currentThread().setUncaughtExceptionHandler((thd, exc) -> {
			System.err.printf("Exception in thread %s:%n", thd.getName());
			exc.printStackTrace();
			System.err.flush();
		});
		// create a handler with custom compact one-line formatting
		var ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		ch.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				var msg = record.getMessage();
				try {
					record.getResourceBundle().getString(record.getMessage());
				} catch (MissingResourceException | NullPointerException e) {
					// do nothing
				}
				var time = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
						.format(record.getInstant().atZone(ZoneId.systemDefault()));
				var level = record.getLevel().getLocalizedName();
				var logName = record.getLoggerName();// "%s.%s".formatted(record.getSourceClassName(),
														// record.getSourceMethodName());
				return "[%s %-40s |%6s] %s%n".formatted(time, logName, level, msg);
			}
		});
		LogManager.getLogManager().reset();
		Logger.getLogger("").setLevel(Level.ALL);
		for (var h : Logger.getLogger("").getHandlers())
			Logger.getLogger("").removeHandler(h);
		Logger.getLogger("").addHandler(ch);
		// set some logging levels
		Logger.getLogger("org.postgresql").setLevel(Level.INFO);

		// parse command line arguments
		log.config(f("ARGUMENTS %s", Arrays.toString(args)));
		var argo = parseArgs(args);
		if (argo.errorMessage != null) {
			System.out.printf("conlangdb: error: %s%n", argo.errorMessage);
			System.exit(1);
		}

		// start SQL connection thread
		DatabaseCommunicator.setupDatabaseConnection(argo);

		// enter HTTP server code
		new ServerMain(argo).start();
	}

	private static Arguments parseArgs(String[] args) {
		var argo = new Arguments();
		int i = 0;
		try {
			for (; i < args.length; ++i) {
				var arg = args[i];
				var m = optionPattern.matcher(arg);
				if (m.matches()) {
					var prefix = m.group(1);
					List<String> opt = new LinkedList<String>();
					if (prefix.length() == 2) {
						opt = m.group(2).chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());
					} else {
						opt.add(m.group(2));
					}
					for (var o : opt) {
						switch (o) {
							case "p":
							case "port":
								argo.port = Integer.parseInt(args[++i]);
								break;
							default:
								// skip
								break;
							// argo.errorMessage = f("unknown option '%s'", o);
							// return argo;
						}
					}
				} else {
					// skip
					break;
					// argo.errorMessage = f("unknown argument '%s'.", arg);
					// break;
				}
			}
		} catch (NumberFormatException e) {
			argo.errorMessage = f("%s is not a number.", args[i]);
		} catch (Exception e) {
			argo.errorMessage = "unknown error on argument(s).";
		}
		return argo;
	}

	@Override
	public CObject clone() {
		return new ServerEntry();
	}
}