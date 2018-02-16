package edu.cornell.cs.multilist.model;

import java.time.LocalDate;

public class FXDateFactory {
	public static ItemDate now() {
		return new LocalItemDate(LocalDate.now());
	}
	public static ItemDate create(LocalDate d) {
		return new LocalItemDate(d);
	}
}
