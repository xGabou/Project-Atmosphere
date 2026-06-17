package net.Gabou.projectatmosphere.modules.ocean;

import net.minecraft.server.level.ServerLevel;

/**
 * Immutable simulation context shared by all basin influences during a tick.
 */
public record OceanUpdateContext(ServerLevel level, long gameTime, float deltaHours) {
}
