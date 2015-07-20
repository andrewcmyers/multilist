package multilist;

public interface Saver {
	boolean fileChosen();
	
	/** Do a save. */
	public void save() throws SaveFailed;

}
