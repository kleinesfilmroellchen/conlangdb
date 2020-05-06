package klfr.conlangdb.database.commands;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

import klfr.conlangdb.database.DatabaseCommand;

/**
 * Wrapper for argumentless commands.
 */
public class NoArgumentCmd<T> extends DatabaseCommand<T> {
	private static final long serialVersionUID = 1L;
	private final DatabaseFunction<T> realToExecute;

	public NoArgumentCmd(final DatabaseFunction<T> toExecute) {
		super(con -> {
			try {
				return toExecute.execute(con);
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL exception in NoArgumentCommand", e);
				try {
					con.rollback();
				} catch (SQLException e1) {
					// do nothing
				}
				return Nothing();
			}
		});
		this.realToExecute = toExecute;
	}

	@Override
	public Stream<Object> getArguments() {
		return Stream.empty();
	}

	@Override
	public <U> Optional<U> getArgument(final int index) {
		return Optional.empty();
	}

	@Override
	public DatabaseCommand<T> clone() {
		return new NoArgumentCmd<T>(this.realToExecute);
	}
}