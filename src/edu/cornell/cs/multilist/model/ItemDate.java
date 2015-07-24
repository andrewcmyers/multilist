package edu.cornell.cs.multilist.model;

public interface ItemDate {
	boolean isBefore(ItemDate d);
	boolean isAfter(ItemDate d);
	int compareTo(ItemDate d);
	ItemDate plusDays(int days);
}
