package klfr.conlangdb.database;

/**
 * A simple enum encoding the two sorting orders ascending and descending along
 * with their SQL keywords.
 */
public enum SortOrder {
	/**
	 * Natural sorting order. This means A-Z, increasing numbers, best text search
	 * matches first.
	 */
	Ascending("ASC"),
	/**
	 * Reverse natural sorting order. This means Z-A, decreasing numbers, worst text
	 * search matches first.
	 */
	Descending("DESC");

	public final String sql;

	private SortOrder(String sql) {
		this.sql = sql;
	}

	/**
	 * Returns the sort order that corresponds to the given sort order sql name.
	 * 
	 * @param queryParamName either "asc" or "desc", disregarding capitalization.
	 * @return the corresponding SortOrder, Ascending for "asc" and Descending for
	 *         "desc". If the string does not match either keyword or is null,
	 *         Ascending is returned.
	 */
	public static SortOrder fromString(String sql) {
		if (sql == null)
			return Ascending;
		switch (sql.toLowerCase()) {
			case "desc":
				return Descending;
			// no need to check the "asc" keyword, actually lol
			default:
				return Ascending;
		}
	}
}