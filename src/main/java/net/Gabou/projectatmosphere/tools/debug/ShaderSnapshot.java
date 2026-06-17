package net.Gabou.projectatmosphere.tools.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nonamecrackers2.simpleclouds.client.mesh.generator.CloudMeshGenerator;
import dev.nonamecrackers2.simpleclouds.client.renderer.CloudImageRenderer;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Utility for capturing GPU shader outputs (procedural textures / framebuffers)
 * into PNG files. Must be called on the client render thread.
 */
public class ShaderSnapshot {

    /**
     * Capture the currently bound framebuffer (screen) into a PNG.
     */
    public static void captureFramebuffer(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        saveBufferAsPNG(buffer, width, height, fileName);
    }

    /**
     * Capture a specific GPU texture (e.g. an FBO color attachment) into a PNG.
     *
     * @param texId    the OpenGL texture ID
     * @param width    width of the texture
     * @param height   height of the texture
     * @param fileName name of the PNG (saved in .minecraft folder)
     */
    public static void captureTexture(int texId, int width, int height, String fileName) {
        RenderSystem.bindTexture(texId);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        saveBufferAsPNG(buffer, width, height, fileName);

        CloudMeshGenerator generator = SimpleCloudsRenderer.getInstance().getMeshGenerator(); // cumulonimbus generator
        File path = new File(Minecraft.getInstance().gameDirectory, "cloud_dumps");

        if (!path.exists())
            path.mkdirs();
// create renderer for one snapshot
        CloudImageRenderer renderer = CloudImageRenderer.basicIsometric(path, generator);

// set background (black or transparent)
        renderer.setBgCol(0f, 0f, 0f);

        renderer.initialize();
        renderer.render();
        renderer.exportToRenderedImage(msg -> Minecraft.getInstance().player.sendSystemMessage(msg));
        renderer.finalize();
        renderer.close();
    }

    private static void saveBufferAsPNG(ByteBuffer buffer, int width, int height, String fileName) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // flip Y because OpenGL origin = bottom-left
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + y * width) * 4;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                int a = buffer.get(i + 3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, height - y - 1, argb);
            }
        }

        try {
            File out = new File(Minecraft.getInstance().gameDirectory, fileName + ".png");
            ImageIO.write(image, "PNG", out);
            System.out.println("[ShaderSnapshot] Saved: " + out.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
