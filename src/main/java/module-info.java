/**
 * The ConlangDB Database system.
 * Depends on the JDK modules for SQL and HTTP.
 * Provides an API for extensions to use.
 */
module klfr.conlangdb {
	requires java.base;
	requires java.sql;
	requires java.net.http;
	requires jdk.httpserver;
}