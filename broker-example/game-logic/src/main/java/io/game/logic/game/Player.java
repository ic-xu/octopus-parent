package io.game.logic.game;


public class Player {

    private int id;

    private String nick ;
    private int skin;
    private int index;
    private Position  position;
    private int life;
    private int kills=0;

    public Player(int id, String nick, int skin, int index, Position position) {
        this.id = id;
        this.nick = nick;
        this.skin = skin;
        this.index = index;
        this.position = position;

    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getKills() {
        return kills;
    }

    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        this.life = life;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public int getSkin() {
        return skin;
    }

    public void setSkin(int skin) {
        this.skin = skin;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", nick='" + nick + '\'' +
                ", skin='" + skin + '\'' +
                ", index='" + index + '\'' +
                ", position='" + position + '\'' +
                '}';
    }
}
