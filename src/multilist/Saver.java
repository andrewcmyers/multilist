package multilist;

public interface Saver {
	boolean fileChosen();
	public void save() throws SaveFailed;
}
