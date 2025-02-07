package dev.soffa.foundation.multitenancy;

import dev.soffa.foundation.commons.Logger;
import dev.soffa.foundation.commons.TextUtil;
import dev.soffa.foundation.model.TenantId;
import lombok.SneakyThrows;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@SuppressWarnings("PMD.ClassNamingConventions")
public final class TenantHolder {

    private static final Logger LOG = Logger.get(TenantHolder.class);

    private static final ThreadLocal<String> CURRENT = new InheritableThreadLocal<>();

    private TenantHolder() {
    }

    public static void clear() {
        set((String) null);
    }

    public static boolean isDefault() {
        String value = CURRENT.get();
        return TextUtil.isEmpty(value) || TenantId.DEFAULT_VALUE.equalsIgnoreCase(value);
    }

    public static Optional<String> get() {
        if (isEmpty(CURRENT.get())) {
            return Optional.empty();
        }
        return Optional.of(CURRENT.get());
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }


    public static void set(TenantId tenantId) {
        if (tenantId == null) {
            set((String) null);
        } else {
            set(tenantId.getValue());
        }
    }


    public static void set(String value) {
        Logger.setTenantId(value);
        if (TextUtil.isEmpty(value)) {
            LOG.trace("Active tenant is set to: default");
            CURRENT.remove();
        } else {
            LOG.trace("Active tenant is set to: %s", value);
            CURRENT.set(value);
        }
    }

    public static <T> T useDefault(Supplier<T> supplier) {
        return use((String) null, supplier);
    }

    public static void use(final String tenantId, Runnable runnable) {
        use(TenantId.of(tenantId), runnable);
    }

    @SneakyThrows
    public static void use(final TenantId tenantId, Runnable runnable) {
        use(tenantId, () -> {
            runnable.run();
            return null;
        });
    }


    @SneakyThrows
    public static <O> O use(final TenantId tenantId, Supplier<O> supplier) {
        String t = tenantId == null ? null : tenantId.getValue();
        return use(t, supplier);
    }

    @SneakyThrows
    public static <O> O use(final String tenantId, Supplier<O> supplier) {
        String current = CURRENT.get();
        if (Objects.equals(tenantId, current)) {
            return supplier.get();
        }
        if (TextUtil.isNotEmpty(current)) {
            LOG.trace("Tenant switch %s --> %s", current, tenantId);
        }
        try {
            set(tenantId);
            return supplier.get();
        } finally {
            if (TextUtil.isNotEmpty(current)) {
                LOG.trace("Tenant restored %s --> %s", tenantId, current);
                set(current);
            }
        }
    }

    public static boolean isNotEmpty() {
        return TextUtil.isNotEmpty(CURRENT.get());
    }

    public static boolean isEmpty() {
        return TextUtil.isEmpty(CURRENT.get());
    }
}
