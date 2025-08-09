package net.Gabou.projectatmosphere.modules.core;

public record WindVector(float baseSpeed, float angleRadians, float gustSpeed) {

    public WindVector add(WindVector other) {
        return new WindVector(
                this.baseSpeed + other.baseSpeed,
                this.angleRadians + other.angleRadians,
                this.gustSpeed + other.gustSpeed
        );
    }

    public WindVector subtract(WindVector other) {
        return new WindVector(
                this.baseSpeed - other.baseSpeed,
                this.angleRadians - other.angleRadians,
                this.gustSpeed - other.gustSpeed
        );
    }

    public WindVector divide(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than zero");
        }
        return new WindVector(
                this.baseSpeed / count,
                this.angleRadians / count,
                this.gustSpeed / count
        );
    }

    
    public static WindVector fromBase(float baseSpeed, float angleRadians) {
        return new WindVector(baseSpeed, angleRadians, baseSpeed);
    }
}
