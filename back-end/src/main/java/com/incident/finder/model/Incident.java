package com.incident.finder.model;

public class Incident {

    private int id;
    private String title;
    private String description;
    private String resolution;

    public Incident() {
    }

    public Incident(int id, String title, String description, String resolution) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.resolution = resolution;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
