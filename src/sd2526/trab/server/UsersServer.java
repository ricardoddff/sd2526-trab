package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Users;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.server.rest.UsersResource;
import sd2526.trab.server.services.UsersService;

public class UsersServer {

    private static final Logger Log = Logger.getLogger(UsersServer.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    public static final int PORT = 8080;
    public static final String SERVICE = Users.SERVICE_NAME;

    public static void main(String[] args) {
        try {
            // Extrair o domínio do hostname (ex: users0.ourorg -> ourorg)
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "fct";

            Log.info("Starting " + SERVICE + " Server for domain: " + domain);

            Hibernate.getInstance();
            UsersService service = new UsersService(domain);
            UsersResource resource = new UsersResource(service);

            ResourceConfig config = new ResourceConfig();
            config.register(resource);

            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format("http://%s:%s/rest", ip, PORT);

            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);
            Log.info(String.format("%s Server ready @ %s", SERVICE, serverURI));

            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, SERVICE, domain, serverURI);
            discovery.start();

        } catch (Exception e) {
            Log.severe("Erro ao arrancar o servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}