package io.octopus.broker.security;

import io.octopus.kernel.kernel.security.IRWController;
import io.octopus.kernel.kernel.subscriptions.Topic;

import java.text.ParseException;
import java.util.*;

/**
 * Used by the ACLFileParser to push all authorizations it finds. ACLAuthorizator uses it in read
 * mode to check it topics matches the ACLs.
 * <p>
 * Not thread safe.
 */
class AuthorizationsCollector implements IRWController {

    private List<Authorization> authorizationArrayList = new ArrayList<>();
    private List<Authorization> mPatternAuthorizations = new ArrayList<>();
    private Map<String, List<Authorization>> mUserAuthorizations = new HashMap<>();
    private boolean mParsingUsersSpecificSection;
    private boolean mParsingPatternSpecificSection;
    private String mCurrentUser = "";

    static final AuthorizationsCollector emptyImmutableCollector() {
        AuthorizationsCollector coll = new AuthorizationsCollector();
        coll.authorizationArrayList = Collections.emptyList();
        coll.mPatternAuthorizations = Collections.emptyList();
        coll.mUserAuthorizations = Collections.emptyMap();
        return coll;
    }

    void parse(String line) throws ParseException {
        Authorization acl = parseAuthLine(line);
        if (acl == null) {
            // skip it's a user
            return;
        }
        if (mParsingUsersSpecificSection) {
            mUserAuthorizations.putIfAbsent(mCurrentUser, new ArrayList<>());
            List<Authorization> userAuths = mUserAuthorizations.get(mCurrentUser);
            userAuths.add(acl);
        } else if (mParsingPatternSpecificSection) {
            mPatternAuthorizations.add(acl);
        } else {
            authorizationArrayList.add(acl);
        }
    }

    protected Authorization parseAuthLine(String line) throws ParseException {
        String[] tokens = line.split("\\s+");
        String keyword = tokens[0].toLowerCase();
        switch (keyword) {
            case "topic":
                return createAuthorization(line, tokens);
            case "user":
                mParsingUsersSpecificSection = true;
                mCurrentUser = tokens[1];
                mParsingPatternSpecificSection = false;
                return null;
            case "pattern":
                mParsingUsersSpecificSection = false;
                mCurrentUser = "";
                mParsingPatternSpecificSection = true;
                return createAuthorization(line, tokens);
            default:
                throw new ParseException(String.format("invalid line definition found %s", line), 1);
        }
    }

    private Authorization createAuthorization(String line, String[] tokens) throws ParseException {
        if (tokens.length > 2) {
            // if the tokenized lines has 3 token the second must be the permission
            try {
                Authorization.Permission permission = Authorization.Permission.valueOf(tokens[1].toUpperCase());
                // bring topic with all original spacing
                Topic topic = new Topic(line.substring(line.indexOf(tokens[2])));

                return new Authorization(topic, permission);
            } catch (IllegalArgumentException iaex) {
                throw new ParseException("invalid permission token", 1);
            }
        }
        Topic topic = new Topic(tokens[1]);
        return new Authorization(topic);
    }

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        return canDoOperation(topic, Authorization.Permission.WRITE, user, client);
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        return canDoOperation(topic, Authorization.Permission.READ, user, client);
    }

    private boolean canDoOperation(Topic topic, Authorization.Permission permission, String username, String client) {
        if (matchACL(authorizationArrayList, topic, permission)) {
            return true;
        }

        if (isNotEmpty(client) || isNotEmpty(username)) {
            for (Authorization auth : mPatternAuthorizations) {
                if (auth.grant(permission)) {
                    Topic substitutedTopic = new Topic(auth.topic.toString().replace("%c", client).replace("%u", username));
                    if (topic.match(substitutedTopic)) {
                        return true;
                    }
                }
            }
        }

        if (isNotEmpty(username)) {
            if (mUserAuthorizations.containsKey(username)) {
                List<Authorization> auths = mUserAuthorizations.get(username);
                if (matchACL(auths, topic, permission)) {
                    return true;
                }
            }
        }
        return false;
    }


    //TODO 权限匹配不完善，不能做到模糊匹配
    private boolean matchACL(List<Authorization> auths, Topic topic, Authorization.Permission permission) {
        for (Authorization auth : auths) {
            if (auth.grant(permission)) {
                if (auth.topic.toString().equals("*")) {
                    return true;
                }
                if (topic.match(auth.topic)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNotEmpty(String client) {
        return client != null && !client.isEmpty();
    }

    public boolean isEmpty() {
        return authorizationArrayList.isEmpty();
    }
}
