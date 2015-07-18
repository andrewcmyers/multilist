package multilist;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {
	Model model;
	
	static final int WIDTH = 320;
	static final int HEIGHT = 540;
	
	public static void main(String[] args) {
		Application.launch(args);
	}

	public void start(Stage stage) {
		stage.setTitle("MultiList");
		Pane p = new VBox();
		Scene sc = new Scene(p);
		stage.setScene(sc);
		sc.getStylesheets().add("style.css");
		
		model = new Model();
		Model.createDummyModel();
		p.getChildren().add(new FxView(model, stage).box);
		stage.sizeToScene();
		stage.setHeight(HEIGHT);
		stage.setWidth(WIDTH);
		stage.show();
	}

}
