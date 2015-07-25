package edu.cornell.cs.multilist.model;

import java.util.Date;

public class AndroidDateFactory {
	public static ItemDate now() {
		return new MilliDate(new Date().getTime());
	}
	public static ItemDate create(long millis) {
		return new MilliDate(millis);
	}
}
