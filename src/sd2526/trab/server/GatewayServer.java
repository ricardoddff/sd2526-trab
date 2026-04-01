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
import sd2526.trab.server.clients.GrpcMessagesClient;
import sd2526.trab.server.clients.GrpcUsersClient;
import sd2526.trab.server.rest.GatewayMessagesResource;
import sd2526.trab.server.rest.GatewayUsersResource;

public class GatewayServer {
    private static final Logger Log = Logger.getLogger(GatewayServer.class.getName());

    public static void main(String[] args) {
        try {
            // 1. Identificar o domínio a partir do hostname
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "fct";

            // 2. Configurar o endereço do servidor Gateway (REST)
            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format("http://%s:8082/rest", ip);

            // 3. Iniciar o Discovery para encontrar outros servidores
            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, "gateway", domain, serverURI);
            discovery.start();

            // 4. Criar as Factories HÍBRIDAS (Suportam REST e gRPC)
            ClientFactory<Users> usersFactory = new GenericClientFactory<>(
                    Users.SERVICE_NAME,
                    discovery,
                    RestUsersClient::new,
                    GrpcUsersClient::new
            );

            ClientFactory<Messages> messagesFactory = new GenericClientFactory<>(
                    Messages.SERVICE_NAME,
                    discovery,
                    RestMessagesClient::new,
                    GrpcMessagesClient::new
            );

            // 5. Injetar as dependências nos recursos de forma estática
            // (Para garantir que o Jersey com construtor vazio funciona 100%)
            GatewayMessagesResource.init(domain, messagesFactory);
            GatewayUsersResource.init(domain, usersFactory);

            // 6. Configurar o Jersey
            ResourceConfig config = new ResourceConfig();

            // Registamos as CLASSES. O Jersey criará as instâncias usando o construtor vazio
            // e usará os dados que injetámos nos métodos init() acima.
            config.register(GatewayMessagesResource.class);
            config.register(GatewayUsersResource.class);

            // 7. Lançar o servidor HTTP
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);
            Log.info("Gateway Server ready for domain " + domain + " @ " + serverURI);

        } catch (Exception e) {
            Log.severe("Erro ao iniciar o Gateway Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}