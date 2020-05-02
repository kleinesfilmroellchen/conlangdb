package klfr.conlangdb.database.commands;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

import klfr.conlangdb.database.DatabaseCommand;

/**
 * Simple general command that retrieves entries from a table. The user can
 * specify what table, which fields to recieve and the limits of the query.
 */
public class TableEntriesCmd extends DatabaseCommand<ResultSet> {
	private static final long serialVersionUID = 1L;

	private final String table;
	private final Iterable<String> fields;
	private final Integer offset;
	private final Integer count;

	/**
	 * Creates a TableEntriesCmd that retrieves entries from a table. Note that
	 * except for the limits, all parameters are not sanitized and interpolated
	 * directly into the query. <strong>Never pass user strings directly to this
	 * constructor!</strong>
	 * 
	 * @param table  The table's name that should be accessed. Follows normal SQL
	 *               conventions and is case insensitive.
	 * @param fields A list of field names to be retrieved from the table. As these
	 *               are directly interpolated, they may be computed fields
	 *               ({@code fielda + fieldb}) or summary fields ({@code count(*)}).
	 * @param offset The first element's index of the table to be retrieved. The
	 *               elements are auto-sorted to the first given field name.
	 *               {@code offset} is zero-based and so 0 will give a result table
	 *               starting at the first element in the entire table.
	 * @param count  How many elements to return. May be zero, in this case, all
	 *               elements are returned. Use with caution, the query might take
	 *               long to process and return a large result set.
	 */
	public TableEntriesCmd(String table, Iterable<String> fields, int offset, int count) {
		super(con -> {
			final String fieldString = String.join(", ", fields);
			final String query = "select " + fieldString + " from " + table + " limit "
					+ (count > 0 ? Integer.toString(count) : "all") + " offset " + Integer.toString(offset) + ";";
			log.fine(query);
			try {
				final var stmt = con.createStatement();
				return Just(stmt.executeQuery(query));
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL exception in single table SELECT.", e);
				return Nothing();
			}
		});
		this.table = table;
		this.fields = fields;
		this.offset = offset;
		this.count = count;
	}

	@Override
	public Stream<Object> getArguments() {
		return Stream.of(table, fields, offset, count);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <U> Optional<U> getArgument(int index) {
		switch (index) {
			case 0:
				return (Optional<U>) Just(table);
			case 1:
				return (Optional<U>) Just(fields);
			case 2:
				return (Optional<U>) Just(offset);
			case 3:
				return (Optional<U>) Just(count);
		}
		return Nothing();
	}

	@Override
	public DatabaseCommand<ResultSet> clone() {
		return new TableEntriesCmd(table, fields, offset, count);
	}

}