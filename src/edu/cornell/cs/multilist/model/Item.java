package edu.cornell.cs.multilist.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Item implements Iterable<Item>, Serializable {

	private static final long serialVersionUID = -7216453968867906427L;
	private String name;
	private Set<Item> parents = newItems();
	private Set<Item> kids = newItems();
	/** kid_ordering: the order in which the kids are presented.
	 * XXX In a multiuser system, this should be user-specific
	 * and factored out into a separate class.
	*/
	private ArrayList<Item> kid_ordering = new ArrayList<Item>(); 
	private ItemDate due_date; // may be null if no due date
	int quantity = 1;
	private boolean fulfilled = true; // fulfilled = complete
	private String note = "";
	/** whether to show fulfilled kids. XXX In a multiuser system, this should be user-specific. */
	boolean showFulfilled = true;
	public boolean removeOnFulfill = false;
	
	/** Invariant:
	    iff an item is in kids, then its parents include this.
	    iff an item is in parents, its kids include this.
	 */
	public boolean invariant() {
		if (quantity < 1) return false;
		for (Item p : parents) {
			if (!p.hasKid(this)) return false;
		}
		if (kids.size() != kid_ordering.size()) return false;
		for (Item k : kids) {
			if (!k.hasParent(this)) return false;
			if (!kid_ordering.contains(k)) return false;
		}
		for (Item k : kid_ordering) {
			if (!kids.contains(k)) return false;
		}
		
		return true;
	}
	@SuppressWarnings("serial")
	public static class Warning extends Exception {
		public Warning(String msg) { super(msg); }
	}
	
	public Item(String name, Item parent) {
		assert parent != null && parent.invariant();
		this.name = name;
		assert !parent.kids.contains(this);
		parents.add(parent);

		parent.kids.add(this);
		kid_ordering = new ArrayList<Item>();
		parent.kid_ordering.add(this);
		due_date = null; // default: no due date
		assert invariant();
	}

//	public static final Item root = new Item();
	
//	public static Item root() { return root; }
	
	/** Return a new (root) item. */
	public Item() {
		name = "ALL";
		assert invariant();
	}	
	public Set<Item> newItems() {
		return new HashSet<Item>();
	}

	public boolean hasKid(Item k) {
		return kids.contains(k);
	}

	@Override
	public Iterator<Item> iterator() {
		return kids.iterator();
	}
	boolean hasParent(Item p) {
		return parents.contains(p);
	}
	public Iterable<Item> parents()	{
		return parents;
	}

	public String toString() {
		return name;
	}
	public String note() {
		return note;
	}
	public void setNote(String n) {
		note = n;
	}
	public boolean isRoot() {
		return parents.size() == 0;
	}
	public boolean addKid(Item k) {
		assert invariant();
		boolean result = kids.add(k);
		if (result) kid_ordering.add(k);
		k.parents.add(this);
		unfulfilledKids_computed = false;
		assert invariant();
		return result;
	}
	public boolean removeKid(Item k) throws Warning {
		assert invariant();
		if (k.numParents() < 2 && k.numKids() > 0) {
			throw new Warning("Item has children that will be lost.");
		}
		boolean result = kids.remove(k);
		assert result;

		kid_ordering.remove(k);
		k.parents.remove(this);
		unfulfilledKids_computed = false;
		assert invariant();
		return result;
	}

	private int numParents() {
		return parents.size();
	}

	public String name() {
		return name;
	}
	public void setName(String n) {
		name = n;
	}

	public int numKids() {
		return kids.size();
	}
	
	public boolean removeWhenComplete() {
		return removeOnFulfill;
//		return false;
	}
	public void setRemoveOnFulfill(boolean b) {
		removeOnFulfill = b;
	}

	/** Set the completed state of this item to `f`.
	 *  @return whether any items (possibly including this)
	 *  were removed as a result.
	 */
	public boolean setCompleted(boolean f, ItemDate now) {
		if (isRoot()) return false;
		if (fulfilled == f) return false;
		boolean ret = false;
		if (!f && due_date != null && due_date.isBefore(now)) {
			due_date = now;
			boolean bumped = false;
			for (Item k : kids) {
				if (k.due_date != null &&
						due_date.isBefore(k.due_date)) {
					due_date = k.due_date;
					bumped = true;
				}
			}
			if (!bumped) due_date = now.plusDays(1);
		}
		fulfilled = f;
		Set<Item> par = parents;
		outer: for (Item p : par) {
			p.unfulfilledKids_computed = false;
			if (removeWhenComplete() && fulfilled) {
				try {
					ret = true;
					p.removeKid(this);
				} catch (Warning e) {
					// silently stop removals that break the rules
				}
			}
			for (Item k : p) {
				if (!k.fulfilled) {
					ret |= p.setCompleted(false, now);
					continue outer;
				}
			}
			ret |= p.setCompleted(true, now);
		}
		return ret;
	}
	
	public ItemDate dueDate() {
		return due_date;
	}

	static final Item[] dummy = new Item[0];
	public static final ItemDate NO_DATE = null;

	public void sortKids(SortOrder order) {
		kid_ordering = new ArrayList<Item>();
		for (Item k : kids) 
			kid_ordering.add(k);
		Item[] a = kids.toArray(dummy);
		Arrays.sort(a, comparator(order));
		for (int i = 0; i < a.length; i++)
			kid_ordering.set(i, a[i]);
	}
	private Comparator<? super Item> comparator(SortOrder order) {
		switch (order) {
		case ALPHABETIC:
			return new Comparator<Item>() {
				@Override
				public int compare(Item o1, Item o2) {
					String s1 = o1.name().toLowerCase();
					String s2 = o2.name().toLowerCase();
					return s1.compareTo(s2);
				}
			};
		case DUE_DATE:
			return new Comparator<Item>() {
				@Override
				public int compare(Item o1, Item o2) {
					ItemDate d1 = o1.dueDate(), d2 = o2.dueDate();
					if (d1 == null && d2 == null)
						return 0;
					if (d1 == null) return -1;
					if (d2 == null) return 1;
					return d1.compareTo(d2);
				}
			};
		}
		return null;
	}
	Item get(int i) {
		assert 0 <= i && i < kids.size();
		return kid_ordering.get(i);
	}

	public Iterable<Item> orderedKids() {
		return kid_ordering;
	}

	/** d may be null if there is no due date. */
	public void setDueDate(ItemDate d) {
		due_date = d;
	}

	public boolean isComplete() {
		return fulfilled;
	}
	
	transient boolean unfulfilledKids;
	transient boolean unfulfilledKids_computed = false;

	public boolean hasIncompleteKids() {
		if (unfulfilledKids_computed) return unfulfilledKids;
		unfulfilledKids_computed = true;
		unfulfilledKids = false;
		for (Item k : kids) {
			if (!k.fulfilled || k.hasIncompleteKids()) {
				unfulfilledKids = true;
				break;
			}
		}
		return unfulfilledKids;
	}
	public boolean showComplete() {
		return showFulfilled;
	}
	public void setShowComplete(boolean c) {
		showFulfilled = c;
	}
}


