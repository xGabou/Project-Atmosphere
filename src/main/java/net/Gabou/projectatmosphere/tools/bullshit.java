package net.Gabou.projectatmosphere.tools;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;

public class bullshit {
    public static void main(String[] args) {
        BigInteger limit = new BigInteger("1000000");
        BigInteger i = BigInteger.ZERO;

        // Buffered writer = fast output
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)), false);
        int wordsPerLine = 100;  // 100 words per line
        String word = "merde ";

        while (i.compareTo(limit) < 0) {
            StringBuilder line = new StringBuilder();
            // Show progress block number
            line.append("[").append(i.divide(BigInteger.valueOf(wordsPerLine)).add(BigInteger.ONE)).append("] ");

            for (int j = 0; j < wordsPerLine && i.compareTo(limit) < 0; j++) {
                line.append(word);
                i = i.add(BigInteger.ONE);
            }

            out.println(line);
        }

        out.flush();  // Ensure everything is printed
    }
}
