package edu.cornell.cs.multilist;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import edu.cornell.cs.multilist.model.FXDateFactory;
import edu.cornell.cs.multilist.model.Item;
import edu.cornell.cs.multilist.model.Item.Warning;
import edu.cornell.cs.multilist.model.ItemDate;
import edu.cornell.cs.multilist.model.LocalItemDate;
import edu.cornell.cs.multilist.model.Model;
import edu.cornell.cs.multilist.model.Position;
import edu.cornell.cs.multilist.model.Position.DateAnalysis;
import edu.cornell.cs.multilist.model.SortOrder;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/** GUI state associated with the current view. */
public class FxView {
	Position pos;
	int edit_row; // which row of the grid is being edited.
	boolean unsaved_update;
	Set<Item> selected = new HashSet<>();

	VBox box;
	GridPane grid;
	StringProperty edit_text;
	HashMap<Item, Pane> itemPanes;
	Button select_menu;
	Pane copy_buffer;
	private Stage stage;
	Saver saver;
		
	FxView(Model m, Stage s, Saver saver) {
		this.saver = saver;
		box = new VBox();
		box.getStyleClass().add("toplevel");
		pos = new Position(m);
		pos.addObserver(new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				unsaved_update = true;
			}
		});
		itemPanes = new HashMap<>();
		stage = s;
		setup_periodic_save();
		setup();
		assert invariant();
	}

	boolean invariant() {
		assert pos.invariant();
		assert itemPanes.size() <= pos.current().numKids();
		return true;
	}
	private void setup_periodic_save() {
		final PauseTransition delay = new PauseTransition(Duration.seconds(10));
		delay.setCycleCount(1);
		delay.setOnFinished(a -> {
			try {
				if (unsaved_update) {
					saver.save();
					warn("Autosaved.");
				}
				unsaved_update = false;
				delay.playFromStart();
			} catch (SaveFailed s) {
				warn(s.getMessage());
			}
		});
		delay.play();
	}

	void setup() {
		final ObservableList<Node> c = box.getChildren();
		c.clear();
		selected.clear();
		c.add(setup_top_line());
		setup_item_rows();
		c.add(grid);
		if (!pos.current().isFulfilled())
			c.add(setup_due_date());

		c.add(setup_notes());

		HBox global_menus = new HBox();
		add(global_menus, setup_selection_menu(), setup_filtering_menu());
		c.add(global_menus);
		setup_copy_buffer(c);
	}
	
	private Node setup_due_date() {
		VBox v = new VBox();
		HBox h = new HBox();
		add(v, h);
		add(h, new Text("Due:"));
		
		LocalItemDate date = (LocalItemDate) pos.current().dueDate();
		final DatePicker datePicker = new DatePicker();
		add(h, datePicker);

		if (date != null)
			datePicker.setValue(date.localDate());
		datePicker.setOnAction(e -> {
			LocalDate d = datePicker.getValue();
				pos.current().setDueDate(FXDateFactory.create(d));
		});
		
		ItemDate now = FXDateFactory.now();

		if (pos.current().numKids() > 0) {
			DateAnalysis r = pos.analyzeDates();
			if (r.first_k != null) {
				HBox h2 = new HBox(), h3 = new HBox();

				add(h2, new Text("First due: " + r.first_k.name() + ", "));
				Label ds1 = new Label(r.first.toString());
				add(h2, ds1);
				if (r.first.isBefore(now))
					ds1.getStyleClass().add("overdue");
				if (pos.current().dueDate() != null && r.first.isAfter(pos.current().dueDate()))
					ds1.getStyleClass().add("too_late");

				if (pos.current().dueDate() != null && r.first_k != r.last_k) {
					add(h3, new Text("Last due: "+ r.last_k.name() + ", "));
					Label ds = new Label(r.last.toString());
					add(h3, ds);
					if (r.last.isAfter(pos.current().dueDate()))
						ds.getStyleClass().add("too_late");
				}
				add(v, h2, h3);
			}	
		}
		return v;
	}
	
	private Node setup_notes() {
		final TextArea t = new TextArea(pos.current().note());
		t.getStyleClass().add("notes");	

		t.setOnKeyTyped(e -> pos.setNote(t.getText()));
		t.setOnMouseExited(e -> pos.setNote(t.getText()));
		return t;
	}

	private Node setup_top_line() {
		final Item current = pos.current();
		HBox toprow = new HBox();
		if (!current.isRoot()) {
			Button up;
			add(toprow, up = new Button("▲"), new Text(pos.topline(current)));
			up.setOnAction(a -> {
				finishEditing();
				pos.moveUp();
				setup();
			});
		}
		return toprow;
	}

	/** Set 'grid' to refer to the pane containing the item checklist. */
	private void setup_item_rows() {
		grid = new GridPane();
		final Item current = pos.current();
		int i = 0;

		for (Item k : pos.items()) {
			if (!current.showFulfilled && k.isFulfilled()) continue;
			Pane h = new HBox();
			itemPanes.put(k, h);
			grid.add(h, 0, i);
			setupRow(k, i);
			i++;
		}
		Button b = new Button("+");
		b.setOnAction(a -> {
			finishEditing();
			Item it = pos.createKid();
			pos.setFulfilled(it, false);
			itemPanes.put(it, new HBox());
			pos.startEditing(it);
			edit_row = current.numKids() - 1;
			setup();
		});
		grid.add(b, 2, i);
	}
	private Node setup_selection_menu() {
		final ContextMenu selection_menu = new ContextMenu();
		MenuItem remove = new MenuItem("remove selected");
		MenuItem copy = new MenuItem("copy selected");
		MenuItem check = new MenuItem("check selected");
		MenuItem uncheck = new MenuItem("uncheck selected");
		MenuItem clear = new MenuItem("clear selection");
		MenuItem select_all = new MenuItem("select all");
		selection_menu.getItems().addAll(select_all, check, uncheck, copy, remove, clear);
		select_menu = new Button("☰");

		select_menu.getStyleClass().add("select_menu");

		select_menu.setOnMousePressed(me ->
			selection_menu.show(select_menu, me.getScreenX(), me.getScreenY()));

		remove.setOnAction(a -> {
			try {
				finishEditing();
				for (Item k : selected)
					pos.removeKid(k);
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}
		});
		clear.setOnAction(a-> {
			selected.clear();
			pos.stopCopying();
			setup();				
		});
		select_all.setOnAction(a -> {
			finishEditing();
			int i = 0;
			for (Item k : pos.items()) {
				if (!itemPanes.containsKey(k)) continue;
				selected.add(k);
				setupRow(k, i++);
			}				
		});
		copy.setOnAction(a -> {
			finishEditing();
			for (Item k : selected) {
				pos.extendCopy(k);
			}
			setup();
		});
		check.setOnAction(a -> {
			finishEditing();
			for (Item k: selected)
				pos.setFulfilled(k, false);
			setup();

		});
		uncheck.setOnAction(a -> {
			finishEditing();
			for (Item k: selected)
				pos.setFulfilled(k, true);
			setup();
		});

		return select_menu;
	}
	
	private Node setup_filtering_menu() {
		final ContextMenu filtering_menu = new ContextMenu();
		MenuItem completed;
		if (pos.showCompleted()) {
			completed = new MenuItem("hide completed");
		} else {
			completed = new MenuItem("show completed");
		}
		MenuItem sort_date = new MenuItem("sort by date");
		MenuItem sort_name = new MenuItem("sort by name");
		filtering_menu.getItems().addAll(completed, sort_name, sort_date);
		final Button b = new Button("☰");

		b.getStyleClass().add("filter_menu");

		b.setOnMousePressed(me ->
		filtering_menu.show(b, me.getScreenX(), me.getScreenY()));
		completed.setOnAction(a -> {
			finishEditing();
			pos.toggleShowCompleted();
			setup();
		});
		sort_date.setOnAction(a -> {
			finishEditing();
			pos.sortKids(SortOrder.DUE_DATE);
			setup();
		});
		sort_name.setOnAction(a -> {
			finishEditing();
			pos.sortKids(SortOrder.ALPHABETIC);
			setup();
		});
		return b;
	}
	
	/** i is the vertical position at which the item is inserted into the grid. */
	private void setupRow(final Item k, final int i) {
		final Pane row = itemPanes.get(k);
		assert row != null;
		
		boolean edited = pos.isEditing(k);
		Button down = null;

		row.getChildren().clear();
		final CheckBox cb;
		HBox checkbox_area = new HBox();
		checkbox_area.getStyleClass().add("checkbox");
		checkbox_area.setMinWidth(180);
	
		add(row, checkbox_area);

		if (edited) {
			final TextField tf = new TextField(k.name());
			edit_text = tf.textProperty();
			add(checkbox_area, cb = new CheckBox(), tf);
			tf.setOnAction(e -> {
				pos.finishEditing(tf.getText());
				setupRow(k, i);
			});
			tf.selectAll();
			tf.end();
			tf.requestFocus();
		} else {	
			add(checkbox_area, cb = new CheckBox(k.name()));
			if (selected.contains(k)) row.getStyleClass().add("selected");
		}
		Region spacer = new Region();
		add(checkbox_area, spacer);
		HBox.setHgrow(spacer, Priority.ALWAYS);
		spacer.setMinWidth(Region.USE_PREF_SIZE);

		cb.setSelected(!k.isFulfilled());
		
		HBox buttons = new HBox();
		grid.add(buttons,  2,  i);
		add(buttons, down = new Button("▶"));
		addHandlers(cb, down, k);
		
		final Button menu;
		final ContextMenu cmenu = new ContextMenu();
		MenuItem remove = new MenuItem("remove");
		MenuItem edit = new MenuItem("edit");
		MenuItem copy = new MenuItem("copy");
		cmenu.getItems().addAll(edit, copy, remove);
		cmenu.getStyleClass().add("item_menu");
		add(buttons, menu = new Button("☰"));
		menu.getStyleClass().add("item_menu_button");

		menu.setOnMousePressed(me ->
			cmenu.show(cb, me.getScreenX(), me.getScreenY()));
		remove.setOnAction(e -> {
			finishEditing();
			try {
				pos.removeKid(k);
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}});
		edit.setOnAction(e -> {
			if (pos.isEditing(k)) return; // already editing this name!
			finishEditing();
			pos.startEditing(k);
			setupRow(k, i);}
				);
		row.setOnMouseClicked(me -> {
			if (me.isMetaDown()) {
				if (selected.contains(k)) {
					row.getStyleClass().remove("selected");
					row.getStyleClass().add("unselected");

//					row.setStyle(" -fx-background-color: transparent"); // needed in JavaFX 2.2!?
					selected.remove(k);
				} else {
					row.getStyleClass().remove("unselected");
					row.getStyleClass().add("selected");
//					row.setStyle(""); // needed in JavaFX 2.2!?
					selected.add(k);
				}
			}}
				);
		copy.setOnAction(a -> {
			finishEditing();
			pos.extendCopy(k);
			setup();
		});
	}

	String item_desc(Set<Item> s) {
		StringBuilder b = new StringBuilder();
		b.append(s.size());
		if (s.size() == 1) b.append(" item");
		else b.append(" items");
		return b.toString();
	}
	
	static Pane add(Pane p, Node n) {
		p.getChildren().add(n);
		return p;
	}
	static Pane add(Pane p, Node... n) {
		p.getChildren().addAll(n);
		return p;
	}
	private void warn(String string) {
		final Popup popup = new Popup();
		Label lab = new Label(string);
		lab.getStyleClass().add("warning");
		popup.getContent().add(lab);
	    popup.setHideOnEscape(true);
	    popup.show(stage);
	    
	    Timeline t = new Timeline(1);
	    t.getKeyFrames().add(new KeyFrame(new Duration(2.0)));
	    
	    t.setOnFinished(e -> popup.hide());
	    t.play();
	}
	
	private void setup_copy_buffer(ObservableList<Node> c) {
		if (!pos.isCopying()) return;
		Region r = new Region();
		c.add(r);
		r.setMinHeight(15);
		copy_buffer = new VBox();
		c.add(copy_buffer);
		Text t = new Text(item_desc(pos.copyBuffer()));
		
		HBox h = new HBox();
		VBox r2 = new VBox();
		add(r2, t);
		add(h, r2);
		add(copy_buffer, h);
		Button paste = new Button("paste");
		paste.getStyleClass().add("paste");
		Button clear = new Button("clear");
		clear.getStyleClass().add("clear");
		add(h, paste, clear);

		r2.setPadding(new Insets(2));
		r2.getStyleClass().add("copied");
		
		clear.setOnAction(a -> {
			pos.stopCopying();
			setup();
		});
		
		paste.setOnAction(ae -> {
			try {
				pos.doCopy();
				setup();
			} catch (Warning w) {
				warn(w.getMessage());
			}
		});
	}
	
	void finishEditing() {
		if (pos.isEditing()) {
			pos.finishEditing(edit_text.getValue());
			setupRow(pos.editItem(), edit_row);				
		}
	}

	private void addHandlers(final CheckBox cb, Button down, final Item k) {
		assert k != null;
		cb.setOnAction(e -> k.setFulfilled(!cb.isSelected()));
		if (down != null) down.setOnAction(e -> {
			finishEditing();
			pos.moveDownTo(k);
			setup();
		});
	}
}
