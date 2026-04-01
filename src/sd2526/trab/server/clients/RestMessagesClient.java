package sd2526.trab.server.clients;

import java.net.URI;
import java.util.List;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;

public class RestMessagesClient extends GenericRestClient implements Messages {

    public RestMessagesClient(URI serverURI) {
        super(serverURI, RestMessages.PATH);
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        return reTry(() -> {
            Response r = target.queryParam(RestMessages.PWD, pwd)
                    .request().accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            return super.verifyResponse(r, String.class);
        });
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        return reTry(() -> {
            // CORRECT: add "mbox" segment
            Response r = target.path("mbox").path(name).path(mid)
                    .queryParam(RestMessages.PWD, pwd)
                    .request().accept(MediaType.APPLICATION_JSON).get();
            return super.verifyResponse(r, Message.class);
        });
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return reTry(() -> {
            // CORRECT: add "mbox" segment
            Response r = target.path("mbox").path(name)
                    .queryParam(RestMessages.PWD, pwd)
                    .request().accept(MediaType.APPLICATION_JSON).get();
            return super.verifyResponse(r, new GenericType<List<String>>() {});
        });
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return reTry(() -> {
            // CORRECT: add "mbox" segment
            Response r = target.path("mbox").path(name).path(mid)
                    .queryParam(RestMessages.PWD, pwd)
                    .request().delete();
            return super.verifyResponse(r, Void.class);
        });
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return reTry(() -> {
            // CORRECT: use path parameters as defined in RestMessages.deleteMessage
            Response r = target.path(name).path(mid)
                    .queryParam(RestMessages.PWD, pwd)
                    .request().delete();
            return super.verifyResponse(r, Void.class);
        });
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        return reTry(() -> {
            // CORRECT: add "mbox" segment
            Response r = target.path("mbox").path(name)
                    .queryParam(RestMessages.PWD, pwd)
                    .queryParam(RestMessages.QUERY, query)
                    .request().accept(MediaType.APPLICATION_JSON).get();
            return super.verifyResponse(r, new GenericType<List<String>>() {});
        });
    }
}