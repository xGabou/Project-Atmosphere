package net.Gabou.projectatmosphere.modules.core;

public record WindVector(float speed, float angleRadians) {

    public WindVector add(WindVector other) {
        return new WindVector(this.speed + other.speed, this.angleRadians + other.angleRadians);
    }
    public WindVector subtract(WindVector other) {
        return new WindVector(this.speed - other.speed, this.angleRadians - other.angleRadians);
    }

    public WindVector divide(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than zero");
        }
        return new WindVector(this.speed / count, this.angleRadians / count);
    }
}