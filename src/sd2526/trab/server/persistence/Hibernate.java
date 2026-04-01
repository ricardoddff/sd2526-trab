package sd2526.trab.server.persistence;

import java.util.List;
import java.util.logging.Logger;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

public class Hibernate {
    private static final Logger Log = Logger.getLogger(Hibernate.class.getName());
    private static Hibernate instance;
    private SessionFactory sessionFactory;

    private Hibernate() {
        try {
            final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .configure() // Isto lê o hibernate.cfg.xml
                    .build();

            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(sd2526.trab.api.User.class)    // <--- ADICIONA ISTO
                    .addAnnotatedClass(sd2526.trab.api.Message.class) // <--- ADICIONA ISTO
                    .buildMetadata()
                    .buildSessionFactory();

        } catch (Exception e) {
            Log.severe("Falha ao criar SessionFactory: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static synchronized Hibernate getInstance() {
        if (instance == null) {
            instance = new Hibernate();
        }
        return instance;
    }

    // --- OPERAÇÕES DE CRUD ---

    /**
     * Persiste um objeto novo.
     */
    public <T> void persist(T obj) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.persist(obj);
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    /**
     * Atualiza um objeto existente.
     */
    public <T> void update(T obj) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.merge(obj); // Merge é mais seguro para objetos que vêm de fora
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    /**
     * Obtém um objeto pela Chave Primária (ID).
     */
    public <T> T get(Class<T> clazz, String id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(clazz, id);
        } // A sessão fecha aqui, mas como o fetch é EAGER, os dados já lá estão
    }

    /**
     * Elimina um objeto.
     */
    public <T> void delete(T obj) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.remove(session.contains(obj) ? obj : session.merge(obj));
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    /**
     * Executa uma query JPQL e devolve uma lista de resultados.
     */
    public <T> List<T> jpql(String query, Class<T> clazz) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(query, clazz).list();
        }
    }
}