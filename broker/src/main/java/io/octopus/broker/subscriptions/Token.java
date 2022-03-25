package io.octopus.broker.subscriptions;

import java.util.Objects;

/**
 * Internal use only class.
 */
public class Token {

    static final Token EMPTY = new Token("");
    public static final Token MULTI = new Token("#");
    public static final Token SINGLE = new Token("+");
    public static final Token ALL = new Token("*");
    public final String name;

    public Token(String s) {
        name = s;
    }

    protected String name() {
        return name;
    }

    protected boolean match(Token t) {
        if (MULTI.equals(t) || SINGLE.equals(t)) {
            return false;
        }

        if (MULTI.equals(this) || SINGLE.equals(this)) {
            return true;
        }

        return equals(t);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Token other = (Token) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
