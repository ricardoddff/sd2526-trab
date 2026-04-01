package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Users;
import sd2526.trab.server.clients.ClientFactory;
import sd2526.trab.server.clients.GenericClientFactory;
import sd2526.trab.server.clients.RestMessagesClient;
import sd2526.trab.server.clients.RestUsersClient;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.server.rest.MessagesResource;
import sd2526.trab.server.services.MessagesService;

public class MessagesServer {
    private static final Logger Log = Logger.getLogger(MessagesServer.class.getName());

    public static void main(String[] args) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "fct";

            Hibernate.getInstance();

            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format("http://%s:8081/rest", ip);

            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, Messages.SERVICE_NAME, domain, serverURI);
            discovery.start();

            // Fábricas
            ClientFactory<Messages> msgFactory = new GenericClientFactory<>(Messages.SERVICE_NAME, discovery, RestMessagesClient::new);
            ClientFactory<Users> usersFactory = new GenericClientFactory<>(Users.SERVICE_NAME, discovery, RestUsersClient::new);

            MessagesService service = new MessagesService(domain, msgFactory, usersFactory);

            // 1. Injetar as dependências estaticamente
            MessagesResource.setService(service);

            ResourceConfig config = new ResourceConfig();

            // 2. Registar a CLASSE no Jersey
            config.register(MessagesResource.class);

            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);
            Log.info("Messages Server ready for domain: " + domain);

        } catch (Exception e) { e.printStackTrace(); }
    }
}