package sd2526.trab.server.rest;

import java.util.List;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;

public class MessagesResource implements RestMessages {

    // O serviço agora é estático para contornar o Jersey
    private static Messages service;

    // CONSTRUTOR VAZIO: É isto que o Jersey precisa para não ignorar a classe!
    public MessagesResource() {
    }

    public static void setService(Messages s) {
        service = s;
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

    @Override public String postMessage(String pwd, Message msg) { return resultOrThrow(service.postMessage(pwd, msg)); }
    @Override public Message getMessage(String name, String mid, String pwd) { return resultOrThrow(service.getInboxMessage(name, mid, pwd)); }
    @Override public List<String> getMessages(String name, String pwd, String query) {
        if (query == null || query.isEmpty()) return resultOrThrow(service.getAllInboxMessages(name, pwd));
        return resultOrThrow(service.searchInbox(name, pwd, query));
    }
    @Override public void removeFromUserInbox(String name, String mid, String pwd) { resultOrThrow(service.removeInboxMessage(name, mid, pwd)); }
    @Override public void deleteMessage(String name, String mid, String pwd) { resultOrThrow(service.deleteMessage(name, mid, pwd)); }
}