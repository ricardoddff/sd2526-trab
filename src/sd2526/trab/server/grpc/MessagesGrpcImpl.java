package sd2526.trab.server.grpc;

import io.grpc.stub.StreamObserver;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.Messages.*; // Importa as classes do messages.proto
import java.util.HashSet;

public class MessagesGrpcImpl extends GrpcMessagesGrpc.GrpcMessagesImplBase {

    private final Messages service;

    public MessagesGrpcImpl(Messages service) {
        this.service = service;
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

    // --- Implementações dos Endpoints ---

    @Override
    public void postMessage(PostMessageArgs request, StreamObserver<PostMessageResult> responseObserver) {
        Result<String> res = service.postMessage(request.getPwd(), grpcMsgToJava(request.getMessage()));
        if (res.isOK()) {
            PostMessageResult response = PostMessageResult.newBuilder().setMid(res.value()).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void getInboxMessage(GetInboxMessageArgs request, StreamObserver<GrpcMessage> responseObserver) {
        Result<Message> res = service.getInboxMessage(request.getName(), request.getMid(), request.getPwd());
        if (res.isOK()) {
            GrpcResultHandler.handleResult(res, responseObserver, javaMsgToGrpc(res.value()));
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void getAllInboxMessages(GetAllInboxMessagesArgs request, StreamObserver<GetAllInboxMessagesResult> responseObserver) {
        Result<java.util.List<String>> res = service.getAllInboxMessages(request.getName(), request.getPwd());
        if (res.isOK()) {
            GetAllInboxMessagesResult response = GetAllInboxMessagesResult.newBuilder().addAllMids(res.value()).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void removeInboxMessage(RemoveInboxMessageArgs request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        Result<Void> res = service.removeInboxMessage(request.getName(), request.getMid(), request.getPwd());
        if (res.isOK()) {
            GrpcResultHandler.handleResult(res, responseObserver, com.google.protobuf.Empty.getDefaultInstance());
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void deleteMessage(DeleteMessageArgs request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        Result<Void> res = service.deleteMessage(request.getName(), request.getMid(), request.getPwd());
        if (res.isOK()) {
            GrpcResultHandler.handleResult(res, responseObserver, com.google.protobuf.Empty.getDefaultInstance());
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }

    @Override
    public void searchInbox(SearchInboxArgs request, StreamObserver<SearchInboxResult> responseObserver) {
        Result<java.util.List<String>> res = service.searchInbox(request.getName(), request.getPwd(), request.getQuery());
        if (res.isOK()) {
            SearchInboxResult response = SearchInboxResult.newBuilder().addAllMids(res.value()).build();
            GrpcResultHandler.handleResult(res, responseObserver, response);
        } else {
            GrpcResultHandler.handleResult(res, responseObserver, null);
        }
    }
}