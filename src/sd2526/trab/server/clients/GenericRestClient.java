package sd2526.trab.server.clients;

import java.net.URI;
import java.util.logging.Logger;
import java.util.function.Supplier;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.GenericType;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;

public abstract class GenericRestClient {
    private static final Logger Log = Logger.getLogger(GenericRestClient.class.getName());

    protected static final int READ_TIMEOUT = 5000;
    protected static final int CONNECT_TIMEOUT = 5000;
    protected static final int MAX_RETRIES = 20;
    protected static final int RETRY_SLEEP = 3000;

    protected final WebTarget target;
    protected final Client client;

    public GenericRestClient(URI serverURI, String path) {
        ClientConfig config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);

        this.client = ClientBuilder.newClient(config);
        this.target = client.target(serverURI).path(path);
    }

    /**
     * Executa uma operação com lógica de re-tentativa em caso de falha de rede.
     */
    protected <T> Result<T> reTry(Supplier<Result<T>> func) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return func.get();
            } catch (ProcessingException x) {
                Log.fine("Timeout ou falha de rede... a tentar novamente (" + (i + 1) + ")");
                try { Thread.sleep(RETRY_SLEEP); } catch (InterruptedException ignored) {}
            } catch (Exception x) {
                x.printStackTrace();
                return Result.error(ErrorCode.INTERNAL_ERROR);
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    /**
     * Converte uma Resposta HTTP num Result<T> do domínio.
     */
    protected <T> Result<T> verifyResponse(Response r, Class<T> clazz) {
        try {
            if (r.getStatus() == Status.OK.getStatusCode()) {
                return Result.ok(r.readEntity(clazz));
            } else if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                return Result.ok(null);
            }
            return Result.error(getErrorCodeFrom(r.getStatus()));
        } finally {
            r.close();
        }
    }

    /**
     * Versão para listas e tipos genéricos.
     */
    protected <T> Result<T> verifyResponse(Response r, GenericType<T> type) {
        try {
            if (r.getStatus() == Status.OK.getStatusCode()) {
                return Result.ok(r.readEntity(type));
            } else if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                return Result.ok(null);
            }
            return Result.error(getErrorCodeFrom(r.getStatus()));
        } finally {
            r.close();
        }
    }

    public static ErrorCode getErrorCodeFrom(int status) {
        return switch (status) {
            case 200, 204 -> ErrorCode.OK;
            case 400 -> ErrorCode.BAD_REQUEST;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 501 -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}