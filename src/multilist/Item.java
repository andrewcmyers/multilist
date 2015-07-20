package multilist;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import multilist.Position.Warning;

public class Item implements Iterable<Item>, Serializable {
	private static final long serialVersionUID = -9103146750499516106L;
	private String name;
	private Set<Item> parents = newItems();
	private Set<Item> kids = newItems();
	/** kid_ordering: the order in which the kids are presented.
	 * Should ideally be user-specific and factored out into a separate class.
	*/
	private ArrayList<Item> kid_ordering = new ArrayList<>(); 
	private Date due_date;
	int quantity = 1;
	boolean fulfilled = true;
	private String note = "";
	/** whether to show fulfilled kids. Should ideally be user-specific. */
	public boolean showFulfilled = true;
	
	/** Invariant:
	    iff an item is in kids, then its parents include this.
	    iff an item is in parents, its kids include this.
	 */
	public boolean invariant() {
		assert quantity >= 1;
		for (Item p : parents)
			assert (p.hasKid(this));
		assert (kids.size() == kid_ordering.size());
		for (Item k : kids) {
			assert (k.hasParent(this));
			assert(kid_ordering.contains(k));
		}
		for (Item k : kid_ordering) {
			assert (kids.contains(k));
		}
		assert isRoot() || due_date != null;
		
		return true;
	}
	
	public Item(String name, Item parent) {
		assert parent != null && parent.invariant();
		this.name = name;
		assert !parent.kids.contains(this);
		parents.add(parent);

		parent.kids.add(this);
		kid_ordering = new ArrayList<>();
		parent.kid_ordering.add(this);
		Date now = new Date();
		due_date = new Date(now.getTime() + 86400*1000); // default: tomorrow
		assert invariant();
	}

	public static final Item root = new Item();
	
	static Item root() { return root; }
	
	private Item() {
		name = "ALL";
		assert invariant();
	}	
	public Set<Item> newItems() {
		return new HashSet<Item>();
	}

	boolean hasKid(Item k) {
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
	boolean isRoot() {
		return parents.size() == 0;
	}
	public boolean addKid(Item k) {
		assert invariant();
		boolean result = kids.add(k);
		if (result) kid_ordering.add(k);
		k.parents.add(this);
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

	public void setFulfilled(boolean f) {
		if (isRoot()) return;
		if (fulfilled == f) return;
		if (!f && due_date.before(new Date())) {
			due_date = new Date();
			boolean bumped = false;
			for (Item k : kids) {
				if (due_date.before(k.due_date)) {
					due_date = k.due_date;
					bumped = true;
				}
			}
			if (!bumped) due_date = new Date(new Date().getTime() + 86400*1000L);
		}
		fulfilled = f;
		outer: for (Item p : parents) {
			for (Item k : p) {
				if (!k.fulfilled) {
					p.setFulfilled(false);
					continue outer;
				}
			}
			p.setFulfilled(true);
		}
	}
	
	public Date dueDate() {
		return due_date;
	}

	static final Item[] dummy = new Item[0];

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
					return o1.name().compareTo(o2.name());
				}
			};
		case DUE_DATE:
			return new Comparator<Item>() {
				@Override
				public int compare(Item o1, Item o2) {
					return o1.dueDate().compareTo(o2.dueDate());
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

	public void setDate(Date d) {
		due_date = d;
	}
}
