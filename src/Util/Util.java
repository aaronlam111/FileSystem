package Util;
public class Util {
    public static byte[] marshal(String send) {
        return send.getBytes();
    }

    public static String unmarshal(byte[] receive) {
        return new String(receive).trim();
    }
}


