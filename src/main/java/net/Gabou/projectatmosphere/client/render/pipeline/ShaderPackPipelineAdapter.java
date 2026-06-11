package net.Gabou.projectatmosphere.client.render.pipeline;

public final class ShaderPackPipelineAdapter implements AtmospherePipelineAdapter {
    public static final ShaderPackPipelineAdapter INSTANCE = new ShaderPackPipelineAdapter();

    private ShaderPackPipelineAdapter() {
    }

    @Override
    public String id() {
        return "projectatmosphere:shader_pack_safe";
    }

    @Override
    public boolean isShaderSafe() {
        return true;
    }
}
