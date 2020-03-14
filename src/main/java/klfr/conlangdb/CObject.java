package klfr.conlangdb;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Custom ConlangDB Object class that provides custom default object functionality.
 * All classes in the project inherit from this.
 */
public abstract class CObject extends Object implements Cloneable, Serializable {
	public static final long serialVersionUID = 1l;
	protected static final Logger log = Logger.getLogger("klfr.conlangdb");

	protected CObject() {
		log.fine(f("CONSTRUCT %s", this.getClass().getCanonicalName()));
	}
	
	/**
	 * Cloning method.
	 */
	public abstract CObject clone();

	/**
	 * String conversion method for displaying debug information.
	 */
	public String toString() {
		return this.getClass().getSimpleName() + ":" + this.hashCode();
	}

	/**
	 * @see java.lang.String#format(String, Object...)
	 */
	public static String f(String format, Object... args) {
		return String.format(format, args);
	}
}