
package klfr.conlangdb;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import klfr.conlangdb.ServerMain.Arguments;

/**
 * Entry method of the server. Responsible for parsing command line arguments
 * and spawing threads.
 */
public class ServerEntry extends CObject {

	private static final long serialVersionUID = 1L;
	private static final Pattern optionPattern = Pattern.compile("(\\-\\-?)(.+)");

	public static void main(String[] args) {
		System.out.println(Arrays.toString(args));
		Thread.currentThread().setUncaughtExceptionHandler((thd, exc) -> {
			System.err.printf("Exception in thread %s:%n%s%n", thd.getName(), exc.getLocalizedMessage());
			System.err.flush();
		});
		var argo = parseArgs(args);
		if (argo.errorMessage != null) {
			System.out.printf("conlangdb: error: %s%n", argo.errorMessage);
			System.exit(1);
		}
		var server = new ServerMain(argo);
		Thread serverT = new Thread(server::start);
		serverT.setName("Server");
		serverT.setDaemon(false);
		serverT.start();
		try {
			while(true)
				Thread.sleep(20000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

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
								argo.errorMessage = f("unknown option '%s'", o);
								return argo;
						}
					}
				} else {
					argo.errorMessage = f("unknown argument '%s'.", arg);
					break;
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