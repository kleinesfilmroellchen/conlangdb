package klfr.conlangdb.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

/**
 * A datatype that represents a range of numeric values with start, stop and
 * step. It implements a lot of interfaces and can be used conveniently for a
 * lot of applications.
 */
public class Range implements Comparable<Range>, Serializable, Cloneable, Collection<Integer> {
	private static final long serialVersionUID = 1L;

	private final Integer start, stop, step;

	/**
	 * A range that goes from start to stop while stepping with a size of step.
	 */
	public Range(Integer start, Integer stop, Integer step) {
		if (step.doubleValue() == 0.0d)
			throw new IllegalArgumentException("Step cannot be 0");
		this.start = start;
		this.stop = stop;
		this.step = step;
	}

	/**
	 * A range that goes from start to stop while stepping with a size of 1.
	 */
	public Range(Integer start, Integer stop) {
		this(start, stop, 1);
	}

	/**
	 * A range that goes from 0 to stop while stepping with a size of 1.
	 */
	public Range(Integer stop) {
		this(0, stop, 1);
	}

	@Override
	public int size() {
		return (int) Math.floor(Math.abs((stop - start) / step.doubleValue()));
	}

	@Override
	public boolean isEmpty() {
		return (step > 0) ? (stop > start) : (stop < start);
	}

	@Override
	public boolean contains(Object o) {
		return false;
	}

	@Override
	public Object[] toArray() {
		final var arr = new Integer[size()];
		int count = 0;
		for (Integer i = start; i < stop; i += step) {
			arr[count] = i;
		}
		return arr;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(T[] a) {
		if (a.getClass().isAssignableFrom(Integer.class)) {
			final var arr = new Integer[size()];
			int count = 0;
			for (Integer i = start; i < stop; i = i + step) {
				arr[count] = i;
			}
			return (T[]) arr;
		}
		return null;
	}

	@Override
	public boolean add(Integer e) {
		return false;
	}

	@Override
	public boolean remove(Object o) {
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends Integer> c) {
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return false;
	}

	@Override
	public void clear() {
	}

	public Integer get(int index) {
		return index >= size() ? stop : start + (index * step);
	}

	@Override
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>() {
			private Integer current = start;

			@Override
			public boolean hasNext() {
				return step > 0 ? current < stop : current > stop;
			}

			@Override
			public Integer next() {
				final var c = current;
				current = current + step;
				return c;
			}

		};
	}

	/**
	 * A range is compared to another range by comparing start - stop - step, in
	 * that order
	 */
	@Override
	public int compareTo(Range o) {
		final int startCompare = this.start - o.start;
		if (startCompare == 0) {
			final int stopCompare = this.stop - o.stop;
			if (stopCompare == 0)
				return this.step - o.step;
			return stopCompare;
		}
		return startCompare;

	}

	public String toString() {
		return String.format("Range(from %s to %s step %s)", start, stop, step);
	}

	public Range clone() {
		return new Range(start, stop, step);
	}

}