package dev.soffa.foundation.data;

import com.zaxxer.hikari.HikariDataSource;
import dev.soffa.foundation.commons.IdGenerator;
import dev.soffa.foundation.commons.TextUtil;
import dev.soffa.foundation.data.jdbi.BeanMapper;
import dev.soffa.foundation.data.jdbi.MapArgumentFactory;
import dev.soffa.foundation.data.jdbi.ObjectArgumentFactory;
import dev.soffa.foundation.data.jdbi.SerializableArgumentFactory;
import dev.soffa.foundation.error.DatabaseException;
import dev.soffa.foundation.model.TenantId;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class SimpleDataStore implements DataStore {

    private static final String TABLE = "table";
    private static final String ID_COLUMN = "idColumn";
    private static final String ID_FIELD = "idField";
    private static final String WHERE = "where";
    private static final String VALUE = "value";
    private static final String BINDING = "binding";
    private static final String COLUMNS = "columns";
    private static final String VALUES = "values";
    private final DB db;

    public SimpleDataStore(DB db) {
        this.db = db;
    }

    @Override
    public <E> E insert(TenantId tenant, @NonNull E model) {
        if (model instanceof EntityLifecycle) {
            EntityLifecycle lc = (EntityLifecycle) model;
            lc.onInsert();
            lc.onSave();
        }
        if (model instanceof EntityModel) {
            EntityModel em = (EntityModel) model;
            if (em.getCreated() == null) {
                em.setCreated(Date.from(Instant.now()));
            }
            if (TextUtil.isEmpty(em.getId())) {
                em.setId(IdGenerator.shortUUID());
            }
        }

        return inTransaction(tenant, model.getClass(), (h, info) -> {
            h.createUpdate("INSERT INTO <table> (<columns>) VALUES (<values>)")
                .define(TABLE, info.getTableName())
                .defineList(COLUMNS, info.getColumnsEscaped())
                .defineList(VALUES, info.getValuesPlaceholder())
                .bindBean(model)
                .execute();
            return model;
        });
    }

    @Override
    public <E> E update(TenantId tenant, @NonNull E model) {
        if (model instanceof EntityLifecycle) {
            EntityLifecycle lc = (EntityLifecycle) model;
            lc.onUpdate();
            lc.onSave();
        }
        return inTransaction(tenant, model.getClass(), (h, info) -> {
            h.createUpdate("UPDATE <table> SET <columns> WHERE <idColumn> = :<idField>")
                .define(TABLE, info.getTableName())
                .defineList(COLUMNS, info.getUpdatePairs())
                .defineList(ID_COLUMN, info.getIdColumn())
                .defineList(ID_FIELD, info.getIdProperty())
                .bindBean(model)
                .execute();
            return model;
        });
    }

    @Override
    public <E> int delete(TenantId tenant, E model) {
        return inTransaction(tenant, model.getClass(), (handle, info) -> {
            // EL
            return handle.createUpdate("DELETE FROM <table> WHERE <idColumn> = :<idField>")
                .define(TABLE, info.getTableName())
                .defineList(ID_COLUMN, info.getIdColumn())
                .defineList(ID_FIELD, info.getIdProperty())
                .bindBean(model)
                .execute();
        });
    }

    @Override
    public <E> int delete(TenantId tenant, @NonNull Class<E> entityClass, @NonNull Criteria criteria) {
        return inTransaction(tenant, entityClass, (handle, info) -> {
            // EL
            return handle.createUpdate("DELETE FROM <table> WHERE <where>")
                .define(TABLE, info.getTableName())
                .define(WHERE, criteria.getWhere())
                .bindMap(criteria.getBinding())
                .execute();
        });
    }

    @Override
    public <E> List<E> findAll(TenantId tenant, Class<E> entityClass) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            // EL
            return buildQuery(handle, entityClass, null)
                .map(BeanMapper.of(info)).collect(Collectors.toList());
        });
    }

    @Override
    public <E> List<E> find(TenantId tenant, Class<E> entityClass, Criteria criteria) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            //EL
            return buildQuery(handle, entityClass, criteria)
                .map(BeanMapper.of(info)).collect(Collectors.toList());
        });
    }

    @Override
    public <E> Optional<E> get(TenantId tenant, Class<E> entityClass, Criteria criteria) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            //EL
            return buildQuery(handle, entityClass, criteria)
                .map(BeanMapper.of(info)).findFirst();
        });
    }

    @Override
    public <E> Optional<E> findById(TenantId tenant, Class<E> entityClass,
                                    Object value) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            //EL
            return handle.createQuery("SELECT * FROM <table> WHERE <idColumn> = :value")
                .define(TABLE, info.getTableName())
                .define(ID_COLUMN, info.getIdColumn())
                .bind(VALUE, value)
                .map(BeanMapper.of(info)).findFirst();
        });
    }

    @Override
    public <E> long count(TenantId tenant, @NonNull Class<E> entityClass) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            //EL
            return handle.createQuery("SELECT COUNT(*) from <table>")
                .define(TABLE, info.getTableName())
                .mapTo(Long.class).first();
        });
    }

    @Override
    public <E> long count(TenantId tenant, @NonNull Class<E> entityClass, @Nullable Criteria criteria) {
        return withHandle(tenant, entityClass, (handle, info) -> {
            // EL
            return buildQuery(handle, entityClass, "SELECT COUNT(*)", criteria)
                .mapTo(Long.class).first();
        });
    }

    // =================================================================================================================

    private <E> Query buildQuery(Handle handle, Class<E> entityClass, @Nullable Criteria criteria) {
        return buildQuery(handle, entityClass, "SELECT *", criteria);
    }

    private <E> Query buildQuery(Handle handle, Class<E> entityClass, String baseQuery, @Nullable Criteria criteria) {
        EntityInfo<E> info = EntityInfo.get(entityClass, db.getTablesPrefix());
        if (criteria == null) {
            return handle.createQuery(baseQuery + " FROM <table>")
                .define(TABLE, info.getTableName());
        } else {
            return handle.createQuery(baseQuery + " FROM <table> WHERE <where>")
                .define(TABLE, info.getTableName())
                .define(WHERE, criteria.getWhere())
                .defineList(BINDING, criteria.getBinding())
                .bindMap(criteria.getBinding());
        }
    }

    private <T, E> T inTransaction(TenantId tenant,
                                   Class<E> entityClass,
                                   BiFunction<Handle, EntityInfo<E>, T> consumer) {
        try {
            EntityInfo<E> info = EntityInfo.get(entityClass, db.getTablesPrefix());
            return getLink(tenant).inTransaction(handle -> consumer.apply(handle, info));
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }

    private <T, E> T withHandle(TenantId tenant,
                                Class<E> entityClass,
                                BiFunction<Handle, EntityInfo<E>, T> consumer) {
        try {
            EntityInfo<E> info = EntityInfo.get(entityClass, db.getTablesPrefix());
            return getLink(tenant).withHandle(handle -> consumer.apply(handle, info));
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }

    private Jdbi getLink(TenantId tenant) {
        DataSource dataSource = db.determineTargetDataSource(tenant);
        Jdbi jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource))
            .installPlugin(new SqlObjectPlugin());
        if (dataSource instanceof HikariDataSource) {
            String url = ((HikariDataSource) dataSource).getJdbcUrl();
            if (url.startsWith("jdbc:postgres")) {
                jdbi.installPlugin(new PostgresPlugin());
            }
        }
        jdbi.registerArgument(new SerializableArgumentFactory());
        jdbi.registerArgument(new MapArgumentFactory());
        jdbi.registerArgument(new ObjectArgumentFactory());
        return jdbi;
    }

}
