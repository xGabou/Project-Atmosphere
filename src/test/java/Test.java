import java.io.*;

public class Test {
    public static void main(String[] args) throws Exception {
        InputStream in = Test.class.getClassLoader()
                .getResourceAsStream("projectatmosphere.mixins.json");

        if (in == null) {
            System.out.println("FILE NOT FOUND");
            return;
        }

        System.out.println("FILE FOUND");

        byte[] first20 = new byte[20];
        int len = in.read(first20);

        System.out.println("First bytes:");
        for (int i = 0; i < len; i++) {
            System.out.printf("%02X ", first20[i]);
        }
        System.out.println();
    }
}
