package edu.cornell.cs.multilist.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class LocalItemDate implements ItemDate, Serializable {
	private static final long serialVersionUID = -1520291420027734223L;
	private LocalDate date;
	public static ZoneOffset zone_offset;
	
	static {
			LocalDateTime dt = LocalDateTime.now();
			ZoneId zid = ZoneId.systemDefault();
			ZonedDateTime zdt = dt.atZone(zid);
			zone_offset = zdt.getOffset();
	//		System.out.println("zone offset = " + zone_offset);
			assert zone_offset != null;
		}

	LocalItemDate(LocalDate d) {
		date = d;
	}
	LocalItemDate(long millis) {
		Instant i = Instant.ofEpochMilli(millis);
		LocalDateTime ldt = LocalDateTime.from(i.atOffset(zone_offset));
		date = ldt.toLocalDate();
	}
	
	@Override
	public boolean isBefore(ItemDate d) {
		return compareTo(d) < 0;
	}

	@Override
	public boolean isAfter(ItemDate d) {
		return compareTo(d) > 0;
	}

	@Override
	public ItemDate plusDays(int days) {
		return new LocalItemDate(localDate().plusDays(days));
	}

	@Override
	public int compareTo(ItemDate d) {
		if (d instanceof LocalItemDate) {
			LocalItemDate ld = (LocalItemDate) d;
			return localDate().compareTo(ld.localDate());
		} else {
			return -1; // XXX
		}
	}
	
	static DateTimeFormatter date_format =
			DateTimeFormatter.ofPattern("MMMM d");

	public String toString() {
		return localDate().format(date_format);
	}
	
	public LocalDate localDate() {
		return date;
	}
}
