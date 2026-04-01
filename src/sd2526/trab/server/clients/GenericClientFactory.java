package sd2526.trab.server.clients;

import java.net.URI;
import java.util.function.Function;
import java.util.logging.Logger;

import sd2526.trab.server.Discovery;

public class GenericClientFactory<T> implements ClientFactory<T> {

    private static final Logger Log = Logger.getLogger(GenericClientFactory.class.getName());

    private final String serviceName;
    private final Discovery discovery;
    private final Function<URI, T> restClientFunc;
    private final Function<URI, T> grpcClientFunc;

    // Novo construtor que aceita os dois protocolos!
    public GenericClientFactory(String serviceName, Discovery discovery, Function<URI, T> restClientFunc, Function<URI, T> grpcClientFunc) {
        this.serviceName = serviceName;
        this.discovery = discovery;
        this.restClientFunc = restClientFunc;
        this.grpcClientFunc = grpcClientFunc;
    }

    // Construtor antigo para não partir código existente (retrocompatibilidade)
    public GenericClientFactory(String serviceName, Discovery discovery, Function<URI, T> restClientFunc) {
        this(serviceName, discovery, restClientFunc, null);
    }

    @Override
    public T get(String domain) {
        URI[] uris = discovery.knownUrisOf(serviceName, domain);

        if (uris.length == 0) {
            Log.warning("Nenhum servidor encontrado para " + serviceName + "@" + domain);
            return null;
        }

        // Pega no primeiro servidor disponível
        URI serverURI = uris[0];

        // Decide qual cliente usar com base no esquema do URI!
        if (serverURI.getScheme().equals("grpc") && grpcClientFunc != null) {
            return grpcClientFunc.apply(serverURI);
        } else if (serverURI.getScheme().equals("http") && restClientFunc != null) {
            return restClientFunc.apply(serverURI);
        }

        throw new RuntimeException("Protocolo não suportado ou cliente não configurado: " + serverURI);
    }
}