package net.Gabou.projectatmosphere.client.render.pipeline;

public final class VanillaPipelineAdapter implements AtmospherePipelineAdapter {
    public static final VanillaPipelineAdapter INSTANCE = new VanillaPipelineAdapter();

    private VanillaPipelineAdapter() {
    }

    @Override
    public String id() {
        return "projectatmosphere:vanilla";
    }
}
