package io.octopus.kernel.kernel;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/5 08:40
 */



public class SessionCreationResult {

     private DefaultSession session;

     private CreationModeEnum mode;

     private Boolean alreadyStored;


    public SessionCreationResult(DefaultSession session, CreationModeEnum mode, Boolean alreadyStored) {
        this.session = session;
        this.mode = mode;
        this.alreadyStored = alreadyStored;
    }


    public Boolean getAlreadyStored() {
        return alreadyStored;
    }


    public CreationModeEnum getMode() {
        return mode;
    }


    public DefaultSession getSession() {
        return session;
    }
}
