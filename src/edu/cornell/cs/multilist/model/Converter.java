package edu.cornell.cs.multilist.model;

import java.util.HashMap;
import java.util.Map;


public class Converter {

	public static Model convert(multilist.v1.Model m) {
		Map<multilist.v1.Item, Item> items = new HashMap<multilist.v1.Item, Item>();
		Model ret = new Model(convertItem(items, m.root));
		System.out.println("Converted!");
		
		return ret;
	}

	private static Item convertItem(Map<multilist.v1.Item, Item> items, multilist.v1.Item it1) {
		if (items.containsKey(it1)) return items.get(it1);
		Item it = new Item();
		if (it1.dueDate() != null)
			it.setDueDate(DateFactory.create(it1.dueDate()));
			
		it.setName(it1.name());
		it.setNote(it1.note());
		it.setFulfilled(it1.isFulfilled());
		for (multilist.v1.Item k : it1) {
			Item k2 = convertItem(items, k);
			it.addKid(k2);
		}
		return it;
	}

}
