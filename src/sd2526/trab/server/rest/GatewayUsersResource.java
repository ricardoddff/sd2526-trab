package sd2526.trab.server.rest;

import java.util.List;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.rest.RestUsers;
import sd2526.trab.server.clients.ClientFactory;

@Singleton
public class GatewayUsersResource implements RestUsers {

    // Campos estáticos para permitir o construtor vazio exigido pelo Jersey
    private static String domain;
    private static ClientFactory<Users> usersFactory;

    // CONSTRUTOR VAZIO: Obrigatório para o Jersey não ignorar a classe
    public GatewayUsersResource() {
    }

    // MÉTODO INIT: Chamado pelo GatewayServer no arranque
    public static void init(String d, ClientFactory<Users> factory) {
        domain = d;
        usersFactory = factory;
    }

    private Users getClient() {
        if (usersFactory == null)
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);

        Users client = usersFactory.get(domain);
        if (client == null)
            throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
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

    @Override
    public String postUser(User user) {
        return resultOrThrow(getClient().postUser(user));
    }

    @Override
    public User getUser(String name, String pwd) {
        return resultOrThrow(getClient().getUser(name, pwd));
    }

    @Override
    public User updateUser(String name, String pwd, User info) {
        return resultOrThrow(getClient().updateUser(name, pwd, info));
    }

    @Override
    public User deleteUser(String name, String pwd) {
        return resultOrThrow(getClient().deleteUser(name, pwd));
    }

    @Override
    public List<User> searchUsers(String name, String pwd, String pattern) {
        return resultOrThrow(getClient().searchUsers(name, pwd, pattern));
    }
}