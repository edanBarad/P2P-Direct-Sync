package com.tactical.p2p;

/**
 * Point - represents a 2D point with x and y coordinates.
 */
public class Point {
    private double x, y;

    // constructor
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // distance -- return the distance of this point to the other point
    public double distance(Point other) {
        return Math.sqrt(Math.pow((this.x-other.getX()),2) + Math.pow((this.y-other.getY()),2));
    }

    // equals -- return true is the points are equal, false otherwise
    public boolean equals(Point other) {
        final double EPSILON = 0.0001;
        return Math.abs(this.x - other.getX()) < EPSILON &&
                Math.abs(this.y - other.getY()) < EPSILON;
    }

    // Return the x and y values of this point
    public double getX() {
        return this.x;
    }
    public double getY() {
        return this.y;
    }

    //Setters
    public void setX(double x){
        this.x = x;
    }
    public void setY(double y){
        this.y = y;
    }
}
