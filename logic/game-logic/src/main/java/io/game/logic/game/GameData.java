package io.game.logic.game;

import java.util.List;

public class GameData {
    public String player_d;
    public String direction;
    public String nick;
    public Integer kills;
    public int life;
    public int skin;
    public int index;
    public String id;
    public String player_id;
    public String damage;

    public String player_id_attack;
    public Position position;
    public List<Player> playersON;


    public String getPlayer_id_attack() {
        return player_id_attack;
    }

    public void setPlayer_id_attack(String player_id_attack) {
        this.player_id_attack = player_id_attack;
    }

    public String getPlayer_d() {
        return player_d;
    }

    public void setPlayer_d(String player_d) {
        this.player_d = player_d;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public Integer getKills() {
        return kills;
    }

    public void setKills(Integer kills) {
        this.kills = kills;
    }

    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        this.life = life;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlayer_id() {
        return player_id;
    }

    public void setPlayer_id(String player_id) {
        this.player_id = player_id;
    }

    public String getDamage() {
        return damage;
    }

    public void setDamage(String damage) {
        this.damage = damage;
    }

    public Position getPosition() {
        return position;
    }

    public List<Player> getPlayersON() {
        return playersON;
    }

    public void setPlayersON(List<Player> playersON) {
        this.playersON = playersON;
    }
}