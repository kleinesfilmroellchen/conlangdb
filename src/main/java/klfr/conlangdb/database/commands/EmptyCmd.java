package klfr.conlangdb.database.commands;

/**
 * Utility command that does nothing and returns a Nothing.
 */
public class EmptyCmd<T> extends NoArgumentCmd<T> {
	private static final long serialVersionUID = 1L;

	public EmptyCmd() {
		super(con -> Nothing());
	}

}