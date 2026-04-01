package sd2526.trab.server.clients;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.Messages.*;

public class GrpcMessagesClient implements Messages {

    private static final Logger Log = Logger.getLogger(GrpcMessagesClient.class.getName());
    protected static final int MAX_RETRIES = 15;
    protected static final int RETRY_SLEEP = 3000;

    private final ManagedChannel channel;
    private final GrpcMessagesGrpc.GrpcMessagesBlockingStub stub;

    public GrpcMessagesClient(URI serverURI) {
        this.channel = ManagedChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort())
                .usePlaintext()
                .build();
        this.stub = GrpcMessagesGrpc.newBlockingStub(channel);
    }

    // --- Tradutores ---
    private Message grpcMsgToJava(GrpcMessage gm) {
        Message m = new Message(
                gm.getId().isEmpty() ? null : gm.getId(),
                gm.getSender().isEmpty() ? null : gm.getSender(),
                new HashSet<>(gm.getDestinationList()),
                gm.getSubject().isEmpty() ? null : gm.getSubject(),
                gm.getContents().isEmpty() ? null : gm.getContents()
        );
        m.setCreationTime(gm.getCreationTime());
        return m;
    }

    private GrpcMessage javaMsgToGrpc(Message m) {
        GrpcMessage.Builder b = GrpcMessage.newBuilder()
                .setSender(m.getSender() == null ? "" : m.getSender())
                .setSubject(m.getSubject() == null ? "" : m.getSubject())
                .setContents(m.getContents() == null ? "" : m.getContents())
                .setCreationTime(m.getCreationTime());

        if (m.getId() != null) b.setId(m.getId());
        if (m.getDestination() != null) b.addAllDestination(m.getDestination());

        return b.build();
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

    // --- Lógica de Retry Genérica ---
    interface GrpcOperation<T> {
        T execute() throws StatusRuntimeException;
    }

    private <T> Result<T> retryOperation(GrpcOperation<Result<T>> op) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return op.execute();
            } catch (StatusRuntimeException e) {
                Status status = e.getStatus();
                if (status.getCode() == Status.Code.UNAVAILABLE || status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                    Log.fine("Falha de rede gRPC, a tentar novamente (" + (i + 1) + ")...");
                    try { Thread.sleep(RETRY_SLEEP); } catch (InterruptedException ignored) {}
                } else {
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
    public Result<String> postMessage(String pwd, Message msg) {
        return retryOperation(() -> {
            PostMessageArgs args = PostMessageArgs.newBuilder().setPwd(pwd).setMessage(javaMsgToGrpc(msg)).build();
            PostMessageResult response = stub.postMessage(args);
            return Result.ok(response.getMid());
        });
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        return retryOperation(() -> {
            GetInboxMessageArgs args = GetInboxMessageArgs.newBuilder().setName(name).setMid(mid).setPwd(pwd).build();
            GrpcMessage response = stub.getInboxMessage(args);
            return Result.ok(grpcMsgToJava(response));
        });
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return retryOperation(() -> {
            GetAllInboxMessagesArgs args = GetAllInboxMessagesArgs.newBuilder().setName(name).setPwd(pwd).build();
            GetAllInboxMessagesResult response = stub.getAllInboxMessages(args);
            return Result.ok(response.getMidsList()); // Retorna logo a lista!
        });
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return retryOperation(() -> {
            RemoveInboxMessageArgs args = RemoveInboxMessageArgs.newBuilder().setName(name).setMid(mid).setPwd(pwd).build();
            stub.removeInboxMessage(args);
            return Result.ok();
        });
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return retryOperation(() -> {
            DeleteMessageArgs args = DeleteMessageArgs.newBuilder().setName(name).setMid(mid).setPwd(pwd).build();
            stub.deleteMessage(args);
            return Result.ok();
        });
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        return retryOperation(() -> {
            SearchInboxArgs args = SearchInboxArgs.newBuilder().setName(name).setPwd(pwd).setQuery(query).build();
            SearchInboxResult response = stub.searchInbox(args);
            return Result.ok(response.getMidsList());
        });
    }
}