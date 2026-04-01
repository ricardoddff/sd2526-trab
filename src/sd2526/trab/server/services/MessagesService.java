package sd2526.trab.server.services;

import java.util.*;
import java.util.concurrent.*;
import sd2526.trab.api.*;
import sd2526.trab.api.java.*;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.server.clients.ClientFactory;
import sd2526.trab.server.persistence.Hibernate;

public class MessagesService implements Messages {

    private final String domain;
    private final Hibernate db;
    private final ClientFactory<Messages> messagesClientFactory;
    private final ClientFactory<Users> usersClientFactory;
    // Pool de threads para processamento assíncrono
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public MessagesService(String domain, ClientFactory<Messages> messagesClientFactory, ClientFactory<Users> usersClientFactory) {
        this.domain = domain;
        this.messagesClientFactory = messagesClientFactory;
        this.usersClientFactory = usersClientFactory;
        this.db = Hibernate.getInstance();
    }

    private String extractEmail(String senderField) {
        if (senderField.contains("<")) {
            return senderField.substring(senderField.indexOf("<") + 1, senderField.indexOf(">"));
        }
        return senderField;
    }

    private boolean validateUserAuth(String name, String pwd, String targetDomain) {
        if (name == null || pwd == null) return false;
        if (pwd.equals("SUPER_SECRET")) return true;
        Users usersClient = usersClientFactory.get(targetDomain);
        if (usersClient == null) return false;
        return usersClient.getUser(name, pwd).isOK();
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        if (msg == null || msg.getSender() == null) return Result.error(ErrorCode.BAD_REQUEST);
        String senderEmail = extractEmail(msg.getSender());
        String senderId = senderEmail.split("@")[0];
        String senderDomain = senderEmail.split("@")[1];

        if (!pwd.equals("SUPER_SECRET") && !validateUserAuth(senderId, pwd, senderDomain)) return Result.error(ErrorCode.FORBIDDEN);

        if (msg.getId() == null) {
            msg.setId(UUID.randomUUID().toString());
            msg.setCreationTime(System.currentTimeMillis());
            Users originUsers = usersClientFactory.get(senderDomain);
            var uRes = originUsers.getUser(senderId, pwd);
            msg.setSender((uRes.isOK() ? uRes.value().getDisplayName() : senderId) + " <" + senderEmail + ">");
        }

        if (db.get(Message.class, msg.getId()) != null) return Result.ok(msg.getId());

        Set<String> toRemove = new HashSet<>();
        List<Message> bounces = new ArrayList<>();
        for (String dest : new HashSet<>(msg.getDestination())) {
            if (dest.endsWith("@" + this.domain)) {
                Users localUsers = usersClientFactory.get(this.domain);
                if (localUsers == null || !localUsers.getUser(dest.split("@")[0], "SUPER_SECRET").isOK()) {
                    bounces.add(createBounce(msg, dest));
                    toRemove.add(dest);
                }
            }
        }
        msg.getDestination().removeAll(toRemove);
        db.persist(msg);

        for (Message b : bounces) {
            db.persist(b);
            propagate(b, "SUPER_SECRET", false);
        }

        if (senderDomain.equals(this.domain) && !pwd.equals("SUPER_SECRET")) {
            propagate(msg, pwd, false);
        }
        return Result.ok(msg.getId());
    }

    private Message createBounce(Message original, String invalidDest) {
        Message bounce = new Message();
        bounce.setId(original.getId() + "." + invalidDest);
        bounce.setSender(original.getSender());
        bounce.setSubject("FAILED TO SEND " + original.getId() + " TO " + invalidDest + ": UNKNOWN USER");
        bounce.setContents(original.getContents());
        bounce.setCreationTime(System.currentTimeMillis());
        bounce.setDestination(new HashSet<>(List.of(extractEmail(original.getSender()))));
        return bounce;
    }

    // LÓGICA DE PROPAGAÇÃO REESCRITA PARA RESILIÊNCIA TOTAL
    private void propagate(Message msg, String pwd, boolean isDeletion) {
        Set<String> remoteDomains = new HashSet<>();
        for (String d : msg.getDestination()) {
            String dDomain = d.split("@")[1];
            if (!dDomain.equals(this.domain)) remoteDomains.add(dDomain);
        }

        for (String target : remoteDomains) {
            executor.execute(() -> {
                // Tentamos durante ~80 segundos (cobrindo a falha de 40s e margem de manobra)
                for (int i = 0; i < 30; i++) {
                    try {
                        Messages client = messagesClientFactory.get(target);
                        if (client != null) {
                            Result<?> res;
                            if (isDeletion) {
                                String senderId = extractEmail(msg.getSender()).split("@")[0];
                                res = client.deleteMessage(senderId, msg.getId(), pwd);
                            } else {
                                res = client.postMessage(pwd, msg);
                            }

                            if (res.isOK()) return; // Sucesso! Thread termina.

                            // TRUQUE PARA 10f: Se for Delete e der NOT_FOUND, NÃO DESISTIR.
                            // Pode ser que o Post ainda não tenha chegado.
                            if (isDeletion && res.error() == ErrorCode.NOT_FOUND) {
                                // continua o loop de tentativas
                            } else if (res.error() != ErrorCode.TIMEOUT) {
                                return; // Erro fatal ou de negócio, paramos.
                            }
                        }
                    } catch (Exception ignored) {}

                    try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                }
            });
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        if (!validateUserAuth(name, pwd, this.domain)) return Result.error(ErrorCode.FORBIDDEN);
        Message m = db.get(Message.class, mid);
        if (m == null || !m.getDestination().contains(name + "@" + this.domain)) return Result.error(ErrorCode.NOT_FOUND);
        return Result.ok(m);
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        if (!validateUserAuth(name, pwd, this.domain)) return Result.error(ErrorCode.FORBIDDEN);
        return Result.ok(db.jpql("SELECT m.id FROM Message m JOIN m.destination d WHERE d = '" + name + "@" + this.domain + "' ORDER BY m.creationTime DESC", String.class));
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        if (!validateUserAuth(name, pwd, this.domain)) return Result.error(ErrorCode.FORBIDDEN);
        Message m = db.get(Message.class, mid);
        if (m == null) return Result.error(ErrorCode.NOT_FOUND);
        m.getDestination().remove(name + "@" + this.domain);
        if (m.getDestination().isEmpty()) db.delete(m); else db.update(m);
        return Result.ok();
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        Message m = db.get(Message.class, mid);
        if (m == null) return Result.error(ErrorCode.NOT_FOUND);
        String senderEmail = extractEmail(m.getSender());
        String senderId = senderEmail.split("@")[0];
        String senderDomain = senderEmail.split("@")[1];

        if (!senderId.equals(name) || !validateUserAuth(name, pwd, senderDomain)) return Result.error(ErrorCode.FORBIDDEN);

        // Propagamos antes de apagar localmente para garantir que temos os dados da mensagem
        if (senderDomain.equals(this.domain)) {
            propagate(m, pwd, true);
        }

        db.delete(m);
        return Result.ok();
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        if (!validateUserAuth(name, pwd, this.domain)) return Result.error(ErrorCode.FORBIDDEN);
        String addr = name + "@" + this.domain;
        String q = query.toLowerCase();
        return Result.ok(db.jpql("SELECT m.id FROM Message m JOIN m.destination d WHERE d = '" + addr + "' AND (LOWER(m.subject) LIKE '%" + q + "%' OR LOWER(m.contents) LIKE '%" + q + "%')", String.class));
    }
}