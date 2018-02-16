package edu.cornell.cs.multilist;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.GridLayout.LayoutParams;
import android.widget.PopupMenu.OnMenuItemClickListener;
import edu.cornell.cs.multilist.model.*;
import edu.cornell.cs.multilist.model.Item.Warning;
import edu.cornell.cs.multilist.model.Position.DateAnalysis;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

	Model model;

	/** GUI-independent state */
	Position pos;
	int edit_row; // which row of the grid is being edited.
	boolean unsaved_update;

	LinearLayout outer;
	ScrollView scroller;
	//LinearLayout box;
	GridLayout grid;
	LinearLayout top_line, box, bottom_line;
	Map<Item, ViewGroup> itemPanes;
	Map<View, Item> items;
	Map<Item, Integer> vert_pos;
	Button select_menu;
	ViewGroup copy_buffer;
	TextView edit_text;
	TextView notes;
	Button clear_date;
	PopupWindow popup; // null if no popup

	static final String APP_STATE_KEY = "state";
	static final String PERSISTENT_STATE_KEY = "MultiList";
	static final String ENCODING_CHARSET = "ISO-8859-1";

	static final int selectedColor = Color.argb(255, 155, 147, 100),
			scrollerColor = Color.argb(255, 32, 32, 40),
			ikArrowColor = Color.argb(255, 32, 200, 255);

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
		restoreState(savedInstanceState);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		// outer : LL (V)
		//   top_line : LL (H)
		//   scroller : scrollview
		//   box: LL (V)
		//     grid: gridview
		//     notes: edittext
		//   bottom_line : LL (H)
		
		outer = new LinearLayout(this);
		outer.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams outer_params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		outer.setLayoutParams(outer_params);
		scroller = new ScrollView(this);
		LinearLayout.LayoutParams scroller_params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		scroller_params.weight = 1.0f;
		scroller.setLayoutParams(scroller_params);
		scroller.setBackgroundColor(scrollerColor);
		
		top_line = new LinearLayout(this);
		bottom_line = new LinearLayout(this);
		add(outer,  top_line, scroller, bottom_line);
		
		box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		add(scroller,  box);
		
		pos = new Position(model);

		setup();
		setContentView(outer);
	}

	private void restoreState(Bundle savedInstanceState) {
		if (savedInstanceState == null) {
			SharedPreferences prefs = getPreferences(MODE_PRIVATE);
			String val = prefs.getString(PERSISTENT_STATE_KEY, null);
			if (val != null) {
				Item dummy = new Item();
				model = new Model(dummy);
				int where = 0;
				try {
					// System.err.println("read string: " + val);
					byte[] bytes = ByteEncoder.decode(val);
					// System.err.println(" -> " + bytes.length + " bytes");
					// System.err.println("hash = " + md5(bytes));
					ObjectInputStream os = new MLObjectInputStream(new ByteArrayInputStream(bytes));
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
		} else if (savedInstanceState.containsKey(APP_STATE_KEY)) {
			Serializable s = savedInstanceState.getSerializable(APP_STATE_KEY);
			model = (Model) s;
		} else {
			Item root = new Item();
			root.setNote("Could not recover state from bundle!");
			model = new Model(root);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle savedState) {
		savedState.putSerializable(APP_STATE_KEY, model);
	}

	@Override
	protected void onPause() {
		finishEditing();
		super.onPause();
	}

	@Override
	protected void onStop() {
		SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit();

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			ObjectOutputStream os = new ObjectOutputStream(bout);

			os.writeObject(model);
			os.close();

			byte[] bytes = bout.toByteArray();
			// System.err.println("Generated " + bytes.length + " bytes");
			// System.err.println("hash = " + md5(bytes));

			String enc = ByteEncoder.encode(bytes);
			// System.err.println(" = " + enc.length() + " chars");
			// System.err.println("output: " + enc);
			edit.putString(PERSISTENT_STATE_KEY, enc);
			edit.commit();
		} catch (IOException e) {
			assert false;
		}
		super.onStop();
	}

    /** Set up all the views for the current model and position.
     */
	private void setup() {

		box.removeAllViews();

		itemPanes = new HashMap<>();
		items = new HashMap<View, Item>();
		vert_pos = new HashMap<Item, Integer>();
		
		setup_top_line();
		setup_item_rows();
		box.addView(grid);
	
		if (pos.current().isRoot() || !pos.current().isComplete())
			box.addView(setup_due_date());
		box.addView(setup_notes());
		
		setup_bottom_line();

		/* bring up soft keyboard -- why doesn't this work? */
		if (pos.isEditing()) {
			edit_text.requestFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(edit_text, InputMethodManager.SHOW_IMPLICIT);
			// edit_text.setShowSoftInputOnFocus(true); // API 21...

			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
			edit_text.requestFocus();
		}
	}

	private void setup_bottom_line() {
		bottom_line.removeAllViews();
		Button b1 = new Button(this);
		b1.setText("+1");
		b1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				newKid(true);
			}
		});
		Button b = new Button(this);
		b.setText("+∞");
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				newKid(false);
			}
		});
		add(bottom_line, b1, b);
		if (pos.isCopying())
			bottom_line.addView(setup_copy_buffer());
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
		// registerForContextMenu(row);

		t.setPadding(5, 5, 5, 5);
		row.setPadding(0, 10, 0, 0);
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
			for (Item k : pos.copyBuffer())
				pos.setCompleted(k, false, AndroidDateFactory.now());
			setup();
			return true;
		} else if (it.equals("uncheck")) {
			finishEditing();
			for (Item k : pos.copyBuffer())
				pos.setCompleted(k, true, AndroidDateFactory.now());
			setup();
			return true;
		}
		return false;
	}

	private View setup_notes() {
		notes = new EditText(this);
		notes.setText(pos.current().note());
		//notes.setInputType(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		// XXX somehow calling setInputType turns OFF multiline input
		notes.clearFocus();
		//notes.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)); // not needed
		return notes;
	}

	private void set_due_date(TextView d) {
		ItemDate id = pos.current().dueDate();
		String ds = id == null ? "none" : pos.dateString(id);
		d.setText("Due: " + ds);
		if (id == null)
			clear_date.setVisibility(View.INVISIBLE);
		else
			clear_date.setVisibility(View.VISIBLE);
	}

	private View setup_due_date() {
		final LinearLayout result = new LinearLayout(this);
		result.setOrientation(LinearLayout.VERTICAL);
		LinearLayout h = new LinearLayout(this);
		clear_date = new Button(this);
		clear_date.setText("clear");
		clear_date.setTextSize(12);

		// LayoutParams params = new LayoutParams();
		// params.setMargins(0, 0, 0, 20);
		// clear_date.setLayoutParams(params);
		final TextView d = new TextView(this);
		set_due_date(d);
		Space sp = new Space(this);
		sp.setMinimumWidth(50);
		add(h, d, sp, clear_date);
		add(result, h);
		final Calendar cal = Calendar.getInstance();
		if (pos.current().dueDate() != Item.NO_DATE) {
			cal.setTimeInMillis(pos.current().dueDate().getTimeMillis());
		}
		final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
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
				DatePickerDialog dia = new DatePickerDialog(MainActivity.this, date, cal.get(Calendar.YEAR),
						cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
				dia.getDatePicker().setSpinnersShown(false);
				dia.getDatePicker().setCalendarViewShown(true);
				dia.show();
			}
		});
		if (pos.current().numKids() > 0) {
			DateAnalysis r = pos.analyzeDates();
			if (r.first_k != null) {
				LinearLayout h2 = new LinearLayout(this), h3 = new LinearLayout(this);
				TextView t2 = new TextView(this);
				t2.setText("First due: " + r.first_k.name() + ", ");
				TextView ds1 = new TextView(this);
				ds1.setText(r.first.toString());
				add(h2, t2, ds1);
				add(result, h2);
				ItemDate now = AndroidDateFactory.now();
				if (r.first.isBefore(now))
					ds1.setTextColor(Color.RED);
				if (pos.current().dueDate() != null && r.first.isAfter(pos.current().dueDate()))
					ds1.setTextColor(Color.RED);

				if (pos.current().dueDate() != null && r.first_k != r.last_k) {
					TextView t3 = new TextView(this);
					t3.setText("Last due: " + r.last_k.name() + ", ");
					TextView ds3 = new TextView(this);
					ds3.setText(r.last.toString());
					add(h3, t3, ds3);
					add(result, h3);
					if (r.last.isAfter(pos.current().dueDate()))
						ds3.setTextColor(Color.RED);
				}
			}
		}
		clear_date.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pos.current().setDueDate(null);
				set_due_date(d);
			}
		});
		return result;
	}

	/** Initialize grid to contain a view of all the checked items */
	private void setup_item_rows() {
		grid = new GridLayout(this);
		final Item current = pos.current();
		int i = 0;

		for (Item k : pos.items()) {
			if (!current.showComplete() && k.isComplete())
				continue;
			ViewGroup h = new LinearLayout(this);
			itemPanes.put(k, h);
			vert_pos.put(k, i);
			grid.addView(h, gridCoord(i, 0));
			setup_row(k, i);
			i++;
		}
	
	}

	protected void newKid(boolean one_shot) {
		finishEditing();
		Item it = pos.createKid();
		if (one_shot) it.setRemoveOnFulfill(true);
		pos.setCompleted(it, false, AndroidDateFactory.now());
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
			tf.setInputType(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
			
			edit_text = tf;
			add(checkbox_area, cb = new CheckBox(this), tf);

			tf.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (keyCode == KeyEvent.KEYCODE_ENTER) {
						pos.finishEditing(tf.getText().toString());
						setup_row(k, i);
					}
					return false;
				}
			});

		} else {
			cb = new CheckBox(this);
			cb.setText(k.name());
			add(checkbox_area, cb);
		}
		cb.setChecked(!k.isComplete());

		ViewGroup buttons = new LinearLayout(this);

		grid.addView(buttons, gridCoord(i, 2));
		down = new Button(this);
		down.setText("►");
		if (k.hasIncompleteKids()) {
			down.setTextColor(ikArrowColor);
		}
		add(buttons, down);
		addHandlers(cb, down, k);
		items.put(cb, k);
		items.put(checkbox_area, k);
		items.put(row, k);

		registerForContextMenu(checkbox_area);
		registerForContextMenu(cb);
		registerForContextMenu(row);
	}

	@Override
	public void onCreateContextMenu(ContextMenu c, View v, ContextMenuInfo inf) {
		Item k = items.get(v);
		if (k == context_item)
			return; // avoid duplicating items
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
		builder.setMessage(message).setTitle("Warning");

		AlertDialog d = builder.create();
		d.show();
	}

	private void addHandlers(final CheckBox cb, Button down, final Item k) {
		assert k != null;

		cb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (k.hasIncompleteKids()) {
					navigateDownTo(k);
					return;					
				}
				boolean removed = pos.setCompleted(k, !cb.isChecked(), AndroidDateFactory.now());
				if (removed || !pos.showCompleted()) {
					finishEditing();
					setup();
				}
			}
		});
		down.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				navigateDownTo(k);
			}
		});
	}
	
	void navigateDownTo(Item k) {
		finishEditing();
		pos.moveDownTo(k);
		setup();
	}

	LayoutParams gridCoord(int r, int c) {
		LayoutParams ret = new GridLayout.LayoutParams();
		ret.rowSpec = GridLayout.spec(r, 1);
		ret.columnSpec = GridLayout.spec(c, 1);
		return ret;
	}

	private View setup_top_line() {
		final Item current = pos.current();
		LinearLayout toprow = top_line;
		top_line.removeAllViews();
		TextView name = new TextView(this);
		if (!pos.current().isRoot()) {
			Button up = new Button(this);
			up.setText("▲");
			name.setText(pos.topline(current));
			add(toprow, up);
			up.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					navigateUp();
				}
			});
		}
	
		name.setPadding(5, 5, 5, 5);
		name.setTextSize(18);
		name.setTextColor(Color.WHITE);
		LinearLayout.LayoutParams expander = new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT);
		expander.weight = 1.0f;
		name.setLayoutParams(expander);
		add(toprow, name);

		if (pos.copyBuffer().contains(current))
			name.setBackgroundColor(selectedColor);
		
		ToggleButton tb = new ToggleButton(this);
		add(toprow, tb);
		tb.setLayoutParams(new LinearLayout.LayoutParams(250, LinearLayout.LayoutParams.MATCH_PARENT));
		tb.setTextOn("all");
		tb.setTextOff("all");
		tb.setChecked(current.showComplete());
		tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				finishEditing();
				current.setShowComplete(isChecked);
				setup();
			}
		});

		return toprow;
	}
	
	void navigateUp() {
		finishEditing();
		Item cur = pos.current();
		pos.moveUp();
		setup();
        scrollTo(cur);
	}
    private void scrollTo(Item cur) {
	    final ViewGroup v = itemPanes.get(cur);
        if (v != null) {
            scroller.post(new Runnable() {
                @Override
                public void run() {
                    scroller.scrollTo(0, v.getTop());
                }
            });
        }
    }

    void beep() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add("new");
		menu.add("hide/show completed");
		menu.add("sort by name");
		menu.add("sort by date");
		menu.add("select all");
		menu.add("import...");
		menu.add("export...");
		return true;
	}
	
	public void onBackPressed() {
		if (popup != null) {
			popup.dismiss();
			popup = null;
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String op = item.getTitle().toString();
		if (op.equals("new")) {
			newKid(false);
		} else if (op.equals("hide/show completed")) {
			toggleShowCompleted();
		} else if (op.equals("sort by name")) {
			sort_by_name();
		} else if (op.equals("sort by date")) {
			sort_by_date();
		} else if (op.equals("select all")) {
			select_all();
		} else if (op.equals("import...")) {
			import_items();
		} else if (op.equals("export...")) {
			export_items();
		}
		return super.onOptionsItemSelected(item);
	}
	
	static class FilePopup {
		EditText prompt;
		PopupWindow popup;
	}
	
	FilePopup createFilePopup(String message) {
		LinearLayout content = new LinearLayout(this);
		content.setBackgroundColor(Color.DKGRAY);
		content.setOrientation(LinearLayout.VERTICAL);
		TextView msg = new TextView(this);
		msg.setText(message);
		final EditText file_prompt = new EditText(this);
		file_prompt.setMaxLines(1);
		file_prompt.setInputType(InputType.TYPE_CLASS_TEXT);
		content.addView(file_prompt);
		content.addView(msg);
		final PopupWindow pw = new PopupWindow(content);
		pw.showAtLocation(outer, Gravity.TOP, 10, 10);
		pw.setOutsideTouchable(true);
		pw.setFocusable(true);
		Display display = getWindowManager().getDefaultDisplay();
		
		int screenWidth = display.getWidth();
		int screenHeight = display.getHeight();
		pw.update(screenWidth-20, screenHeight/4);
		popup = pw;
		
		FilePopup ret = new FilePopup();
		ret.prompt = file_prompt;
		ret.popup = pw;
		return ret;
	}

	private void export_items() {
		final FilePopup fpop = createFilePopup("Export item to file:");
		
		fpop.prompt.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER) {			
//					IO.exportToFile(model.root, popup.prompt.getText().toString());
					IO.exportToFile(pos.current(), fpop.prompt.getText().toString());

					fpop.popup.dismiss();
					return true;
				}
				return false;
			}			
		});
	}

	private void import_items() {
		final FilePopup fpop = createFilePopup("Import from file:");
		fpop.prompt.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER) {	
					Object read_obj = IO.importFromFile(fpop.prompt.getText().toString());
					fpop.popup.dismiss();
					if (read_obj instanceof Model) {
						System.out.println("read a model successfully?");
						// Models should be merged.
						model = (Model) read_obj;
						pos = new Position(model);
					} else if (read_obj instanceof Item) {
						pos.current().addKid((Item) read_obj);
					}
					setup();
					return true;
				}
				return false;
			}			
		});
	}

	private void select_all() {
		finishEditing();
		for (Item k : pos.current())
			pos.extendCopy(k);
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
