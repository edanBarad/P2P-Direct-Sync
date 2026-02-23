package com.tactical.p2p;

public class Line {
    private Point start = new Point(0, 0);
    private Point end = new Point(0, 0);;
    private double slope;
    // constructors
    public Line(Point start, Point end) {
        this.start = start;
        this.end = end;
        setSlope();
    }
    public Line(double x1, double y1, double x2, double y2) {
        this.start = new Point(x1, y1);
        this.end = new Point(x2, y2);
        setSlope();
    }

    private void setSlope(){
        if (this.start.equals(this.end)) this.slope = Double.NaN;
        else if (this.start.getX() == this.end.getX()){
            this.slope = Double.POSITIVE_INFINITY;      //Geometry.Line is vertical
        }else if (this.start.getY() == this.end.getY()){
            this.slope = 0.0;
        }else {
            this.slope = (this.start.getY() - this.end.getY()) / (this.start.getX() - this.end.getX());
        }
    }

    public void setStart(Point start){
        this.start = start;
        setSlope();
    }

    public void setEnd(Point end){
        this.end = end;
        setSlope();
    }

    public double getSlope(){
        return this.slope;
    }

    // Return the length of the line
    public double length() {
        return this.start.distance(this.end);
    }

    // Returns the middle point of the line
    public Point middle() {
        return new Point((this.start.getX()+this.end.getX())/2, (this.start.getY()+this.end.getY())/2);
    }

    // Returns the start point of the line
    public Point start() {
        return this.start;
    }

    // Returns the end point of the line
    public Point end() {
        return this.end;
    }

    // Returns true if the lines intersect, false otherwise
    public boolean isIntersecting(Line other) {
        if (this.slope == other.getSlope()){
            if (this.isOnLine(other.start) || this.isOnLine(other.end) || other.isOnLine(this.start) || other.isOnLine(this.end)) return true;
            return false;
        }
        else{
            Point intersection = this.intersectionWith(other);
            if (intersection == null) return false;             //Intersection is out of bounds
            else return this.isOnLine(intersection) && other.isOnLine(intersection);
        }
    }

    // Returns the intersection point if the lines intersect,
    // and null otherwise.
    public Point intersectionWith(Line other) {
        if (this.start.equals(other.start) || this.start.equals(other.end)) return this.start;
        else if (this.end.equals(other.start) || this.end.equals(other.end)) return this.end;
        if (this.slope == other.getSlope()) {   //Parallel lines
            return null;
        }
        double x, y;
        //Check vertical lines
        if (this.slope == Double.POSITIVE_INFINITY) {
            // This line is vertical, x is fixed
            x = this.start.getX();
            // Calculate y using the other line's equation
            y = other.getSlope() * (x - other.start().getX()) + other.start().getY();
        } else if (other.getSlope() == Double.POSITIVE_INFINITY) {
            // Other line is vertical, x is fixed
            x = other.start().getX();
            // Calculate y using this line's equation
            y = this.slope * (x - this.start.getX()) + this.start.getY();
        } else {
            // Neither line is vertical, use standard formula(Shown on tablet)
            x = (this.slope*this.start.getX() - this.start.getY() - other.getSlope()*other.start().getX() + other.start.getY()) / (this.slope - other.getSlope());
            y = this.slope*(x-this.start.getX()) + this.start.getY();
        }
        Point point = new Point(x, y);
        if (this.isOnLine(point) && other.isOnLine(point)) return point;
        else return null;
    }

    // equals -- return true is the lines are equal, false otherwise
    public boolean equals(Line other) {
        if (this.start.equals(other.start())){
            return this.end.equals(other.end());
        } else if (this.start.equals(other.end())) {
            return this.end.equals(other.start());
        }
        return false;
    }

    //This function will return if a given point is on the line using the formula (y-y1)=m(x-x1)
    public boolean isOnLine(Point point){
        final double EPSILON = 0.000000001;
        if (this.slope == Double.POSITIVE_INFINITY){
            return (Math.abs(point.getX() - this.start.getX()) < EPSILON &&
                    point.distance(this.middle()) <= this.start.distance(this.middle()) + EPSILON);
        } else if (this.slope == 0.0){
            return (Math.abs(point.getY() - this.start.getY()) < EPSILON &&
                    point.distance(this.middle()) <= this.start.distance(this.middle()) + EPSILON);
        }
        if (Math.abs((point.getY()-this.start.getY()) - (this.slope*(point.getX()-this.start.getX()))) < EPSILON){
            return point.distance(this.middle()) <= this.start.distance(this.middle()) + EPSILON;
        }
        return false;
    }

}