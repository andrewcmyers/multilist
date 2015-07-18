package multilist;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** A Position represents a current view of the system. It is independent of the GUI framework but
 *  contains non-persistent state associated with the current view.
 */
public class Position {
	private Model model;
	private ArrayList<Item> path;

	private Item current;
	public Item current() { return current; }
	public Model model() { return model; }
	boolean editing = false;
	Item edit_item; // member of current if editing is true.
	boolean copying = false;
	Set<Item> copy_buffer;
	static final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
	
	boolean invariant() {
		if (!current.isRoot()) {
			assert path.get(path.size() - 1).hasKid(current);
		}
		for (int i = 0; i < path.size() - 1; i++) {
			assert path.get(i).hasKid(path.get(i + 1));
		}
		if (editing) {
			assert edit_item != null && current.hasKid(edit_item);
		}
		if (copying) {
			assert copy_buffer != null && copy_buffer.size() > 0;
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

	Iterable<Item> items() {
		return current.orderedKids();
	}
	public Item addKid() {
		Item result = new Item("", current);
		current.addKid(result);
		return result;
	}

	/** Add k to the current copy selection. Requires: not editing. */
	public void extendCopy(Item k) {
		assert !editing;
		if (!copying) {
			copy_buffer = new HashSet<>();
			copying = true;
		}
		copy_buffer.add(k);
	}
	@SuppressWarnings("serial")
	public static class Warning extends Exception {
		public Warning(String msg) { super(msg); }
	}
	
	/** return error message if some part of the copy failed. */
	public void doCopy() throws Warning {
		if (!copying) throw new Warning("Nothing ready to copy.");
		for (Item k : copy_buffer) {
			if (reachable(k, current)) throw new Warning(k.name() + " cannot be under itself.");
		}
		for (Item k : copy_buffer)
			current.addKid(k);
		
		return;
	}
	private boolean reachable(Item k1, Item k2) {
		Set<Item> seen = new HashSet<>();
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
	boolean isEditing(Item k) {
		return (editing && k == edit_item);
	}
	public void finishEditing(String value) {
		assert editing;
		Item it = edit_item;
		it.setName(value);
		editing = false;
	}

	void startEditing(Item k) {
		assert !editing;
		
		editing = true;
		edit_item = k;
	}
	public String dateString(Date dueDate) {
		Date now = new Date();
		Calendar c1 = Calendar.getInstance();
		Calendar c2 = Calendar.getInstance();
		Locale loc = Locale.getDefault();
		long yearMillis = 365L * 24L * 3600L * 1000L;
		c1.setTime(now);
		c2.setTimeInMillis(dueDate.getTime());
		
		
		double diff = (c2.getTimeInMillis() - c1.getTimeInMillis())/(0.0 + yearMillis);
		
		String m = c2.getDisplayName(Calendar.MONTH, Calendar.LONG, loc);
		String d = Integer.toString(c2.get(Calendar.DAY_OF_MONTH));
		StringBuilder b = new StringBuilder();
		if (diff > 0.75 || diff < -0.25) {
			b.append(c2.get(Calendar.YEAR));
			b.append(' ');
		}
		b.append(m);
		b.append(' ');
		b.append(d);
//		return b.toString();
		return Position.dateFormat.format(dueDate);
	}

}