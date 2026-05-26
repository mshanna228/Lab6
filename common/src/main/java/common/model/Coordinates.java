package common.model;

import java.io.Serializable;

/**
 * Класс координат работника.
 * x — максимальное значение 709, не может быть null.
 * y — значение должно быть больше -414, не может быть null.
 */
public class Coordinates implements Serializable, Comparable<Coordinates> {
    private static final long serialVersionUID = 1L;

    private Integer x; // Максимальное значение поля: 709, Поле не может быть null
    private Double y;  // Значение поля должно быть больше -414, Поле не может быть null

    public Coordinates(Integer x, Double y) {
        setX(x);
        setY(y);
    }

    public Coordinates() {}

    public Integer getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    public void setX(Integer x) {
        if (x == null) throw new IllegalArgumentException("X не может быть null");
        if (x > 709) throw new IllegalArgumentException("X не может быть больше 709");
        this.x = x;
    }

    public void setY(Double y) {
        if (y == null) throw new IllegalArgumentException("Y не может быть null");
        if (y <= -414) throw new IllegalArgumentException("Y должен быть строго больше -414");
        this.y = y;
    }

    /**
     * Сравнение по местоположению: сначала по X, затем по Y.
     */
    @Override
    public int compareTo(Coordinates other) {
        int cmpX = this.x.compareTo(other.x);
        if (cmpX != 0) return cmpX;
        return this.y.compareTo(other.y);
    }

    @Override
    public String toString() {
        return "Coordinates{x=" + x + ", y=" + y + "}";
    }
}
