package com.ofss.configuration.error;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/** Fail-closed Oracle diagnostic recognition; never log or return diagnostics. */
public final class OracleUniqueConstraint {
    private static final String IDENTIFIER = "(\"[^\"]+\"|[A-Za-z][A-Za-z0-9_$#]*)";
    private static final Pattern IDENTITY = Pattern.compile(
            "^\\s*ORA-00001:[^\\r\\n(]*\\(\\s*" + IDENTIFIER + "\\s*\\.\\s*"
                    + IDENTIFIER + "\\s*\\)");
    private OracleUniqueConstraint() {}

    public static boolean matches(Throwable failure, String constraint) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        boolean recognized = false;
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SQLException sql) {
                if (sql.getErrorCode() == 1) {
                    var identity = IDENTITY.matcher(sql.getMessage() == null ? "" : sql.getMessage());
                    if (!identity.find() || !"SUPPLIER_PAYMENT_APP".equals(identifier(identity.group(1)))
                            || !constraint.equals(identifier(identity.group(2)))) return false;
                    recognized = true;
                }
                if (sql.getNextException() != null) pending.addLast(sql.getNextException());
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
        }
        return recognized;
    }

    private static String identifier(String name) {
        return name.startsWith("\"") ? name.substring(1, name.length() - 1) : name.toUpperCase(Locale.ROOT);
    }
}
