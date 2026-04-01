package sd2526.trab.server.rest;

import java.util.List;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.server.clients.ClientFactory;

public class GatewayMessagesResource implements RestMessages {

    // Instâncias estáticas
    private static String domain;
    private static ClientFactory<Messages> messagesFactory;

    // CONSTRUTOR VAZIO para o Jersey sorrir
    public GatewayMessagesResource() {
    }

    public static void init(String d, ClientFactory<Messages> factory) {
        domain = d;
        messagesFactory = factory;
    }

    private Messages getClient() {
        Messages client = messagesFactory.get(domain);
        if (client == null) throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
        return client;
    }

    private <T> T resultOrThrow(Result<T> result) {
        if (result == null) throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        if (result.isOK()) return result.value();
        Status status = switch (result.error()) {
            case CONFLICT -> Status.CONFLICT;
            case NOT_FOUND -> Status.NOT_FOUND;
            case FORBIDDEN -> Status.FORBIDDEN;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            case TIMEOUT -> Status.GATEWAY_TIMEOUT;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
        throw new WebApplicationException(status);
    }

    @Override public String postMessage(String pwd, Message msg) { return resultOrThrow(getClient().postMessage(pwd, msg)); }
    @Override public Message getMessage(String name, String mid, String pwd) { return resultOrThrow(getClient().getInboxMessage(name, mid, pwd)); }
    @Override public List<String> getMessages(String name, String pwd, String query) {
        if (query == null || query.isEmpty()) return resultOrThrow(getClient().getAllInboxMessages(name, pwd));
        return resultOrThrow(getClient().searchInbox(name, pwd, query));
    }
    @Override public void removeFromUserInbox(String name, String mid, String pwd) { resultOrThrow(getClient().removeInboxMessage(name, mid, pwd)); }
    @Override public void deleteMessage(String name, String mid, String pwd) { resultOrThrow(getClient().deleteMessage(name, mid, pwd)); }
}