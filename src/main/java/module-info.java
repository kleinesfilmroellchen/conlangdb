/**
 * The ConlangDB Database system.
 * Depends on the JDK modules for SQL and HTTP.
 * Depends on the Takes Web Framework.
 * Depends on the JSON library reference implementation.
 * Provides an API for extensions to use.
 */
module klfr.conlangdb {
	requires java.base;
	requires java.sql;
	requires java.net.http;
	requires org.json;
	requires takes;
}