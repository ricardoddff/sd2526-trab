package sd2526.trab.server.services;

import java.util.List;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.server.persistence.Hibernate;

public class UsersService implements Users {

    private final Hibernate db;
    private final String domain;

    public UsersService(String domain) {
        this.domain = domain;
        this.db = Hibernate.getInstance();
    }

    @Override
    public Result<String> postUser(User user) {
        if (user == null || user.getName() == null || user.getPwd() == null || user.getDomain() == null) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User existingUser = db.get(User.class, user.getName());
        if (existingUser != null) {
            // Teste de Idempotência rigoroso:
            // A password, o displayName e o domain têm de ser IGUAIS para ser considerado um retry (200).
            if (existingUser.getPwd().equals(user.getPwd()) &&
                    existingUser.getDisplayName().equals(user.getDisplayName()) &&
                    existingUser.getDomain().equals(user.getDomain())) {
                return Result.ok(user.getName() + "@" + user.getDomain());
            } else {
                // Se algum dado for diferente, é um Conflito (409).
                return Result.error(ErrorCode.CONFLICT);
            }
        }

        db.persist(user);
        return Result.ok(user.getName() + "@" + user.getDomain());
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        if (name == null || pwd == null) return Result.error(ErrorCode.BAD_REQUEST);

        User user = db.get(User.class, name);

        if (user == null) {
            // Se for o MessagesServer a perguntar internamente, damos a verdade (404)
            if (pwd.equals("SUPER_SECRET")) return Result.error(ErrorCode.NOT_FOUND);

            // Se for um cliente normal, bloqueamos por segurança (403)
            return Result.error(ErrorCode.FORBIDDEN);
        }

        if (!pwd.equals("SUPER_SECRET") && !user.getPwd().equals(pwd)) {
            return Result.error(ErrorCode.FORBIDDEN);
        }

        return Result.ok(user);
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User info) {
        if (info == null) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        // O VERDADEIRO MOTIVO DO 400: Tentativa de alterar campos imutáveis!
        // Se o cliente enviar um 'name' que não seja null e seja diferente do atual, é proibido.
        if (info.getName() != null && !info.getName().equals(name)) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        // O mesmo para o domínio.
        if (info.getDomain() != null && !info.getDomain().equals(this.domain)) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        // Valida as credenciais
        Result<User> res = getUser(name, pwd);
        if (!res.isOK()) return res;

        User user = res.value();
        boolean changed = false;

        // Se trouxer password nova, atualiza
        if (info.getPwd() != null && !info.getPwd().isEmpty()) {
            user.setPwd(info.getPwd());
            changed = true;
        }
        // Se trouxer displayName novo, atualiza
        if (info.getDisplayName() != null && !info.getDisplayName().isEmpty()) {
            user.setDisplayName(info.getDisplayName());
            changed = true;
        }

        // Se algo mudou, grava na base de dados. Se não mudou nada (tudo null),
        // o changed fica false e ele simplesmente devolve o user com 200 OK!
        if (changed) {
            db.update(user);
        }

        return Result.ok(user);
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        Result<User> res = getUser(name, pwd);
        if (!res.isOK()) return res;

        db.delete(res.value());
        return Result.ok(res.value());
    }

    @Override
    public Result<List<User>> searchUsers(String name, String pwd, String pattern) {
        Result<User> res = getUser(name, pwd);
        if (!res.isOK()) return Result.error(res.error());

        String query = "SELECT u FROM User u WHERE LOWER(u.name) LIKE '%" + pattern.toLowerCase() + "%'";
        List<User> list = db.jpql(query, User.class);
        return Result.ok(list);
    }
}