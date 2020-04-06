package klfr.conlangdb;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Custom ConlangDB Object class that provides custom default object
 * functionality. All classes in the project inherit from this.
 */
public abstract class CObject extends Object implements Cloneable, Serializable {
	public static final long serialVersionUID = 1l;
	protected static final Logger log = Logger.getLogger("klfr.conlangdb");

	protected CObject() {
		log.finest(f("CONSTRUCT %s", this.getClass().getCanonicalName()));
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

	public static <T> Optional<T> Nothing() {
		return Optional.empty();
	}

	public static <T> Optional<T> Just(T x) {
		return Optional.ofNullable(x);
	}

	/**
	 * Creates an expression that functions as an if-else construct. It is given the
	 * if-clause and the else-clause as expressions
	 * 
	 * @param <T>   The resulting type of the expression.
	 * @param _if   The clause to execute if the condition is true.
	 * @param _else The clause to execute if the condition is false.
	 * @return A function that will, upon recieving a condition, execute either the
	 *         if or the else statement and return the return value that the
	 *         statement returned.
	 */
	public static <T> Function<Boolean, T> ifelse(Supplier<T> _if, Supplier<T> _else) {
		return cond -> cond ? _if.get() : _else.get();
	}
}