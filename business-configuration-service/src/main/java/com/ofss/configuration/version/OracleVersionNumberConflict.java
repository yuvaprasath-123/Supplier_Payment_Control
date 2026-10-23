package com.ofss.configuration.version;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

final class OracleVersionNumberConflict {
    // Oracle exposes the vendor code separately, but the qualified constraint
    // identity is in the ORA-00001 diagnostic. Parse its first parenthesized
    // identity, not a substring elsewhere (SQL/values may appear in details).
    // The intervening text may be localized. Unknown formats fail closed.
    private static final String IDENTIFIER = "(\"[^\"]+\"|[A-Za-z][A-Za-z0-9_$#]*)";
    private static final Pattern IDENTITY = Pattern.compile(
            "^\\s*ORA-00001:[^\\r\\n(]*\\(\\s*" + IDENTIFIER + "\\s*\\.\\s*"
                    + IDENTIFIER + "\\s*\\)");

    private OracleVersionNumberConflict() {}

    static boolean matches(Throwable failure) {
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
                    if (!identity.find()
                            || !"SUPPLIER_PAYMENT_APP".equals(identifier(identity.group(1)))
                            || !"UK_CFG_VERSION_NUMBER".equals(identifier(identity.group(2)))) {
                        return false;
                    }
                    recognized = true;
                }
                if (sql.getNextException() != null) pending.addLast(sql.getNextException());
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
        }
        return recognized;
    }

    private static String identifier(String name) {
        return name.startsWith("\"") ? name.substring(1, name.length() - 1)
                : name.toUpperCase(Locale.ROOT);
    }
}
