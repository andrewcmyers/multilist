package edu.cornell.cs.multilist.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class MLObjectInputStream extends ObjectInputStream {

	public MLObjectInputStream(InputStream in) throws IOException {
		super(in);
		enableResolveObject(true);
	}
	
	protected Class<?> resolveClass(ObjectStreamClass oc) throws ClassNotFoundException, IOException {
//		System.out.print("resolving " + oc.getName());
		Class<?> ret;
		if (oc.getSerialVersionUID() == -1641355975895301388L)
			ret = Class.forName("multilist.v1.Item");
		else if (oc.getSerialVersionUID() == 6674947740041255296L)
			ret = Class.forName("multilist.v1.Model");
//		else if (oc.getSerialVersionUID() == -7216453968867906427L)
//			ret = Class.forName("edu.cornell.cs.multilist.model.v2.Item");
		else if (oc.getSerialVersionUID() == 8977719630296637624L)
			ret = Class.forName("edu.cornell.cs.multilist.model.Item");
		else if (oc.getSerialVersionUID() == 8560376537976534311L)
			ret = Class.forName("edu.cornell.cs.multilist.model.Model");
		else if (oc.getSerialVersionUID() == -1520291420027734223L)
			ret = Class.forName("edu.cornell.cs.multilist.model.LocalItemDate");
		else ret = super.resolveClass(oc);
		
//		System.out.println(" to " + ret);

		return ret;
	}
}
