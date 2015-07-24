package edu.cornell.cs.multilist.model;

import java.io.Serializable;

/** A Model represents the persistent state associated with a given user's view of the system. */
public class Model implements Serializable {
	private static final long serialVersionUID = 8560376537976534311L;
	public final Item root;
	
	public Model(Item r) {
		root = r;
	}
	
	/** Make the empty model into the example model. */
	public void exampleModel() {
		Item grocery = new Item("grocery", root);
		Item hardware = new Item("hardware", root);
		new Item("carrots", grocery);
		new Item("hammer", hardware);
		Item detergent = new Item("detergent", grocery);
		hardware.addKid(detergent);
		new Item("nails", hardware);
	}
}
