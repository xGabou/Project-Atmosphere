package net.Gabou.projectatmosphere.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class TornadoMesh {
    private static int vao = -1;
    private static int vbo = -1;

    public static void init() {
        if (vao != -1) return; // already initialized

        // Vertex buffer with a single point at origin
        FloatBuffer vertexData = BufferUtils.createFloatBuffer(3);
        vertexData.put(0f).put(0f).put(0f).flip();

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STATIC_DRAW);

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(0); // Location 0
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GL30.glBindVertexArray(0); // unbind
    }

    public static void drawInstanced(int count) {
        GL30.glBindVertexArray(vao);
        GL31.glDrawArraysInstanced(GL11.GL_POINTS, 0, 1, count); // draw 1 point, instanced `count` times
        GL30.glBindVertexArray(0);
    }

    public static int uploadTornadoSSBO(Vec3 pos, float radius, int count) {
        ByteBuffer buf = BufferUtils.createByteBuffer(6 * 4 * count); // 6 fields * 4 bytes per entry

        for (int i = 0; i < count; i++) {
            buf.putInt(i); // side ID (0–19)
            buf.putFloat((float) pos.x);
            buf.putFloat((float) pos.y);
            buf.putFloat((float) pos.z);
            buf.putFloat(radius);
            buf.putFloat(1.0f); // brightness
        }

        buf.flip();

        int ssbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buf, GL15.GL_STATIC_DRAW);
        return ssbo;
    }
    public static void drawTestTriangle() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0, 1, 0).color(255, 0, 0, 255).endVertex();
        buffer.vertex(-1, -1, 0).color(0, 255, 0, 255).endVertex();
        buffer.vertex(1, -1, 0).color(0, 0, 255, 255).endVertex();
        tesselator.end();
    }

}

