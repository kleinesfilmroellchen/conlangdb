/**
 * The ConlangDB Database system.
 * Depends on the JDK modules for SQL and HTTP.
 * Depends on the Takes Web Framework.
 * Depends on the JSON library reference implementation.
 * Provides an API for extensions to use.
 */
open module klfr.conlangdb {
	requires java.base;
	requires java.sql;
	requires transitive org.json;
	requires transitive takes;
	exports klfr.conlangdb;
	exports klfr.conlangdb.util;
	exports klfr.conlangdb.http.util;
}