package sd2526.trab.server.clients;

public interface ClientFactory<T> {
    T get(String domain);
}