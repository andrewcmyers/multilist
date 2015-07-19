package multilist;

import java.io.Serializable;

/** A Model represents the persistent state associated with a given user's view of the system. */
public class Model implements Serializable {
//	private static final long serialVersionUID = 660521719328234318L;
	public final Item root = Item.root();
	
	static void exampleModel() {
		Item root = Item.root();
		Item grocery = new Item("grocery", root);
		Item hardware = new Item("hardware", root);
		new Item("carrots", grocery);
		new Item("hammer", hardware);
		Item detergent = new Item("detergent", grocery);
		hardware.addKid(detergent);
		new Item("nails", hardware);
	}
}
