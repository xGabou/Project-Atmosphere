package net.Gabou.projectatmosphere.command.tree;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Loads Simple Clouds command implementations only after the backend is present. */
final class OptionalSimpleCloudsCommands {
    private static final String TYPE_NAME =
            "net.Gabou.projectatmosphere.compat.simpleclouds.SimpleCloudsCommandTrees";

    private OptionalSimpleCloudsCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tornado() {
        return invokeTree("tornado");
    }

    static LiteralArgumentBuilder<CommandSourceStack> hurricane() {
        return invokeTree("hurricane");
    }

    static void addAliases(LiteralArgumentBuilder<CommandSourceStack> root) {
        invoke("addAliases", new Class<?>[]{LiteralArgumentBuilder.class}, root);
    }

    @SuppressWarnings("unchecked")
    private static LiteralArgumentBuilder<CommandSourceStack> invokeTree(String methodName) {
        return (LiteralArgumentBuilder<CommandSourceStack>) invoke(methodName, new Class<?>[0]);
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class<?> type = Class.forName(TYPE_NAME, true, OptionalSimpleCloudsCommands.class.getClassLoader());
            Method method = type.getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Simple Clouds command " + methodName + " failed", cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Simple Clouds command integration failed to load", exception);
        }
    }
}
