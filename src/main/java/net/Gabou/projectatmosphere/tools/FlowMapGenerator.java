package net.Gabou.projectatmosphere.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class FlowMapGenerator {
    public static void main(String[] args) throws Exception {
        int size = 512;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double u = (x / (double) size) - 0.5;
                double v = (y / (double) size) - 0.5;
                double angle = Math.atan2(v, u);
                // encode unit-vector into RG [0..1]
                float fx = (float)((Math.cos(angle) * 0.5) + 0.5);
                float fz = (float)((Math.sin(angle) * 0.5) + 0.5);

                int r = (int)(fx * 255);
                int g = (int)(fz * 255);
                int rgb = (r << 16) | (g << 8);
                img.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(img, "PNG", new File("flowmap.png"));
    }
}
