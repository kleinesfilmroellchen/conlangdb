package klfr.conlangdb.database.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Pattern;

import klfr.conlangdb.CResources;

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
public class CreateServerFunctionsCmd extends NoArgumentCmd<Object> {

	/**
	 * Layout of the first line of each python file that specifies the SQL code to
	 * be run when creating the function. As this is a valid Python comment line, it
	 * can safely be left in the final code.
	 */
	public static final Pattern functionDefinitionLine = Pattern
			.compile("^\\p{IsWhite_Space}*\\#\\p{IsWhite_Space}*(.+)$");

	public CreateServerFunctionsCmd() {
		super(con -> {
			try {
				con.setAutoCommit(true);
				final var stmt = con.createStatement();
				final var files = new Scanner(CResources.open("server-functions/functions.txt").get());
				while (files.hasNextLine()) {
					final var file = files.nextLine();
					final var pythonCode = CResources.open("server-functions/" + file + ".py").get();
					final var pythonCodeScanner = new Scanner(
							CResources.open("server-functions/" + file + ".py").get());
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
								+ functionCode + "$$ LANGUAGE plpython3u IMMUTABLE;";
						log.finer(qry);
						stmt.execute(qry);
					} catch (final SQLException e) {
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