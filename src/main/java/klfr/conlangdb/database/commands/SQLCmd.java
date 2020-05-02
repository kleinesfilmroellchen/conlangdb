package klfr.conlangdb.database.commands;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import klfr.conlangdb.database.DatabaseCommand;

/**
 * Command for directly executing an SQL query. Should be used with caution.
 */
public class SQLCmd extends DatabaseCommand<ResultSet> {
	private static final long serialVersionUID = 1L;
	private final String sql;

	public SQLCmd(final String sql) {
		super(con -> {
			try {
				con.setAutoCommit(true);
				final var stmt = con.createStatement();
				stmt.execute(sql);
				return Optional.ofNullable(stmt.getResultSet());
			} catch (final SQLException e) {
				throw new RuntimeException(e);
			}
		});
		this.sql = sql;
	}

	@Override
	public Stream<Object> getArguments() {
		return Stream.of(sql);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getArgument(final int index) {
		return index == 0 ? (Optional<T>) Optional.of(sql) : Optional.empty();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Stream<T> onlyArguments(final Class<T> clazz) {
		return clazz.isInstance(sql) ? Stream.<T>of((T) sql) : Stream.empty();
	}

	@Override
	public DatabaseCommand<ResultSet> clone() {
		return new SQLCmd(sql);
	}
}