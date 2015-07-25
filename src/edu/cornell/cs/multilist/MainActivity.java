package edu.cornell.cs.multilist;

import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import edu.cornell.cs.multilist.model.AndroidDateFactory;
import edu.cornell.cs.multilist.model.Item;
import edu.cornell.cs.multilist.model.Item.Warning;
import edu.cornell.cs.multilist.model.ItemDate;
import edu.cornell.cs.multilist.model.Model;
import edu.cornell.cs.multilist.model.Position;

public class MainActivity extends Activity {

	Model model;

	/** GUI-independent state */
	Position pos;
	int edit_row; // which row of the grid is being edited.
	boolean unsaved_update;
	Set<Item> selected = new HashSet<Item>();

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
	
	static final int selectedColor = Color.argb(255, 0, 255, 255);
	
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
//					System.err.println("Found a String of " +
//							val.length() + " chars");
//					System.err.println("string: " + val);
					byte[] bytes = ByteEncoder.decode(val);
//					System.err.println("read = " + bytes.length + " bytes");
//					System.err.println("hash = " + md5(bytes));
					where = 1;
					ObjectInputStream os = new ObjectInputStream(
							new ByteArrayInputStream(bytes));
					where = 2;
//					System.err.println("calling readObject..." + bytes);
					model = (Model) os.readObject();
					where = 3;
					
//					System.err.println("completed");
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
		
		ScrollView sv = new ScrollView(this);
		box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		sv.addView(box);
	
		grid = new GridLayout(this);
		setContentView(sv);
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
		selected.clear();
		itemPanes = new HashMap<Item, ViewGroup>();
		items = new HashMap<View, Item>();
		vert_pos = new HashMap<Item, Integer>();
		box.addView(setup_top_line());
		setup_item_rows();
		box.addView(grid);
		if (!pos.current().isFulfilled())
			box.addView(setup_due_date());
		box.addView(setup_notes());
//		LinearLayout global_menus = new LinearLayout(this);
//
//		add(global_menus, setup_selection_menu(), setup_filtering_menu());
//		add(box, global_menus);
//		setup_copy_buffer();
	}

	private void setup_copy_buffer() {
		// TODO Auto-generated method stub
	}

	private View setup_selection_menu() {
		// TODO Auto-generated method stub
		return null;
	}

	private View setup_filtering_menu() {
		// TODO Auto-generated method stub
		return null;
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
				finishEditing();
				Item it = pos.createKid();
				pos.setFulfilled(it, false, AndroidDateFactory.now());
				itemPanes.put(it, new LinearLayout(MainActivity.this));
				pos.startEditing(it);
				edit_row = current.numKids() - 1;
				setup();	
			}
		});
		grid.addView(b, gridCoord(i, 2));
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
		items.put(checkbox_area,  k);
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
            if (selected.contains(k))
            	row.setBackgroundColor(selectedColor);
		}
		cb.setChecked(!k.isFulfilled());

		ViewGroup buttons = new LinearLayout(this);

		grid.addView(buttons, gridCoord(i, 2));
		down = new Button(this);
		down.setText("▶");
		add(buttons, down);
		addHandlers(cb, down, k);

		registerForContextMenu(checkbox_area);
	}
	@Override
	public
	void onCreateContextMenu(ContextMenu c, View v, ContextMenuInfo inf) {
		System.err.println("cfeating ctxt menu");
		c.add("edit");
		c.add("copy");
		c.add("remove");
		context_view = v;
	}
	View context_view;
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		//This hook is called whenever an item in a context menu is selected.
		String op = (String) item.getTitle();
		System.err.println("op = " + op);
		Item k = items.get(context_view);
		if (op.equals("edit")) {
			finishEditing();
			System.err.println("edited item is " + k);
			pos.startEditing(k);
			System.err.println("at vert pos " + vert_pos.get(k));
			setup_row(k, vert_pos.get(k));
		} else if (op.equals("remove")) {
			finishEditing();
			try {
				pos.removeKid(k);
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}
		}
		return true;
	}

//	final Button menu;
//	final ContextMenu cmenu = new ContextMenu();
//	MenuItem remove = new MenuItem("remove");
//	MenuItem edit = new MenuItem("edit");
//	MenuItem copy = new MenuItem("copy");
//	cmenu.getItems().addAll(edit, copy, remove);
//	add(buttons, menu = new Button("☰"));
//
//	menu.setOnMousePressed(me ->
//	cmenu.show(cb, me.getScreenX(), me.getScreenY()));

//	edit.setOnAction(e -> {
//		if (pos.isEditing(k)) return; // already editing this name!
//		finishEditing();
//		pos.startEditing(k);
//		setupRow(k, i);}
//			);
//	cb.setOnMouseClicked(me -> {
//		if (me.isMetaDown()) {
//			if (selected.contains(k)) {
//				row.getStyleClass().remove("selected");
//				row.getStyleClass().add("unselected");
//
//				row.setStyle(" -fx-background-color: transparent"); // should not be necessary
//				selected.remove(k);
//			} else {
//				row.getStyleClass().remove("unselected");
//				row.getStyleClass().add("selected");
//				row.setStyle("");
//				selected.add(k);
//			}
//		}}
//			);
//	copy.setOnAction(a -> {
//		finishEditing();
//		pos.extendCopy(k);
//		setup();
//	});
//}
	
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
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
