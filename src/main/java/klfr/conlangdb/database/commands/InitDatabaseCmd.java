package klfr.conlangdb.database.commands;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import klfr.conlangdb.CResources;
import klfr.conlangdb.util.StringStreamUtil;

/**
 * Command for initializing the database: Setting up all the tables, if
 * necessary, creating PL/PgSQL triggers for defaults, generated columns and
 * constraints.<br>
 * <br>
 * Master comand for deleting the database:
 * {@code drop table tlanguage cascade; drop table tword cascade; drop table reltranslation cascade; drop table relattributeforword cascade; drop table tdefinition cascade; drop table twordattribute cascade;}
 */
public class InitDatabaseCmd extends NoArgumentCmd<Object> {
	private static final Logger log = Logger.getLogger(InitDatabaseCmd.class.getCanonicalName());

	public InitDatabaseCmd() {
		super(con -> {
			try {
				final var code = StringStreamUtil.stringify(CResources.open("sql/create-database.sql").get()).split(Pattern.quote("--JAVA-SEPARATOR-NEXT-CMD\n"));
				log.finer(Arrays.toString(code));

				con.setAutoCommit(true);
				final var stmt = con.createStatement();
				for (var query : code) {
					stmt.addBatch(query);
				}
				stmt.executeBatch();
			} catch (final SQLException | IOException e) {
				throw new RuntimeException(e);
			}
			return Optional.empty();
		});

	}

	private static final long serialVersionUID = 1L;
}