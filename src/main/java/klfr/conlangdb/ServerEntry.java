
package klfr.conlangdb;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
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
		ch.setLevel(Level.ALL);
		ch.setFormatter(new Formatter() {
			private final DateTimeFormatter dateTimeFmt = new DateTimeFormatterBuilder()
					.appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
					.appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(":")
					.appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(":")
					.appendValue(ChronoField.MILLI_OF_SECOND, 3).toFormatter();

			@Override
			public String format(LogRecord record) {
				var msg = record.getMessage();
				try {
					msg = record.getResourceBundle().getString(record.getMessage());
				} catch (MissingResourceException | NullPointerException e) {
					// do nothing
				}
				final var time = dateTimeFmt.format(record.getInstant().atZone(ZoneId.systemDefault()));
				final var level = record.getLevel().getLocalizedName().substring(0,
						Math.min(record.getLevel().getLocalizedName().length(), 5));
				// the replacement makes the application's logs significantly smaller
				final var logName = record.getLoggerName().replace("klfr.conlangdb.", "~");
				final var threadname = threadFromID(record.getThreadID()).orElse(Thread.currentThread()).getName().replace("BkParallel", "HTTPBk");

				return "[%s %-10s:%-40s|%5s] %s%n".formatted(time, threadname, logName, level, msg)
						+ (record.getThrown() == null ? ""
								: f("EXCEPTION: %s | Stack trace:%n%s", record.getThrown().toString(),
										Arrays.asList(record.getThrown().getStackTrace()).stream()
												.map(x -> x.toString()).collect(() -> new StringBuilder(),
														(builder, str) -> builder.append("in ").append(str)
																.append(System.lineSeparator()),
														(b1, b2) -> b1.append(b2))));
			}

			/**
			 * Helper that retrieves a reference to the thread object given the thread's ID,
			 * or Nothing if it doesn't exist.
			 */
			private Optional<Thread> threadFromID(final int threadID) {
				return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getId() == threadID).findAny();
			}
		});
		LogManager.getLogManager().reset();
		Logger.getLogger("").setLevel(Level.FINER);
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