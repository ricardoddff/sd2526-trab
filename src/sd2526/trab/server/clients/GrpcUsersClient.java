package sd2526.trab.server.clients;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.grpc.Users.*;

public class GrpcUsersClient implements Users {

    private static final Logger Log = Logger.getLogger(GrpcUsersClient.class.getName());
    protected static final int MAX_RETRIES = 4;
    protected static final int RETRY_SLEEP = 2500;

    private final ManagedChannel channel;
    private final GrpcUsersGrpc.GrpcUsersBlockingStub stub;

    public GrpcUsersClient(URI serverURI) {
        // Cria o canal de comunicação para o endereço (grpc://host:port)
        this.channel = ManagedChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort())
                .usePlaintext() // Sem TLS para os testes base
                .build();
        this.stub = GrpcUsersGrpc.newBlockingStub(channel);
    }

    // --- Tradutores (Iguais aos do Impl) ---
    private User grpcUserToJava(GrpcUser gu) {
        User u = new User();
        u.setName(gu.getName().isEmpty() ? null : gu.getName());
        if (gu.hasPwd()) u.setPwd(gu.getPwd());
        if (gu.hasDisplayName()) u.setDisplayName(gu.getDisplayName());
        if (gu.hasDomain()) u.setDomain(gu.getDomain());
        return u;
    }

    private GrpcUser javaUserToGrpc(User u) {
        GrpcUser.Builder builder = GrpcUser.newBuilder();
        if (u.getName() != null) builder.setName(u.getName());
        if (u.getPwd() != null) builder.setPwd(u.getPwd());
        if (u.getDisplayName() != null) builder.setDisplayName(u.getDisplayName());
        if (u.getDomain() != null) builder.setDomain(u.getDomain());
        return builder.build();
    }

    private ErrorCode statusToErrorCode(Status status) {
        return switch (status.getCode()) {
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCode.CONFLICT;
            case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
            case INVALID_ARGUMENT -> ErrorCode.BAD_REQUEST;
            case UNIMPLEMENTED -> ErrorCode.NOT_IMPLEMENTED;
            case DEADLINE_EXCEEDED, UNAVAILABLE -> ErrorCode.TIMEOUT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    // --- Lógica de Retry Genérica para gRPC ---
    interface GrpcOperation<T> {
        T execute() throws StatusRuntimeException;
    }

    private <T> Result<T> retryOperation(GrpcOperation<Result<T>> op) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return op.execute();
            } catch (StatusRuntimeException e) {
                Status status = e.getStatus();
                // Se o erro for de rede/disponibilidade, faz retry
                if (status.getCode() == Status.Code.UNAVAILABLE || status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                    Log.fine("Falha de rede gRPC, a tentar novamente (" + (i + 1) + ")...");
                    try { Thread.sleep(RETRY_SLEEP); } catch (InterruptedException ignored) {}
                } else {
                    // Erros de negócio (404, 403, 409) são devolvidos imediatamente
                    return Result.error(statusToErrorCode(status));
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Result.error(ErrorCode.INTERNAL_ERROR);
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    // --- Endpoints ---

    @Override
    public Result<String> postUser(User user) {
        return retryOperation(() -> {
            PostUserResult response = stub.postUser(javaUserToGrpc(user));
            return Result.ok(response.getUserAddress());
        });
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        return retryOperation(() -> {
            GetUserArgs args = GetUserArgs.newBuilder().setName(name).setPwd(pwd).build();
            GetUserResult response = stub.getUser(args);
            return Result.ok(grpcUserToJava(response.getUser()));
        });
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User info) {
        return retryOperation(() -> {
            UpdateUserArgs args = UpdateUserArgs.newBuilder().setName(name).setPwd(pwd).setInfo(javaUserToGrpc(info)).build();
            UpdateUserResult response = stub.updateUser(args);
            return Result.ok(grpcUserToJava(response.getUser()));
        });
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        return retryOperation(() -> {
            DeleteUserArgs args = DeleteUserArgs.newBuilder().setName(name).setPwd(pwd).build();
            DeleteUserResult response = stub.deleteUser(args);
            return Result.ok(grpcUserToJava(response.getUser()));
        });
    }

    @Override
    public Result<List<User>> searchUsers(String name, String pwd, String pattern) {
        return retryOperation(() -> {
            SearchUsersArgs args = SearchUsersArgs.newBuilder().setName(name).setPwd(pwd).setQuery(pattern).build();
            // Server Streaming: O gRPC devolve um iterador de respostas!
            Iterator<GrpcUser> iterator = stub.searchUsers(args);
            List<User> list = new ArrayList<>();
            while (iterator.hasNext()) {
                list.add(grpcUserToJava(iterator.next()));
            }
            return Result.ok(list);
        });
    }
}