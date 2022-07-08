package io.game.logic.game;

import java.util.List;

public class GameData {
    public String player;
    public String direction;
    public String nick;
    public Integer kills;
    public int life;
    public int skin;
    public int index;
    public String id;
    public String playerId;
    public String damage;

    public String playerIdAttack;
    public Position position;
    public List<Player> playersON;


    public String getPlayerIdAttack() {
        return playerIdAttack;
    }

    public void setPlayerIdAttack(String playerIdAttack) {
        this.playerIdAttack = playerIdAttack;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
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

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
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