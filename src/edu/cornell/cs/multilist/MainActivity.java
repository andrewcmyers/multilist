package edu.cornell.cs.multilist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ScrollView;
import android.widget.TextView;
import edu.cornell.cs.multilist.model.AndroidDateFactory;
import edu.cornell.cs.multilist.model.Item;
import edu.cornell.cs.multilist.model.Item.Warning;
import edu.cornell.cs.multilist.model.ItemDate;
import edu.cornell.cs.multilist.model.Model;
import edu.cornell.cs.multilist.model.Position;
import edu.cornell.cs.multilist.model.SortOrder;

public class MainActivity extends Activity {

	Model model;

	/** GUI-independent state */
	Position pos;
	int edit_row; // which row of the grid is being edited.
	boolean unsaved_update;
//	Set<Item> selected = new HashSet<Item>();

	ScrollView outer;
	LinearLayout box;
	GridLayout grid;
	Map<Item, ViewGroup> itemPanes;
	Map<View, Item> items;
	Map<Item, Integer> vert_pos;
	Button select_menu;
	ViewGroup copy_buffer;
	TextView edit_text;
	TextView notes;
	
	static final String APP_STATE_KEY = "state";
	static final String PERSISTENT_STATE_KEY = "MultiList";
	static final String ENCODING_CHARSET = "ISO-8859-1";
	
	static final int selectedColor = Color.argb(255, 155, 147, 100);
	
// Unconverted JavaFX stuff:;
//	Button select_menu;
//	Pane copy_buffer;
//	private Stage stage;
//	Saver saver;
	
	int md5(byte[] b) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
			md.update(b);
			byte[] res = md.digest();
			return res[0] + (res[1] << 8) + (res[2] << 16) + (res[3] << 24);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			SharedPreferences prefs = getPreferences(MODE_PRIVATE);
			String val = prefs.getString(PERSISTENT_STATE_KEY, null);
			if (val != null) {
				Item dummy = new Item();
				model = new Model(dummy);
				int where = 0;
				try {
//					System.err.println("read string: " + val);
					byte[] bytes = ByteEncoder.decode(val);
//					System.err.println(" -> " + bytes.length + " bytes");
//					System.err.println("hash = " + md5(bytes));
					ObjectInputStream os = new ObjectInputStream(
							new ByteArrayInputStream(bytes));
					model = (Model) os.readObject();
					os.close();
				} catch (StreamCorruptedException e) {
					dummy.setNote("Stream corrupted recovering state: " + e.getMessage());
				} catch (IOException e) {
					dummy.setNote("I/O exception recovering state: (" + where + ")" + e.getMessage());
				} catch (ClassNotFoundException e) {
					dummy.setNote("Class cast exception recovering state: " + e.getMessage());
				}
			} else {
				Item root = new Item();
				model = new Model(root);
			}
		} else
		if (savedInstanceState.containsKey(APP_STATE_KEY)) {
			Serializable s = savedInstanceState.getSerializable(APP_STATE_KEY);
			model = (Model) s;
		} else {
			Item root = new Item();
			root.setNote("Could not recover state from bundle!");
			model = new Model(root);
		}
		
		outer = new ScrollView(this);
		box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		outer.addView(box);
	
		grid = new GridLayout(this);
		setContentView(outer);
		pos = new Position(model);

		setup();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle savedState) {
		savedState.putSerializable(APP_STATE_KEY, model);
	}
	
	@Override protected void onPause() {
		finishEditing();
		super.onPause();
	}
	
	@Override protected void onStop(){ 
		SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit();
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			ObjectOutputStream os = new ObjectOutputStream(bout);

			os.writeObject(model);
			os.close();
			
			byte[] bytes = bout.toByteArray();
//			System.err.println("Generated " + bytes.length + " bytes");
//			System.err.println("hash = " + md5(bytes));
			
			
			String enc = ByteEncoder.encode(bytes);
//			System.err.println("  = " + enc.length() + " chars");
//			System.err.println("output: " + enc);
			edit.putString(PERSISTENT_STATE_KEY, enc);
			edit.commit();
		} catch (IOException e) {
			assert false;
		}
		super.onStop();
	}
	
	private void setup() {
		box.removeAllViews();
		
		itemPanes = new HashMap<Item, ViewGroup>();
		items = new HashMap<View, Item>();
		vert_pos = new HashMap<Item, Integer>();
		box.addView(setup_top_line());
		setup_item_rows();
		box.addView(grid);
		if (!pos.current().isFulfilled())
			box.addView(setup_due_date());
		box.addView(setup_notes());
		if (pos.isCopying())
			box.addView(setup_copy_buffer());
	}

	private View setup_copy_buffer() {
		assert pos.isCopying();
		
		TextView s = new TextView(this);
		s.setText("selected: ");
		s.setTextColor(Color.WHITE);
		TextView t = new TextView(this);
		t.setText(pos.copybufferDesc());
		t.setTextColor(Color.WHITE);
		
		LinearLayout row = new LinearLayout(this);
		row.setBackgroundColor(selectedColor);
		add(row, s, t);
		Button b = new Button(this);
		
//		b.setImageBitmap(bm);
		b.setText("≡");
		b.setBackgroundColor(Color.TRANSPARENT);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu m = new PopupMenu(MainActivity.this, v);
				m.getMenu().add("clear");
				m.getMenu().add("check");
				m.getMenu().add("uncheck");
				m.getMenu().add("paste");
				m.getMenu().add("remove");
				m.show();
				m.setOnMenuItemClickListener(new OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						String it = item.getTitle().toString();
						return do_selection_op(it);					
					}
				});
			}
		});
		add(row, b);
//		registerForContextMenu(row);
	
		
		t.setPadding(5, 5, 5, 5);
		row.setPadding(0,  10,  0,  0);
		return row;
	}
	
	private boolean do_selection_op(String it) {
		if (it.equals("clear")) {
			finishEditing();
			pos.stopCopying();
			setup();
			return true;
		} else if (it.equals("paste")) {
			try {
				pos.doCopy();
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}
			return true;
		} else if (it.equals("remove")) {
			try {
				finishEditing();
				for (Item k : pos.copyBuffer())
					pos.removeKid(k);
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}
			return true;
		} else if (it.equals("check")) {
			finishEditing();
			for (Item k: pos.copyBuffer())
				pos.setFulfilled(k, false, AndroidDateFactory.now());
			setup();
			return true;
		} else if (it.equals("uncheck")) {
			finishEditing();
			for (Item k: pos.copyBuffer())
				pos.setFulfilled(k, true, AndroidDateFactory.now());
			setup();
			return true;
		}
		return false;
	}

	private View setup_notes() {
		notes = new EditText(this);
		notes.setText(pos.current().note());
		return notes;
	}
	
	private void set_due_date(TextView d) {
		ItemDate id = pos.current().dueDate();
		String ds = id == null ?
				  "none"
				: pos.dateString(id);
		d.setText("Due: " + ds);
	}

	private View setup_due_date() {
		final TextView d = new TextView(this);
		set_due_date(d);
		final Calendar cal = Calendar.getInstance();
		if (pos.current().dueDate() != Item.NO_DATE) {
			cal.setTimeInMillis(pos.current().dueDate().getTimeMillis());
		}
		final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int monthOfYear,
					int dayOfMonth) {
				cal.set(Calendar.YEAR, year);
				cal.set(Calendar.MONTH, monthOfYear);
				cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
				pos.current().setDueDate(AndroidDateFactory.create(cal.getTimeInMillis()));
				set_due_date(d);
			}
		};	
		d.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				DatePickerDialog dia = new DatePickerDialog(MainActivity.this, date,
	            		cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
	                    cal.get(Calendar.DAY_OF_MONTH));
				dia.getDatePicker().setSpinnersShown(false);
				dia.getDatePicker().setCalendarViewShown(true);
				dia.show();
			}
		});
		return d;
	}

	private void setup_item_rows() {
		grid = new GridLayout(this);
		final Item current = pos.current();
		int i = 0;

		for (Item k : pos.items()) {
			if (!current.showFulfilled && k.isFulfilled()) continue;
			ViewGroup h = new LinearLayout(this);
			itemPanes.put(k, h);
			vert_pos.put(k, i);
			grid.addView(h, gridCoord(i, 0));
			setup_row(k, i);
			i++;
		}
		Button b = new Button(this);
		b.setText("+");

		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				newKid();
			}
		});
		grid.addView(b, gridCoord(i, 2));
	}

	protected void newKid() {
		finishEditing();
		Item it = pos.createKid();
		pos.setFulfilled(it, false, AndroidDateFactory.now());
		itemPanes.put(it, new LinearLayout(MainActivity.this));
		pos.startEditing(it);
		edit_row = pos.current().numKids() - 1;
		setup();
	}

	private void finishEditing() {
		pos.setNote(notes.getText().toString());
		if (pos.isEditing()) {
			pos.finishEditing(edit_text.getText().toString());
			setup_row(pos.editItem(), edit_row);				
		}
	}

	static ViewGroup add(ViewGroup p, View... n) {
		for (View v : n)
			p.addView(v);
		return p;
	}

	private void setup_row(final Item k, final int i) {
		final ViewGroup row = itemPanes.get(k);
		boolean edited = pos.isEditing(k);
		Button down = null;
		row.removeAllViews();
		final android.widget.CheckBox cb;
		ViewGroup checkbox_area = new LinearLayout(this);
		checkbox_area.setMinimumWidth(180);
		add(row, checkbox_area);
		
		if (pos.isCopying() && pos.copyBuffer().contains(k)) {
			row.setBackgroundColor(selectedColor);
		}
		
		if (edited) {
			final TextView tf = new EditText(this);
			tf.setLines(1);
			tf.setText(k.name());
			edit_text = tf;
			add(checkbox_area, cb = new CheckBox(this), tf);
			
			tf.setOnKeyListener(new OnKeyListener() {
				@Override public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (keyCode == KeyEvent.KEYCODE_ENTER) {
						pos.finishEditing(tf.getText().toString());
						setup_row(k, i);
					}
					return false;
				}
			});
//			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//			imm.showSoftInput(tf, InputMethodManager.SHOW_IMPLICIT);
			tf.requestFocus();
		} else {
			cb = new CheckBox(this);
			cb.setText(k.name());
			add(checkbox_area, cb);
		}
		cb.setChecked(!k.isFulfilled());

		ViewGroup buttons = new LinearLayout(this);

		grid.addView(buttons, gridCoord(i, 2));
		down = new Button(this);
		down.setText("▶");
		add(buttons, down);
		addHandlers(cb, down, k);
		items.put(cb,  k);
		items.put(checkbox_area, k);
		items.put(row,  k);

		registerForContextMenu(checkbox_area);
		registerForContextMenu(cb);
		registerForContextMenu(row);
	}
	@Override
	public
	void onCreateContextMenu(ContextMenu c, View v, ContextMenuInfo inf) {
		Item k = items.get(v);
		if (k == context_item) return; // avoid duplicating items
		c.add("edit");
		c.add("select");
		c.add("remove");
	
		context_item = k;
	}
	Item context_item;
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		String op = item.getTitle().toString();
		Item k = context_item;
		if (op.equals("edit")) {
			editKid(k);
		} else if (op.equals("select")) {
			copyKid(k);
		} else if (op.equals("remove")) {
			removeKid(k);
		}
		return true;
	}

	private void editKid(Item k) {
		finishEditing();
		pos.startEditing(k);
		setup_row(k, vert_pos.get(k));
	}

	private void removeKid(Item k) {
		finishEditing();
		try {
			pos.removeKid(k);
			setup();
		} catch (Warning w) {
			warn(w.getMessage());
		}
	}
	
	private void copyKid(Item k) {
		finishEditing();
		if (pos.copyBuffer().contains(k))
			pos.copyBuffer().remove(k);
		else
			pos.extendCopy(k);
		setup();
	}

	@Override
	public void onContextMenuClosed(Menu m) {
		context_item = null;
	}
	
	private void warn(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
			   .setTitle("Warning");
		
		AlertDialog d = builder.create();
		d.show();
	}

	private void addHandlers(final CheckBox cb, Button down, final Item k) {
		assert k != null;

		cb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				k.setFulfilled(!cb.isChecked(), AndroidDateFactory.now());
			}
		});
		down.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finishEditing();
				pos.moveDownTo(k);
				setup();
			}
		});
	}

	LayoutParams gridCoord(int r, int c) {
		LayoutParams ret = new GridLayout.LayoutParams();
		ret.rowSpec = GridLayout.spec(r, 1);
		ret.columnSpec = GridLayout.spec(c, 1);
		return ret;
	}

	private View setup_top_line() {
		final Item current = pos.current();
		LinearLayout toprow = new LinearLayout(this);
		if (!pos.current().isRoot()) {
			Button up = new Button(this);
			up.setText("▲");
			TextView name = new TextView(this);
			name.setText(pos.topline(current));
			name.setPadding(5, 5, 5, 5);
			name.setTextSize(20);
			name.setTextColor(Color.WHITE);
			if (pos.copyBuffer().contains(current))
				name.setBackgroundColor(selectedColor);
			add(toprow, up, name);
			up.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finishEditing();
					pos.moveUp();
					setup();
				}
			});
		}
		return toprow;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
		menu.add("new");
		menu.add("hide/show completed");
		menu.add("sort by name");
		menu.add("sort by date");		
		menu.add("select all");
//		menu.add("check all"); // these go to the selection menu
//		menu.add("uncheck all");
//		menu.add("clear selection");
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
//		int id = item.getItemId();
//		if (id == R.id.action_settings) {
//			return true;
//		}
		String op = item.getTitle().toString();
		if (op.equals("new")) {
			newKid();
		} else if (op.equals("hide/show completed")) {
			toggleShowCompleted();
		} else if (op.equals("sort by name")) {
			sort_by_name();
		} else if (op.equals("sort by date")) {
			sort_by_date();
		} else if (op.equals("select all")) {
			select_all();
		}
		
		return super.onOptionsItemSelected(item);
	}

	private void select_all() {
		finishEditing();
		for (Item k : pos.current()) {
			pos.extendCopy(k);
		}
		setup();
	}

	private void sort_by_date() {
		finishEditing();
		pos.sortKids(SortOrder.DUE_DATE);
		setup();
	}

	private void sort_by_name() {
		finishEditing();
		pos.sortKids(SortOrder.ALPHABETIC);
		setup();
	}

	private void toggleShowCompleted() {
		finishEditing();
		pos.toggleShowCompleted();
		setup();
	}
}
