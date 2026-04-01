package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Users;
import sd2526.trab.server.clients.ClientFactory;
import sd2526.trab.server.clients.GenericClientFactory;
import sd2526.trab.server.clients.GrpcMessagesClient;
import sd2526.trab.server.clients.GrpcUsersClient;
import sd2526.trab.server.clients.RestMessagesClient;
import sd2526.trab.server.clients.RestUsersClient;
import sd2526.trab.server.grpc.MessagesGrpcImpl;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.server.services.MessagesService;

public class MessagesServerGrpcMain {

    private static final Logger Log = Logger.getLogger(MessagesServerGrpcMain.class.getName());
    public static final int PORT = 8081;

    public static void main(String[] args) {
        try {
            // 1. Identificar o domínio
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "fct";

            Log.info("Starting gRPC Messages Server for domain: " + domain);

            // 2. Iniciar a persistência
            Hibernate.getInstance();

            // 3. Iniciar o Discovery
            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format("grpc://%s:%s/grpc", ip, PORT);
            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, Messages.SERVICE_NAME, domain, serverURI);
            discovery.start();

            // 4. Configurar as Factories para comunicação inter-servidores
            // Elas agora suportam ambos os protocolos conforme o que o Discovery encontrar
            ClientFactory<Messages> msgFactory = new GenericClientFactory<>(
                    Messages.SERVICE_NAME,
                    discovery,
                    RestMessagesClient::new,
                    GrpcMessagesClient::new
            );

            ClientFactory<Users> usersFactory = new GenericClientFactory<>(
                    Users.SERVICE_NAME,
                    discovery,
                    RestUsersClient::new,
                    GrpcUsersClient::new
            );

            // 5. Criar o serviço com a lógica de negócio
            MessagesService service = new MessagesService(domain, msgFactory, usersFactory);

            // 6. Iniciar o servidor gRPC
            Server server = ServerBuilder.forPort(PORT)
                    .addService(new MessagesGrpcImpl(service))
                    .build();

            server.start();
            Log.info("gRPC Messages Server ready @ " + serverURI);

            // 7. Manter o servidor vivo
            server.awaitTermination();

        } catch (Exception e) {
            Log.severe("Erro no servidor gRPC Messages: " + e.getMessage());
            e.printStackTrace();
        }
    }
}