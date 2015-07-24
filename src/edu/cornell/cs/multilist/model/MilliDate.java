package edu.cornell.cs.multilist.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MilliDate implements ItemDate, Serializable {
	private static final long serialVersionUID = -6412064705166329629L;
	long millis; // epoch milliseconds
	
	public MilliDate(long m) {
		millis = m;
	}
	@Override
	public boolean isBefore(ItemDate d) {
		MilliDate dd = (MilliDate) d;
		return millis < dd.millis;
	}

	@Override
	public boolean isAfter(ItemDate d) {
		MilliDate dd = (MilliDate) d;
		return millis > dd.millis;
	}

	@Override
	public int compareTo(ItemDate d) {
		MilliDate dd = (MilliDate) d;
		if (millis  < dd.millis) return -1;
		if (millis > dd.millis) return 1;
		return 0;
	}

	@Override
	public ItemDate plusDays(int days) {
		MilliDate ret = new MilliDate(0);
		ret.millis = millis + 86400L * 1000L;
		return ret;
	}

	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd");
		return sdf.format(new Date(millis));
	}
	@Override
	public long getTimeMillis() {
		return millis;
	}
}
