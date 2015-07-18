package multilist;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
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
import multilist.Position.Warning;

/** GUI state associated with the current view. */
public class FxView {
	VBox box;
	GridPane grid;
	Position pos;
	StringProperty edit_text;
	int edit_row;
	HashMap<Item, Pane> itemPanes;
	Set<Item> selected = new HashSet<>();
	Button select_menu;
	Pane copy_buffer;
	private Stage stage;
		
	FxView(Model m, Stage s) {
		box = new VBox();
		pos = new Position(m);
		itemPanes = new HashMap<>();
		stage = s;
		setup();
		assert invariant();
	}
	boolean invariant() {
		assert pos.invariant();
		for (Item i : pos.items()) {
			assert itemPanes.get(i) != null;
		}
		assert itemPanes.size() == pos.current().numKids();
		return true;
	}

	String topline(Item k) {
		StringBuilder b = new StringBuilder();
		b.append("   ");
		b.append(k.name());
		boolean first = true;
		for (Item i : k.parents()) {
			if (i == pos.model().root) continue;
			b.append(first ? "→" : ", ");
			first = false;
			b.append(i.name());
		}
		return b.toString();
	}

	void setup() {
		final ObservableList<Node> c = box.getChildren();
		c.clear();
		selected.clear();
		c.add(setup_top_line());
		setup_item_rows();
		c.add(grid);
		if (!pos.current().isRoot()) {
			if (!pos.current().fulfilled) {
				HBox h = new HBox();
				add(h, new Text("Due:"));
				final TextField t = new TextField(pos.dateString(pos.current().dueDate()));
				add (h,t);
				c.add(h);
				
				t.setOnAction(new EventHandler<ActionEvent> (){

					@Override
					public void handle(ActionEvent arg0) {
						try {
							Date d = Position.dateFormat.parse(t.getText());
							pos.current().setDate(d);
						} catch (ParseException e) {
							warn("could not parse date " + e.getMessage());
						}
						
					}
					
				});
//				c.add(new TextField(pos.dateString(pos.current().dueDate())));
				
			}
			final TextArea t = new TextArea(pos.current().note());
			t.getStyleClass().add("notes");
			EventHandler<Event> updateNotes = new EventHandler<Event>() {
				@Override
				public void handle(Event e) {
					pos.current().setNote(t.getText());
				}
			};
			t.setOnKeyTyped(updateNotes);
			t.setOnMouseExited(updateNotes);
			c.add(t);
		}
		HBox global_menus = new HBox();
		add(global_menus, setup_selection_menu(), setup_filtering_menu());
		c.add(global_menus);
		setup_copy_buffer(c);
	}
	
	private Node setup_top_line() {
		final Item current = pos.current();
		HBox toprow = new HBox();
		if (!current.isRoot()) {
			Button up;
			add(toprow, up = new Button("▲"), new Text(topline(current)));
			up.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent a) {
					finishEditing();
					pos.moveUp();
					setup();
				}
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
			if (!current.showFulfilled && k.fulfilled) continue;
			Pane h = new HBox();
			itemPanes.put(k, h);
			grid.add(h, 0, i);
			setupRow(k, i);
			i++;
		}
		Button b = new Button("+");
		b.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent a) {
				finishEditing();
				Item it = pos.addKid();
				it.setFulfilled(false);
				itemPanes.put(it, new HBox());
				pos.startEditing(it);
				edit_row = current.numKids() - 1;
				setup();
			}
		});
		grid.add(b, 2, i);
	}
	private Node setup_selection_menu() {
		final ContextMenu global_menu = new ContextMenu();
		MenuItem remove = new MenuItem("remove selected");
		MenuItem copy = new MenuItem("copy selected");
		MenuItem clear = new MenuItem("clear selection");
		MenuItem select_all = new MenuItem("select all");
		global_menu.getItems().addAll(remove, copy, clear, select_all);
		select_menu = new Button("☰");

		select_menu.getStyleClass().add("global_menu");

		select_menu.setOnMousePressed(new EventHandler<MouseEvent>(){
			@Override public void handle(MouseEvent me) {
				global_menu.show(select_menu, me.getScreenX(), me.getScreenY());
			}
		});
		remove.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				try {
					finishEditing();
					for (Item k : selected)
						pos.current().removeKid(k);
					setup();
				} catch (Warning w) {
					warn(w.getMessage());
				}
			}});
		clear.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				selected.clear();
				pos.copying = false;
				setup();				
			}
		});
		select_all.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				int i = 0;
				for (Item k : pos.items()) {
					if (!itemPanes.containsKey(k)) continue;
					selected.add(k);
					setupRow(k, i++);
				}				
			}	
		});
		copy.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				for (Item k : selected) {
					pos.extendCopy(k);
				}
				setup();
			}			
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
		MenuItem sort_name = new MenuItem("alphabetize");
		filtering_menu.getItems().addAll(completed, sort_name, sort_date);
		final Button b = new Button("☰");

		b.getStyleClass().add("filter_menu");

		b.setOnMousePressed(new EventHandler<MouseEvent>(){
			@Override public void handle(MouseEvent me) {
				filtering_menu.show(b, me.getScreenX(), me.getScreenY());
			}
		});
		completed.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				pos.toggleShowCompleted();
				setup();
			}
		});
		sort_date.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				pos.current().sortKids(SortOrder.DUE_DATE);
				setup();
			}	
		});
		sort_name.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				pos.current().sortKids(SortOrder.ALPHABETIC);
				setup();
			}	
		});
		return b;
	}
	
	/** i is the vertical position at which the item is inserted into the grid. */
	private void setupRow(final Item k, final int i) {
		final Pane row = itemPanes.get(k);
		assert row != null;
		
		boolean edited = pos.isEditing(k);
		final Item current = pos.current();
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
			tf.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent e) {
					k.setName(tf.getText());
					pos.editing = false;
					setupRow(k, i);
				}		
			});
			tf.selectAll();
			tf.end();
			tf.requestFocus();
		} else {	
			add(checkbox_area, cb = new CheckBox(k.name()));
			if (selected.contains(k)) cb.getStyleClass().add("selected");
		}
		Region spacer = new Region();
		add(checkbox_area, spacer);
		HBox.setHgrow(spacer, Priority.ALWAYS);
		spacer.setMinWidth(Region.USE_PREF_SIZE);

		cb.setSelected(!k.fulfilled);
		
		HBox buttons = new HBox();
		grid.add(buttons,  2,  i);
		add(buttons, down = new Button("‣"));
		addHandlers(cb, down, k);
		
		final Button menu;
		final ContextMenu cmenu = new ContextMenu();
		MenuItem remove = new MenuItem("remove");
		MenuItem edit = new MenuItem("edit");
		MenuItem copy = new MenuItem("copy");
		cmenu.getItems().addAll(edit, copy, remove);
		add(buttons, menu = new Button("☰"));

		menu.setOnMousePressed(new EventHandler<MouseEvent>(){
			@Override
			public void handle(MouseEvent me) {
				cmenu.show(cb, me.getScreenX(), me.getScreenY());
			}
		});
		remove.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent e) {
				// TODO should check for whether it's okay to remove, really.
				finishEditing();
				try {
					current.removeKid(k);
					setup();
				} catch (Warning w) {
					warn(w.getMessage());
				}
			}
		});
		edit.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent e) {
				if (pos.isEditing(k)) return; // already editing this name!
				finishEditing();
				pos.startEditing(k);

				setupRow(k, i);
			}
		});
		cb.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override public void handle(MouseEvent me) {
				if (me.isMetaDown()) {
					if (selected.contains(k)) {
						cb.getStyleClass().remove("selected");
						selected.remove(k);
					} else {
						cb.getStyleClass().add("selected");
						selected.add(k);
					}
				}
			}
		});
		copy.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent a) {
				finishEditing();
				pos.extendCopy(k);
				setup();
			}			
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
	    
	    t.setOnFinished(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent arg0) {
				popup.hide();
			}
	    });
	    t.play();
	}
	
	private void setup_copy_buffer(ObservableList<Node> c) {
		if (!pos.copying) return;
		Region r = new Region();
		c.add(r);
		r.setMinHeight(15);
		copy_buffer = new VBox();
		c.add(copy_buffer);
		Text t = new Text(item_desc(pos.copy_buffer));
		
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
		
		clear.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent a) {
				pos.copying = false;
				setup();
			}
		});
		paste.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent arg0) {
				try {
					pos.doCopy();
					setup();
				} catch (Warning w) {
					warn(w.getMessage());
				}
			}			
		});
		
	}
	
	void finishEditing() {
		if (pos.editing) {
			pos.finishEditing(edit_text.getValue());
			setupRow(pos.edit_item, edit_row);				
		}
	}
	

	private void addHandlers(final CheckBox cb, Button down, final Item k) {
		assert k != null;
		cb.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent e) {
				k.setFulfilled(!cb.isSelected());
			}
		});

		if (down != null) down.setOnAction(new EventHandler<ActionEvent>() {
			@Override public void handle(ActionEvent e) {
				finishEditing();
				pos.moveDownTo(k);
				setup();
			}});
	}
}
