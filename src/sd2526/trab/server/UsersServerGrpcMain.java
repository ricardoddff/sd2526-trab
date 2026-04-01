package sd2526.trab.server;

import java.net.InetAddress;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.logging.Logger;
import sd2526.trab.api.java.Users;
import sd2526.trab.server.grpc.UsersGrpcImpl;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.server.services.UsersService;

public class UsersServerGrpcMain {

    private static final Logger Log = Logger.getLogger(UsersServerGrpcMain.class.getName());
    public static final int PORT = 8080; // Ou a porta que definires nas properties para gRPC

    public static void main(String[] args) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "fct";

            Log.info("Starting gRPC Users Server for domain: " + domain);

            // Iniciar a DB
            Hibernate.getInstance();

            // O mesmo serviço de sempre!
            UsersService service = new UsersService(domain);

            // Em vez do ResourceConfig do Jersey, usamos o ServerBuilder do gRPC
            Server server = ServerBuilder.forPort(PORT)
                    .addService(new UsersGrpcImpl(service))
                    .build();

            server.start();

            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format("grpc://%s:%s/grpc", ip, PORT);
            Log.info("gRPC Users Server ready @ " + serverURI);

            // Anunciar o servidor no Discovery!
            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, Users.SERVICE_NAME, domain, serverURI);
            discovery.start();

            // Impedir que o programa termine
            server.awaitTermination();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}