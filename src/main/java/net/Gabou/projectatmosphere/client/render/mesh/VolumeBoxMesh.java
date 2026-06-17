package net.Gabou.projectatmosphere.client.render.mesh;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

public final class VolumeBoxMesh implements AutoCloseable {
    private VertexBuffer buffer;

    public void ensureInitialized() {
        if (this.buffer != null) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // Front (+Z)
        vertex(builder, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        vertex(builder, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        vertex(builder, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        vertex(builder, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F);

        // Back (-Z)
        vertex(builder, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        vertex(builder, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        vertex(builder, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        vertex(builder, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F);

        // Left (-X)
        vertex(builder, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        vertex(builder, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        vertex(builder, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        vertex(builder, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);

        // Right (+X)
        vertex(builder, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        vertex(builder, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        vertex(builder, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        vertex(builder, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F);

        // Top (+Y)
        vertex(builder, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
        vertex(builder, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F);
        vertex(builder, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        vertex(builder, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);

        // Bottom (-Y)
        vertex(builder, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        vertex(builder, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        vertex(builder, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        vertex(builder, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F);

        this.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.buffer.bind();
        this.buffer.upload(builder.end());
        VertexBuffer.unbind();
    }

    public void draw(ShaderInstance shader, Matrix4f modelViewMat, Matrix4f projMat) {
        this.ensureInitialized();
        this.buffer.bind();
        this.buffer.drawWithShader(modelViewMat, projMat, shader);
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v) {
        builder.vertex(x, y, z).uv(u, v).endVertex();
    }
}
