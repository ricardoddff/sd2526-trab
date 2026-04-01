package sd2526.trab.server.rest;

import java.util.List;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.rest.RestUsers;

@Path(RestUsers.PATH)
public class UsersResource implements RestUsers {

    private final Users service;

    public UsersResource(Users service) {
        this.service = service;
    }

    private <T> T resultOrThrow(Result<T> result) {
        if (result.isOK()) return result.value();

        Status status = switch (result.error()) {
            case CONFLICT -> Status.CONFLICT;
            case NOT_FOUND -> Status.NOT_FOUND;
            case FORBIDDEN -> Status.FORBIDDEN;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
        throw new WebApplicationException(status);
    }

    @Override public String postUser(User user) { return resultOrThrow(service.postUser(user)); }
    @Override public User getUser(String name, String pwd) { return resultOrThrow(service.getUser(name, pwd)); }
    @Override public User updateUser(String name, String pwd, User info) { return resultOrThrow(service.updateUser(name, pwd, info)); }
    @Override public User deleteUser(String name, String pwd) { return resultOrThrow(service.deleteUser(name, pwd)); }
    @Override public List<User> searchUsers(String name, String pwd, String pattern) { return resultOrThrow(service.searchUsers(name, pwd, pattern)); }
}