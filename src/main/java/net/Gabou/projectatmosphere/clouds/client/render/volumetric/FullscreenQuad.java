package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Shared static fullscreen quad for the volumetric cloud passes. */
public final class FullscreenQuad {
    private static VertexBuffer quad;

    private FullscreenQuad() {
    }

    public static void draw(ShaderInstance shader) {
        ensure();
        if (quad == null) {
            return;
        }
        quad.bind();
        quad.drawWithShader(new Matrix4f(), new Matrix4f(), shader);
        VertexBuffer.unbind();
    }

    public static void shutdown() {
        if (quad != null) {
            quad.close();
            quad = null;
        }
    }

    private static void ensure() {
        if (quad != null) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(builder.end());
        VertexBuffer.unbind();
    }
}
