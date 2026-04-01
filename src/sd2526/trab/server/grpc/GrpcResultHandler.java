package sd2526.trab.server.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;

public class GrpcResultHandler {

    // Envia a resposta de sucesso, ou atira a exceção gRPC correspondente
    public static <T, R> void handleResult(Result<T> result, StreamObserver<R> responseObserver, R responseContent) {
        if (result.isOK()) {
            responseObserver.onNext(responseContent);
            responseObserver.onCompleted();
        } else {
            Status grpcStatus = statusCodeToGrpcStatus(result.error());
            responseObserver.onError(grpcStatus.asRuntimeException());
        }
    }

    // Traduz para gRPC
    private static Status statusCodeToGrpcStatus(ErrorCode error) {
        return switch (error) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT -> Status.ALREADY_EXISTS;
            case FORBIDDEN -> Status.PERMISSION_DENIED;
            case BAD_REQUEST -> Status.INVALID_ARGUMENT;
            case TIMEOUT -> Status.DEADLINE_EXCEEDED;
            case NOT_IMPLEMENTED -> Status.UNIMPLEMENTED;
            default -> Status.INTERNAL;
        };
    }
}