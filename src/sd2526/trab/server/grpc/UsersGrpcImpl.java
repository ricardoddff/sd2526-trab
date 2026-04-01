package sd2526.trab.server.grpc;

import io.grpc.stub.StreamObserver;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.grpc.Users.*; // Importa as mensagens geradas (GrpcUser, GetUserArgs, etc)

public class UsersGrpcImpl extends GrpcUsersGrpc.GrpcUsersImplBase {

    private final Users service;

    public UsersGrpcImpl(Users service) {
        this.service = service;
    }

    // --- Tradutores ---
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

    // --- Implementações dos Endpoints ---

    @Override
    public void postUser(GrpcUser request, StreamObserver<PostUserResult> responseObserver) {
        Result<String> res = service.postUser(grpcUserToJava(request));
        if (res.isOK()) {
            PostUserResult response = PostUserResult.newBuilder().setUserAddress(res.value()).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void getUser(GetUserArgs request, StreamObserver<GetUserResult> responseObserver) {
        Result<User> res = service.getUser(request.getName(), request.getPwd());
        if (res.isOK()) {
            GetUserResult response = GetUserResult.newBuilder().setUser(javaUserToGrpc(res.value())).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void updateUser(UpdateUserArgs request, StreamObserver<UpdateUserResult> responseObserver) {
        Result<User> res = service.updateUser(request.getName(), request.getPwd(), grpcUserToJava(request.getInfo()));
        if (res.isOK()) {
            UpdateUserResult response = UpdateUserResult.newBuilder().setUser(javaUserToGrpc(res.value())).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void deleteUser(DeleteUserArgs request, StreamObserver<DeleteUserResult> responseObserver) {
        Result<User> res = service.deleteUser(request.getName(), request.getPwd());
        if (res.isOK()) {
            DeleteUserResult response = DeleteUserResult.newBuilder().setUser(javaUserToGrpc(res.value())).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void searchUsers(SearchUsersArgs request, StreamObserver<GrpcUser> responseObserver) {
        Result<java.util.List<User>> res = service.searchUsers(request.getName(), request.getPwd(), request.getQuery());
        if (res.isOK()) {
            // Em gRPC Server Streaming, fazemos um onNext por cada elemento da lista!
            for (User u : res.value()) {
                responseObserver.onNext(javaUserToGrpc(u));
            }
            responseObserver.onCompleted();
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }
}