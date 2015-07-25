package edu.cornell.cs.multilist.model;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;

import edu.cornell.cs.multilist.model.Item.Warning;

/** A Position represents a current view of the system. It is independent of the GUI framework but
 *  contains non-persistent state associated with the current view. Does not use Java 8 features.
 */
public class Position extends Observable {
	private Model model;
	private ArrayList<Item> path;

	private Item current;
	public Item current() { return current; }
	public Model model() { return model; }
	private boolean editing = false;
	private Item edit_item; // member of current if editing is true.
	private Set<Item> copy_buffer = new HashSet<Item>();
	static final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
	
	public boolean invariant() {
		if (!current.isRoot()) {
			assert path.get(path.size() - 1).hasKid(current);
		}
		for (int i = 0; i < path.size() - 1; i++) {
			assert path.get(i).hasKid(path.get(i + 1));
		}
		if (isEditing()) {
			assert edit_item != null && current.hasKid(edit_item);
		}
		return true;
	}
	public Position(Model m) {
		model = m;
		current = m.root;
		path = new ArrayList<Item>();
		editing = false;
		assert invariant();
	}

	public void moveUp() {
		assert invariant();
		current = path.get(path.size() - 1);
		path.remove(path.size() - 1);
		assert invariant();
	}

	public void moveDownTo(Item k) {
		assert invariant();
		assert current.hasKid(k);
		assert k != null;
		path.add(current);
		current = k;
		assert invariant();
	}

	public Iterable<Item> items() {
		return current.orderedKids();
	}
	public Item createKid() {
		Item result = new Item("", current);
		current.addKid(result);
		notifyChanged();
		return result;
	}

	/** Add k to the current copy selection. Requires: not editing. */
	public void extendCopy(Item k) {
		assert !isEditing();
		copy_buffer.add(k);
	}
	public String copybufferDesc() {
		StringBuilder b = new StringBuilder();
		b.append(copy_buffer.size());
		if (copy_buffer.size() == 1) b.append(" item");
		else b.append(" items");
		return b.toString();
	}
	/** return error message if some part of the copy failed. */
	public void doCopy() throws Warning {
		if (!isCopying()) throw new Warning("Nothing ready to copy.");
		for (Item k : copy_buffer) {
			if (reachable(k, current)) throw new Warning(k.name() + " cannot be under itself.");
		}
		for (Item k : copy_buffer)
			addKid(k);
		return;
	}

	private boolean reachable(Item k1, Item k2) {
		Set<Item> seen = new HashSet<Item>();
		return reachable1(k1, k2, seen);
	}
	private boolean reachable1(Item k1, Item k2, Set<Item> seen) {
		if (k1 == k2) return true;
		if (seen.contains(k1)) return false;
		seen.add(k1);
		for (Item k : k1) {
			if (reachable1(k, k2, seen)) return true;
		}
		return false;
	}
	public boolean showCompleted() {
		return current.showFulfilled;
	}
	public void toggleShowCompleted() {
		current.showFulfilled = !current.showFulfilled;	
	}
	public boolean isEditing(Item k) {
		return (editing && k == edit_item);
	}
	public void finishEditing(String value) {
		assert isEditing();
		Item it = edit_item;
		setName(it, value);
		editing = false;
	}

	public void startEditing(Item k) {
		assert !editing;
		
		editing = true;
		edit_item = k;
	}
//	public String dateString(LocalDate dueDate) {
//		Date now = new Date();
//		Calendar c1 = Calendar.getInstance();
//		Calendar c2 = Calendar.getInstance();
//		Locale loc = Locale.getDefault();
//		long yearMillis = 365L * 24L * 3600L * 1000L;
//		c1.setTime(now);
//		c2.setTimeInMillis(dueDate.getTime());
//		
//		
//		double diff = (c2.getTimeInMillis() - c1.getTimeInMillis())/(0.0 + yearMillis);
//		
//		String m = c2.getDisplayName(Calendar.MONTH, Calendar.LONG, loc);
//		String d = Integer.toString(c2.get(Calendar.DAY_OF_MONTH));
//		StringBuilder b = new StringBuilder();
//		if (diff > 0.75 || diff < -0.25) {
//			b.append(c2.get(Calendar.YEAR));
//			b.append(' ');
//		}
//		b.append(m);
//		b.append(' ');
//		b.append(d);
////		return b.toString();
//		return Position.dateFormat.format(dueDate);
//	}
	
	void notifyChanged() {
		setChanged();
		notifyObservers();
	}
	
	public static class DateAnalysis {
		public Item first_k, last_k;
		public ItemDate first, last;
	}

	public DateAnalysis analyzeDates() {
		DateAnalysis r = new DateAnalysis();
	
		if (current.numKids() > 0) {
			for (Item k : current) {
				if (k.dueDate() != null && !k.isFulfilled() && (r.first == null || k.dueDate().isBefore(r.first))) {
					r.first = k.dueDate();
					r.first_k = k;
				}
				if (k.dueDate() != null && !k.isFulfilled() && (r.last == null || k.dueDate().isAfter(r.last))) {
					r.last = k.dueDate();
					r.last_k = k;
				}
			}
		}
		return r;
	}
	
	/** A description of the current item and its parents. */
	public String topline(Item k) {
		StringBuilder b = new StringBuilder();
		b.append(k.name());
		boolean first = true;
		for (Item i : k.parents()) {
			if (i == model().root) continue;
			b.append(first ? "→" : ", ");
			first = false;
			b.append(i.name());
		}
		return b.toString();
	}

	// Item mutators that notify the model.
	public void setNote(String text) {
		current.setNote(text);
		notifyChanged();
	}
	public void setFulfilled(Item it, boolean b, ItemDate now) {
		it.setFulfilled(b, now);
		notifyChanged();		
	}
	private void addKid(Item k) {
		current.addKid(k);
		notifyChanged();		
	}
	public void removeKid(Item k) throws Item.Warning {
		if (!current.hasKid(k))
			throw new Item.Warning("Can't remove from another view");
		current.removeKid(k);
		notifyChanged();		
	}
	public void sortKids(SortOrder dueDate) {
		current.sortKids(dueDate);
		notifyChanged();		
	}
	public void setName(Item k, String text) {
		k.setName(text);
		notifyChanged();		
	}
	public void setDate(ItemDate d) {
		current.setDueDate(d);
		notifyChanged();		
	}
	public boolean isEditing() {
		return editing;
	}
	public Item editItem() {
		return edit_item;
	}
	public String dateString(ItemDate date) {
		return date.toString();
	}
	public boolean isCopying() {
		return copy_buffer.size() > 0;
	}
//	public void setCopying(boolean copying) {
//		this.copying = copying;
//	}
	public Set<Item> copyBuffer() {
		return copy_buffer;
	}
	public void stopCopying() {
		copy_buffer.clear();
	}
}
