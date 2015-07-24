package edu.cornell.cs.multilist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import edu.cornell.cs.multilist.model.Converter;
import edu.cornell.cs.multilist.model.Item;
import edu.cornell.cs.multilist.model.Model;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class JavaFX extends Application {
	Model model;
	static String filename = null;
	
	static final int WIDTH = 320;
	static final int HEIGHT = 540;
	
	public static void main(String[] args) {
		if (args.length == 1) {
			filename = args[0];
		} else if (args.length > 1) {
			System.out.println("Usage: multilist <filename>");
			System.exit(1);
		}
		Application.launch(args);
	}

	public void start(Stage stage) {
		stage.setTitle("MultiList");
		Pane p = new VBox();
		Scene sc = new Scene(p);
		stage.setScene(sc);
		sc.getStylesheets().add("style.css");
		
		
		if (filename == null) {
			Item root = new Item();
			model = new Model(root);
			model.exampleModel();
		} else {
			try {
				ObjectInputStream input = new MLObjectInputStream(new FileInputStream(filename));
				Object m = input.readObject();
//				System.out.println("Read " + m);
				if (m instanceof Model) {
					model = (Model) m;
				} else if (m instanceof multilist.v1.Model) {
					System.out.println("This is a version 1 model.");
					model = Converter.convert((multilist.v1.Model) m);
				}
//				System.out.println("Read successful.");

				input.close();
			} catch (FileNotFoundException e) {
				model = new Model(new Item());
				try {
					new Save().save();
				} catch (SaveFailed e2) {
					System.out.println(e2.getMessage());
					System.exit(1);
				}
				// create new file...
			} catch (IOException|ClassNotFoundException e) {
				System.out.println(e.getMessage());
				System.exit(1);
			} catch (ClassCastException e) {
				System.out.println("Not a saved model: " + e.getMessage());
				System.exit(1);
			}
		}

		p.getChildren().add(new FxView(model, stage, new Save()).box);
		stage.sizeToScene();
		stage.setHeight(HEIGHT);
		stage.setWidth(WIDTH);
		stage.show();
	}

	private class Save implements Saver {
		public boolean fileChosen() {
			return true;
		}
		@Override public void save() throws SaveFailed {
			OutputStream outs;
			if (filename == null)
				filename = "dummy.mlist";
			try {
				File newf = new File(filename + ".tmp");
				outs = new FileOutputStream(newf);
		
				ObjectOutputStream out = new ObjectOutputStream(outs);
				
				out.writeObject(model);
				
				out.flush();
				out.close();
				File old = new File(filename);
				newf.renameTo(old);
			} catch (FileNotFoundException e) {
				throw new SaveFailed("Unable to create " + filename + " : "
						+ e.getMessage());
			} catch (IOException e) {
				throw new SaveFailed("I/O exception accessing "
						+ filename + ": " +e.getMessage());
			}
		}
	}

}
