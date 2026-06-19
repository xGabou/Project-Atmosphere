package net.Gabou.projectatmosphere.clouds.client.render;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import java.util.Locale;

/**
 * OpenGL debug helpers for the PA cloud renderer.
 * Uses KHR_debug when available and always drains glGetError around critical passes.
 */
public final class CloudGlDebug {
    private static volatile boolean initialized;
    private static volatile Callback debugCallback;

    private CloudGlDebug() {
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        var capabilities = GL.getCapabilities();
        if (capabilities == null || !capabilities.GL_KHR_debug) {
            initialized = true;
            return;
        }

        try {
            debugCallback = GLUtil.setupDebugMessageCallback();
        } catch (Throwable throwable) {
            ProjectAtmosphere.LOGGER.warn("[CloudGL] Failed to install KHR_debug callback.", throwable);
        } finally {
            initialized = true;
        }
    }

    public static void pushGroup(String label) {
        if (!isDebugGroupSupported() || label == null || label.isBlank()) {
            return;
        }
        GL43.glPushDebugGroup(GL43.GL_DEBUG_SOURCE_APPLICATION, 0, label);
    }

    public static void popGroup() {
        if (!isDebugGroupSupported()) {
            return;
        }
        GL43.glPopDebugGroup();
    }

    public static void checkErrors(String context) {
        boolean anyError = false;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            anyError = true;
            ProjectAtmosphere.LOGGER.warn(
                    "[CloudGL] error context={} code=0x{} name={}",
                    context,
                    String.format(Locale.ROOT, "%04X", error),
                    decodeError(error)
            );
        }

        if (!anyError && ProjectAtmosphere.DEBUG_MODE) {
            return;
        }
    }

    private static boolean isDebugGroupSupported() {
        var capabilities = GL.getCapabilities();
        return capabilities != null && capabilities.GL_KHR_debug;
    }

    private static String decodeSource(int source) {
        return switch (source) {
            case GL43.GL_DEBUG_SOURCE_API -> "API";
            case GL43.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "WINDOW_SYSTEM";
            case GL43.GL_DEBUG_SOURCE_SHADER_COMPILER -> "SHADER_COMPILER";
            case GL43.GL_DEBUG_SOURCE_THIRD_PARTY -> "THIRD_PARTY";
            case GL43.GL_DEBUG_SOURCE_APPLICATION -> "APPLICATION";
            case GL43.GL_DEBUG_SOURCE_OTHER -> "OTHER";
            default -> "UNKNOWN(" + source + ")";
        };
    }

    private static String decodeType(int type) {
        return switch (type) {
            case GL43.GL_DEBUG_TYPE_ERROR -> "ERROR";
            case GL43.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "DEPRECATED_BEHAVIOR";
            case GL43.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "UNDEFINED_BEHAVIOR";
            case GL43.GL_DEBUG_TYPE_PORTABILITY -> "PORTABILITY";
            case GL43.GL_DEBUG_TYPE_PERFORMANCE -> "PERFORMANCE";
            case GL43.GL_DEBUG_TYPE_OTHER -> "OTHER";
            case GL43.GL_DEBUG_TYPE_MARKER -> "MARKER";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    private static String decodeSeverity(int severity) {
        return switch (severity) {
            case GL43.GL_DEBUG_SEVERITY_HIGH -> "HIGH";
            case GL43.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM";
            case GL43.GL_DEBUG_SEVERITY_LOW -> "LOW";
            case GL43.GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION";
            default -> "UNKNOWN(" + severity + ")";
        };
    }

    private static String decodeError(int error) {
        return switch (error) {
            case GL11.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11.GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW";
            case GL11.GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW";
            case GL11.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION";
            default -> "UNKNOWN(" + error + ")";
        };
    }
}
