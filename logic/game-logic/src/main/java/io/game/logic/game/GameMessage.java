package io.game.logic.game;

public class GameMessage {

    public String action;
    public String time;
    public GameData data;
    public boolean error;
    public String message;

    public GameMessage(String action, String time, GameData data, boolean error, String message) {
        this.action = action;
        this.time = time;
        this.data = data;
        this.error = error;
        this.message = message;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public GameData getData() {
        return data;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

