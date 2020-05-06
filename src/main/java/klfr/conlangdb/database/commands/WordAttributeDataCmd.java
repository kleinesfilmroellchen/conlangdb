package klfr.conlangdb.database.commands;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;

import klfr.conlangdb.database.DatabaseCommand;

/**
 * This command retrieves word attribute data from the database. It can either
 * retrieve all word attributes for a certain language or all attributes that a
 * certain word has.
 */
public class WordAttributeDataCmd extends DatabaseCommand<ResultSet> {
	private static final long serialVersionUID = 1L;

	private final long wordID;
	private final String language;

	/**
	 * Retrieve all word attribute that the given language has.
	 * 
	 * @param language The language code.
	 */
	public WordAttributeDataCmd(String language) {
		super(con -> {
			try {
				final var stmt = con
						.prepareStatement("select name, description, symbol from twordattribute where lid=?;");
				stmt.setString(1, language);
				final var rset = stmt.executeQuery();
				return Just(rset);
			} catch (SQLException e) {
				log.log(Level.SEVERE, "", e);
				return Nothing();
			}
		});
		this.language = language;
		this.wordID=-1;
	}

	/**
	 * Retrieve all word attributes that the given word has.
	 * 
	 * @param wordID Word ID number, as appears in the database.
	 */
	public WordAttributeDataCmd(long wordID) {
		super(con -> {
			try {
				final var stmt = con.prepareStatement("select name, description, symbol from twordattribute"
						+ " join relattributeforword on aid=id"
						+ " where relattributeforword.wid=?;");
				stmt.setLong(1, wordID);
				final var rset = stmt.executeQuery();
				return Just(rset);
			} catch (SQLException e) {
				log.log(Level.SEVERE, "", e);
				return Nothing();
			}
		});
		this.language = "";
		this.wordID = wordID;
	}

	@Override
	public Stream<Object> getArguments() {
		return language.isEmpty() ? Stream.of(wordID) : Stream.of(language);
	}

	@Override
	public <U> Optional<U> getArgument(int index) {
		return index == 0 ? language.isEmpty() ? (Optional<U>)Just(wordID) : (Optional<U>)Just(language) : Nothing();
	}

	@Override
	public DatabaseCommand<ResultSet> clone() {
		return language.isEmpty() ? new WordAttributeDataCmd(wordID) : new WordAttributeDataCmd(language);
	}

}