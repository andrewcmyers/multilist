package edu.cornell.cs.multilist;

public class ByteEncoder {
	/** Encode b into printable ASCII chars. Needed because Base64 is
	 * not available in Java 7!?
	 */
	static String encode(byte[] b) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < b.length; i++) {
			byte bi = b[i];
			if (bi >= ' ' && bi < '~' && bi != '\\') {
				s.append((char)bi);
			} else {
				int code = (int)bi & 0xFF;
				s.append('\\');
				s.append(hexdigit(code >> 4));
				s.append(hexdigit(code & 0xf));
			}
		}
		String result = s.toString();
//		assert same_array(decode(result), b);
		return result;
	}
		
	static final char[] hexdigits = {'0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static char hexdigit(int i) {
		assert i >= 0 && i < 16;
		return hexdigits[i];
	}
	private static int fromhex(char c) {
		assert (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
		if (c >= '0' && c <= '9') return (int)c - 48;
		return (int)c + (10 - 97);
	}
	static boolean same_array(byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		for (int i = 0; i < a.length; i++)
			if (a[i] != b[i]) return false;
		return true;
	}
	/** Decode from printable ASCII chars. */
	static byte[] decode(String s) {
		byte[] b = new byte[4];
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			byte bi;
			if (c != '\\') {
				bi = (byte) c;
			} else {
				char h = s.charAt(++i);
				char l = s.charAt(++i);
				bi = (byte)(fromhex(h) * 16 + fromhex(l));
			}
			if (b.length == n) {
				byte[] b2 = new byte[n*2];
				System.arraycopy(b, 0, b2, 0, n);
				b = b2;
			}
			b[n++] = bi;
		}
		byte[] res = new byte[n];
		System.arraycopy(b, 0, res, 0, n);
		assert encode(res).equals(s);
		return res;
	}	
}
